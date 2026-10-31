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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.NotificationHandlingReceiver
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.databinding.NotificationlistHeaderItemBinding
import org.openhab.habdroid.databinding.NotificationlistItemBinding
import org.openhab.habdroid.model.CloudMessage
import org.openhab.habdroid.model.CloudNotificationAction
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getBackgroundTasksManager
import org.openhab.habdroid.util.getConnectionFactory

class CloudNotificationAdapter(
    private val context: Context,
    private val usePrimaryServer: Boolean,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val allNotifications = mutableListOf<CloudMessage.CloudNotification>()
    private val collapsedNotifications = mutableListOf<AdapterItem.NotificationEntry>()
    private val displayItems = mutableListOf<AdapterItem>()
    private val inflater = LayoutInflater.from(context)
    private var highlightedId: String? = null
    private var selectedTag: String? = null

    // Attachments like item:CameraSnapshot are the same for many notifications, so load each one only once.
    // They show the current state, so they are only kept in memory and not stored for the notification.
    private val sharedImageLoads = mutableMapOf<String, Deferred<Bitmap?>>()
    private val loadedImages = mutableMapOf<String, Bitmap>()

    var currentTagFilter: String?
        get() = selectedTag
        set(value) {
            if (selectedTag != value) {
                selectedTag = value
                rebuildDisplayItems()
            }
        }

    fun setNotifications(notifications: List<CloudMessage.CloudNotification>) {
        val ids = mutableSetOf<String>()
        allNotifications.clear()
        allNotifications.addAll(notifications.filter { it.id.persistedId.isEmpty() || ids.add(it.id.persistedId) })
        collapseByReferenceId()
        rebuildDisplayItems()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        allNotifications.clear()
        collapsedNotifications.clear()
        sharedImageLoads.clear()
        loadedImages.clear()
        displayItems.clear()
        notifyDataSetChanged()
    }

    fun getTotalNotificationCount(): Int = collapsedNotifications.size

    fun getTagCounts(): List<Pair<String, Int>> = collapsedNotifications
        .mapNotNull { it.notification.tag?.takeIf { tag -> tag.isNotEmpty() } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

    fun findPositionForId(id: String): Int = displayItems.indexOfFirst { item ->
        item is AdapterItem.NotificationEntry && item.notification.id.persistedId == id
    }

    fun highlightItem(id: String) {
        highlightedId = id
        val position = findPositionForId(id)
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    /**
     * Collapse notifications with the same reference ID into the latest one, keeping the number of older ones
     */
    private fun collapseByReferenceId() {
        collapsedNotifications.clear()
        val entryByReferenceId = mutableMapOf<String, Int>()
        for (notification in allNotifications) {
            val referenceId = notification.id.referenceId
            val existingIndex = referenceId?.let { entryByReferenceId[it] }
            if (existingIndex == null) {
                if (referenceId != null) {
                    entryByReferenceId[referenceId] = collapsedNotifications.size
                }
                collapsedNotifications.add(AdapterItem.NotificationEntry(notification, 0))
            } else {
                val existing = collapsedNotifications[existingIndex]
                collapsedNotifications[existingIndex] = existing.copy(olderCount = existing.olderCount + 1)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun rebuildDisplayItems() {
        displayItems.clear()
        val filtered = if (selectedTag == null) {
            collapsedNotifications
        } else {
            collapsedNotifications.filter { it.notification.tag == selectedTag }
        }
        var lastDayKey = -1L
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        for (entry in filtered) {
            val notif = entry.notification
            if (notif.createdTimestamp > 0L) {
                cal.timeInMillis = notif.createdTimestamp
                val dayKey = cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
                if (dayKey != lastDayKey) {
                    lastDayKey = dayKey
                    val headerText = DateUtils.getRelativeTimeSpanString(
                        notif.createdTimestamp,
                        now,
                        DateUtils.DAY_IN_MILLIS,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY
                    ).toString()
                    displayItems.add(AdapterItem.Header(headerText))
                }
            }
            displayItems.add(entry)
        }
        notifyDataSetChanged()
    }

    private fun getLoadedImage(notification: CloudMessage.CloudNotification): Bitmap? =
        CacheManager.getInstance(context).getNotificationImageFromMemory(notification.id.persistedId)
            ?: notification.mediaAttachmentUrl?.let { url -> loadedImages[url] }

    private suspend fun loadImage(notification: CloudMessage.CloudNotification, conn: Connection, size: Int): Bitmap? {
        val url = notification.mediaAttachmentUrl ?: return null
        val load = sharedImageLoads.getOrPut(url) {
            scope.async(Dispatchers.IO) { notification.loadImage(conn, context, size) }
        }
        val bitmap = load.await()
        if (bitmap != null) {
            loadedImages[url] = bitmap
        } else if (sharedImageLoads[url] === load) {
            // Try again when the card is bound the next time
            sharedImageLoads.remove(url)
        }
        return bitmap
    }

    override fun getItemCount(): Int = displayItems.size

    override fun getItemViewType(position: Int): Int = when (displayItems[position]) {
        is AdapterItem.Header -> VIEW_TYPE_HEADER
        else -> VIEW_TYPE_NOTIFICATION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_TYPE_HEADER -> HeaderViewHolder(
            NotificationlistHeaderItemBinding.inflate(inflater, parent, false)
        )

        else -> NotificationViewHolder(
            NotificationlistItemBinding.inflate(inflater, parent, false),
            usePrimaryServer,
            scope,
            ::getLoadedImage,
            ::loadImage
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NotificationViewHolder -> {
                val entry = displayItems[position] as AdapterItem.NotificationEntry
                holder.bind(entry.notification, entry.olderCount)
                if (highlightedId != null && entry.notification.id.persistedId == highlightedId) {
                    highlightedId = null
                    holder.itemView.playPressAnimationAndCallBack {}
                }
            }

            is HeaderViewHolder -> {
                val header = displayItems[position] as AdapterItem.Header
                holder.bind(header.title)
            }
        }
    }

    sealed class AdapterItem {
        data class Header(val title: String) : AdapterItem()
        data class NotificationEntry(val notification: CloudMessage.CloudNotification, val olderCount: Int) :
            AdapterItem()
    }

    class HeaderViewHolder(private val binding: NotificationlistHeaderItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.headerTitle.text = title
        }
    }

    class NotificationViewHolder(
        private val binding: NotificationlistItemBinding,
        private val usePrimaryServer: Boolean,
        private val scope: CoroutineScope,
        private val loadedImage: (CloudMessage.CloudNotification) -> Bitmap?,
        private val imageLoader: suspend (CloudMessage.CloudNotification, Connection, Int) -> Bitmap?
    ) : RecyclerView.ViewHolder(binding.root) {
        private var imageLoadJob: Job? = null

        fun bind(notification: CloudMessage.CloudNotification, olderCount: Int) {
            val ctx = itemView.context
            if (notification.createdTimestamp > 0L) {
                binding.notificationCreated.text = DateUtils.formatDateTime(
                    ctx,
                    notification.createdTimestamp,
                    DateUtils.FORMAT_SHOW_TIME
                )
                binding.notificationCreated.isVisible = true
            } else {
                binding.notificationCreated.isGone = true
            }

            binding.notificationOlderCount.apply {
                text = ctx.resources.getQuantityString(R.plurals.notification_older_count, olderCount, olderCount)
                isVisible = olderCount > 0
            }

            val displayTitle = notification.title.ifEmpty {
                notification.tag.orEmpty().ifEmpty {
                    ctx.getString(R.string.app_notifications)
                }
            }
            binding.notificationTitle.text = displayTitle
            binding.notificationTitle.isVisible = true

            binding.notificationMessage.apply {
                text = notification.message
                isVisible = notification.message.isNotEmpty()
            }

            binding.notificationTag.apply {
                text = notification.tag
                isGone = notification.tag.isNullOrEmpty()
            }

            val cf = ctx.getConnectionFactory()
            val connInfo = if (usePrimaryServer) cf.currentPrimary else cf.currentActive
            val conn = connInfo?.usableConnection ?: connInfo?.cloud?.connection

            if (conn != null && notification.icon != null) {
                binding.notificationIcon.setImageUrl(
                    conn,
                    notification.icon.toUrl(
                        ctx,
                        ctx.determineDataUsagePolicy(conn).loadIconsWithState
                    ),
                    timeoutMillis = 2000
                )
            } else {
                binding.notificationIcon.applyFallbackDrawable()
            }

            imageLoadJob?.cancel()
            binding.notificationImage.setImageDrawable(null)
            binding.notificationImageCard.setOnClickListener(null)
            binding.notificationImageProgress.isGone = true
            if (notification.mediaAttachmentUrl == null) {
                binding.notificationImageCard.isGone = true
            } else {
                val cacheManager = CacheManager.getInstance(ctx)
                val persistedId = notification.id.persistedId
                val cachedBitmap = loadedImage(notification)
                if (cachedBitmap != null) {
                    showImage(notification, cachedBitmap)
                } else {
                    binding.notificationImageCard.isVisible = true
                    binding.notificationImageProgress.isVisible = true
                    val targetWidth = ctx.resources.displayMetrics.widthPixels
                    imageLoadJob = scope.launch {
                        val createdTimestamp = notification.createdTimestamp
                        var bitmap = cacheManager.loadNotificationImageFromDisk(persistedId, createdTimestamp)
                        if (bitmap == null && conn != null) {
                            bitmap = imageLoader(notification, conn, targetWidth)
                        }
                        binding.notificationImageProgress.isGone = true
                        if (bitmap != null) {
                            showImage(notification, bitmap)
                        } else {
                            binding.notificationImageCard.isGone = true
                        }
                    }
                }
            }

            binding.notificationActions.apply {
                removeAllViews()
                val actions = notification.actions.orEmpty()
                isVisible = actions.isNotEmpty()
                actions.forEach { action ->
                    val chip = Chip(ctx).apply {
                        text = action.label
                        isCheckable = false
                        setOnClickListener { executeAction(ctx, notification, action) }
                    }
                    addView(chip)
                }
            }

            if (notification.onClickAction != null) {
                binding.notificationCard.setOnClickListener {
                    executeAction(ctx, notification, notification.onClickAction)
                }
            } else {
                binding.notificationCard.setOnClickListener(null)
                binding.notificationCard.isClickable = false
            }
        }

        private fun showImage(notification: CloudMessage.CloudNotification, bitmap: Bitmap) {
            binding.notificationImage.setImageBitmap(bitmap)
            binding.notificationImageCard.isVisible = true
            binding.notificationImageCard.setOnClickListener {
                val ctx = itemView.context
                ctx.startActivity(NotificationImageActivity.createIntent(ctx, notification, usePrimaryServer))
            }
        }

        private fun executeAction(
            context: Context,
            notification: CloudMessage.CloudNotification,
            cna: CloudNotificationAction
        ) {
            val intent = NotificationHandlingReceiver.createActivityIntent(context, notification.id, cna)
            val action = cna.action
            when {
                intent != null -> context.startActivity(intent)

                action is CloudNotificationAction.Action.ItemCommandAction ->
                    context.getBackgroundTasksManager().enqueueNotificationAction(action)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_NOTIFICATION = 0
        private const val VIEW_TYPE_HEADER = 1
    }
}
