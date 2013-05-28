/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by belovictor on 5/23/13.
 */
public class OpenHABFragmentPagerAdapter extends FragmentStatePagerAdapter implements ViewPager.OnPageChangeListener {

    private static final String TAG = "OpenHABFragmentPagerAdapter";
    private List<OpenHABWidgetListFragment> fragmentList;
    private FragmentManager fragmentManager;
    private boolean notifyDataSetChangedPending = false;
    private int columnsNumber = 1;
    private String openHABBaseUrl;
    private String sitemapRootUrl;
    private String openHABUsername;
    private String openHABPassword;

    public OpenHABFragmentPagerAdapter(FragmentManager fm) {
        super(fm);
        fragmentManager = fm;
        fragmentList = new ArrayList<OpenHABWidgetListFragment>(0);
    }

    @Override
    public Fragment getItem(int position) {
        Log.d(TAG, String.format("getItem(%d)", position));
        OpenHABWidgetListFragment fragment = fragmentList.get(position);
        return fragment;
    }

    @Override
    public int getCount() {
        return fragmentList.size();
    }

    @Override
    public int getItemPosition(Object object) {
        Log.d(TAG, "getItemPosition");
        if (fragmentList.contains(object)) {
            int index = fragmentList.indexOf(object);
            return index;
        }
        return POSITION_NONE;
    }

    public List<OpenHABWidgetListFragment> getFragmentList() {
        return fragmentList;
    }

    public void setFragmentList(List<OpenHABWidgetListFragment>fragments) {
        fragmentList = fragments;
        notifyDataSetChanged();
    }

    public void clearFragmentList() {
        fragmentList.clear();
        notifyDataSetChanged();
    }

    @Override
    public float getPageWidth(int position) {
        float pageWidth = 1.0f / columnsNumber;
        return  pageWidth;
    }


    public void openPage(String pageUrl) {
        Log.d(TAG, "openPage(" + pageUrl + ")");
        OpenHABWidgetListFragment fragment = OpenHABWidgetListFragment.withPage(pageUrl, openHABBaseUrl,
                sitemapRootUrl, openHABUsername, openHABPassword);
        fragmentList.add(fragment);
        notifyDataSetChanged();
    }

    public void onPageScrolled(int i, float v, int i2) {

    }

    public void onPageSelected(int i) {
        Log.d(TAG, String.format("onPageSelected(%d)", i));
        if (i < fragmentList.size() - 1) {
            Log.d(TAG, "new position is less then current");
            fragmentList.remove(fragmentList.size() - 1);
            notifyDataSetChangedPending = true;
        }
    }

    public void onPageScrollStateChanged(int state) {
        Log.d(TAG, String.format("onPageScrollStateChanged(%d)", state));
        if (state == ViewPager.SCROLL_STATE_IDLE && notifyDataSetChangedPending) {
            Log.d(TAG, "Scrolling finished");
            notifyDataSetChanged();
            notifyDataSetChangedPending = false;
        }

    }

    @Override
    public CharSequence getPageTitle(int position) {
        Log.d(TAG, String.format("getPageTitle(%d)", position));
        return fragmentList.get(position).getTitle();
    }

    public int getColumnsNumber() {
        return columnsNumber;
    }

    public void setColumnsNumber(int columnsNumber) {
        this.columnsNumber = columnsNumber;
    }

    public String getOpenHABBaseUrl() {
        return openHABBaseUrl;
    }

    public void setOpenHABBaseUrl(String openHABBaseUrl) {
        this.openHABBaseUrl = openHABBaseUrl;
    }

    public String getSitemapRootUrl() {
        return sitemapRootUrl;
    }

    public void setSitemapRootUrl(String sitemapRootUrl) {
        this.sitemapRootUrl = sitemapRootUrl;
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
