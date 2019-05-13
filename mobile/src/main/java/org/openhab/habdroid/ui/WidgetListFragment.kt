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
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.widget.RecyclerViewSwipeRefreshLayout
import org.openhab.habdroid.util.CacheManager

import java.util.ArrayList
import java.util.Locale

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

    val displayPageUrl: String
        get() = arguments?.getString("displayPageUrl") ?: ""

    val title: String?
        get() = titleOverride ?: arguments?.getString("title")

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

    override fun onItemClicked(item: Widget): Boolean {
        if (item.linkedPage != null) {
            val activity = activity as MainActivity?
            activity?.onWidgetSelected(item.linkedPage, this@WidgetListFragment)
            return true
        }
        return false
    }

    override fun onItemLongClicked(item: Widget) {
        val widget = item
        val labels = ArrayList<String>()
        val commands = ArrayList<String>()

        if (widget.item != null) {
            // If the widget has mappings, we will populate names and commands with
            // values from those mappings
            if (widget.hasMappingsOrItemOptions()) {
                for ((value, label) in widget.mappingsOrItemOptions) {
                    labels.add(label)
                    commands.add(value)
                }
                // Else we only can do it for Switch widget with On/Off/Toggle commands
            } else if (widget.type === Widget.Type.Switch) {
                val item = widget.item
                if (item.isOfTypeOrGroupType(Item.Type.Switch)) {
                    labels.add(getString(R.string.nfc_action_on))
                    commands.add("ON")
                    labels.add(getString(R.string.nfc_action_off))
                    commands.add("OFF")
                    labels.add(getString(R.string.nfc_action_toggle))
                    commands.add("TOGGLE")
                } else if (item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
                    labels.add(getString(R.string.nfc_action_up))
                    commands.add("UP")
                    labels.add(getString(R.string.nfc_action_down))
                    commands.add("DOWN")
                    labels.add(getString(R.string.nfc_action_toggle))
                    commands.add("TOGGLE")
                }
            } else if (widget.type === Widget.Type.Colorpicker) {
                labels.add(getString(R.string.nfc_action_on))
                commands.add("ON")
                labels.add(getString(R.string.nfc_action_off))
                commands.add("OFF")
                labels.add(getString(R.string.nfc_action_toggle))
                commands.add("TOGGLE")
                if (widget.state != null) {
                    labels.add(getString(R.string.nfc_action_current_color))
                    commands.add(widget.state.asString)
                }
            } else if (widget.type === Widget.Type.Setpoint || widget.type === Widget.Type.Slider) {
                val state = widget.state?.asNumber
                if (state != null) {
                    val currentState = state.toString()
                    labels.add(currentState)
                    commands.add(currentState)

                    val minValue = ParsedState.NumberState.withValue(state, widget.minValue).toString()
                    if (currentState != minValue) {
                        labels.add(minValue)
                        commands.add(minValue)
                    }

                    val maxValue = ParsedState.NumberState.withValue(state, widget.maxValue).toString()
                    if (currentState != maxValue) {
                        labels.add(maxValue)
                        commands.add(maxValue)
                    }

                    if (widget.switchSupport) {
                        labels.add(getString(R.string.nfc_action_on))
                        commands.add("ON")
                        labels.add(getString(R.string.nfc_action_off))
                        commands.add("OFF")
                    }
                }
            }
        }
        if (widget.linkedPage != null) {
            labels.add(getString(R.string.nfc_action_to_sitemap_page))
        }

        if (!labels.isEmpty()) {
            AlertDialog.Builder(activity)
                    .setTitle(R.string.nfc_dialog_title)
                    .setItems(labels.toTypedArray()) { dialog, which ->
                        val item = if (which < commands.size) widget.item else null
                        val link = if (which == commands.size) widget.linkedPage?.link else null
                        val writeTagIntent = if (item != null)
                            WriteTagActivity.createItemUpdateIntent(activity!!,
                                    item.name, commands[which], labels[which], item.label ?: "")
                        else if (link != null)
                            WriteTagActivity.createSitemapNavigationIntent(activity!!, link)
                        else
                            null
                        startActivityForResult(writeTagIntent, 0);
                    }
                    .show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_widgetlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated() " + displayPageUrl)
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
        Log.d(TAG, "onStart() " + displayPageUrl)
        super.onStart()
        val activity = activity as MainActivity
        activity.triggerPageUpdate(displayPageUrl, false)
        startOrStopVisibleViewHolders(true)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() " + displayPageUrl)
        startOrStopVisibleViewHolders(false)
    }

    fun setHighlightedPageLink(highlightedPageLink: String?) {
        this.highlightedPageLink = highlightedPageLink
        val adapter = adapter ?: return

        val position = if (highlightedPageLink != null) {
            adapter.itemList.indexOfFirst { w -> w.linkedPage != null && w.linkedPage.link == highlightedPageLink }
        } else {
            -1
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
        recyclerView.isVisible = !widgets.isEmpty()
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

    override fun toString(): String {
        return String.format(Locale.US, "%s [url=%s, title=%s]",
                super.toString(), displayPageUrl, title)
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
