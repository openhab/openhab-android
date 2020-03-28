/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.CloudNotification
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.util.isDataSaverActive
import java.util.ArrayList

class CloudNotificationAdapter(context: Context, private val loadMoreListener: () -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = ArrayList<CloudNotification>()
    private val inflater = LayoutInflater.from(context)
    private var hasMoreItems: Boolean = false
    private var waitingForMoreData: Boolean = false
    private var highlightedPosition = -1

    fun addLoadedItems(items: List<CloudNotification>, hasMoreItems: Boolean) {
        this.items.addAll(items)
        this.hasMoreItems = hasMoreItems
        waitingForMoreData = false
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        hasMoreItems = false
        waitingForMoreData = false
        notifyDataSetChanged()
    }

    fun findPositionForId(id: String): Int {
        return items.indexOfFirst { item -> item.id == id }
    }

    fun highlightItem(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)
    }

    override fun getItemCount(): Int {
        return items.size + if (hasMoreItems) 1 else 0
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) VIEW_TYPE_LOADING else VIEW_TYPE_NOTIFICATION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_LOADING) {
            LoadingIndicatorViewHolder(inflater, parent)
        } else {
            NotificationViewHolder(inflater, parent)
        }
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
        private val createdView: TextView = itemView.findViewById(R.id.notificationCreated)
        private val messageView: TextView = itemView.findViewById(R.id.notificationMessage)
        private val iconView: WidgetImageView = itemView.findViewById(R.id.notificationImage)
        private val severityView: TextView = itemView.findViewById(R.id.notificationSeverity)

        fun bind(notification: CloudNotification) {
            createdView.text = DateUtils.getRelativeDateTimeString(itemView.context,
                notification.createdTimestamp,
                DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0)
            messageView.text = notification.message

            val conn = ConnectionFactory.cloudConnectionOrNull
            if (notification.icon != null && conn != null) {
                iconView.setImageUrl(
                    conn,
                    notification.icon.toUrl(itemView.context, !itemView.context.isDataSaverActive()),
                    itemView.resources.getDimensionPixelSize(R.dimen.notificationlist_icon_size),
                    2000
                )
            } else {
                iconView.setImageResource(R.drawable.ic_openhab_appicon_24dp)
            }
            severityView.text = notification.severity
            severityView.isGone = notification.severity.isNullOrEmpty()
        }
    }

    class LoadingIndicatorViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.notificationlist_loading_item, parent, false))

    companion object {
        private const val VIEW_TYPE_NOTIFICATION = 0
        private const val VIEW_TYPE_LOADING = 1
    }
}
