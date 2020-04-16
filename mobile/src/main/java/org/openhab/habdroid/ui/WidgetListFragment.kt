/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.widget.ContextMenuAwareRecyclerView
import org.openhab.habdroid.ui.widget.RecyclerViewSwipeRefreshLayout
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.SuggestedCommandsFactory
import org.openhab.habdroid.util.ToastType
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.openInBrowser
import org.openhab.habdroid.util.showToast

/**
 * This class is apps' main fragment which displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

class WidgetListFragment : Fragment(), WidgetAdapter.ItemClickListener,
    OpenHabApplication.OnDataSaverActiveStateChangedListener {
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
        adapter = activity.connection?.let { conn -> WidgetAdapter(activity, conn, this) }

        layoutManager = LinearLayoutManager(activity)
        layoutManager.recycleChildrenOnDetach = true

        recyclerView = view.findViewById(R.id.recyclerview)
        recyclerView.setRecycledViewPool(activity.viewPool)
        recyclerView.addItemDecoration(WidgetAdapter.WidgetItemDecoration(view.context))
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        registerForContextMenu(recyclerView)

        refreshLayout = view.findViewById(R.id.swiperefresh)
        refreshLayout.applyColors(R.attr.colorPrimary, R.attr.colorAccent)
        refreshLayout.recyclerView = recyclerView
        refreshLayout.setOnRefreshListener {
            activity.showRefreshHintSnackbarIfNeeded()
            CacheManager.getInstance(activity).clearCache()
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
    }

    override fun onStop() {
        super.onStop()
        (requireContext().applicationContext as OpenHabApplication).unregisterSystemDataSaverStateChangedListener(this)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() $displayPageUrl")
        lastContextMenu?.close()
        startOrStopVisibleViewHolders(false)
    }

    override fun onSystemDataSaverActiveStateChanged(active: Boolean) {
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
            val holder = recyclerView.findViewHolderForAdapterPosition(i)
            if (holder is WidgetAdapter.HeavyDataViewHolder) {
                holder.handleDataSaverChange(active)
            } else if (holder is WidgetAdapter.AbstractMapViewHolder) {
                holder.handleDataSaverChange()
            }
        }
    }

    override fun onItemClicked(widget: Widget): Boolean {
        if (widget.linkedPage != null) {
            val activity = activity as MainActivity?
            activity?.onWidgetSelected(widget.linkedPage, this@WidgetListFragment)
            return true
        }
        return false
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
        val widget = if (info is ContextMenuAwareRecyclerView.RecyclerContextMenuInfo)
            adapter?.getItemForContextMenu(info) else null
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
        val context = context ?: return
        val suggestedCommands = suggestedCommandsFactory.fill(widget)
        val nfcSupported = NfcAdapter.getDefaultAdapter(context) != null || Util.isEmulator()
        val hasCommandOptions = suggestedCommands.commands.isNotEmpty() || suggestedCommands.shouldShowCustom

        // Offer opening website if only one position is set
        if (widget.type == Widget.Type.Mapview && widget.item?.state?.asLocation != null) {
            menu.add(Menu.NONE, CONTEXT_MENU_ID_OPEN_IN_MAPS, Menu.NONE, R.string.open_in_maps)
        }

        if (widget.linkedPage != null) {
            if (nfcSupported) {
                if (hasCommandOptions) {
                    val nfcMenu = menu.addSubMenu(Menu.NONE, CONTEXT_MENU_ID_WRITE_ITEM_TAG, Menu.NONE,
                        R.string.nfc_action_write_command_tag)
                    nfcMenu.setHeaderTitle(R.string.item_picker_dialog_title)
                    populateStatesMenu(nfcMenu, context, suggestedCommands) { state, mappedState ->
                        startActivity(WriteTagActivity.createItemUpdateIntent(
                            context,
                            widget.item?.name ?: return@populateStatesMenu,
                            state,
                            mappedState,
                            widget.label)
                        )
                    }
                }
                menu.add(Menu.NONE, CONTEXT_MENU_ID_WRITE_SITEMAP_TAG, Menu.NONE, R.string.nfc_action_to_sitemap_page)
            }
            if (hasCommandOptions) {
                val widgetMenu = menu.addSubMenu(Menu.NONE, CONTEXT_MENU_ID_CREATE_HOME_SCREEN_WIDGET, Menu.NONE,
                    R.string.create_home_screen_widget_title)
                widgetMenu.setHeaderTitle(R.string.item_picker_dialog_title)
                populateStatesMenu(widgetMenu, context, suggestedCommands) { state, mappedState ->
                    requestPinAppWidget(context, widget, state, mappedState)
                }
            }
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                val shortcutMenu = menu.addSubMenu(Menu.NONE, CONTEXT_MENU_ID_PIN_HOME_MENU, Menu.NONE,
                    R.string.home_shortcut_pin_to_home)
                shortcutMenu.setHeaderTitle(R.string.settings_openhab_theme)
                shortcutMenu.add(
                    Menu.NONE,
                    CONTEXT_MENU_ID_PIN_HOME_WHITE,
                    Menu.NONE,
                    R.string.theme_name_light
                ).setOnMenuItemClickListener {
                    createShortcut(context, widget.linkedPage, true)
                    return@setOnMenuItemClickListener true
                }

                shortcutMenu.add(
                    Menu.NONE,
                    CONTEXT_MENU_ID_PIN_HOME_BLACK,
                    Menu.NONE,
                    R.string.theme_name_dark
                ).setOnMenuItemClickListener {
                    createShortcut(context, widget.linkedPage, false)
                    return@setOnMenuItemClickListener true
                }
            }
        } else if (hasCommandOptions) {
            if (nfcSupported) {
                val nfcMenu = menu.addSubMenu(Menu.NONE, CONTEXT_MENU_ID_WRITE_ITEM_TAG, Menu.NONE,
                    R.string.nfc_action_write_command_tag)
                nfcMenu.setHeaderTitle(R.string.item_picker_dialog_title)
                populateStatesMenu(nfcMenu, context, suggestedCommands) { state, mappedState ->
                    startActivity(WriteTagActivity.createItemUpdateIntent(
                        context,
                        widget.item?.name ?: return@populateStatesMenu,
                        state,
                        mappedState,
                        widget.label)
                    )
                }

                val widgetMenu = menu.addSubMenu(Menu.NONE, CONTEXT_MENU_ID_CREATE_HOME_SCREEN_WIDGET, Menu.NONE,
                    R.string.create_home_screen_widget_title)
                widgetMenu.setHeaderTitle(R.string.item_picker_dialog_title)
                populateStatesMenu(widgetMenu, context, suggestedCommands) { state, mappedState ->
                    requestPinAppWidget(context, widget, state, mappedState)
                }
            } else {
                menu.setHeaderTitle(R.string.create_home_screen_widget_title)
                populateStatesMenu(menu, context, suggestedCommands) { state, mappedState ->
                    requestPinAppWidget(context, widget, state, mappedState)
                }
            }
        }
    }

    private fun populateStatesMenu(
        menu: Menu,
        context: Context,
        suggestedCommands: SuggestedCommandsFactory.SuggestedCommands,
        callback: (state: String, mappedState: String) -> Unit
    ) {
        val listener = object : MenuItem.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem?): Boolean {
                val id = item?.itemId ?: return false
                if (id == CONTEXT_MENU_ID_WRITE_CUSTOM_TAG) {
                    val input = EditText(context)
                    input.inputType = suggestedCommands.inputTypeFlags
                    val customDialog = AlertDialog.Builder(context)
                        .setTitle(getString(R.string.item_picker_custom))
                        .setView(input)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            callback(input.text.toString(), input.text.toString())
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    input.setOnFocusChangeListener { _, hasFocus ->
                        val mode = if (hasFocus)
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        else
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        customDialog.window?.setSoftInputMode(mode)
                    }
                    return true
                } else if (id < suggestedCommands.commands.size) {
                    callback(suggestedCommands.commands[id], suggestedCommands.labels[id])
                    return true
                }
                return false
            }
        }

        suggestedCommands.labels.forEachIndexed { index, label ->
            menu.add(Menu.NONE, index, Menu.NONE, label).setOnMenuItemClickListener(listener)
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
        val itemCount = adapter?.itemCount ?: 0
        for (pos in 0 until itemCount) {
            val holder =
                recyclerView.findViewHolderForAdapterPosition(pos) as WidgetAdapter.ViewHolder?
            holder?.dialogManager?.close()
        }
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

    private fun requestPinAppWidget(context: Context, widget: Widget, state: String, mappedState: String) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val data = ItemUpdateWidget.ItemUpdateWidgetData(
                widget.item?.name ?: return,
                state,
                widget.item.label.orEmpty(),
                null,
                mappedState,
                widget.icon
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
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            val remoteViews = ItemUpdateWidget.getRemoteViews(context, true, null, null, data)
            appWidgetManager.requestPinAppWidget(
                ComponentName(context, ItemUpdateWidget::class.java),
                bundleOf(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW to remoteViews),
                successCallback
            )
        } else {
            context.showToast(R.string.create_home_screen_widget_not_supported, ToastType.ERROR)
        }
    }

    private fun createShortcut(
        context: Context,
        linkedPage: LinkedPage,
        whiteBackground: Boolean
    ) = GlobalScope.launch {
        val connection = ConnectionFactory.usableConnectionOrNull ?: return@launch
        /**
         *  Icon size is defined in {@link AdaptiveIconDrawable}. Foreground size of
         *  46dp instead of 72dp adds enough border to the icon.
         *  46dp foreground + 2 * 31dp border = 108dp
         **/
        val foregroundSize = context.resources.dpToPixel(46F).toInt()
        val iconBitmap = if (linkedPage.icon != null) {
            try {
                connection.httpClient
                    .get(linkedPage.icon.toUrl(context, true))
                    .asBitmap(foregroundSize, true)
                    .response
            } catch (e: HttpClient.HttpException) {
                null
            }
        } else {
            null
        }

        val icon = if (iconBitmap != null) {
            val borderSize = context.resources.dpToPixel(31F)
            val totalFrameWidth = (borderSize * 2).toInt()
            val bitmapWithBackground = Bitmap.createBitmap(
                iconBitmap.width + totalFrameWidth,
                iconBitmap.height + totalFrameWidth,
                iconBitmap.config)
            with(Canvas(bitmapWithBackground)) {
                drawColor(if (whiteBackground) Color.WHITE else Color.DKGRAY)
                drawBitmap(iconBitmap, borderSize, borderSize, null)
            }
            IconCompat.createWithAdaptiveBitmap(bitmapWithBackground)
        } else {
            // Fall back to openHAB icon
            IconCompat.createWithResource(context, R.mipmap.icon)
        }

        val sitemapUri = linkedPage.link.toUri()
        val shortSitemapUri = sitemapUri.path?.substring(14).orEmpty()

        val startIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SITEMAP_SELECTED
            putExtra(MainActivity.EXTRA_SITEMAP_URL, shortSitemapUri)
        }

        val name = if (linkedPage.title.isEmpty()) context.getString(R.string.app_name) else linkedPage.title
        val shortcutInfo = ShortcutInfoCompat.Builder(context,
            shortSitemapUri + '-' + System.currentTimeMillis())
            .setShortLabel(name)
            .setIcon(icon)
            .setIntent(startIntent)
            .build()

        val success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        withContext(Dispatchers.Main) {
            if (success) {
                context.showToast(R.string.home_shortcut_success_pinning, ToastType.SUCCESS)
            } else {
                context.showToast(R.string.home_shortcut_error_pinning, ToastType.ERROR)
            }
        }
    }

    override fun toString(): String {
        return "${super.toString()} [url=$displayPageUrl, title=$title]"
    }

    companion object {
        private val TAG = WidgetListFragment::class.java.simpleName
        private const val CONTEXT_MENU_ID_WRITE_ITEM_TAG = 1000
        private const val CONTEXT_MENU_ID_CREATE_HOME_SCREEN_WIDGET = 1001
        private const val CONTEXT_MENU_ID_WRITE_SITEMAP_TAG = 1002
        private const val CONTEXT_MENU_ID_PIN_HOME_MENU = 1003
        private const val CONTEXT_MENU_ID_PIN_HOME_WHITE = 1004
        private const val CONTEXT_MENU_ID_PIN_HOME_BLACK = 1005
        private const val CONTEXT_MENU_ID_OPEN_IN_MAPS = 1006
        private const val CONTEXT_MENU_ID_WRITE_CUSTOM_TAG = 10000

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
