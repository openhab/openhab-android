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
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABNotification;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MySmartImageView;

import java.util.ArrayList;

/**
 * Created by belovictor on 03/04/15.
 */
public class OpenHABNotificationAdapter extends ArrayAdapter<OpenHABNotification> {
    private int mResource;
    private String mOpenHABUsername;
    private String mOpenHABPassword;
    private String mOpenHABBaseUrl;

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
                String iconUrl = mOpenHABBaseUrl + "/images/" + Uri.encode(notification.getIcon() + ".png");
                imageView.setImageUrl(iconUrl, R.drawable.openhabiconsmall,
                        mOpenHABUsername, mOpenHABPassword);
            } else {
                imageView.setImageDrawable(getContext().getResources().getDrawable(R.drawable.openhab));
            }
        }
        createdView.setText(DateUtils.getRelativeDateTimeString(this.getContext(), notification.getCreated().getTime(), DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
        messageView.setText(notification.getMessage());
        return convertView;
    }

    public String getOpenHABUsername() {
        return mOpenHABUsername;
    }

    public void setOpenHABUsername(String openHABUsername) {
        this.mOpenHABUsername = openHABUsername;
    }

    public String getOpenHABPassword() {
        return mOpenHABPassword;
    }

    public void setOpenHABPassword(String openHABPassword) {
        this.mOpenHABPassword = openHABPassword;
    }

    public String getOpenHABBaseUrl() {
        return mOpenHABBaseUrl;
    }

    public void setOpenHABBaseUrl(String mOpenHABBaseUrl) {
        this.mOpenHABPassword = mOpenHABBaseUrl;
    }
}
