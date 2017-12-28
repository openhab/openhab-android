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
import android.os.Build;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionAvailbilityAwareAcivity;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.Connections;
import org.openhab.habdroid.model.OpenHABNotification;
import org.openhab.habdroid.util.MySmartImageView;

import java.util.ArrayList;

public class OpenHABNotificationAdapter extends ArrayAdapter<OpenHABNotification> {
    private int mResource;

    public OpenHABNotificationAdapter(Context context, int resource, ArrayList<OpenHABNotification> objects) {
        super(context, resource, objects);
        mResource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        OpenHABNotification notification = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mResource, parent, false);
        }
        TextView createdView = (TextView)convertView.findViewById(R.id.notificationCreated);
        TextView messageView = (TextView)convertView.findViewById(R.id.notificationMessage);
        MySmartImageView imageView = (MySmartImageView)convertView.findViewById(R.id.notificationImage);
        if (imageView != null) {
            if (notification.getIcon() != null && imageView != null) {
                Connection conn;
                if (getContext() instanceof ConnectionAvailbilityAwareAcivity) {
                    conn = ((ConnectionAvailbilityAwareAcivity) getContext())
                            .getConnection(Connections.CLOUD);
                } else {
                    conn = ConnectionFactory.getConnection(Connections.CLOUD, getContext());
                }
                String iconUrl = conn.getOpenHABUrl() + "/images/" + Uri.encode(notification
                        .getIcon() + ".png");
                imageView.setImageUrl(iconUrl, R.drawable.icon_blank,
                        conn.getUsername(), conn.getPassword());
            } else {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    imageView.setImageDrawable(getContext().getDrawable(R.drawable.icon_blank));
                } else {
                    imageView.setImageDrawable(getContext().getResources().getDrawable(R.drawable.icon_blank));
                }
            }
        }
        createdView.setText(DateUtils.getRelativeDateTimeString(this.getContext(), notification.getCreated().getTime(), DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
        messageView.setText(notification.getMessage());
        return convertView;
    }
}
