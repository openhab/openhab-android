/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

package org.openhab.habdroid.ui

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.nfc.NfcAdapter
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.DemoConnection
import org.openhab.habdroid.databinding.DrawerHeaderBinding
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.model.WebViewUi
import org.openhab.habdroid.model.sortedWithDefaultName
import org.openhab.habdroid.ui.bridge.MainUiMenu
import org.openhab.habdroid.ui.bridge.MenuEntry
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.IconBackground
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.areSitemapsShownInDrawer
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getDefaultSitemap
import org.openhab.habdroid.util.getGroupItems
import org.openhab.habdroid.util.getIconFallbackColor
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getSecretPrefs

/**
 * Manages the items of the drawer of [activity]: the entries of Main UI's sidebar, the sitemaps, the options of the
 * app and the server selection.
 *
 * @param onItemSelected Called when an item of the app is selected, after the drawer has been closed.
 */
class DrawerController(private val activity: MainActivity, private val onItemSelected: (MenuItem) -> Boolean) {
    private val prefs = activity.getPrefs()
    private val drawerMenu: Menu
    private val drawerHeaderBinding: DrawerHeaderBinding
    private val drawerIconTintList: ColorStateList?
    private var inServerSelectionMode = false
    private var mainUiMenu: MainUiMenu? = null
    private val mainUiMenuGroupIds = mutableListOf<Int>()
    private val mainUiMenuItemIds = mutableListOf<Int>()
    private var nextMainUiMenuId = MAIN_UI_MENU_FIRST_ID
    private val mainUiMenuEntries = mutableMapOf<Int, MenuEntry>()
    private val mainUiMenuToggles = mutableMapOf<Int, String>()
    private val expandedMainUiMenuEntries = mutableSetOf<String>()

    init {
        val navigationView = activity.binding.leftDrawer
        navigationView.inflateMenu(R.menu.left_drawer)
        drawerMenu = navigationView.menu
        // We only want to tint the menu icons, but not our loaded sitemap icons. NavigationView
        // unfortunately doesn't support this directly, so we tint the icon drawables manually
        // instead of letting NavigationView do it.
        drawerIconTintList = navigationView.itemIconTintList
        navigationView.itemIconTintList = null

        drawerMenu.forEach { item -> item.icon = applyDrawerIconTint(item.icon) }

        navigationView.setNavigationItemSelectedListener { item ->
            if (item.groupId in mainUiMenuGroupIds) {
                return@setNavigationItemSelectedListener onMainUiMenuItemSelected(item)
            }
            activity.binding.drawerContainer.closeDrawers()
            val handled = onItemSelected(item)
            if (item.groupId == R.id.servers) {
                updateServerName()
                // Menu views aren't updated in a click handler, so defer the menu update
                activity.launch {
                    setServerSelectionMode(false)
                }
            }
            handled
        }

        drawerHeaderBinding = DrawerHeaderBinding.bind(navigationView.getHeaderView(0))
        drawerHeaderBinding.serverSelector.setOnClickListener { setServerSelectionMode(!inServerSelectionMode) }
    }

    fun updateServerEntries() {
        // Remove existing items from server group
        drawerMenu.getGroupItems(R.id.servers)
            .forEach { item -> drawerMenu.removeItem(item.itemId) }

        // Add new items
        if (activity.connection is DemoConnection) {
            drawerHeaderBinding.drawerModeSwitcher.isGone = true
        } else {
            val configs = prefs.getConfiguredServerIds()
                .mapNotNull { id -> ServerConfiguration.load(prefs, activity.getSecretPrefs(), id) }
            configs.forEachIndexed { index, config ->
                drawerMenu.add(R.id.servers, config.id, APP_DRAWER_ITEM_ORDER + index, config.name)
            }
            drawerHeaderBinding.drawerModeSwitcher.isGone = configs.size <= 1
        }
        drawerHeaderBinding.serverNameLabel.isGone = drawerHeaderBinding.drawerModeSwitcher.isGone
        drawerHeaderBinding.serverSelector.isClickable = drawerHeaderBinding.drawerModeSwitcher.isVisible
        if (!drawerHeaderBinding.serverSelector.isClickable) {
            inServerSelectionMode = false
        }

        updateServerName()
        updateItemVisibility()
    }

