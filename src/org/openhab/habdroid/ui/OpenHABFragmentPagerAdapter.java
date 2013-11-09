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

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;

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
    private boolean actualColumnCountChanged = false;
    private int mSelectedPage;

    public OpenHABFragmentPagerAdapter(FragmentManager fm) {
        super(fm);
        fragmentManager = fm;
        fragmentList = new ArrayList<OpenHABWidgetListFragment>(0);
    }

    @Override
    public Fragment getItem(int position) {
        Log.d(TAG, String.format("getItem(%d)", position));
//        OpenHABWidgetListFragment fragment = fragmentList.get(position);
//        return fragment;
        return fragmentList.get(position);
    }

    @Override
    public int getCount() {
        return fragmentList.size();
    }

    @Override
    public int getItemPosition(Object object) {
        Log.d(TAG, "getItemPosition");
//        if (actualColumnCountChanged)
//            return POSITION_NONE;
//        if (fragmentList.contains(object)) {
//            int index = fragmentList.indexOf(object);
//            return index;
//            return fragmentList.indexOf(object);
//        }
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

    public OpenHABWidgetListFragment getFragment(int position) {
        if (position < fragmentList.size()) {
            return fragmentList.get(position);
        }
        return null;
    }

    public int getPositionByUrl(String pageUrl) {
        for (int i = 0; i < fragmentList.size(); i++) {
            if (fragmentList.get(i).getDisplayPageUrl().equals(pageUrl)) {
                return i;
            }
        }
        return -1;
    }

    public int getPosition(OpenHABWidgetListFragment fragment) {
        if (fragmentList.contains(fragment)) {
            return fragmentList.indexOf(fragment);
        }
        return -1;
    }

    @Override
    public float getPageWidth(int position) {
        float pageWidth;
/*        pageWidth = 1.0f / getActualColumnsNumber();*/
        if (getActualColumnsNumber() > 1) {
            if (position == fragmentList.size()-1) { // Last fragment
                pageWidth = 0.67f;
            } else {
                pageWidth = 0.33f;
            }
        } else {
            pageWidth = 1.0f;
        }
        Log.d(TAG, String.format("getPageWidth(%d) returned %f", position, pageWidth));
        return  pageWidth;
    }

    public int getActualColumnsNumber() {
        if (mSelectedPage + 1 < columnsNumber && fragmentList.size() > 0) {
            return fragmentList.size();
        }
        return columnsNumber;
    }

    public void openPage(String pageUrl) {
        Log.d(TAG, "openPage(" + pageUrl + ")");
        OpenHABWidgetListFragment fragment = OpenHABWidgetListFragment.withPage(pageUrl, openHABBaseUrl,
                sitemapRootUrl, openHABUsername, openHABPassword, fragmentList.size());
        fragmentList.add(fragment);
        notifyDataSetChanged();
    }

    public void openPage(String pageUrl, int position) {
        Log.d(TAG, "openPage(" + pageUrl + ")");
        int oldColumnCount = getActualColumnsNumber();
        if (position < fragmentList.size()) {
            for (int i=fragmentList.size()-1; i>=position; i--) {
                fragmentList.remove(i);
                Log.d(TAG, String.format("Removing fragment at position %d", i));
            }
            notifyDataSetChanged();
        }
        OpenHABWidgetListFragment fragment = OpenHABWidgetListFragment.withPage(pageUrl, openHABBaseUrl,
                sitemapRootUrl, openHABUsername, openHABPassword, position);
        fragmentList.add(fragment);
        Log.d(TAG, String.format("Old columns = %d, new columns = %d", oldColumnCount, getActualColumnsNumber()));
//        if (getActualColumnsNumber() != oldColumnCount)
            actualColumnCountChanged = true;
        Log.d(TAG, "Before notifyDataSetChanged");
        notifyDataSetChanged();
        Log.d(TAG, "After notifyDataSetChanged");
        actualColumnCountChanged = false;
    }

    public void onPageScrolled(int i, float v, int i2) {

    }

    public void onPageSelected(int pageSelected) {
        Log.d(TAG, String.format("onPageSelected(%d)", pageSelected));
        mSelectedPage = pageSelected;
        int oldColumnCount = getActualColumnsNumber();
        if (pageSelected < fragmentList.size() - 1) {
            Log.d(TAG, "new position is less then current");
            // If we have more then 1 column, notify pager of change here
/*            if (columnsNumber > 1) {
                if (mSelectedPage < fragmentList.size() - 1) {
                    Log.d(TAG, "new position is less then current");
                    for(int i=fragmentList.size()-1; i>mSelectedPage; i--) {
                        fragmentList.remove(i);
                    }
                }
                if (getActualColumnsNumber() != oldColumnCount)
                    actualColumnCountChanged = true;
                notifyDataSetChanged();
                actualColumnCountChanged = false;
            // If only 1 column, set flag to notify pager later, after transition is complete
            } else {
                notifyDataSetChangedPending = true;
            }*/
            for(int i=fragmentList.size()-1; i>mSelectedPage; i--) {
                Log.d(TAG, String.format("Removing page %d", i));
                fragmentList.remove(i);
            }
            notifyDataSetChanged();
        }
    }

    public void onPageScrollStateChanged(int state) {
        Log.d(TAG, String.format("onPageScrollStateChanged(%d)", state));
        // If scroll was finished and there is a flag to notify pager pending
        if (state == ViewPager.SCROLL_STATE_IDLE && notifyDataSetChangedPending) {
            Log.d(TAG, "Scrolling finished");
            if (mSelectedPage < fragmentList.size() - 1) {
                Log.d(TAG, "new position is less then current");
                for(int i=fragmentList.size()-1; i>mSelectedPage; i--) {
                    fragmentList.remove(i);
                }
            }
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
