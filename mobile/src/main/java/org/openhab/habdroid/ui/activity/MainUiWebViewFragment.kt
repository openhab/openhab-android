/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui.activity

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import okhttp3.HttpUrl
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.bridge.MainUiBridge
import org.openhab.habdroid.ui.bridge.MainUiBridgeProtocol
import org.openhab.habdroid.ui.bridge.MainUiIconLoader
import org.openhab.habdroid.ui.bridge.MainUiMenu
import org.openhab.habdroid.ui.bridge.MenuEntry
import org.openhab.habdroid.ui.bridge.MenuSection
import org.openhab.habdroid.ui.bridge.NavState
import org.openhab.habdroid.ui.bridge.NavbarState
import org.openhab.habdroid.ui.bridge.PageImpl
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isDarkModeActive
import org.openhab.habdroid.util.loadActiveServerConfig
import org.openhab.habdroid.util.resolveThemedColor

class MainUiWebViewFragment :
    AbstractWebViewFragment(),
    MainUiBridge.Callback {
    override val titleRes = R.string.mainmenu_openhab_main_ui
    override val errorMessageRes = R.string.main_ui_error
    override val urlToLoad = "/"
    override val pathForError = "/"

    // Main UI's own sidebar opens with a swipe from the edge, unless the app shows it in its drawer
    override val lockDrawer get() = MainUiBridgeProtocol.FEATURE_MENU !in acceptedFeatures
    override val shortcutIcon = R.mipmap.ic_shortcut_main_ui
    override val shortcutAction = MainActivity.ACTION_MAIN_UI_SELECTED

    private var bridge: MainUiBridge? = null
    private val icons by lazy { MainUiIconLoader(requireContext(), this) }
    private var defaultTitle: String? = null

    /**
     * What Main UI took over from the app on the page now loaded.
     */
    private var acceptedFeatures = emptySet<String>()
    private var navbar: NavbarState? = null
    private var menuSections: List<MenuSection>? = null
    private var sseConnected = false

    /**
     * Main UI reports the event stream as disconnected until it connects, and 5.2 doesn't always report that it
     * did. So only show it lost the connection once it had one.
     */
    private var sseWasConnected = false

    /**
     * Ids of the menu items for the buttons of Main UI's navbar, and the ids of these buttons.
     */
    private val navbarActionItemIds = mutableListOf<Int>()
    private val navbarActions = mutableMapOf<Int, String>()

    /**
     * Where the user was in Main UI, to put them back there when the page is loaded again, e.g. after switching
     * between local and remote connection.
     */
    private var navState: NavState? = null
    private var navStateServerId = -1

    /**
     * A command to run once Main UI is loaded.
     */
    private var pendingCommand: String? = null

    private val canRestoreRoutes get() = restorableNavState != null && MainUiBridge.isSupported()

    private val restorableNavState get() = navState?.takeIf {
        navStateServerId == context?.getPrefs()?.getActiveServerId()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        defaultTitle = title
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pendingCommand = arguments?.getString(KEY_BRIDGE_COMMAND)
        } else {
            navStateServerId = savedInstanceState.getInt(KEY_NAV_STATE_SERVER_ID, -1)
            navState = savedInstanceState.getString(KEY_NAV_STATE)?.let { MainUiBridgeProtocol.parseNavState(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navState?.let {
            outState.putString(KEY_NAV_STATE, MainUiBridgeProtocol.buildNavState(it))
            outState.putInt(KEY_NAV_STATE_SERVER_ID, navStateServerId)
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenu()
        updateConnectionIndicator()
    }

    override fun onPause() {
        super.onPause()
        mainActivity?.setMainUiMenu(null)
        mainActivity?.setProgressIndicatorVisible(false)
    }

    override fun onDestroyView() {
        bridge?.destroy()
        bridge = null
        super.onDestroyView()
    }

    override fun buildUrl(connection: Connection, url: String): HttpUrl {
        val connectionUrl = connection.httpClient.buildUrl(url)
        val urlBuilder = connectionUrl.newBuilder()
        if (connection is CloudConnection && connectionUrl.host == connection.httpClient.targetHost) {
            urlBuilder
                .scheme(connection.proxyUrl.scheme)
                .host(connection.proxyUrl.host)
                .port(connection.proxyUrl.port)
        }

        val mainUiStartPage = context?.loadActiveServerConfig()?.mainUiStartPage
        // Pages are only put back when Main UI starts on its front page
        if (!mainUiStartPage.isNullOrEmpty() && !(url == urlToLoad && canRestoreRoutes)) {
            urlBuilder.encodedPath(mainUiStartPage)
        }

        return urlBuilder.build()
    }

    override fun urlForRestoredState(savedUrl: String) = if (canRestoreRoutes) urlToLoad else savedUrl

    override fun setUpJavascriptInterfaces(webView: WebView, url: HttpUrl, appInterface: OHAppInterface) {
        val bridge = bridge ?: if (MainUiBridge.isSupported()) MainUiBridge(webView, this, this) else null
        if (bridge?.install(MainUiBridgeProtocol.originOf(url)) != true) {
            super.setUpJavascriptInterfaces(webView, url, appInterface)
            return
        }
        this.bridge = bridge
        updateStartupInfo()
        webView.removeJavascriptInterface("OHApp")
        webView.addJavascriptInterface(appInterface, MainUiBridge.NATIVE_INTERFACE_NAME)
        pendingCommand?.let { command ->
            bridge.run(command)
            pendingCommand = null
        }
    }

    override fun onPageStarted() {
        val bridge = bridge ?: return
        bridge.onPageStarted()
        // Main UI says again what it takes over once the new page is up
        acceptedFeatures = emptySet()
        navbar = null
        menuSections = null
        sseConnected = false
        sseWasConnected = false
        updateFeatures()
    }

    override fun onPageReady(impl: PageImpl, accepted: List<String>) {
        Log.d(TAG, "Main UI bridge ready: $impl, took $accepted")
        acceptedFeatures = accepted.toSet()
        updateFeatures()
    }

    override fun onNavbarChanged(state: NavbarState) {
        navbar = state
        updateNavbar()
    }

    override fun onMenuChanged(sections: List<MenuSection>) {
        menuSections = sections
        updateMenu()
    }

    override fun onConnectionStateChanged(sseConnected: Boolean) {
        Log.d(TAG, "Main UI event stream connected: $sseConnected")
        this.sseConnected = sseConnected
        sseWasConnected = sseWasConnected || sseConnected
        updateConnectionIndicator()
    }

    override fun getProxyCredentials(): Pair<String, String>? {
        val connection = webViewConnection ?: return null
        val username = connection.username
        val password = connection.password
        return if (username.isNullOrEmpty() || password.isNullOrEmpty()) null else Pair(username, password)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateMenu(menu, inflater)
        navbarActions.clear()
        val state = navbar?.takeIf { MainUiBridgeProtocol.FEATURE_NAVBAR in acceptedFeatures } ?: return
        val context = requireContext()
        val iconColor = context.resolveThemedColor(R.attr.colorOnSurfaceVariant)
        val iconSize = resources.getDimensionPixelSize(R.dimen.main_ui_icon_size)
        (state.leading + state.trailing).forEachIndexed { index, action ->
            val itemId = navbarActionItemIds.getOrNull(index)
                ?: View.generateViewId().also { navbarActionItemIds.add(it) }
            val item = menu.add(Menu.NONE, itemId, index, action.displayLabel)
            navbarActions[itemId] = action.id
            item.isEnabled = !action.disabled
            if (action.icon != null) {
                item.icon = icons.get(action.icon, iconColor, iconSize) { activity?.invalidateMenu() }
                    ?: GradientDrawable().apply { setSize(iconSize, iconSize) }
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            } else {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val actionId = navbarActions[item.itemId] ?: return super.onMenuItemSelected(item)
        bridge?.activateNavbarAction(actionId)
        return true
    }

    override fun onNavChanged(state: NavState) {
        Log.d(TAG, "Main UI is on ${state.path}, history ${state.history}, modal ${state.modal}")
        navState = state
        navStateServerId = context?.getPrefs()?.getActiveServerId() ?: -1
        // A reload of the front page, e.g. by Main UI itself, should put back the pages too
        updateStartupInfo()
        mainActivity?.updateTitle()
    }

    override fun canGoBack(): Boolean = canGoBackInMainUi() || stepsBackInWebView() > 0

    // Like Main UI's own bar: pages opened from the sidebar show the drawer button, even with pages behind them
    override val showsUpButton get() = if (MainUiBridgeProtocol.FEATURE_NAVBAR in acceptedFeatures) {
        navbar?.back != null
    } else {
        null
    }

    override fun goBack(): Boolean {
        val bridge = bridge
        val state = navState
        if (bridge == null || state == null || !canGoBackInMainUi()) {
            val steps = stepsBackInWebView()
            if (steps == 0) {
                return false
            }
            // WebView.goBackOrForward() skips these pages too, going back from the page itself doesn't
            webView?.evaluateJavascript("history.go(-$steps)", null)
            return true
        }
        if (state.modal) {
            bridge.closeModals()
        } else {
            bridge.back()
        }
        return true
    }

    /**
     * Runs a command of the UI command item or a notification action in Main UI.
     *
     * @return false if the command can't be run through the bridge.
     */
    fun runCommand(command: String): Boolean = bridge?.run(command) == true

    /**
     * How far back the web view goes, skipping redundant history entries like [AbstractWebViewFragment.goBack].
     *
     * WebView.canGoBack() skips pages that went somewhere else without a tap in the page, so that pages can't
     * keep the user from going back. Main UI goes to other pages when the user taps the app's bar or drawer, e.g.
     * to openHAB's sign-in page for the edit button, so these pages must not be skipped.
     */
    private fun stepsBackInWebView(): Int {
        val list = webView?.copyBackForwardList() ?: return 0
        if (list.currentIndex <= 0) {
            return 0
        }
        val currentUrl = list.currentItem?.url
        var index = list.currentIndex - 1
        while (index > 0 && list.getItemAtIndex(index).url == currentUrl) {
            index--
        }
        return list.currentIndex - index
    }

    private fun canGoBackInMainUi(): Boolean {
        val state = navState ?: return false
        val hasBackButton = MainUiBridgeProtocol.FEATURE_NAVBAR in acceptedFeatures && navbar?.back != null
        return bridge?.isMainUiReady == true && (state.modal || state.history.size > 1 || hasBackButton)
    }

    private fun updateFeatures() {
        mainActivity?.setDrawerLocked(lockDrawer)
        updateNavbar()
        updateMenu()
        updateConnectionIndicator()
    }

    /**
     * Shows Main UI's navbar in the app's bar, if Main UI handed it over.
     */
    private fun updateNavbar() {
        val state = navbar?.takeIf { MainUiBridgeProtocol.FEATURE_NAVBAR in acceptedFeatures }
        title = state?.title?.takeIf { it.isNotEmpty() } ?: defaultTitle
        setActionBarWanted(state?.hidden != true)
        mainActivity?.updateTitle()
        activity?.invalidateMenu()
    }

    private fun updateMenu() {
        val sections = menuSections?.takeIf { MainUiBridgeProtocol.FEATURE_MENU in acceptedFeatures }
        if (!isResumed) {
            return
        }
        mainActivity?.setMainUiMenu(sections?.let { MainUiMenu(it, icons, ::onMenuEntrySelected) })
    }

    private fun onMenuEntrySelected(entry: MenuEntry) {
        val bridge = bridge ?: return
        if (entry.path != null) {
            bridge.navigate(entry.path)
        } else {
            bridge.activateMenuEntry(entry.id)
        }
    }

    /**
     * Shows that Main UI lost its connection to the server's event stream, as Main UI's own bar would.
     */
    private fun updateConnectionIndicator() {
        if (isResumed && MainUiBridgeProtocol.FEATURE_NAVBAR in acceptedFeatures) {
            mainActivity?.setProgressIndicatorVisible(sseWasConnected && !sseConnected)
        }
    }

    private fun updateStartupInfo() {
        val context = context ?: return
        val info = MainUiBridgeProtocol.buildHostInfo(
            appVersion = BuildConfig.VERSION_NAME,
            features = listOf(
                MainUiBridgeProtocol.FEATURE_NAVBAR,
                MainUiBridgeProtocol.FEATURE_MENU,
                MainUiBridgeProtocol.FEATURE_ROUTE_RESTORE
            ),
            theme = "md",
            darkMode = context.isDarkModeActive(),
            restore = restorableNavState
        )
        bridge?.setStartupInfo(info)
    }

    companion object {
        private val TAG = MainUiWebViewFragment::class.java.simpleName

        private const val KEY_NAV_STATE = "nav_state"
        private const val KEY_NAV_STATE_SERVER_ID = "nav_state_server_id"
        const val KEY_BRIDGE_COMMAND = "bridge_command"
    }
}
