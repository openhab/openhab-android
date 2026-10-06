// Copyright (c) 2010-2026 Contributors to the openHAB project
//
// See the NOTICE file(s) distributed with this work for additional
// information.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0
//
// SPDX-License-Identifier: EPL-2.0

// OHBridge shim. Speaks the host bridge protocol (docs/mainui-bridge) for Main UI versions that
// predate it, and for pages that aren't Main UI at all. Plain JS with no build step, so the
// Android app can inject the same file.
//
// Runs at document start, after the host has installed window.OHBridge. Reads:
//   OHBridge.info               startup data from the host (features, initialHistory, layout, …)
//   window.OHBridgeShimConfig   { basePath }: what Main UI's addresses hang off, no trailing slash
//
// Stands down as soon as Main UI announces itself with ui.hello { impl: 'mainui' }.
;(function () {
  'use strict'

  var bridge = window.OHBridge
  if (!bridge || window.__ohBridgeShim) return
  window.__ohBridgeShim = true

  // Android: the object addWebMessageListener creates may not take extra properties, see README
  var info = bridge.info || window.OHBridgeInfo || {}
  var config = window.OHBridgeShimConfig || {}
  var BASE = config.basePath || ''
  var SHIM_FEATURES = ['navbar', 'menu', 'routeRestore']
  var accepted = (info.features || []).filter(function (f) {
    return SHIM_FEATURES.indexOf(f) >= 0
  })
  var layout = info.layout || {}
  var active = true
  var helloSent = false

  function wants(feature) {
    return accepted.indexOf(feature) >= 0
  }

  // ---------------------------------------------------------------------------
  // Transport
  // ---------------------------------------------------------------------------

  var hostPost = bridge.postMessage

  function post(msg) {
    msg.v = 1
    try {
      hostPost.call(bridge, JSON.stringify(msg))
    } catch (e) {}
  }

  function send(type, payload) {
    if (active) post({ type: type, payload: payload || {} })
  }

  function reply(id, result) {
    if (id) post({ type: 'reply', replyTo: id, payload: { ok: true, result: result } })
  }

  function fail(id, code, message) {
    if (id) post({ type: 'reply', replyTo: id, payload: { ok: false, error: { code: code, message: message } } })
  }

  // A Main UI that speaks the bridge says so in its own ui.hello. Watch for it and get out of the way.
  try {
    bridge.postMessage = function (json) {
      try {
        var msg = JSON.parse(json)
        if (msg && msg.type === 'ui.hello' && msg.payload && msg.payload.impl === 'mainui') standDown()
      } catch (e) {}
      return hostPost.call(bridge, json)
    }
  } catch (e) {}

  bridge.onmessage = function (event) {
    var msg
    try {
      msg = JSON.parse(event.data)
    } catch (e) {
      return
    }
    if (active && msg && msg.type && msg.type !== 'reply') handle(msg)
  }

  function standDown() {
    active = false
    removeStyle('oh-bridge-navbar-style')
    removeStyle('oh-bridge-menu-style')
    removeStyle('oh-bridge-layout-style')
    document.querySelectorAll('.' + PROXIED_CLASS).forEach(function (el) {
      el.classList.remove(PROXIED_CLASS)
    })
  }

  // ---------------------------------------------------------------------------
  // Styles
  // ---------------------------------------------------------------------------

  function setStyle(id, css) {
    var el = document.getElementById(id)
    if (!el) {
      el = document.createElement('style')
      el.id = id
      ;(document.head || document.documentElement).appendChild(el)
    }
    if (el.textContent !== css) el.textContent = css
  }

  function removeStyle(id) {
    var el = document.getElementById(id)
    if (el) el.remove()
  }

  // Leave Main UI's navbar in the layout — Framework7 sizes pages and absolutely positioned
  // content (the map page) from --f7-navbar-height — and hide only what the host's bar draws.
  // opacity, not visibility, because the labels below are read with innerText. A stylesheet
  // rule, because Framework7 writes inline opacity on .navbar-bg and .title.
  var PROXIED_CLASS = 'oh-navbar-proxied'
  var navbarCSS =
    '.' + PROXIED_CLASS + ' > .navbar-bg,' +
    '.' + PROXIED_CLASS + ' > .navbar-inner > .left,' +
    '.' + PROXIED_CLASS + ' > .navbar-inner > .title,' +
    '.' + PROXIED_CLASS + ' > .navbar-inner > .nav-title,' +
    '.' + PROXIED_CLASS + ' > .navbar-inner > .right' +
    '{opacity:0 !important;pointer-events:none !important}' +
    // A panel opens under the host's bar. Framework7 positions panels from
    // --f7-appbar-app-offset for exactly this; scoped to panels because the views read it too.
    '.panel,.panel-backdrop{--f7-appbar-app-offset:var(--f7-navbar-height)}'

  // The host shows the sidebar in its own menu, so Main UI's left panel goes. Framework7 moves the
  // main view over for a panel pinned open on wide screens; put it back.
  var menuCSS =
    '.panel-left,.panel-backdrop{display:none !important}' +
    '.view-main,.framework7-root > .view,.framework7-root > .views{margin-left:0 !important}'

  // The host owns the bar height. Main UI's own navbar stays the same size underneath it.
  function applyLayout(next) {
    layout = next || {}
    var h = Number(layout.navbarHeight)
    if (h > 0) {
      setStyle(
        'oh-bridge-layout-style',
        ':root,html.ios,html.md,html.aurora{--f7-navbar-height:' + h + 'px !important}'
      )
    }
    syncDocumentPadding(true)
    scheduleEditorFix()
  }

  if (wants('navbar')) setStyle('oh-bridge-navbar-style', navbarCSS)
  if (wants('menu')) setStyle('oh-bridge-menu-style', menuCSS)
  applyLayout(layout)

  // ---------------------------------------------------------------------------
  // Page kind and readiness
  // ---------------------------------------------------------------------------

  function isMainUIDocument() {
    return !!(document.querySelector('.framework7-root, .view-main') || document.getElementById('app'))
  }

  function router() {
    var el = document.querySelector('.view-main')
    return el && el.f7View ? el.f7View.router : null
  }

  function activePage() {
    return document.querySelector('.view-main .page-current') || document.querySelector('.page-current')
  }

  // ---------------------------------------------------------------------------
  // Navbar
  // ---------------------------------------------------------------------------

  // Framework7 keeps closed popups mounted, and a page can hold one (the code editor's "Parse
  // Errors" popup sits inside the Thing page), so only count an overlay that is open. Panels are
  // never mirrored.
  function isUsableNavbar(navbar) {
    if (navbar.closest('.panel')) return false
    var overlay = navbar.closest('.popup, .sheet-modal, .dialog, .actions-modal, .login-screen')
    return !overlay || overlay.classList.contains('modal-in')
  }

  function firstUsableNavbar(selectors) {
    for (var i = 0; i < selectors.length; i++) {
      var found = document.querySelectorAll(selectors[i])
      for (var j = 0; j < found.length; j++) {
        if (isUsableNavbar(found[j])) return found[j]
      }
    }
    return null
  }

  // A page's navbar is a direct child of .page, so '> .navbar' cannot match a popup inside it.
  function pageNavbar() {
    return firstUsableNavbar([
      '.view-main .page-current > .navbar',
      '.view-main .navbar.navbar-current',
      '.page-current > .navbar'
    ])
  }

  // The frontmost navbar: an open popup or sheet wins over the page.
  function activeNavbar() {
    return firstUsableNavbar([
      '.popup.modal-in .navbar',
      '.sheet-modal.modal-in .navbar',
      '.view-main .page-current > .navbar',
      '.view-main .navbar.navbar-current',
      '.page-current > .navbar',
      '.navbar:not(.navbar-hidden)',
      '.navbar'
    ])
  }

  // Only mirror what is on screen — activeNavbar()'s fallbacks can reach a previous page that is
  // still mounted. On the iOS theme Framework7 lifts navbars out of pages into .navbars, so
  // containment alone rejects them all.
  function ownsBar(navbar) {
    if (!navbar) return false
    if (navbar.closest('.popup.modal-in, .sheet-modal.modal-in')) return true
    if (navbar.matches('.navbar-current')) return true
    if (navbar.matches('.navbar-previous, .navbar-next, .stacked')) return false
    var page = activePage()
    return !page || page.contains(navbar)
  }

  // Guard every write. classList.add/remove rewrite the attribute even when nothing changes,
  // which the MutationObserver below sees, so an unguarded write loops forever.
  function markProxied(navbar) {
    document.querySelectorAll('.' + PROXIED_CLASS).forEach(function (el) {
      if (el !== navbar) el.classList.remove(PROXIED_CLASS)
    })
    if (navbar && !navbar.classList.contains(PROXIED_CLASS)) navbar.classList.add(PROXIED_CLASS)
  }

  // A hideNavbar page reserves no room for the host's bar, so pad it and push the floating icons
  // down. Reversible, in case the page gains a navbar later.
  function syncPagePadding(hasNavbar) {
    var page = activePage()
    if (!page) return
    var needsPadding = !hasNavbar
    if (needsPadding === !!page.__ohPadded) return
    page.__ohPadded = needsPadding
    var offset = needsPadding ? 'calc(var(--f7-navbar-height) + var(--f7-safe-area-top))' : ''
    var pc = page.querySelector('.page-content')
    if (pc) pc.style.paddingTop = offset
    page.querySelectorAll('.sidebar-icon, .fullscreen-icon').forEach(function (el) {
      el.style.marginTop = offset
    })
  }

  // A page that isn't Main UI — an openHAB error page, Basic UI, the REST docs — reserves no room
  // for the host's bar, so pad the body itself.
  function syncDocumentPadding(force) {
    var body = document.body
    if (!body || !wants('navbar')) return
    var needsPadding = !isMainUIDocument()
    if (!force && needsPadding === !!body.__ohPadded) return
    body.__ohPadded = needsPadding
    var h = Number(layout.navbarHeight) > 0 ? Number(layout.navbarHeight) : 44
    body.style.paddingTop = needsPadding ? 'calc(' + h + 'px + env(safe-area-inset-top, 0px))' : ''
  }

  function iconOf(el) {
    if (!el) return null
    var f7 = el.querySelector('i.f7-icons, .f7-icons')
    if (f7 && f7.textContent.trim()) return { name: 'f7:' + f7.textContent.trim() }
    var md = el.querySelector('i.material-icons, .material-icons')
    if (md && md.textContent.trim()) return { name: 'material:' + md.textContent.trim() }
    var img = el.querySelector('img[src*="/icon/"]')
    if (img) {
      try {
        var url = new URL(img.getAttribute('src'), location.href)
        var name = decodeURIComponent(url.pathname.split('/icon/')[1] || '').replace(/\.(svg|png)$/, '')
        var set = url.searchParams.get('iconset') || 'classic'
        if (name) return { name: 'oh:' + set + ':' + name }
      } catch (e) {}
    }
    var svg = el.querySelector('svg')
    if (svg) return { svg: svg.outerHTML }
    return null
  }

  function iconText(el) {
    var icon = el.querySelector('.f7-icons, .material-icons')
    return icon ? (icon.textContent || '').trim() : ''
  }

  // The visible text, without the icon font's ligature name.
  function labelOf(el) {
    var label = el.getAttribute('aria-label') || el.getAttribute('title')
    if (label) return label.trim()
    var text = (el.innerText || '').trim()
    var glyph = iconText(el)
    if (glyph && text.indexOf(glyph) === 0) text = text.slice(glyph.length).trim()
    return text
  }

  var nextProxyId = 0
  var backEl = null

  // A button keeps its id for as long as it stays in the page, so an id the host holds still
  // finds it, and an unchanged navbar reads back the same. The counter keeps ids unique across
  // the document: a popup's close button must not share an id with the page behind it.
  function proxyIdOf(el) {
    var id = el.getAttribute('data-oh-proxy')
    if (!id) {
      id = String(++nextProxyId)
      el.setAttribute('data-oh-proxy', id)
    }
    return id
  }

  function emptyNavbar() {
    return { title: '', titleInContent: false, hidden: false, back: null, leading: [], trailing: [] }
  }

  function readNavbar(navbar) {
    if (!ownsBar(navbar)) return emptyNavbar()

    var titleEl = navbar.querySelector('.title') || navbar.querySelector('[class*="title"]')
    var state = emptyNavbar()
    state.title = titleEl ? titleEl.innerText.trim() : ''
    state.hidden = !!navbar.closest('.navbar-hidden')
    // An expanded large title already shows the page title, so the host shouldn't repeat it.
    state.titleInContent = navbar.classList.contains('navbar-large') && !navbar.classList.contains('navbar-large-collapsed')

    backEl = null

    var buttons = navbar.querySelectorAll(
      '.navbar-inner .left a, .navbar-inner .left button, .navbar-inner .right a, .navbar-inner .right button'
    )
    Array.prototype.forEach.call(buttons, function (el) {
      var inRight = !!el.closest('.navbar-inner .right')
      var glyph = iconText(el)
      // The right "Other Apps" panel button and the exit-to-app button: the host already does both.
      if (inRight && el.classList.contains('panel-open')) return
      if (inRight && glyph === 'square_arrow_right') return
      // The left panel button opens the sidebar, which the host shows in its own menu.
      if (!inRight && el.classList.contains('panel-open') && wants('menu')) return

      // Standard F7 back links have .back. openHAB's oh-nav-content uses a Vue click handler
      // (@click="back") with a chevron_left (iOS) or arrow_left_md (MD) icon instead.
      var isBack = el.classList.contains('back') || (!inRight && (glyph === 'chevron_left' || glyph === 'arrow_left_md'))
      var label = labelOf(el)
      if (isBack) {
        backEl = el
        state.back = label ? { label: label } : {}
        return
      }
      var icon = iconOf(el)
      if (!label && !icon) return
      var action = { id: proxyIdOf(el), label: label || glyph }
      if (icon) action.icon = icon
      if (el.classList.contains('disabled') || el.disabled) action.disabled = true
      ;(inRight ? state.trailing : state.leading).push(action)
    })
    return state
  }

  var lastNavbarJSON = null

  function reportNavbar() {
    if (!wants('navbar') || !helloSent) return
    var navbar = activeNavbar()
    var owns = ownsBar(navbar)
    markProxied(owns ? navbar : null)
    syncPagePadding(!!pageNavbar())
    syncDocumentPadding(false)
    var json = JSON.stringify(readNavbar(navbar))
    if (json === lastNavbarJSON) return
    lastNavbarJSON = json
    send('navbar.state', JSON.parse(json))
  }

  // ---------------------------------------------------------------------------
  // Sidebar menu
  // ---------------------------------------------------------------------------

  var SKIP_ITEM = '.submenu-customize-entry, .submenu-expand-entry, .submenu-collapse-entry'

  function itemTitle(li) {
    var title = li.querySelector('.item-title')
    if (!title) return ''
    var clone = title.cloneNode(true)
    clone.querySelectorAll('.item-footer, .item-header').forEach(function (el) {
      el.remove()
    })
    return (clone.innerText || clone.textContent || '').trim()
  }

  function readItem(li) {
    var link = li.querySelector(':scope > a, :scope > .item-link, :scope > .item-content')
    if (!link) return null
    var href = link.getAttribute('href')
    var label = itemTitle(li)
    if (!label) return null
    var item = { id: href || label, label: label }
    if (href && href !== '#') item.path = href
    var footer = li.querySelector('.item-footer')
    if (footer && footer.innerText.trim()) item.footer = footer.innerText.trim()
    var icon = iconOf(li.querySelector('.item-media') || li)
    if (icon) item.icon = icon
    if (li.classList.contains('currentsection')) item.active = true
    return item
  }

  function readList(list) {
    var items = []
    var lis = list.querySelectorAll(':scope > ul > li')
    Array.prototype.forEach.call(lis, function (li) {
      // Submenus sit in their own <li> after the entry they belong to.
      var sub = li.querySelector('ul.menu-sublinks')
      if (sub && !li.querySelector(':scope > a')) {
        var parent = items[items.length - 1]
        if (!parent) return
        var children = []
        Array.prototype.forEach.call(sub.querySelectorAll(':scope > li'), function (subLi) {
          if (subLi.matches(SKIP_ITEM)) return
          var child = readItem(subLi)
          if (child) children.push(child)
        })
        if (children.length) parent.children = children
        return
      }
      var item = readItem(li)
      if (item) items.push(item)
    })
    return items
  }

  function sectionId(items, index) {
    var path = items[0] && items[0].path
    if (path && path.indexOf('/page/') === 0) return 'pages'
    if (path) return path.split('/')[1] || 's' + index
    return 's' + index
  }

  function readMenu() {
    var panel = document.querySelector('.panel-left')
    if (!panel) return null
    var sections = []
    var accountSection = null
    var pendingTitle = null
    // The account block sits in the page's fixed slot, outside .page-content.
    var nodes = panel.querySelectorAll('.page-content > .block-title, .page-content > .list, .account')
    Array.prototype.forEach.call(nodes, function (node, index) {
      if (node.classList.contains('block-title')) {
        pendingTitle = node.innerText.trim()
        return
      }
      if (node.classList.contains('account')) {
        var account = []
        var unlock = node.querySelector('.button')
        if (unlock) {
          unlock.setAttribute('data-oh-menu', 'unlock')
          account.push({
            id: 'unlock',
            label: unlock.getAttribute('aria-label') || unlock.getAttribute('title') || 'Unlock Administration',
            icon: iconOf(unlock) || undefined
          })
        }
        var list = node.querySelector('.list')
        if (list) account = account.concat(readList(list))
        if (account.length) accountSection = { id: 'account', items: account }
        return
      }
      var items = readList(node)
      if (!items.length) return
      var section = { id: sectionId(items, index), items: items }
      if (pendingTitle) section.title = pendingTitle
      pendingTitle = null
      sections.push(section)
    })
    // Main UI renders the account block first in the DOM but shows it at the bottom.
    if (accountSection) sections.push(accountSection)
    return { sections: sections }
  }

  var lastMenuJSON = null

  function reportMenu() {
    if (!wants('menu') || !helloSent) return
    var menu = readMenu()
    if (!menu) return
    var json = JSON.stringify(menu)
    if (json === lastMenuJSON) return
    lastMenuJSON = json
    send('menu.state', menu)
  }

  // ---------------------------------------------------------------------------
  // Route capture and restore
  // ---------------------------------------------------------------------------

  var VIEW_ID = 'view_main' // the name Main UI gives its main view
  var STORAGE_KEY = 'f7router-' + VIEW_ID + '-history'
  var RESTORE = info.initialHistory && info.initialHistory.length ? info.initialHistory : null
  var RESTORE_PROPS = info.initialProps || null
  // True until the restored pages have their props back. Reporting before then would save the
  // pages without them.
  var restoring = false

  function isAppRoot() {
    // Logging in comes back to the front page as /?code=...&state=..., which Main UI reads from
    // the address itself; replacing it here would lose the login.
    if (location.search) return false
    var path = location.pathname
    return path === BASE || path === BASE + '/'
  }

  // Main UI reads this list as it starts, so write it first. Only on the app's front page, which
  // is what the host asked for: a tile or a retry could otherwise pick it up.
  if (RESTORE && wants('routeRestore') && isAppRoot()) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(RESTORE))
    } catch (e) {}
    seedBrowserHistory(RESTORE)
    restoring = true
  }

  // Going back in Main UI is the browser going back, and a fresh page has nothing behind it. This
  // also moves us from the front page to the page we want. Everything the page fetches is
  // relative to the top, so changing the address is safe.
  function seedBrowserHistory(stack) {
    try {
      history.replaceState(stateFor(stack[0]), '', BASE + stack[0])
      for (var i = 1; i < stack.length; i++) {
        history.pushState(stateFor(stack[i]), '', BASE + stack[i])
      }
    } catch (e) {}
  }

  // Main UI looks here to work out where "back" goes, so match what it writes.
  function stateFor(url) {
    var state = {}
    state[VIEW_ID] = { url: url }
    return state
  }

  var MODAL_KEYS = ['popup', 'popover', 'sheet', 'actions', 'panel', 'loginScreen', 'customModal']
  var PROPS_ONLY = /\/(duplicate|stub)$/ // only work when opened from inside the app

  function matchRoute(r, url) {
    if (!url || url.charAt(0) !== '/') return null
    var m
    try {
      m = r.findMatchingRoute(url.split('#')[0])
    } catch (e) {
      return null
    }
    return m && m.route ? m : null
  }

  function isPopup(m) {
    for (var i = 0; i < MODAL_KEYS.length; i++) {
      if (m.route[MODAL_KEYS[i]]) return true // e.g. /analyzer/
    }
    return false
  }

  // Popups and the like show up in the address but cannot be opened again directly.
  function navigable(r, url) {
    var m = matchRoute(r, url)
    if (!m || isPopup(m)) return false
    if (PROPS_ONLY.test(url.split('#')[0].split('?')[0])) return false
    return m.route.path !== '(.*)' // nothing real behind this address
  }

  // `deep` gives a page its back link, `defineVars` sets its variables. Nothing else is kept;
  // other pages get passed live objects that mean nothing once saved.
  var KEPT_PROPS = ['deep', 'defineVars']

  // Opening a popup adds it to Framework7's history but adds no props for it, and closing it
  // removes the last of both, which throws away the props of the page underneath. Stand in for
  // each open popup so both lists keep step.
  var POPUP_PROPS = { popup: true }
  function padPopupProps(r) {
    if (!r.propsHistory) return
    var open = 0
    for (var i = r.history.length - 1; i >= 0; i--) {
      var m = matchRoute(r, String(r.history[i]))
      if (!m || !isPopup(m)) break
      open++
    }
    var padded = 0
    for (var j = r.propsHistory.length - 1; j >= 0 && r.propsHistory[j] === POPUP_PROPS; j--) padded++
    for (; padded < open; padded++) r.propsHistory.push(POPUP_PROPS)
  }

  // Framework7 only adds to and removes from the end of both lists, but does not keep them the
  // same length (a restored page starts with none), so line them up from the end.
  function propsAt(r, i) {
    var list = r.propsHistory || []
    var p = list[i - (r.history.length - list.length)]
    var kept = {}
    if (!p) return kept
    for (var k = 0; k < KEPT_PROPS.length; k++) {
      if (p[KEPT_PROPS[k]] !== undefined) kept[KEPT_PROPS[k]] = p[KEPT_PROPS[k]]
    }
    return kept
  }

  function isModalOpen() {
    return !!document.querySelector('.popup.modal-in, .sheet-modal.modal-in, .popover.modal-in')
  }

  function captureNav() {
    var r = router()
    if (!r || !r.history || restoring) return null
    padPopupProps(r)
    var stack = []
    var props = []
    for (var i = 0; i < r.history.length; i++) {
      var url = String(r.history[i]).split('#')[0]
      if (!navigable(r, url)) continue
      var p
      try {
        p = JSON.stringify(propsAt(r, i))
      } catch (e) {
        p = '{}'
      }
      if (stack.length && stack[stack.length - 1] === url) {
        props[props.length - 1] = p // same page twice, keep how it was last opened
        continue
      }
      stack.push(url)
      props.push(p)
    }
    if (!stack.length) return null
    return { path: stack[stack.length - 1], history: stack, props: props, modal: isModalOpen() }
  }

  // Main UI opens the restored page from its address alone, so it comes up without its props: no
  // back link, no variables. Hand them back to Framework7 for every page, and open the current
  // page again with its own.
  function restoreProps(r) {
    var n = r.history.length
    var k = RESTORE.length
    if (r.history[n - 1] !== RESTORE[k - 1]) return // not the pages we put back
    var list = []
    for (var i = 0; i < n; i++) {
      var p = {}
      var j = i - (n - k)
      if (RESTORE_PROPS && j >= 0) {
        try {
          p = JSON.parse(RESTORE_PROPS[j]) || {}
        } catch (e) {}
      }
      list.push(p)
    }
    r.propsHistory = list
    var top = list[n - 1]
    if (Object.keys(top).length) {
      r.navigate(r.history[n - 1], { reloadCurrent: true, animate: false, browserHistory: false, props: top })
    }
  }

  // Framework7 ignores a navigation while another is still under way, so wait for Main UI to
  // finish opening the restored page.
  function whenRestoredPageOpen() {
    var tries = 0
    var poll = setInterval(function () {
      var r = router()
      var page = r && r.currentPageEl && r.currentPageEl.f7Page
      var ready = !!page && r.allowPageChange && page.route.url === RESTORE[RESTORE.length - 1]
      if (!ready && ++tries <= 100) return
      clearInterval(poll)
      if (ready) {
        try {
          restoreProps(r)
        } catch (e) {}
      }
      restoring = false
      reportNav()
    }, 100)
  }
  if (restoring) whenRestoredPageOpen()

  var lastNavJSON = null
  var navTimer = null

  function reportNav() {
    if (navTimer) clearTimeout(navTimer)
    // Let Main UI finish updating before reading anything.
    navTimer = setTimeout(function () {
      navTimer = null
      if (!helloSent) return
      var state = captureNav()
      if (!state) return
      var json = JSON.stringify(state)
      if (json === lastNavJSON) return
      lastNavJSON = json
      send('nav.changed', state)
    }, 0)
  }

  // ---------------------------------------------------------------------------
  // Legacy Main UI hooks
  // ---------------------------------------------------------------------------

  // Main UI only exposes window.MainUI (used for popups and closing modals) when the app offers
  // goFullscreen, and reports its event stream through sseConnected. exitToApp and pinToHome are
  // left out on purpose: the host's own bar and menu cover them.
  var legacy = window.OHApp || {}
  legacy.goFullscreen = function () {}
  legacy.sseConnected = function (connected) {
    send('connection.state', { sseConnected: !!connected })
  }
  if (info.theme) {
    legacy.preferTheme = function () {
      return info.theme
    }
  }
  if (info.darkMode) {
    legacy.preferDarkMode = function () {
      return info.darkMode
    }
  }
  window.OHApp = legacy

  // ---------------------------------------------------------------------------
  // Host → web
  // ---------------------------------------------------------------------------

  function clickTagged(attr, value) {
    var el = document.querySelector('[' + attr + '="' + String(value).replace(/"/g, '\\"') + '"]')
    if (!el) return false
    el.click()
    return true
  }

  function legacyCommand(command) {
    if (!window.MainUI || typeof window.MainUI.handleCommand !== 'function') return false
    window.MainUI.handleCommand(command)
    return true
  }

  function handle(msg) {
    var id = msg.id
    var p = msg.payload || {}
    var r = router()
    var isMainUI = isMainUIDocument()
    try {
      switch (msg.type) {
        case 'layout.changed':
          applyLayout(p)
          return reply(id)
        case 'ui.reload':
          reply(id)
          setTimeout(function () {
            location.reload()
          }, 0)
          return
        case 'navbar.activate':
          return clickTagged('data-oh-proxy', p.id) ? reply(id) : fail(id, 'not_found')
        case 'menu.activate':
          if (!document.querySelector('.panel-left')) return fail(id, isMainUI ? 'not_ready' : 'not_allowed')
          if (clickTagged('data-oh-menu', p.id)) return reply(id)
          // Entries with a path go through nav.navigate; this covers anything else rendered as a link.
          return clickTagged('href', p.id) ? reply(id) : fail(id, 'not_found')
        case 'nav.navigate':
        case 'nav.back':
        case 'nav.openModal':
        case 'nav.closeModals':
        case 'nav.getState':
          if (!isMainUI) return fail(id, 'not_allowed')
          if (!r || !helloSent) return fail(id, 'not_ready')
          return navCommand(msg.type, p, r, id)
        default:
          return fail(id, 'unknown_type')
      }
    } catch (e) {
      fail(id, 'failed', String(e && e.message ? e.message : e))
    }
  }

  function navCommand(type, p, r, id) {
    switch (type) {
      case 'nav.navigate':
        if (!p.path) return fail(id, 'not_found')
        r.navigate(p.path)
        return reply(id)
      case 'nav.back':
        if (backEl && document.contains(backEl) && !backEl.classList.contains('back')) backEl.click()
        else r.back()
        return reply(id)
      case 'nav.openModal':
        return legacyCommand(p.kind + ':' + p.target) ? reply(id) : fail(id, 'not_ready')
      case 'nav.closeModals':
        return legacyCommand('close') ? reply(id) : fail(id, 'not_ready')
      case 'nav.getState':
        return reply(id, captureNav())
    }
  }

  // ---------------------------------------------------------------------------
  // Script editor padding
  // ---------------------------------------------------------------------------

  // The rule script editor fills the page down to its bottom edge, under the home indicator and
  // the floating button. Give it room for both.
  var editorTimer = null
  function scheduleEditorFix() {
    if (editorTimer) clearTimeout(editorTimer)
    editorTimer = setTimeout(function () {
      editorTimer = null
      requestAnimationFrame(fixScriptEditor)
    }, 100)
  }

  function fixScriptEditor() {
    var editor = document.querySelector('.rule-script-editor.v-codemirror')
    if (!editor) return
    var page = editor.closest('.page')
    if (!page || !page.querySelector('.toolbar')) return
    var bottom = Number(layout.insets && layout.insets.bottom) || 0
    var fab = page.querySelector('.fab') || document.querySelector('.fab')
    if (fab) bottom += (fab.getBoundingClientRect().height || 56) + 16
    var pageContent = page.querySelector('.page-content')
    if (pageContent) pageContent.style.paddingBottom = bottom + 'px'
    var scroller = editor.querySelector('.cm-scroller') || editor.querySelector('.CodeMirror-scroll') || editor.querySelector('.cm-content')
    if (scroller && scroller !== editor) {
      scroller.style.paddingBottom = bottom + 'px'
      scroller.style.overflowY = 'auto'
    } else {
      editor.style.paddingBottom = bottom + 'px'
    }
    editor.style.marginBottom = bottom + 'px'
  }

  window.addEventListener('resize', scheduleEditorFix)

  // ---------------------------------------------------------------------------
  // Start-up and change tracking
  // ---------------------------------------------------------------------------

  function readyNavbar() {
    return firstUsableNavbar([
      '.popup.modal-in .navbar',
      '.sheet-modal.modal-in .navbar',
      '.view-main .page-current > .navbar',
      '.view-main .navbar.navbar-current',
      '.page-current > .navbar'
    ])
  }

  // A hideNavbar page never produces a navbar to wait for, so a rendered page without one counts.
  function readyPageWithoutNavbar() {
    var page = activePage()
    return !!page && !!page.querySelector('.page-content') && !pageNavbar()
  }

  function sendHello(impl) {
    if (helloSent || !active) return
    helloSent = true
    send('ui.hello', {
      protocol: 1,
      impl: impl,
      accepted: impl === 'other' ? [] : accepted,
      features: impl === 'other' ? [] : SHIM_FEATURES.concat(['layout'])
    })
  }

  // Collapses a burst of mutations into one pass per frame. It still runs before the next paint,
  // so a new page's navbar never flashes under the host's bar.
  var framePending = false
  function scheduleReport() {
    if (framePending || !active) return
    framePending = true
    requestAnimationFrame(function () {
      framePending = false
      if (!active) return
      reportNavbar()
      reportMenu()
    })
  }

  function onMutation(mutations) {
    scheduleReport()
    for (var i = 0; i < mutations.length; i++) {
      var added = mutations[i].addedNodes
      for (var j = 0; j < added.length; j++) {
        var n = added[j]
        if (n.nodeType === 1 && (n.classList.contains('rule-script-editor') || (n.querySelector && n.querySelector('.rule-script-editor')))) {
          scheduleEditorFix()
          return
        }
      }
    }
  }

  function startMainUI() {
    sendHello('shim')
    reportNav()
    reportNavbar()
    reportMenu()
    new MutationObserver(onMutation).observe(document.body || document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class']
    })
    // Framework7 hides the navbar and collapses large titles from a scroll handler, which touches
    // no class the observer sees.
    document.addEventListener('scroll', scheduleReport, { capture: true, passive: true })
    scheduleEditorFix()
  }

  // Main UI announces its first page through pushState/replaceState, which these catch.
  var origPush = history.pushState
  var origReplace = history.replaceState
  history.pushState = function () {
    var out = origPush.apply(history, arguments)
    reportNav()
    scheduleReport()
    return out
  }
  history.replaceState = function () {
    var out = origReplace.apply(history, arguments)
    reportNav()
    scheduleReport()
    return out
  }
  window.addEventListener('popstate', function () {
    reportNav()
    scheduleReport()
  })

  // Wait for the first page to render before saying hello, so the host can take over a page that
  // is already laid out.
  function waitForFirstPage() {
    if (!active) return
    if (!isMainUIDocument()) {
      sendHello('other')
      syncDocumentPadding(true)
      return
    }
    var settled = function () {
      return readyNavbar() || readyPageWithoutNavbar()
    }
    if (settled()) return startMainUI()
    var observer = new MutationObserver(function () {
      if (!settled()) return
      observer.disconnect()
      startMainUI()
    })
    observer.observe(document.body || document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class']
    })
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', waitForFirstPage)
  } else {
    waitForFirstPage()
  }
})()