    fun updateSitemapEntries() {
        val serverProperties = activity.serverProperties
        val connection = activity.connection
        val defaultSitemapItem = drawerMenu.findItem(R.id.default_sitemap)
        val sitemaps = serverProperties?.sitemaps
            ?.sortedWithDefaultName(prefs.getDefaultSitemap(connection)?.name.orEmpty())

        drawerMenu.getGroupItems(R.id.sitemaps)
            .filter { item -> item !== defaultSitemapItem }
            .forEach { item -> drawerMenu.removeItem(item.itemId) }

        if (sitemaps?.isNotEmpty() != true) {
            return
        }

        if (prefs.areSitemapsShownInDrawer()) {
            sitemaps.forEachIndexed { index, sitemap ->
                val item = drawerMenu.add(
                    R.id.sitemaps,
                    sitemap.name.hashCode(),
                    APP_DRAWER_ITEM_ORDER + index,
                    sitemap.label
                )
                loadSitemapIcon(sitemap, item)
            }
        } else {
            val sitemap = serverProperties.sitemaps.firstOrNull { s ->
                s.name == prefs.getDefaultSitemap(connection)?.name.orEmpty()
            }
            if (sitemap != null) {
                defaultSitemapItem.title = sitemap.label
                loadSitemapIcon(sitemap, defaultSitemapItem)
            } else {
                defaultSitemapItem.title = activity.getString(R.string.mainmenu_openhab_selectsitemap)
                defaultSitemapItem.icon =
                    applyDrawerIconTint(ContextCompat.getDrawable(activity, R.drawable.ic_openhab_appicon_24dp))
            }
        }

        updateItemVisibility()
    }

    private fun updateServerName() {
        if (activity.connection is DemoConnection) {
            drawerHeaderBinding.serverName.text = activity.getString(R.string.settings_openhab_demomode)
        } else {
            val activeConfig = ServerConfiguration.load(prefs, activity.getSecretPrefs(), prefs.getActiveServerId())
            drawerHeaderBinding.serverName.text = activeConfig?.name
        }
    }

    fun updateItemVisibility() {
        val serverProperties = activity.serverProperties
        val connection = activity.connection
        val serverItems = drawerMenu.getGroupItems(R.id.servers)
        drawerMenu.setGroupVisible(R.id.servers, serverItems.size > 1 && inServerSelectionMode)
        mainUiMenuGroupIds.forEach { groupId -> drawerMenu.setGroupVisible(groupId, !inServerSelectionMode) }

        if (serverProperties?.sitemaps?.isNotEmpty() == true && !inServerSelectionMode) {
            drawerMenu.setGroupVisible(R.id.sitemaps, true)

            val defaultSitemapItem = drawerMenu.findItem(R.id.default_sitemap)
            defaultSitemapItem.isVisible = !prefs.areSitemapsShownInDrawer()
        } else {
            drawerMenu.setGroupVisible(R.id.sitemaps, false)
        }

        if (inServerSelectionMode) {
            drawerMenu.setGroupVisible(R.id.options, false)
        } else {
            drawerMenu.setGroupVisible(R.id.options, true)

            val notificationsItem = drawerMenu.findItem(R.id.notifications)
            notificationsItem.isVisible = activity.getConnectionFactory().currentActive?.cloud?.connection != null

            val habPanelItem = drawerMenu.findItem(R.id.habpanel)
            habPanelItem.isVisible = serverProperties?.hasWebViewUiInstalled(WebViewUi.HABPANEL) == true &&
                prefs.getBoolean(PrefKeys.DRAWER_ENTRY_HABPANEL, true)
            activity.manageHabPanelShortcut(serverProperties?.hasWebViewUiInstalled(WebViewUi.HABPANEL) == true)

            val mainUiItem = drawerMenu.findItem(R.id.main_ui)
            mainUiItem.isVisible = serverProperties?.hasWebViewUiInstalled(WebViewUi.MAIN_UI) == true &&
                prefs.getBoolean(PrefKeys.DRAWER_ENTRY_MAIN_UI, true)

            val frontailItem = drawerMenu.findItem(R.id.frontail)
            frontailItem.isVisible = serverProperties != null &&
                connection?.connectionType == Connection.TYPE_LOCAL &&
                prefs.getBoolean(PrefKeys.DRAWER_ENTRY_FRONTAIL, false)

            val nfcItem = drawerMenu.findItem(R.id.nfc)
            nfcItem.isVisible = serverProperties != null &&
                (NfcAdapter.getDefaultAdapter(activity) != null || Util.isEmulator()) &&
                prefs.getPrimaryServerId() == prefs.getActiveServerId() &&
                prefs.getBoolean(PrefKeys.DRAWER_ENTRY_NFC, true)
        }
    }

    fun setServerSelectionMode(inServerMode: Boolean) {
        if (inServerMode == inServerSelectionMode) {
            return
        }
        inServerSelectionMode = inServerMode
        drawerHeaderBinding.drawerModeSwitcher.setImageResource(
            if (inServerSelectionMode) R.drawable.ic_menu_up_24dp else R.drawable.ic_menu_down_24dp
        )
        updateItemVisibility()
    }

