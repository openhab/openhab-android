/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
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
import org.openhab.habdroid.util.*

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

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_widgetlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated() $displayPageUrl")
        super.onViewCreated(view, savedInstanceState)

        val activity = activity as MainActivity
        adapter = WidgetAdapter(activity, activity.connection!!, this)

        layoutManager = LinearLayoutManager(activity)
        layoutManager.recycleChildrenOnDetach = true

        recyclerView = view.findViewById(R.id.recyclerview)
        recyclerView.setRecycledViewPool(activity.viewPool)
        recyclerView.addItemDecoration(WidgetAdapter.WidgetItemDecoration(view.context))
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

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
        val labels = suggestedCommands.labels
        val commands = suggestedCommands.commands
        if (widget.linkedPage != null) {
            labels.add(getString(R.string.nfc_action_to_sitemap_page))
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                labels.add(getString(R.string.home_shortcut_pin_to_home))
            }
        }

        if (labels.isEmpty()) {
            return false
        }

        AlertDialog.Builder(context)
                .setTitle(R.string.nfc_dialog_title)
                .setItems(labels.toTypedArray()) { _, which ->
                    if (which == commands.size + 1 && widget.linkedPage != null) {
                        createShortcut(context, widget.linkedPage)
                    } else {
                        val itemToHandle = if (which < commands.size) widget.item else null
                        val linkToHandle = if (which == commands.size) widget.linkedPage?.link else null
                        if (itemToHandle != null) {
                            startActivity(WriteTagActivity.createItemUpdateIntent(context,
                                    itemToHandle.name, commands[which],
                                    labels[which], itemToHandle.label.orEmpty()))
                        } else if (linkToHandle != null) {
                            startActivity(WriteTagActivity.createSitemapNavigationIntent(
                                    context, linkToHandle))
                        }
                    }
                }
                .show()

        return true
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
            with (Canvas(bitmapWithBackground)) {
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
        context.startActivity(startIntent)

        val name = if (linkedPage.title.isNullOrEmpty())
            context.getString(R.string.app_name) else linkedPage.title
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
