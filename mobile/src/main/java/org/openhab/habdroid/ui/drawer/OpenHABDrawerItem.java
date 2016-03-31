/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.drawer;

import android.graphics.drawable.Drawable;

import org.openhab.habdroid.model.OpenHABSitemap;

/**
 * Created by belovictor on 28/01/15.
 */
public class OpenHABDrawerItem {

    public enum DrawerItemType {
        SITEMAP_ITEM, // A sitemap item which corresponds with a sitemap page
        MENU_ITEM, // A static menu item, such as settings
        MENU_WITH_COUNT, // A menu item with a counter on the right side, notifications as an example
        HEADER_ITEM, // A header item placed in front of a group of items
        DIVIDER_ITEM
    }
    private String mLabelText;
    private Drawable mIcon;
    private DrawerItemType mItemType;
    private OpenHABSitemap mSiteMap;
    private int mCount = 0;
    private int mTag;

    public OpenHABDrawerItem() {
    }

    // A constructor to create a SITEMAP_ITEM
    public OpenHABDrawerItem(OpenHABSitemap sitemap) {
        this.mSiteMap = sitemap;
        this.mLabelText = sitemap.getLabel();
        this.mItemType = DrawerItemType.SITEMAP_ITEM;
    }

    public static OpenHABDrawerItem headerItem(String labelText) {
        OpenHABDrawerItem newItem = new OpenHABDrawerItem();
        newItem.mLabelText = labelText;
        newItem.setItemType(DrawerItemType.HEADER_ITEM);
        return newItem;
    }

    public static OpenHABDrawerItem dividerItem() {
        OpenHABDrawerItem newItem = new OpenHABDrawerItem();
        newItem.setItemType(DrawerItemType.DIVIDER_ITEM);
        return newItem;
    }

    public static OpenHABDrawerItem menuItem(String labelText, Drawable icon, int tag) {
        OpenHABDrawerItem newItem = new OpenHABDrawerItem();
        newItem.mLabelText = labelText;
        newItem.mIcon = icon;
        newItem.setItemType(DrawerItemType.MENU_ITEM);
        newItem.setTag(tag);
        return newItem;
    }

    public static OpenHABDrawerItem menuItem(String labelText, Drawable icon) {
        return menuItem(labelText, icon, 0);
    }

    public static OpenHABDrawerItem menuWithCountItem(String labelText, Drawable icon, int count, int tag) {
        OpenHABDrawerItem newItem = new OpenHABDrawerItem();
        newItem.mLabelText = labelText;
        newItem.mIcon = icon;
        newItem.mCount = count;
        newItem.setItemType(DrawerItemType.MENU_WITH_COUNT);
        newItem.setTag(tag);
        return newItem;
    }

    public static OpenHABDrawerItem menuWithCountItem(String labelText, Drawable icon, int count) {
        return menuWithCountItem(labelText, icon, count, 0);
    }

        public String getLabelText() {
        return mLabelText;
    }

    public void setLabelText(String labelText) {
        this.mLabelText = labelText;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public void setIcon(Drawable icon) {
        this.mIcon = icon;
    }

    public DrawerItemType getItemType() {
        return mItemType;
    }

    public void setItemType(DrawerItemType itemType) {
        this.mItemType = itemType;
    }

    public OpenHABSitemap getSiteMap() {
        return mSiteMap;
    }

    public void setSiteMap(OpenHABSitemap siteMap) {
        this.mSiteMap = siteMap;
    }

    public int getCount() {
        return mCount;
    }

    public void setCount(int count) {
        this.mCount = count;
    }

    public int getTag() {
        return mTag;
    }

    public void setTag(int tag) {
        this.mTag = tag;
    }
}
