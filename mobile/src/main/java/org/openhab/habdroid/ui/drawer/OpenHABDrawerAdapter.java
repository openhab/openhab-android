/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Victor Belov
 *  @since 1.4.0
 *
 */

package org.openhab.habdroid.ui.drawer;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.util.MySmartImageView;

import java.util.List;

public class OpenHABDrawerAdapter extends ArrayAdapter<OpenHABSitemap> {

    public static final int TYPE_SITEMAPITEM = 0;
    public static final int TYPES_COUNT = 1;
    private static final String TAG = "OpenHABDrawerAdapter";
    private String openHABBaseUrl = "http://demo.openhab.org:8080/";
    private String openHABUsername = "";
    private String openHABPassword = "";

    public OpenHABDrawerAdapter(Context context, int resource,
                                List<OpenHABSitemap> objects) {
        super(context, resource, objects);
    }

    @SuppressWarnings("deprecation")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final RelativeLayout drawerItemView;
        LinearLayout drawerDivider;
        TextView drawerItemLabelTextView;
        MySmartImageView drawerItemImage;
        int drawerItemLayout;
        OpenHABSitemap openHABSitemap = getItem(position);
        switch (this.getItemViewType(position)) {
            case TYPE_SITEMAPITEM:
                drawerItemLayout = R.layout.openhabdrawer_item;
                break;
            default:
                drawerItemLayout = R.layout.openhabdrawer_item;
                break;
        }
        if (convertView == null) {
            drawerItemView = new RelativeLayout(getContext());
            String inflater = Context.LAYOUT_INFLATER_SERVICE;
            LayoutInflater vi;
            vi = (LayoutInflater)getContext().getSystemService(inflater);
            vi.inflate(drawerItemLayout, drawerItemView, true);
        } else {
            drawerItemView = (RelativeLayout) convertView;
        }

        drawerItemLabelTextView = (TextView)drawerItemView.findViewById(R.id.itemlabel);
        drawerItemImage = (MySmartImageView)drawerItemView.findViewById(R.id.itemimage);
        drawerDivider = (LinearLayout)drawerItemView.findViewById(R.id.drawerdivider);
        if (position == getCount()-1)
            drawerDivider.setVisibility(View.VISIBLE);
        else
            drawerDivider.setVisibility(View.INVISIBLE);
        if (openHABSitemap.getLabel() != null && drawerItemLabelTextView != null) {
            drawerItemLabelTextView.setText(openHABSitemap.getLabel());
        } else {
            drawerItemLabelTextView.setText(openHABSitemap.getName());
        }
        if (openHABSitemap.getIcon() != null && drawerItemImage != null) {
            String iconUrl = openHABBaseUrl + "images/" + Uri.encode(openHABSitemap.getIcon() + ".png");
            drawerItemImage.setImageUrl(iconUrl, R.drawable.openhabiconsmall,
                    openHABUsername, openHABPassword);
        } else {
            String iconUrl = openHABBaseUrl + "images/" + ".png";
            drawerItemImage.setImageUrl(iconUrl, R.drawable.openhabiconsmall,
                    openHABUsername, openHABPassword);
        }
        return drawerItemView;
    }

    @Override
    public int getViewTypeCount() {
        return TYPES_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        return TYPE_SITEMAPITEM;
    }
    public boolean areAllItemsEnabled() {
        return false;
    }

    public boolean isEnabled(int position) {
        return true;
    }


    public String getOpenHABBaseUrl() {
        return openHABBaseUrl;
    }

    public void setOpenHABBaseUrl(String openHABBaseUrl) {
        this.openHABBaseUrl = openHABBaseUrl;
    }

    public String getOpenHABUsername() {
        return openHABUsername;
    }

    public void setOpenHABUsername(String openHABUsername) {
        this.openHABUsername = openHABUsername;
    }

    public String getOpenHABPassword() {
        return openHABPassword;
    }

    public void setOpenHABPassword(String openHABPassword) {
        this.openHABPassword = openHABPassword;
    }
}
