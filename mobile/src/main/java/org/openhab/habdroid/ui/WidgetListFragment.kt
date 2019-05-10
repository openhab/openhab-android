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
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.widget.RecyclerViewSwipeRefreshLayout
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.Util

import java.util.ArrayList
import java.util.Locale

/**
 * This class is apps' main fragment which displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

class WidgetListFragment : Fragment(), WidgetAdapter.ItemClickListener {
    @VisibleForTesting lateinit var recyclerView: RecyclerView
    private lateinit var emptyPageView: LinearLayout
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: WidgetAdapter
    // Url of current sitemap page displayed
    private lateinit var pageUrl: String
    // parent activity
    private lateinit var mainActivity: MainActivity
    private var titleOverride: String? = null
    private lateinit var refreshLayout: RecyclerViewSwipeRefreshLayout
    private var highlightedPageLink: String? = null

    val displayPageUrl: String
        get() = arguments!!.getString("displayPageUrl")

    val title: String
        get() = if (titleOverride != null) titleOverride!! else arguments!!.getString("title")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
        if (titleOverride == null) {
            if (savedInstanceState != null) {
                titleOverride = savedInstanceState.getString("title")
            } else {
                titleOverride = args!!.getString("title")
            }
        }
        pageUrl = args!!.getString("displayPageUrl")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d(TAG, "onActivityCreated() " + pageUrl)
        mainActivity = activity as MainActivity

        adapter = WidgetAdapter(mainActivity, mainActivity.connection!!, this)

        layoutManager = LinearLayoutManager(mainActivity)
        layoutManager.recycleChildrenOnDetach = true

        recyclerView.setRecycledViewPool(mainActivity.viewPool)
        recyclerView.addItemDecoration(WidgetAdapter.WidgetItemDecoration(mainActivity))
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("title", titleOverride)
    }

    override fun onItemClicked(widget: Widget): Boolean {
        if (widget.linkedPage != null) {
            mainActivity.onWidgetSelected(widget.linkedPage, this@WidgetListFragment)
            return true
        }
        return false
    }

    override fun onItemLongClicked(widget: Widget) {
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
                val state = widget?.state?.asNumber
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
        Log.d(TAG, "onViewCreated() " + pageUrl)
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerview)
        emptyPageView = view.findViewById(android.R.id.empty)
        refreshLayout = view.findViewById(R.id.swiperefresh)

        Util.applySwipeLayoutColors(refreshLayout, R.attr.colorPrimary, R.attr.colorAccent)
        refreshLayout.recyclerView = recyclerView
        refreshLayout.setOnRefreshListener {
            mainActivity.showRefreshHintSnackbarIfNeeded()
            CacheManager.getInstance(activity!!).clearCache()
            mainActivity.triggerPageUpdate(pageUrl, true)
        }
    }

    override fun onStart() {
        Log.d(TAG, "onStart() " + pageUrl)
        super.onStart()
        mainActivity.triggerPageUpdate(pageUrl, false)
        startOrStopVisibleViewHolders(true)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() " + pageUrl)
        startOrStopVisibleViewHolders(false)
    }

    fun setHighlightedPageLink(highlightedPageLink: String?) {
        this.highlightedPageLink = highlightedPageLink
        if (adapter == null) {
            return
        }
        if (highlightedPageLink != null) {
            for (i in 0 until adapter.itemCount) {
                val page = adapter.getItem(i).linkedPage
                if (page != null && highlightedPageLink == page.link) {
                    if (adapter.setSelectedPosition(i)) {
                        layoutManager.scrollToPosition(i)
                    }
                    return
                }
            }
        }
        // We didn't find a matching page link, so unselect everything
        adapter.setSelectedPosition(-1)
    }

    fun updateTitle(pageTitle: String) {
        titleOverride = pageTitle.replace("[\\[\\]]".toRegex(), "")
        if (mainActivity != null) {
            mainActivity.updateTitle()
        }
    }

    fun updateWidgets(widgets: List<Widget>) {
        if (adapter == null) {
            return
        }
        adapter.update(widgets, refreshLayout.isRefreshing)
        val emptyPage = widgets.size == 0
        recyclerView.visibility = if (emptyPage) View.GONE else View.VISIBLE
        emptyPageView.visibility = if (emptyPage) View.VISIBLE else View.GONE
        setHighlightedPageLink(highlightedPageLink)
        refreshLayout.isRefreshing = false
    }

    fun updateWidget(widget: Widget) {
        if (adapter != null) {
            adapter.updateWidget(widget)
        }
    }

    private fun startOrStopVisibleViewHolders(start: Boolean) {
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
            val holder = recyclerView.findViewHolderForAdapterPosition(i) as WidgetAdapter.ViewHolder?
            if (holder != null) {
                if (start) {
                    holder.start()
                } else {
                    holder.stop()
                }
            }
        }
    }

    override fun toString(): String {
        return String.format(Locale.US, "%s [url=%s, title=%s]",
                super.toString(), pageUrl, title)
    }

    companion object {
        private val TAG = WidgetListFragment::class.java.simpleName

        fun withPage(pageUrl: String, pageTitle: String): WidgetListFragment {
            val fragment = WidgetListFragment()
            val args = Bundle()
            args.putString("displayPageUrl", pageUrl)
            args.putString("title", pageTitle)
            fragment.arguments = args
            return fragment
        }
    }
}
