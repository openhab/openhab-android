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
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.DialogNotificationImageBinding
import org.openhab.habdroid.databinding.NotificationlistHeaderItemBinding
import org.openhab.habdroid.databinding.NotificationlistItemBinding
import org.openhab.habdroid.databinding.NotificationlistLoadingItemBinding
import org.openhab.habdroid.model.CloudMessage
import org.openhab.habdroid.model.CloudNotificationAction
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getBackgroundTasksManager
import org.openhab.habdroid.util.getConnectionFactory

class CloudNotificationAdapter(
    context: Context,
    private val usePrimaryServer: Boolean = false,
    private val loadMoreListener: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val allNotifications = mutableListOf<CloudMessage.CloudNotification>()
    private val displayItems = mutableListOf<AdapterItem>()
    private val inflater = LayoutInflater.from(context)
    private var hasMoreItems: Boolean = false
    private var waitingForMoreData: Boolean = false
    private var highlightedPosition = -1
    private var selectedTag: String? = null

    var currentTagFilter: String?
        get() = selectedTag
        set(value) {
            if (selectedTag != value) {
                selectedTag = value
                rebuildDisplayItems()
            }
        }

    fun addLoadedItems(loaded: List<CloudMessage.CloudNotification>, hasMoreItems: Boolean) {
        val existingIds = allNotifications.mapTo(mutableSetOf()) { it.id.persistedId }
        val newItems = loaded.filter { it.id.persistedId.isEmpty() || existingIds.add(it.id.persistedId) }
        allNotifications.addAll(newItems)
        this.hasMoreItems = hasMoreItems
        waitingForMoreData = false
        rebuildDisplayItems()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        allNotifications.clear()
        displayItems.clear()
        hasMoreItems = false
        waitingForMoreData = false
        notifyDataSetChanged()
    }

    fun getTotalNotificationCount(): Int = allNotifications.size

    fun getTagCounts(): List<Pair<String, Int>> = allNotifications
        .mapNotNull { it.tag?.takeIf { tag -> tag.isNotEmpty() } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

    fun findPositionForId(id: String): Int = displayItems.indexOfFirst { item ->
        item is AdapterItem.NotificationEntry && item.notification.id.persistedId == id
    }

    fun highlightItem(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun rebuildDisplayItems() {
        displayItems.clear()
        val filtered = if (selectedTag == null) {
            allNotifications
        } else {
            allNotifications.filter { it.tag == selectedTag }
        }
        var lastDayKey = -1L
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        for (notif in filtered) {
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
            displayItems.add(AdapterItem.NotificationEntry(notif))
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = displayItems.size + if (hasMoreItems) 1 else 0

    override fun getItemViewType(position: Int): Int = when {
        position == displayItems.size -> VIEW_TYPE_LOADING
        displayItems[position] is AdapterItem.Header -> VIEW_TYPE_HEADER
        else -> VIEW_TYPE_NOTIFICATION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_TYPE_LOADING -> LoadingIndicatorViewHolder(
            NotificationlistLoadingItemBinding.inflate(inflater, parent, false)
        )

        VIEW_TYPE_HEADER -> HeaderViewHolder(
            NotificationlistHeaderItemBinding.inflate(inflater, parent, false)
        )

        else -> NotificationViewHolder(
            NotificationlistItemBinding.inflate(inflater, parent, false),
            usePrimaryServer
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NotificationViewHolder -> {
                val entry = displayItems[position] as AdapterItem.NotificationEntry
                holder.bind(entry.notification)
            }

            is HeaderViewHolder -> {
                val header = displayItems[position] as AdapterItem.Header
                holder.bind(header.title)
            }

            else -> {
                // loading indicator
                holder.itemView.isVisible = hasMoreItems
                if (hasMoreItems && !waitingForMoreData) {
                    loadMoreListener()
                    waitingForMoreData = true
                }
            }
        }

        if (position == highlightedPosition) {
            holder.itemView.playPressAnimationAndCallBack {
                highlightedPosition = -1
            }
        }
    }

    sealed class AdapterItem {
        data class Header(val title: String) : AdapterItem()
        data class NotificationEntry(val notification: CloudMessage.CloudNotification) : AdapterItem()
    }

    class HeaderViewHolder(private val binding: NotificationlistHeaderItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.headerTitle.text = title
        }
    }

    class NotificationViewHolder(
        private val binding: NotificationlistItemBinding,
        private val usePrimaryServer: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        private var imageLoadJob: Job? = null

        fun bind(notification: CloudMessage.CloudNotification) {
            val ctx = itemView.context
            if (notification.createdTimestamp > 0L) {
                val timeStr = DateUtils.formatDateTime(
                    ctx,
                    notification.createdTimestamp,
                    DateUtils.FORMAT_SHOW_TIME
                )
                val relStr = DateUtils.getRelativeTimeSpanString(
                    notification.createdTimestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
                binding.notificationCreated.text = "$timeStr · $relStr"
                binding.notificationCreated.isVisible = true
            } else {
                binding.notificationCreated.isGone = true
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
            if (notification.mediaAttachmentUrl == null) {
                binding.notificationImage.setImageDrawable(null)
                binding.notificationImageCard.isGone = true
                binding.notificationImageCard.setOnClickListener(null)
            } else {
                val cachedBitmap = notification.getCachedImage(ctx)
                if (cachedBitmap != null) {
                    binding.notificationImage.setImageBitmap(cachedBitmap)
                    binding.notificationImageCard.isVisible = true
                    binding.notificationImageCard.setOnClickListener {
                        showImageDialog(ctx, notification, cachedBitmap)
                    }
                } else if (conn != null) {
                    binding.notificationImage.setImageDrawable(null)
                    binding.notificationImageCard.isGone = true
                    val targetWidth = ctx.resources.displayMetrics.widthPixels
                    imageLoadJob = CoroutineScope(Dispatchers.IO + Job()).launch {
                        val bitmap = notification.loadImage(conn, ctx, targetWidth)
                        withContext(Dispatchers.Main) {
                            if (bitmap != null) {
                                binding.notificationImage.setImageBitmap(bitmap)
                                binding.notificationImageCard.isVisible = true
                                binding.notificationImageCard.setOnClickListener {
                                    showImageDialog(ctx, notification, bitmap)
                                }
                            } else {
                                binding.notificationImageCard.isGone = true
                            }
                        }
                    }
                } else {
                    binding.notificationImage.setImageDrawable(null)
                    binding.notificationImageCard.isGone = true
                }
            }

            binding.notificationActions.removeAllViews()
            val actions = notification.actions.orEmpty()
            if (actions.isEmpty()) {
                binding.notificationActions.isGone = true
            } else {
                binding.notificationActions.isVisible = true
                for (action in actions) {
                    val chip = Chip(ctx).apply {
                        text = action.label
                        isCheckable = false
                        setOnClickListener {
                            executeAction(ctx, notification, action)
                        }
                    }
                    binding.notificationActions.addView(chip)
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

        private fun showImageDialog(context: Context, notification: CloudMessage.CloudNotification, bitmap: Bitmap) {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val dialogBinding = DialogNotificationImageBinding.inflate(LayoutInflater.from(context))
            dialogBinding.photoView.setImageBitmap(bitmap)
            dialogBinding.dialogTitle.text = notification.title.ifEmpty {
                notification.message.ifEmpty { context.getString(R.string.app_notifications) }
            }
            val subtitleParts = mutableListOf<String>()
            if (notification.title.isNotEmpty() && notification.message.isNotEmpty()) {
                subtitleParts.add(notification.message)
            }
            if (notification.createdTimestamp > 0L) {
                subtitleParts.add(
                    DateUtils.formatDateTime(
                        context,
                        notification.createdTimestamp,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
                    )
                )
            }
            dialogBinding.dialogSubtitle.text = subtitleParts.joinToString(" · ")
            dialogBinding.dialogSubtitle.isVisible = subtitleParts.isNotEmpty()
            dialogBinding.closeButton.setOnClickListener { dialog.dismiss() }
            dialog.setContentView(dialogBinding.root)
            dialog.show()
        }

        private fun executeAction(
            context: Context,
            notification: CloudMessage.CloudNotification,
            cna: CloudNotificationAction
        ) {
            when (val action = cna.action) {
                is CloudNotificationAction.Action.ItemCommandAction -> {
                    context.getBackgroundTasksManager().enqueueNotificationAction(action)
                }

                is CloudNotificationAction.Action.UiCommandAction -> {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        this.action = Intent.ACTION_VIEW
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_UI_COMMAND, action.command)
                        putExtra(MainActivity.EXTRA_CLOUD_NOTIFICATION_ID, notification.id)
                    }
                    context.startActivity(intent)
                }

                is CloudNotificationAction.Action.UrlAction -> {
                    val intent = Intent(Intent.ACTION_VIEW, action.url.toUri())
                    context.startActivity(intent)
                }

                is CloudNotificationAction.Action.NoAction -> {
                    // no-op
                }
            }
        }
    }

    class LoadingIndicatorViewHolder(binding: NotificationlistLoadingItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val VIEW_TYPE_NOTIFICATION = 0
        private const val VIEW_TYPE_LOADING = 1
        private const val VIEW_TYPE_HEADER = 2
    }
}
