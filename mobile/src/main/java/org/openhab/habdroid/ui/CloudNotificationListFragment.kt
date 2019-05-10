/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import okhttp3.Call
import okhttp3.Headers
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.CloudNotification
import org.openhab.habdroid.ui.widget.DividerItemDecoration
import org.openhab.habdroid.util.AsyncHttpClient
import org.openhab.habdroid.util.Util

import java.util.ArrayList
import java.util.Locale

/**
 * Mandatory empty constructor for the fragment manager to instantiate the
 * fragment (e.g. upon screen orientation changes).
 */
class CloudNotificationListFragment : Fragment(), View.OnClickListener, SwipeRefreshLayout.OnRefreshListener {
    // keeps track of current request to cancel it in onPause
    private var requestHandle: Call? = null

    private lateinit var notificationAdapter: CloudNotificationAdapter

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var swipeLayout: SwipeRefreshLayout
    private lateinit var emptyView: View
    private lateinit var emptyWatermark: ImageView
    private lateinit var emptyMessage: TextView
    private lateinit var retryButton: View
    private var loadOffset: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView")
        val view = inflater.inflate(R.layout.fragment_notificationlist, container, false)
        swipeLayout = view.findViewById(R.id.swipe_container)
        swipeLayout.setOnRefreshListener(this)
        Util.applySwipeLayoutColors(swipeLayout, R.attr.colorPrimary, R.attr.colorAccent)

        recyclerView = view.findViewById(android.R.id.list)
        emptyView = view.findViewById(android.R.id.empty)
        emptyMessage = view.findViewById(R.id.empty_message)
        emptyWatermark = view.findViewById(R.id.watermark)
        retryButton = view.findViewById(R.id.retry_button)
        retryButton.setOnClickListener(this)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var context = activity as Context
        notificationAdapter = CloudNotificationAdapter(context, { loadNotifications(false) })
        layoutManager = LinearLayoutManager(activity)

        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(context))
        recyclerView.adapter = notificationAdapter
        Log.d(TAG, "onActivityCreated()")
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
        if (requestHandle != null) {
            requestHandle!!.cancel()
        }
    }

    override fun onRefresh() {
        Log.d(TAG, "onRefresh()")
        loadNotifications(true)
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(TAG, "onDetach()")
    }

    override fun onClick(view: View) {
        if (view === retryButton) {
            loadNotifications(true)
        }
    }

    private fun loadNotifications(clearExisting: Boolean) {
        val conn = ConnectionFactory.getConnection(Connection.TYPE_CLOUD)
        if (conn == null) {
            updateViewVisibility(false, true)
            return
        }
        if (clearExisting) {
            notificationAdapter.clear()
            loadOffset = 0
            updateViewVisibility(true, false)
        }

        // If we're passed an ID to be highlighted initially, we'd theoretically need to load all
        // items instead of loading page-wise. As the initial highlight is only needed for
        // notifications and a new notification is very likely to be contained in the first page,
        // we skip that additional effort.
        val url = String.format(Locale.US, "api/v1/notifications?limit=%d&skip=%d",
                PAGE_SIZE, loadOffset)
        requestHandle = conn.asyncHttpClient.get(url, object : AsyncHttpClient.StringResponseHandler() {
            override fun onSuccess(responseBody: String, headers: Headers) {
                try {
                    val items = ArrayList<CloudNotification>()
                    val jsonArray = JSONArray(responseBody)
                    for (i in 0 until jsonArray.length()) {
                        val sitemapJson = jsonArray.getJSONObject(i)
                        items.add(CloudNotification.fromJson(sitemapJson))
                    }
                    Log.d(TAG, "Notifications request success, got " + items.size + " items")
                    loadOffset += items.size
                    notificationAdapter.addLoadedItems(items, items.size == PAGE_SIZE)
                    handleInitialHighlight()
                    updateViewVisibility(false, false)
                } catch (e: JSONException) {
                    Log.d(TAG, "Notification response could not be parsed", e)
                    updateViewVisibility(false, true)
                }

            }

            override fun onFailure(request: Request, statusCode: Int, error: Throwable) {
                updateViewVisibility(false, true)
                Log.e(TAG, "Notifications request failure", error)
            }
        })
    }

    private fun handleInitialHighlight() {
        val args = arguments
        val highlightedId = args!!.getString("highlightedId")
        if (TextUtils.isEmpty(highlightedId)) {
            return
        }

        val position = notificationAdapter.findPositionForId(highlightedId!!)
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            recyclerView.postDelayed({ notificationAdapter.highlightItem(position) }, 600)
        }

        // highlight only once
        args.remove("highlightedId")
    }

    private fun updateViewVisibility(loading: Boolean, loadError: Boolean) {
        val showEmpty = !loading && (notificationAdapter.itemCount == 0 || loadError)
        recyclerView.visibility = if (showEmpty) View.GONE else View.VISIBLE
        emptyView.visibility = if (showEmpty) View.VISIBLE else View.GONE
        swipeLayout.isRefreshing = loading
        emptyMessage.setText(
                if (loadError) R.string.notification_list_error else R.string.notification_list_empty)
        emptyWatermark.setImageResource(
                if (loadError) R.drawable.ic_connection_error else R.drawable.ic_no_notifications)
        retryButton.visibility = if (loadError) View.VISIBLE else View.GONE
    }

    companion object {
        private val TAG = CloudNotificationListFragment::class.java.simpleName

        private val PAGE_SIZE = 20

        fun newInstance(highlightedId: String?): CloudNotificationListFragment {
            val f = CloudNotificationListFragment()
            val args = Bundle()
            args.putString("highlightedId", highlightedId)
            f.arguments = args
            return f
        }
    }
}
