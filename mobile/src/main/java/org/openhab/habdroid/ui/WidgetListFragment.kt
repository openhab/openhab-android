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

package org.openhab.habdroid.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.widget.ContextMenuAwareRecyclerView
import org.openhab.habdroid.ui.widget.RecyclerViewSwipeRefreshLayout
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.IconBackground
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.PendingIntent_Mutable
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.SuggestedCommandsFactory
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getIconFallbackColor
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getStringOrEmpty
import org.openhab.habdroid.util.openInBrowser
import org.openhab.habdroid.util.useCompactSitemapLayout

/**
 * This class is apps' main fragment which displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

class WidgetListFragment :
    Fragment(),
    WidgetAdapter.ItemClickListener,
    WidgetAdapter.FragmentPresenter,
    AbstractWidgetBottomSheet.ConnectionGetter,
    OpenHabApplication.OnDataUsagePolicyChangedListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    @VisibleForTesting lateinit var recyclerView: RecyclerView
    private lateinit var refreshLayout: RecyclerViewSwipeRefreshLayout
    private lateinit var emptyPageView: View
    private lateinit var layoutManager: LinearLayoutManager
    private var adapter: WidgetAdapter? = null
    private var lastContextMenu: ContextMenu? = null

    // parent activity
    private var titleOverride: String? = null
    private var highlightedPageLink: String? = null
    private val suggestedCommandsFactory by lazy {
        SuggestedCommandsFactory(requireContext(), false)
    }
    val displayPageUrl get() = arguments?.getString("displayPageUrl").orEmpty()
    val title get() = titleOverride ?: arguments?.getString("title")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (titleOverride == null) {
            titleOverride = savedInstanceState?.getString("title")
                ?: arguments?.getString("title")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("title", titleOverride)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_widgetlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated() $displayPageUrl")
        super.onViewCreated(view, savedInstanceState)

        val activity = activity as MainActivity
        adapter = activity.connection?.let { conn ->
            WidgetAdapter(activity, activity.serverProperties!!.flags, conn, this, this)
        }

        layoutManager = LinearLayoutManager(activity)
        layoutManager.recycleChildrenOnDetach = true

        recyclerView = view.findViewById(R.id.recyclerview)
        recyclerView.setRecycledViewPool(activity.viewPool)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        registerForContextMenu(recyclerView)

        refreshLayout = view.findViewById(R.id.swiperefresh)
        refreshLayout.applyColors()
        refreshLayout.recyclerView = recyclerView
        refreshLayout.setOnRefreshListener {
            activity.showRefreshHintSnackbarIfNeeded()
            CacheManager.getInstance(activity).clearCache(false)
            activity.triggerPageUpdate(displayPageUrl, true)
        }

        emptyPageView = view.findViewById(android.R.id.empty)
    }

    override fun onDetach() {
        closeAllDialogs()
        super.onDetach()
    }

    override fun onStart() {
        Log.d(TAG, "onStart() $displayPageUrl")
        super.onStart()
        val activity = activity as MainActivity
        activity.triggerPageUpdate(displayPageUrl, false)
        startOrStopVisibleViewHolders(true)
        (activity.applicationContext as OpenHabApplication).registerSystemDataSaverStateChangedListener(this)

        val prefs = activity.getPrefs()
        adapter?.setCompactMode(prefs.useCompactSitemapLayout())
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        (requireContext().applicationContext as OpenHabApplication).unregisterSystemDataSaverStateChangedListener(this)
        requireActivity().getPrefs().unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() $displayPageUrl")
        lastContextMenu?.close()
        startOrStopVisibleViewHolders(false)
    }

    override fun onDataUsagePolicyChanged() {
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
            val holder = recyclerView.findViewHolderForAdapterPosition(i)
            if (holder is WidgetAdapter.HeavyDataViewHolder) {
                holder.handleDataUsagePolicyChange()
            } else if (holder is WidgetAdapter.AbstractMapViewHolder) {
                holder.handleDataUsagePolicyChange()
            }
        }
        (activity as MainActivity?)?.showDataSaverHintSnackbarIfNeeded()
    }

    override fun onItemClicked(widget: Widget): Boolean {
        if (widget.linkedPage != null) {
            val activity = activity as MainActivity?
            activity?.onWidgetSelected(widget.linkedPage, this@WidgetListFragment)
            return true
        }
        return false
    }

    override fun getConnection(): Connection? = adapter?.connection

    override fun showBottomSheet(sheet: AbstractWidgetBottomSheet, widget: Widget) {
        sheet.arguments = AbstractWidgetBottomSheet.createArguments(widget)
        sheet.show(childFragmentManager, "${sheet.javaClass.simpleName}-${widget.id}")
    }

    override fun showSelectionFragment(fragment: DialogFragment, widget: Widget) {
        fragment.show(childFragmentManager, "Selection-${widget.id}")
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == PrefKeys.SITEMAP_COMPACT_MODE && prefs != null) {
            // Make the adapter reload views according to the new mode
            adapter?.setCompactMode(prefs.useCompactSitemapLayout())
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        if (menuInfo is ContextMenuAwareRecyclerView.RecyclerContextMenuInfo) {
            val widget = adapter?.getItemForContextMenu(menuInfo)
            if (widget != null) {
                lastContextMenu = menu
                populateContextMenu(widget, menu)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val context = context
        val info = item.menuInfo
        val widget = if (info is ContextMenuAwareRecyclerView.RecyclerContextMenuInfo) {
            adapter?.getItemForContextMenu(info)
        } else {
            null
        }
        if (widget != null && context != null) {
            when (item.itemId) {
                CONTEXT_MENU_ID_WRITE_SITEMAP_TAG -> {
                    widget.linkedPage?.link?.let {
                        startActivity(WriteTagActivity.createSitemapNavigationIntent(context, it))
                    }
                    return true
                }
                CONTEXT_MENU_ID_OPEN_IN_MAPS -> {
                    widget.item?.state?.asLocation?.toMapsUrl()?.toUri().openInBrowser(context)
                    return true
                }
            }
        }
        return super.onContextItemSelected(item)
    }

    private fun populateContextMenu(widget: Widget, menu: ContextMenu) {
        val activity = (activity as AbstractBaseActivity?) ?: return
        val suggestedCommands = suggestedCommandsFactory.fill(widget)
        val nfcSupported = NfcAdapter.getDefaultAdapter(activity) != null || Util.isEmulator()
        val hasCommandOptions = suggestedCommands.entries.isNotEmpty() || suggestedCommands.shouldShowCustom

        // Offer opening website if only one position is set
        if (widget.type == Widget.Type.Mapview && widget.item?.state?.asLocation != null) {
            menu.add(Menu.NONE, CONTEXT_MENU_ID_OPEN_IN_MAPS, Menu.NONE, R.string.open_in_maps)
        }

        // Offer widget for all Items. For read-only Items the "Show state" widget is useful.
        if (widget.item != null) {
            menu.add(
                Menu.NONE,
                CONTEXT_MENU_ID_SHOW_CHART,
                Menu.NONE,
                R.string.analyse
            ).setOnMenuItemClickListener {
                val mainActivity = activity as MainActivity
                val serverFlags = mainActivity.serverProperties?.flags ?: 0
                val intent = mainActivity.getChartDetailsActivityIntent(widget, serverFlags)
                mainActivity.startActivity(intent)
                return@setOnMenuItemClickListener true
            }

            val widgetMenu = menu.addSubMenu(
                Menu.NONE,
                CONTEXT_MENU_ID_CREATE_HOME_SCREEN_WIDGET,
                Menu.NONE,
                R.string.create_home_screen_widget_title
            )
            widgetMenu.setHeaderTitle(R.string.item_picker_dialog_title)
            populateStatesMenu(widgetMenu, activity, suggestedCommands, false) { state, mappedState, _ ->
                requestPinAppWidget(
                    context = activity,
                    widget = widget,
                    state = state,
                    mappedState = mappedState,
                    showState = false
                )
            }
            widgetMenu.add(Menu.NONE, Int.MAX_VALUE, Menu.NONE, R.string.create_home_screen_widget_no_command)
                .setOnMenuItemClickListener {
                    requestPinAppWidget(
                        context = activity,
                        widget = widget,
                        state = "",
                        mappedState = "",
                        showState = true
                    )
                    true
                }
        }

        if (widget.linkedPage != null && ShortcutManagerCompat.isRequestPinShortcutSupported(activity)) {
            val shortcutMenu = menu.addSubMenu(
                Menu.NONE,
                CONTEXT_MENU_ID_PIN_HOME_MENU,
                Menu.NONE,
                R.string.home_shortcut_pin_to_home
            )
            shortcutMenu.setHeaderTitle(R.string.settings_openhab_theme)
            shortcutMenu.add(
                Menu.NONE,
                CONTEXT_MENU_ID_PIN_HOME_WHITE,
                Menu.NONE,
                R.string.theme_name_light
            ).setOnMenuItemClickListener {
                createShortcut(activity, widget.linkedPage, true)
                return@setOnMenuItemClickListener true
            }

            shortcutMenu.add(
                Menu.NONE,
                CONTEXT_MENU_ID_PIN_HOME_BLACK,
                Menu.NONE,
                R.string.theme_name_dark
            ).setOnMenuItemClickListener {
                createShortcut(activity, widget.linkedPage, false)
                return@setOnMenuItemClickListener true
            }
        }

        if (hasCommandOptions && nfcSupported) {
            val nfcMenu = menu.addSubMenu(
                Menu.NONE,
                CONTEXT_MENU_ID_WRITE_ITEM_TAG,
                Menu.NONE,
                R.string.nfc_action_write_command_tag
            )
            nfcMenu.setHeaderTitle(R.string.item_picker_dialog_title)
            populateStatesMenu(
                nfcMenu,
                activity,
                suggestedCommands,
                suggestedCommands.shouldShowCustom
            ) { state, mappedState, itemId ->
                startActivity(
                    WriteTagActivity.createItemUpdateIntent(
                        activity,
                        widget.item?.name ?: return@populateStatesMenu,
                        state,
                        mappedState,
                        widget.label,
                        itemId == CONTEXT_MENU_ID_WRITE_DEVICE_ID
                    )
                )
            }
        }

        if (widget.linkedPage != null && nfcSupported) {
            menu.add(Menu.NONE, CONTEXT_MENU_ID_WRITE_SITEMAP_TAG, Menu.NONE, R.string.nfc_action_to_sitemap_page)
        }

        widget.item?.let {
            menu.add(Menu.NONE, CONTEXT_MENU_ID_COPY_ITEM_NAME, Menu.NONE, R.string.show_and_copy_item_name)
                .setOnMenuItemClickListener {
                    val itemName = widget.item.name
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        // Avoid duplicate notifications
                        // https://developer.android.com/develop/ui/views/touch-and-input/copy-paste?hl=en#duplicate-notifications
                        Snackbar.make(
                            activity.findViewById(android.R.id.content),
                            activity.getString(R.string.copied_item_name, itemName),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    val clipboardManager = activity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText(activity.getString(R.string.app_name), itemName)
                    clipboardManager.setPrimaryClip(clipData)
                    true
                }
        }
    }

    private fun populateStatesMenu(
        menu: Menu,
        context: Context,
        suggestedCommands: SuggestedCommandsFactory.SuggestedCommands,
        showDeviceId: Boolean,
        callback: (state: String, mappedState: String, itemId: Int) -> Unit
    ) {
        val listener = object : MenuItem.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem): Boolean {
                val id = item.itemId
                when {
                    id == CONTEXT_MENU_ID_WRITE_CUSTOM_TAG -> {
                        val input = EditText(context)
                        input.inputType = suggestedCommands.inputTypeFlags
                        val customDialog = AlertDialog.Builder(context)
                            .setTitle(getString(R.string.item_picker_custom))
                            .setView(input)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                callback(input.text.toString(), input.text.toString(), id)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        input.setOnFocusChangeListener { _, hasFocus ->
                            val mode = if (hasFocus) {
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                            } else {
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                            }
                            customDialog.window?.setSoftInputMode(mode)
                        }
                        return true
                    }
                    id == CONTEXT_MENU_ID_WRITE_DEVICE_ID -> {
                        callback(context.getPrefs().getStringOrEmpty(PrefKeys.DEV_ID), "", id)
                        return true
                    }
                    id < suggestedCommands.entries.size -> {
                        val entry = suggestedCommands.entries[id]
                        callback(entry.command, entry.label, id)
                        return true
                    }
                    else -> return false
                }
            }
        }

        suggestedCommands.entries.forEachIndexed { index, entry ->
            menu.add(Menu.NONE, index, Menu.NONE, entry.label).setOnMenuItemClickListener(listener)
        }

        val deviceId = context.getPrefs().getStringOrEmpty(PrefKeys.DEV_ID)
        if (showDeviceId && deviceId.isNotEmpty()) {
            menu.add(
                Menu.NONE,
                CONTEXT_MENU_ID_WRITE_DEVICE_ID,
                Menu.NONE,
                getString(R.string.device_identifier_suggested_command_nfc_tag, deviceId)
            ).setOnMenuItemClickListener(listener)
        }

        if (suggestedCommands.shouldShowCustom) {
            menu.add(Menu.NONE, CONTEXT_MENU_ID_WRITE_CUSTOM_TAG, Menu.NONE, R.string.item_picker_custom)
                .setOnMenuItemClickListener(listener)
        }
    }

    fun setHighlightedPageLink(highlightedPageLink: String?) {
        this.highlightedPageLink = highlightedPageLink
        val adapter = adapter ?: return

        val position = adapter.itemList.indexOfFirst { w ->
            w.linkedPage != null && w.linkedPage.link == highlightedPageLink
        }
        if (adapter.setSelectedPosition(position) && position >= 0) {
            layoutManager.scrollToPosition(position)
        }
    }

    fun updateTitle(pageTitle: String) {
        titleOverride = pageTitle.replace("[\\[\\]]".toRegex(), "")
        val activity = activity as MainActivity?
        activity?.updateTitle()
    }

    fun updateWidgets(widgets: List<Widget>) {
        val adapter = adapter ?: return
        adapter.update(widgets, refreshLayout.isRefreshing)
        updateUiState(adapter)
        setHighlightedPageLink(highlightedPageLink)
        refreshLayout.isRefreshing = false
    }

    fun updateWidget(widget: Widget) {
        adapter?.let {
            it.updateWidget(widget)
            updateUiState(it)
        }
    }

    private fun updateUiState(adapter: WidgetAdapter) {
        recyclerView.isVisible = adapter.hasVisibleWidgets
        emptyPageView.isVisible = !recyclerView.isVisible
    }

    fun closeAllDialogs() {
        // XXX: use child fragment manager
    }

    private fun startOrStopVisibleViewHolders(start: Boolean) {
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
            val holder = recyclerView.findViewHolderForAdapterPosition(i) as WidgetAdapter.ViewHolder?
            if (start) {
                holder?.start()
            } else {
                holder?.stop()
            }
        }
    }

    private fun requestPinAppWidget(
        context: Context,
        widget: Widget,
        state: String,
        mappedState: String,
        showState: Boolean
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val widgetLabel = widget.item?.label.orEmpty()
            val data = ItemUpdateWidget.ItemUpdateWidgetData(
                widget.item?.name ?: return,
                state,
                widgetLabel,
                getString(R.string.item_update_widget_text, widgetLabel, mappedState),
                mappedState,
                widget.icon?.withCustomState(""),
                showState
            )

            val callbackIntent = Intent(context, ItemUpdateWidget::class.java).apply {
                action = ItemUpdateWidget.ACTION_CREATE_WIDGET
                putExtra(
                    ItemUpdateWidget.EXTRA_BUNDLE,
                    bundleOf(ItemUpdateWidget.EXTRA_DATA to data)
                )
            }

            val successCallback = PendingIntent.getBroadcast(
                context,
                0,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent_Mutable
            )

            val remoteViews = ItemUpdateWidget.getRemoteViews(
                context,
                true,
                null,
                null,
                data,
                widget.state?.asString.orEmpty()
            )
            appWidgetManager.requestPinAppWidget(
                ComponentName(context, ItemUpdateWidget::class.java),
                bundleOf(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW to remoteViews),
                successCallback
            )
        } else {
            (activity as? MainActivity)?.showSnackbar(
                MainActivity.SNACKBAR_TAG_SHORTCUT_INFO,
                R.string.create_home_screen_widget_not_supported,
                Snackbar.LENGTH_LONG
            )
        }
    }

    private fun createShortcut(activity: AbstractBaseActivity, linkedPage: LinkedPage, whiteBackground: Boolean) =
        activity.launch {
            val connection = ConnectionFactory.activeUsableConnection?.connection ?: return@launch

            /**
             *  Icon size is defined in {@link AdaptiveIconDrawable}. Foreground size of
             *  46dp instead of 72dp adds enough border to the icon.
             *  46dp foreground + 2 * 31dp border = 108dp
             **/
            val foregroundSize = activity.resources.dpToPixel(46F).toInt()
            val iconBitmap = if (linkedPage.icon != null) {
                try {
                    val iconFallbackColor = activity.getIconFallbackColor(
                        if (whiteBackground) IconBackground.LIGHT else IconBackground.DARK
                    )
                    connection.httpClient
                        .get(linkedPage.icon.toUrl(activity, true))
                        .asBitmap(foregroundSize, iconFallbackColor, ImageConversionPolicy.ForceTargetSize)
                        .response
                } catch (e: HttpClient.HttpException) {
                    null
                }
            } else {
                null
            }

            val bitmapConfig = iconBitmap?.config
            val icon = if (bitmapConfig != null) {
                val borderSize = activity.resources.dpToPixel(31F)
                val totalFrameWidth = (borderSize * 2).toInt()
                val bitmapWithBackground = Bitmap.createBitmap(
                    iconBitmap.width + totalFrameWidth,
                    iconBitmap.height + totalFrameWidth,
                    bitmapConfig
                )
                with(Canvas(bitmapWithBackground)) {
                    drawColor(if (whiteBackground) Color.WHITE else Color.DKGRAY)
                    drawBitmap(iconBitmap, borderSize, borderSize, null)
                }
                IconCompat.createWithAdaptiveBitmap(bitmapWithBackground)
            } else {
                // Fall back to openHAB icon
                IconCompat.createWithResource(activity, R.mipmap.icon)
            }

            val sitemapUri = linkedPage.link.toUri()
            val shortSitemapUri = sitemapUri.path?.substring(14).orEmpty()

            val startIntent = Intent(activity, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SITEMAP_SELECTED
                putExtra(MainActivity.EXTRA_SITEMAP_URL, shortSitemapUri)
                putExtra(MainActivity.EXTRA_SERVER_ID, activity.getPrefs().getActiveServerId())
            }

            val name = if (linkedPage.title.isEmpty()) activity.getString(R.string.app_name) else linkedPage.title
            val shortcutInfo = ShortcutInfoCompat.Builder(activity, shortSitemapUri + '-' + System.currentTimeMillis())
                .setShortLabel(name)
                .setIcon(icon)
                .setIntent(startIntent)
                .setAlwaysBadged()
                .build()

            val success = ShortcutManagerCompat.requestPinShortcut(activity, shortcutInfo, null)
            withContext(Dispatchers.Main) {
                if (success) {
                    (activity as? MainActivity)?.showSnackbar(
                        MainActivity.SNACKBAR_TAG_SHORTCUT_INFO,
                        R.string.home_shortcut_success_pinning,
                        Snackbar.LENGTH_SHORT
                    )
                } else {
                    (activity as? MainActivity)?.showSnackbar(
                        MainActivity.SNACKBAR_TAG_SHORTCUT_INFO,
                        R.string.home_shortcut_error_pinning,
                        Snackbar.LENGTH_LONG
                    )
                }
            }
        }

    override fun toString() = "${super.toString()} [url=$displayPageUrl, title=$title]"

    companion object {
        private val TAG = WidgetListFragment::class.java.simpleName
        private const val CONTEXT_MENU_ID_WRITE_ITEM_TAG = 1000
        private const val CONTEXT_MENU_ID_CREATE_HOME_SCREEN_WIDGET = 1001
        private const val CONTEXT_MENU_ID_WRITE_SITEMAP_TAG = 1002
        private const val CONTEXT_MENU_ID_PIN_HOME_MENU = 1003
        private const val CONTEXT_MENU_ID_PIN_HOME_WHITE = 1004
        private const val CONTEXT_MENU_ID_PIN_HOME_BLACK = 1005
        private const val CONTEXT_MENU_ID_OPEN_IN_MAPS = 1006
        private const val CONTEXT_MENU_ID_COPY_ITEM_NAME = 1007
        private const val CONTEXT_MENU_ID_SHOW_CHART = 1008
        private const val CONTEXT_MENU_ID_WRITE_CUSTOM_TAG = 10000
        private const val CONTEXT_MENU_ID_WRITE_DEVICE_ID = 10001

        fun withPage(pageUrl: String, pageTitle: String?): WidgetListFragment {
            val fragment = WidgetListFragment()
            fragment.arguments = bundleOf(
                "displayPageUrl" to pageUrl,
                "title" to pageTitle
            )
            return fragment
        }
    }
}
