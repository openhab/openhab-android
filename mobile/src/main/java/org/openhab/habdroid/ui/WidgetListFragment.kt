/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
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
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.widget.RecyclerViewSwipeRefreshLayout
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.SuggestedCommandsFactory
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs
import java.util.ArrayList

/**
 * This class is apps' main fragment which displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

class WidgetListFragment : Fragment(), WidgetAdapter.ItemClickListener {
    @VisibleForTesting lateinit var recyclerView: RecyclerView
    private lateinit var refreshLayout: RecyclerViewSwipeRefreshLayout
    private lateinit var emptyPageView: View
    private lateinit var layoutManager: LinearLayoutManager
    private var adapter: WidgetAdapter? = null
    // parent activity
    private var titleOverride: String? = null
    private var highlightedPageLink: String? = null
    private val suggestedCommandsFactory by lazy {
        SuggestedCommandsFactory(context!!, false)
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

    override fun onStart() {
        Log.d(TAG, "onStart() $displayPageUrl")
        super.onStart()
        val activity = activity as MainActivity
        activity.triggerPageUpdate(displayPageUrl, false)
        startOrStopVisibleViewHolders(true)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() $displayPageUrl")
        startOrStopVisibleViewHolders(false)
    }

    override fun onItemClicked(widget: Widget): Boolean {
        if (widget.linkedPage != null) {
            val activity = activity as MainActivity?
            activity?.onWidgetSelected(widget.linkedPage, this@WidgetListFragment)
            return true
        }
        return false
    }

    override fun onItemLongClicked(widget: Widget): Boolean {
        val context = context ?: return false
        val suggestedCommands = suggestedCommandsFactory.fill(widget)
        val nfcLabels = suggestedCommands.labels
        val nfcCommands = suggestedCommands.commands

        if (suggestedCommands.shouldShowCustom) {
            nfcLabels.add(getString(R.string.item_picker_custom))
        }

        val actionLabels: MutableList<String> = ArrayList()
        val actionCommands: MutableList<String> = ArrayList()

        if (nfcLabels.isNotEmpty()) {
            actionLabels.add(getString(R.string.nfc_action_write_command_tag))
            actionCommands.add(COMMAND_WRITE_COMMAND_TAG)
        }

        if (widget.linkedPage != null) {
            actionLabels.add(getString(R.string.nfc_action_to_sitemap_page))
            actionCommands.add(COMMAND_WRITE_SITEMAP_TAG)
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                actionLabels.add(getString(R.string.home_shortcut_pin_to_home))
                actionCommands.add(COMMAND_CREATE_SHORTCUT)
            }
        }

        // Skip first menu if only item update tag is possible
        if (actionLabels.size == 1 && actionCommands[0] == COMMAND_WRITE_COMMAND_TAG) {
            showNfcStatesMenu(context, suggestedCommands, nfcCommands, nfcLabels, widget)
        } else {
            AlertDialog.Builder(context)
                .setTitle(widget.label)
                .setItems(actionLabels.toTypedArray()) { _, which ->
                    when (actionCommands[which]) {
                        COMMAND_WRITE_COMMAND_TAG -> {
                            showNfcStatesMenu(context, suggestedCommands, nfcCommands, nfcLabels, widget)
                        }
                        COMMAND_WRITE_SITEMAP_TAG -> {
                            widget.linkedPage?.link?.let {
                                startActivity(WriteTagActivity.createSitemapNavigationIntent(context,
                                    widget.linkedPage.link))
                            }
                        }
                        COMMAND_CREATE_SHORTCUT -> widget.linkedPage?.let { createShortcut(context, widget.linkedPage) }
                    }
                }
                .show()
        }

        return true
    }

    private fun showNfcStatesMenu(
        context: Context,
        suggestedCommands: SuggestedCommandsFactory.SuggestedCommands,
        commands: MutableList<String>,
        labels: MutableList<String>,
        widget: Widget
    ) {
        val name = widget.item?.name ?: return
        val labelArray = labels.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.nfc_action_write_command_tag)
            .setItems(labelArray) { _, which ->
                if (which == labelArray.size - 1 && suggestedCommands.shouldShowCustom) {
                    val input = EditText(context)
                    input.inputType = suggestedCommands.inputTypeFlags
                    val customDialog = AlertDialog.Builder(context)
                        .setTitle(getString(R.string.item_picker_custom))
                        .setView(input)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            startActivity(WriteTagActivity.createItemUpdateIntent(context, name, input.text.toString(),
                                input.text.toString(), widget.item.label.orEmpty())) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    input.setOnFocusChangeListener { _, hasFocus ->
                        val mode = if (hasFocus)
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        else
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        customDialog.window?.setSoftInputMode(mode)
                    }
                } else {
                    startActivity(WriteTagActivity.createItemUpdateIntent(context, name, commands[which], labels[which],
                        widget.item.label.orEmpty()))
                }
            }
            .show()
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
        recyclerView.isVisible = widgets.isNotEmpty()
        emptyPageView.isVisible = widgets.isEmpty()
        setHighlightedPageLink(highlightedPageLink)
        refreshLayout.isRefreshing = false
    }

    fun updateWidget(widget: Widget) {
        adapter?.updateWidget(widget)
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

    private fun createShortcut(context: Context, linkedPage: LinkedPage) = GlobalScope.launch {
        val iconFormat = context.getPrefs().getIconFormat()
        val url = Uri.Builder()
            .appendEncodedPath(linkedPage.iconPath)
            .appendQueryParameter("format", iconFormat)
            .toString()
        val connection = ConnectionFactory.usableConnectionOrNull ?: return@launch
        /**
         *  Icon size is defined in {@link AdaptiveIconDrawable}. Foreground size of
         *  46dp instead of 72dp adds enough border to the icon.
         *  46dp foreground + 2 * 31dp border = 108dp
         **/
        val foregroundSize = context.resources.dpToPixel(46F).toInt()
        val icon = try {
            val bitmap = connection.httpClient.get(url).asBitmap(foregroundSize, true).response
            val borderSize = context.resources.dpToPixel(31F)
            val totalFrameWidth = (borderSize * 2).toInt()
            val bitmapWithBackground = Bitmap.createBitmap(
                bitmap.width + totalFrameWidth,
                bitmap.height + totalFrameWidth,
                bitmap.config)
            with(Canvas(bitmapWithBackground)) {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, borderSize, borderSize, null)
            }
            IconCompat.createWithAdaptiveBitmap(bitmapWithBackground)
        } catch (e: HttpClient.HttpException) {
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
                Toasty.success(context, R.string.home_shortcut_success_pinning).show()
            } else {
                Toasty.error(context, R.string.home_shortcut_error_pinning).show()
            }
        }
    }

    override fun toString(): String {
        return "${super.toString()} [url=$displayPageUrl, title=$title]"
    }

    companion object {
        private val TAG = WidgetListFragment::class.java.simpleName
        private const val COMMAND_WRITE_COMMAND_TAG = "command_tag"
        private const val COMMAND_WRITE_SITEMAP_TAG = "sitemap_tag"
        private const val COMMAND_CREATE_SHORTCUT = "shortcut"

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