    /**
     * Shows the entries of Main UI's sidebar at the top of the drawer, or removes them if [menu] is null.
     */
    fun setMainUiMenu(menu: MainUiMenu?) {
        mainUiMenu = menu
        updateMainUiMenu()
    }

    private fun updateMainUiMenu() {
        mainUiMenuGroupIds.forEach { groupId -> drawerMenu.removeGroup(groupId) }
        mainUiMenuEntries.clear()
        mainUiMenuToggles.clear()
        val menu = mainUiMenu ?: return

        val iconColor = activity.getIconFallbackColor(IconBackground.APP_THEME)
        val iconSize = activity.resources.getDimensionPixelSize(R.dimen.main_ui_icon_size)
        var order = 0
        var itemCount = 0
        fun nextItemId() = mainUiMenuItemIds.getOrNull(itemCount++)
            ?: nextMainUiMenuId++.also { mainUiMenuItemIds.add(it) }

        menu.sections.forEachIndexed { index, section ->
            val groupId = mainUiMenuGroupIds.getOrNull(index)
                ?: nextMainUiMenuId++.also { mainUiMenuGroupIds.add(it) }
            val target = if (section.title.isNullOrEmpty()) {
                drawerMenu
            } else {
                drawerMenu.addSubMenu(groupId, nextItemId(), order++, section.title)
            }

            fun addEntry(entry: MenuEntry) {
                val item = target.add(groupId, nextItemId(), order++, entry.label)
                mainUiMenuEntries[item.itemId] = entry
                item.isCheckable = entry.active
                item.isChecked = entry.active
                item.icon = entry.icon
                    ?.let { icon -> menu.icons.get(icon, iconColor, iconSize) { updateMainUiMenu() } }
                    ?: GradientDrawable().apply { setSize(iconSize, iconSize) }
            }

            section.items.forEach { entry ->
                addEntry(entry)
                entry.children.forEach { child -> addEntry(child) }
                if (entry.more.isNotEmpty()) {
                    val expanded = entry.id in expandedMainUiMenuEntries
                    if (expanded) {
                        entry.more.forEach { child -> addEntry(child) }
                    }
                    val toggleTitle = if (expanded) R.string.main_ui_menu_show_less else R.string.main_ui_menu_show_all
                    val toggle = target.add(groupId, nextItemId(), order++, toggleTitle)
                    mainUiMenuToggles[toggle.itemId] = entry.id
                }
            }
        }
        updateItemVisibility()
    }

    private fun onMainUiMenuItemSelected(item: MenuItem): Boolean {
        mainUiMenuToggles[item.itemId]?.let { entryId ->
            if (!expandedMainUiMenuEntries.remove(entryId)) {
                expandedMainUiMenuEntries.add(entryId)
            }
            // Menu views aren't updated in a click handler, so defer the menu update
            activity.launch { updateMainUiMenu() }
            return true
        }
        val entry = mainUiMenuEntries[item.itemId] ?: return false
        activity.binding.drawerContainer.closeDrawers()
        mainUiMenu?.onSelected?.invoke(entry)
        return true
    }

    private fun loadSitemapIcon(sitemap: Sitemap, item: MenuItem) {
        val defaultIcon = ContextCompat.getDrawable(activity, R.drawable.ic_openhab_appicon_24dp)
        item.icon = applyDrawerIconTint(defaultIcon)
        val conn = activity.connection

        if (sitemap.icon == null || conn == null) {
            return
        }
        activity.launch {
            try {
                item.icon = conn.httpClient
                    .get(sitemap.icon.toUrl(activity, activity.determineDataUsagePolicy(conn).loadIconsWithState))
                    .asBitmap(
                        defaultIcon!!.intrinsicWidth,
                        activity.getIconFallbackColor(IconBackground.APP_THEME),
                        ImageConversionPolicy.ForceTargetSize
                    )
                    .response
                    .toDrawable(activity.resources)
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Could not fetch icon for sitemap ${sitemap.name}")
            }
        }
    }

    private fun applyDrawerIconTint(icon: Drawable?): Drawable? {
        if (icon == null) {
            return null
        }
        val wrapped = DrawableCompat.wrap(icon.mutate())
        DrawableCompat.setTintList(wrapped, drawerIconTintList)
        return wrapped
    }

    companion object {
        private val TAG = DrawerController::class.java.simpleName

        // Puts entries of the app after those of Main UI's sidebar
        private const val APP_DRAWER_ITEM_ORDER = 10000

        // IDs of Main UI's entries must not collide with the server entries, which use the server ID
        private const val MAIN_UI_MENU_FIRST_ID = 0x01000000
    }
}
