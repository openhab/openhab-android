/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.content.Context;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.model.OpenHABNotification;
import org.openhab.habdroid.util.MySmartImageView;

import java.util.ArrayList;
import java.util.Locale;

public class OpenHABNotificationAdapter extends
        RecyclerView.Adapter<OpenHABNotificationAdapter.NotificationViewHolder> {
    private final ArrayList<OpenHABNotification> mItems;
    private final LayoutInflater mInflater;
    private final Context mContext;

    public OpenHABNotificationAdapter(Context context, ArrayList<OpenHABNotification> items) {
        super();
        mItems = items;
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public NotificationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new NotificationViewHolder(mInflater, parent);
    }

    @Override
    public void onBindViewHolder(NotificationViewHolder holder, int position) {
        OpenHABNotification notification = mItems.get(position);

        holder.mCreatedView.setText(DateUtils.getRelativeDateTimeString(mContext,
                notification.getCreated().getTime(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
        holder.mMessageView.setText(notification.getMessage());

        if (notification.getIcon() != null) {
            Connection conn = ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
            String iconUrl = String.format(Locale.US, "%s/images/%s.png",
                    conn.getOpenHABUrl(), Uri.encode(notification.getIcon()));
            holder.mIconView.setImageUrl(iconUrl, conn.getUsername(), conn.getPassword(),
                    R.drawable.ic_openhab_appicon_24dp);
        } else {
            holder.mIconView.setImageResource(R.drawable.ic_openhab_appicon_24dp);
        }
    }

    public static class NotificationViewHolder extends RecyclerView.ViewHolder {
        final TextView mCreatedView;
        final TextView mMessageView;
        final MySmartImageView mIconView;

        public NotificationViewHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.openhabnotificationlist_item, parent, false));
            mCreatedView = itemView.findViewById(R.id.notificationCreated);
            mMessageView = itemView.findViewById(R.id.notificationMessage);
            mIconView = itemView.findViewById(R.id.notificationImage);
        }
    }
}
