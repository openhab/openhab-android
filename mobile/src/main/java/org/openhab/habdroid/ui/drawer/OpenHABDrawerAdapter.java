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
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.MySmartImageView;

import java.util.List;

public class OpenHABDrawerAdapter extends ArrayAdapter<OpenHABDrawerItem> {

    public static final int TYPE_SITEMAPITEM = 0;
    public static final int TYPE_MENU_ITEM = 1;
    public static final int TYPE_MENU_WITH_COUNT = 2;
    public static final int TYPE_HEADER_ITEM = 3;
    public static final int TYPE_DIVIDER_ITEM = 4;
    public static final int TYPES_COUNT = 5;
    private static final String TAG = "OpenHABDrawerAdapter";
    private String openHABBaseUrl = "http://demo.openhab.org:8080/";
    private String openHABUsername = "";
    private String openHABPassword = "";

    public OpenHABDrawerAdapter(Context context, int resource,
                                List<OpenHABDrawerItem> objects) {
        super(context, resource, objects);
    }

    @SuppressWarnings("deprecation")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final RelativeLayout drawerItemView;
        LinearLayout drawerDivider;
        TextView drawerItemLabelTextView;
        TextView drawerItemCountLabelTextView;
        MySmartImageView drawerItemImage;
        int drawerItemLayout;
        OpenHABDrawerItem drawerItem = getItem(position);
        switch (this.getItemViewType(position)) {
            case TYPE_SITEMAPITEM:
                drawerItemLayout = R.layout.openhabdrawer_sitemap_item;
                break;
            case TYPE_MENU_ITEM:
                drawerItemLayout = R.layout.openhabdrawer_menuwithcount;
                break;
            case TYPE_MENU_WITH_COUNT:
                drawerItemLayout = R.layout.openhabdrawer_menuwithcount;
                break;
            case TYPE_HEADER_ITEM:
                drawerItemLayout = R.layout.openhabdrawer_header_item;
                break;
            case TYPE_DIVIDER_ITEM:
                drawerItemLayout = R.layout.openhabdrawer_divider_item;
                break;
            default:
                drawerItemLayout = R.layout.openhabdrawer_sitemap_item;
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

        // Find all needed views
        drawerItemLabelTextView = (TextView)drawerItemView.findViewById(R.id.itemlabel);
        drawerItemCountLabelTextView = (TextView)drawerItemView.findViewById(R.id.itemcountlabel);
        drawerItemImage = (MySmartImageView)drawerItemView.findViewById(R.id.itemimage);
        switch (this.getItemViewType(position)) {
            case TYPE_SITEMAPITEM:
                if (drawerItem.getSiteMap().getLabel() != null && drawerItemLabelTextView != null) {
                    drawerItemLabelTextView.setText(drawerItem.getSiteMap().getLabel());
                } else {
                    drawerItemLabelTextView.setText(drawerItem.getSiteMap().getName());
                }
                if (drawerItem.getSiteMap().getIcon() != null && drawerItemImage != null) {
                    String iconUrl = openHABBaseUrl + "images/" + Uri.encode(drawerItem.getSiteMap().getIcon() + ".png");
                    drawerItemImage.setImageUrl(iconUrl, R.drawable.openhabiconsmall,
                            openHABUsername, openHABPassword);
                } else {
                    String iconUrl = openHABBaseUrl + "images/" + ".png";
                    drawerItemImage.setImageDrawable(getContext().getResources().getDrawable(R.drawable.openhabicon_light));
                }
                break;
            case TYPE_DIVIDER_ITEM:
                break;
            default:
                if (drawerItemLabelTextView != null && drawerItem.getLabelText() != null) {
                    drawerItemLabelTextView.setText(drawerItem.getLabelText());
                }
                if (drawerItemImage != null && drawerItem.getIcon() != null) {
                    drawerItemImage.setImageDrawable(drawerItem.getIcon());
                }
                if (drawerItem.getCount() > 0 && drawerItemCountLabelTextView != null) {
                    Log.d(TAG, "Showing count = " + String.valueOf(drawerItem.getCount()));
                    drawerItemCountLabelTextView.setText(String.valueOf(drawerItem.getCount()));
                } else if (drawerItemCountLabelTextView != null) {
                    Log.d(TAG, "Not showing count " + String.valueOf(drawerItem.getCount()));
                    drawerItemCountLabelTextView.setText("");
                } else {
                    Log.d(TAG, "No count label");
                }
                break;
        }
        return drawerItemView;
    }

    @Override
    public int getViewTypeCount() {
        return TYPES_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        switch(getItem(position).getItemType()) {
            case SITEMAP_ITEM:
                return TYPE_SITEMAPITEM;
            case MENU_ITEM:
                return TYPE_MENU_ITEM;
            case MENU_WITH_COUNT:
                return TYPE_MENU_WITH_COUNT;
            case HEADER_ITEM:
                return TYPE_HEADER_ITEM;
            case DIVIDER_ITEM:
                return TYPE_DIVIDER_ITEM;
            default:
                return TYPE_MENU_ITEM;
        }
    }

    public boolean areAllItemsEnabled() {
        return false;
    }

    public boolean isEnabled(int position) {
        if (getItem(position).getItemType() == OpenHABDrawerItem.DrawerItemType.DIVIDER_ITEM ||
                getItem(position).getItemType() == OpenHABDrawerItem.DrawerItemType.HEADER_ITEM)
            return false;
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
