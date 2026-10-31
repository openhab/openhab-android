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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.FragmentNotificationlistBinding
import org.openhab.habdroid.model.CloudMessage
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.toCloudMessage
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.map

/**
 * Mandatory empty constructor for the fragment manager to instantiate the
 * fragment (e.g. upon screen orientation changes).
 */
class CloudNotificationListFragment : Fragment() {
    private lateinit var binding: FragmentNotificationlistBinding
    val recyclerView get() = binding.list

    // keeps track of current request to cancel it in onPause
    private var requestJob: Job? = null
    private lateinit var adapter: CloudNotificationAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView")
        binding = FragmentNotificationlistBinding.inflate(inflater, container, false)

        binding.skeletonList.apply {
            repeat(10) {
                addView(inflater.inflate(R.layout.notificationlist_item, this, false))
            }
        }
        binding.skeletonLayout.showSkeleton()

        binding.swipeContainer.apply {
            setOnRefreshListener {
                Log.d(TAG, "onRefresh()")
                binding.swipeContainer.isRefreshing = false
                loadNotifications()
            }
            applyColors()
        }
        binding.retryButton.setOnClickListener {
            loadNotifications()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CloudNotificationAdapter(view.context, usePrimaryServer(), viewLifecycleOwner.lifecycleScope)
        layoutManager = LinearLayoutManager(view.context)

        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        loadNotifications()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
        // Cancel request for notifications if there was any
        requestJob?.cancel()
    }

    private fun loadNotifications() {
        val activity = activity as AbstractBaseActivity? ?: return
        val connInfo = if (usePrimaryServer()) {
            activity.getConnectionFactory().currentPrimary
        } else {
            activity.getConnectionFactory().currentActive
        }
        val conn = connInfo?.cloud?.connection
        if (conn == null) {
            updateViewVisibility(loading = false, loadError = true)
            return
        }
        adapter.clear()
        binding.tagFilterGroup.removeAllViews()
        binding.tagFilterScroll.isVisible = false
        updateViewVisibility(loading = true, loadError = false)

        // Load all notifications at once, so tag counts and collapsed notifications cover all of them.
        // The server keeps notifications for 30 days and returns at most 100 per request.
        requestJob?.cancel()
        requestJob = activity.launch {
            try {
                val notifications = mutableListOf<CloudMessage.CloudNotification>()
                var skip = 0
                var pages = 0
                do {
                    val url = "api/v1/notifications?limit=$PAGE_SIZE&skip=$skip"
                    val response = conn.httpClient.get(url).asText().response
                    val items = JSONArray(response).map { obj -> obj.toCloudMessage() }
                    notifications.addAll(items.filterIsInstance<CloudMessage.CloudNotification>())
                    skip += items.size
                } while (items.isNotEmpty() && ++pages < MAX_PAGES)
                Log.d(TAG, "Notifications request success, got ${notifications.size} items")
                adapter.setNotifications(notifications)
                updateTagFilterChips()
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

    private fun updateTagFilterChips() {
        val ctx = context ?: return
        val tagCounts = adapter.getTagCounts()
        val group = binding.tagFilterGroup
        group.setOnCheckedStateChangeListener(null)
        group.removeAllViews()
        if (tagCounts.isEmpty()) {
            binding.tagFilterScroll.isVisible = false
            return
        }
        val activeTag = adapter.currentTagFilter
        val allChip = Chip(ctx).apply {
            id = View.generateViewId()
            text = getString(
                R.string.notification_tag_filter_chip,
                getString(R.string.notification_tag_filter_all),
                adapter.getTotalNotificationCount()
            )
            isCheckable = true
            isChecked = activeTag == null
            tag = null
        }
        group.addView(allChip)
        for ((tagName, count) in tagCounts) {
            val chip = Chip(ctx).apply {
                id = View.generateViewId()
                text = getString(R.string.notification_tag_filter_chip, tagName, count)
                isCheckable = true
                isChecked = activeTag == tagName
                tag = tagName
            }
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            val selectedChip = checkedId?.let { chipGroup.findViewById<Chip>(it) }
            adapter.currentTagFilter = selectedChip?.tag as? String
            layoutManager.scrollToPosition(0)
        }
        binding.tagFilterScroll.isVisible = true
    }

    private fun handleInitialHighlight() {
        val highlightedId = arguments?.getString("highlightedId") ?: return
        val position = adapter.findPositionForId(highlightedId)
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            recyclerView.postDelayed({ adapter.highlightItem(highlightedId) }, 600)
        }

        // highlight only once
        arguments?.remove("highlightedId")
    }

    private fun updateViewVisibility(loading: Boolean, loadError: Boolean) {
        val showEmpty = !loading && (adapter.getTotalNotificationCount() == 0 || loadError)
        binding.list.isVisible = !showEmpty
        binding.tagFilterScroll.isVisible = !showEmpty && !loading && adapter.getTagCounts().isNotEmpty()
        binding.empty.isVisible = showEmpty
        binding.skeletonLayout.isVisible = loading
        binding.emptyMessage.setText(
            if (loadError) R.string.notification_list_error else R.string.notification_list_empty
        )
        binding.watermark.setImageResource(
            if (loadError) R.drawable.ic_connection_error else R.drawable.ic_no_notifications
        )
        binding.retryButton.isVisible = loadError
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
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 20

        private val TAG = CloudNotificationListFragment::class.java.simpleName

        fun newInstance(highlightedId: String?, primaryServer: Boolean): CloudNotificationListFragment {
            val f = CloudNotificationListFragment()
            f.arguments = Bundle().apply {
                putString("highlightedId", highlightedId)
                putBoolean("primary", primaryServer)
            }
            return f
        }
    }
}
