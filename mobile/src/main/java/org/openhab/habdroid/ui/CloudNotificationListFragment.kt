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

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.faltenreich.skeletonlayout.SkeletonLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.CloudMessage
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.toCloudMessage
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.map

/**
 * Mandatory empty constructor for the fragment manager to instantiate the
 * fragment (e.g. upon screen orientation changes).
 */
class CloudNotificationListFragment :
    Fragment(),
    View.OnClickListener,
    SwipeRefreshLayout.OnRefreshListener {
    lateinit var recyclerView: RecyclerView
    private lateinit var skeleton: SkeletonLayout
    private lateinit var swipeLayout: SwipeRefreshLayout
    private lateinit var retryButton: Button
    private lateinit var emptyView: View
    private lateinit var emptyWatermark: ImageView
    private lateinit var emptyMessage: TextView

    // keeps track of current request to cancel it in onPause
    private var requestJob: Job? = null
    private lateinit var adapter: CloudNotificationAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var loadOffset: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView")
        val view = inflater.inflate(R.layout.fragment_notificationlist, container, false)

        recyclerView = view.findViewById(android.R.id.list)
        emptyView = view.findViewById(android.R.id.empty)
        emptyMessage = view.findViewById(R.id.empty_message)
        emptyWatermark = view.findViewById(R.id.watermark)
        skeleton = view.findViewById(R.id.skeletonLayout)
        view.findViewById<LinearLayout>(R.id.skeletonList).apply {
            repeat(10) {
                addView(inflater.inflate(R.layout.notificationlist_item, null))
            }
        }
        skeleton.showSkeleton()

        swipeLayout = view.findViewById(R.id.swipe_container)
        swipeLayout.setOnRefreshListener(this)
        swipeLayout.applyColors()

        retryButton = view.findViewById(R.id.retry_button)
        retryButton.setOnClickListener(this)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CloudNotificationAdapter(view.context) { loadNotifications(false) }
        layoutManager = LinearLayoutManager(view.context)

        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        loadNotifications(true)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
        // Cancel request for notifications if there was any
        requestJob?.cancel()
    }

    override fun onRefresh() {
        Log.d(TAG, "onRefresh()")
        swipeLayout.isRefreshing = false
        loadNotifications(true)
    }

    override fun onClick(view: View) {
        if (view === retryButton) {
            loadNotifications(true)
        }
    }

    private fun loadNotifications(clearExisting: Boolean) {
        val activity = activity as AbstractBaseActivity? ?: return
        val conn = if (usePrimaryServer()) {
            ConnectionFactory.primaryCloudConnection?.connection
        } else {
            ConnectionFactory.activeCloudConnection?.connection
        }
        if (conn == null) {
            updateViewVisibility(loading = false, loadError = true)
            return
        }
        if (clearExisting) {
            adapter.clear()
            loadOffset = 0
            updateViewVisibility(loading = true, loadError = false)
        }

        // If we're passed an ID to be highlighted initially, we'd theoretically need to load all
        // items instead of loading page-wise. As the initial highlight is only needed for
        // notifications and a new notification is very likely to be contained in the first page,
        // we skip that additional effort.
        val url = "api/v1/notifications?limit=$PAGE_SIZE&skip=$loadOffset"
        requestJob = activity.launch {
            try {
                val response = conn.httpClient.get(url).asText().response
                val allItems = JSONArray(response).map { obj -> obj.toCloudMessage() }
                val filteredItems = allItems.filterIsInstance<CloudMessage.CloudNotification>()
                Log.d(TAG, "Notifications request success, got ${allItems.size} items")
                loadOffset += allItems.size
                adapter.addLoadedItems(filteredItems, allItems.size == PAGE_SIZE)
                handleInitialHighlight()
                updateViewVisibility(loading = false, loadError = false)
            } catch (e: JSONException) {
                updateViewVisibility(loading = false, loadError = true)
                Log.d(TAG, "Notification response could not be parsed", e)
            } catch (e: HttpClient.HttpException) {
                updateViewVisibility(loading = false, loadError = true)
                Log.e(TAG, "Notifications request failure", e)
            }
        }
    }

    private fun handleInitialHighlight() {
        val highlightedId = arguments?.getString("highlightedId") ?: return
        val position = adapter.findPositionForId(highlightedId)
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            recyclerView.postDelayed({ adapter.highlightItem(position) }, 600)
        }

        // highlight only once
        arguments?.remove("highlightedId")
    }

    private fun updateViewVisibility(loading: Boolean, loadError: Boolean) {
        val showEmpty = !loading && (adapter.itemCount == 0 || loadError)
        recyclerView.isVisible = !showEmpty
        emptyView.isVisible = showEmpty
        skeleton.isVisible = loading
        emptyMessage.setText(
            if (loadError) R.string.notification_list_error else R.string.notification_list_empty
        )
        emptyWatermark.setImageResource(
            if (loadError) R.drawable.ic_connection_error else R.drawable.ic_no_notifications
        )
        retryButton.isVisible = loadError
    }

    private fun usePrimaryServer() = requireArguments().getBoolean("primary")

    fun getTitle(context: Context): String {
        val prefs = context.getPrefs()
        var title = context.getString(R.string.app_notifications)
        if (prefs.getConfiguredServerIds().size > 1) {
            val serverId = if (usePrimaryServer()) prefs.getPrimaryServerId() else prefs.getActiveServerId()
            val serverName = ServerConfiguration.load(prefs, context.getSecretPrefs(), serverId)?.name
            title = context.getString(R.string.ui_on_server, title, serverName)
        }

        return title
    }

    companion object {
        const val PAGE_SIZE = 50

        private val TAG = CloudNotificationListFragment::class.java.simpleName

        fun newInstance(highlightedId: String?, primaryServer: Boolean): CloudNotificationListFragment {
            val f = CloudNotificationListFragment()
            f.arguments = bundleOf("highlightedId" to highlightedId, "primary" to primaryServer)
            return f
        }
    }
}
