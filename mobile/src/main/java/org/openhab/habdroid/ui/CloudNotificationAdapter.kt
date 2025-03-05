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
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.CloudMessage
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.util.determineDataUsagePolicy

class CloudNotificationAdapter(context: Context, private val loadMoreListener: () -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<CloudMessage.CloudNotification>()
    private val existingReferenceIds = mutableSetOf<String>()
    private val inflater = LayoutInflater.from(context)
    private var hasMoreItems: Boolean = false
    private var waitingForMoreData: Boolean = false
    private var highlightedPosition = -1

    fun addLoadedItems(loaded: List<CloudMessage.CloudNotification>, hasMoreItems: Boolean) {
        val existingItemCount = items.size
        val relevant = loaded.filter {
            // Collapse multiple notifications with the same reference ID into the latest one by accepting either
            // - notifications without reference ID or
            // - notifications whose reference ID we haven't seen yet
            it.id.referenceId == null || existingReferenceIds.add(it.id.referenceId)
        }
        items.addAll(relevant)
        notifyItemRangeInserted(existingItemCount, relevant.size)
        if (this.hasMoreItems && !hasMoreItems) {
            notifyItemRemoved(items.size)
        } else if (!this.hasMoreItems && hasMoreItems) {
            notifyItemInserted(items.size)
        }
        this.hasMoreItems = hasMoreItems
        waitingForMoreData = false
    }

    fun clear() {
        val existingItemCount = itemCount
        items.clear()
        existingReferenceIds.clear()
        hasMoreItems = false
        waitingForMoreData = false
        notifyItemRangeRemoved(0, existingItemCount)
    }

    fun findPositionForId(id: String): Int = items.indexOfFirst { item -> item.id.persistedId == id }

    fun highlightItem(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)
    }

    override fun getItemCount(): Int = items.size + if (hasMoreItems) 1 else 0

    override fun getItemViewType(position: Int): Int =
        if (position == items.size) VIEW_TYPE_LOADING else VIEW_TYPE_NOTIFICATION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_LOADING) {
            LoadingIndicatorViewHolder(inflater, parent)
        } else {
            NotificationViewHolder(inflater, parent)
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is NotificationViewHolder) {
            holder.bind(items[position])
        } else {
            // loading indicator
            holder.itemView.isVisible = hasMoreItems
            if (hasMoreItems && !waitingForMoreData) {
                loadMoreListener()
                waitingForMoreData = true
            }
        }

        if (position == highlightedPosition) {
            holder.itemView.playPressAnimationAndCallBack {
                highlightedPosition = -1
            }
        }
    }

    class NotificationViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.notificationlist_item, parent, false)) {
        private val titleView: TextView = itemView.findViewById(R.id.notificationTitle)
        private val messageView: TextView = itemView.findViewById(R.id.notificationMessage)
        private val createdView: TextView = itemView.findViewById(R.id.notificationCreated)
        private val iconView: WidgetImageView = itemView.findViewById(R.id.notificationIcon)
        private val imageView: WidgetImageView = itemView.findViewById(R.id.notificationImage)
        private val tagView: TextView = itemView.findViewById(R.id.notificationTag)

        fun bind(notification: CloudMessage.CloudNotification) {
            createdView.text = DateUtils.getRelativeDateTimeString(
                itemView.context,
                notification.createdTimestamp,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                0
            )
            titleView.text = notification.title
            titleView.isVisible = notification.title.isNotEmpty()
            messageView.text = notification.message
            messageView.isVisible = notification.message.isNotEmpty()

            val conn = ConnectionFactory.activeCloudConnection?.connection
            if (conn == null) {
                iconView.applyFallbackDrawable()
                imageView.isVisible = false
            } else {
                if (notification.icon != null) {
                    iconView.setImageUrl(
                        conn,
                        notification.icon.toUrl(
                            itemView.context,
                            itemView.context.determineDataUsagePolicy(conn).loadIconsWithState
                        ),
                        timeoutMillis = 2000
                    )
                } else {
                    iconView.applyFallbackDrawable()
                }
                imageView.isVisible = notification.mediaAttachmentUrl != null
                CoroutineScope(Dispatchers.IO + Job()).launch {
                    val bitmap = notification.loadImage(conn, itemView.context, itemView.width)
                    withContext(Dispatchers.Main) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
            tagView.text = notification.tag
            tagView.isGone = notification.tag.isNullOrEmpty()
        }
    }

    class LoadingIndicatorViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.notificationlist_loading_item, parent, false))

    companion object {
        private const val VIEW_TYPE_NOTIFICATION = 0
        private const val VIEW_TYPE_LOADING = 1
    }
}
