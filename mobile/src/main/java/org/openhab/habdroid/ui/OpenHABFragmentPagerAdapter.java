/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;

import org.openhab.habdroid.model.OpenHABLinkedPage;

import java.util.ArrayList;
import java.util.List;

public class OpenHABFragmentPagerAdapter extends FragmentStatePagerAdapter implements ViewPager.OnPageChangeListener {

    private static final String TAG = OpenHABFragmentPagerAdapter.class.getSimpleName();
    private List<Fragment> fragmentList = new ArrayList<>();
    private FragmentManager fragmentManager;
    private boolean notifyDataSetChangedPending = false;
    private int columnsNumber = 1;
    private String sitemapRootUrl;
    private int mSelectedPage;

    public OpenHABFragmentPagerAdapter(FragmentManager fm) {
        super(fm);
        fragmentManager = fm;
    }

    @Override
    public Fragment getItem(int position) {
        Log.d(TAG, String.format("getItem(%d)", position));
        return fragmentList.get(position);
    }

    @Override
    public int getCount() {
        return fragmentList.size();
    }

    @Override
    public int getItemPosition(Object object) {
        Log.d(TAG, "getItemPosition");
        if (columnsNumber == 1 && fragmentList.contains(object)) {
            return fragmentList.indexOf(object);
        } else {
            return POSITION_NONE;
        }
    }

    public List<Fragment> getFragmentList() {
        return fragmentList;
    }

    public void setFragmentList(List<Fragment>fragments) {
        fragmentList = fragments;
        notifyDataSetChanged();
    }

    public void clearFragmentList() {
        fragmentList.clear();
        notifyDataSetChanged();
    }

    public Fragment getFragment(int position) {
        if (position < fragmentList.size()) {
            return fragmentList.get(position);
        }
        return null;
    }

    public int getPositionByUrl(String pageUrl) {
        for (int i = 0; i < fragmentList.size(); i++) {
            if (fragmentList.get(i) instanceof OpenHABWidgetListFragment)
                if (((OpenHABWidgetListFragment)fragmentList.get(i)).getDisplayPageUrl().equals(pageUrl)) {
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
        if (fragmentList.size() > 0) {
            if (fragmentList.get(fragmentList.size() - 1) instanceof OpenHABWidgetListFragment) {
                if (mSelectedPage + 1 < columnsNumber && fragmentList.size() > 0) {
                    return fragmentList.size();
                }
                return columnsNumber;
            } else {
                return 1;
            }
        }
        return columnsNumber;
    }

    /**
     * This method assumes, that a remote URL (altUrl) is set and always uses this one, instead
     * of the currently used URL to connect to openHAB. Notifications are always handled in the
     * remote url, if there's any. However, the caller of this method must ensure, that this
     * method is not called, when no openHAB remote URL is set.
     */
    public void openNotifications() {
        if (fragmentList.size() > 0) {
            if (!(fragmentList.get(fragmentList.size() - 1) instanceof OpenHABNotificationFragment)) {
                removeLastFragmentIfNotWidgetList();
                OpenHABNotificationFragment fragment = getNewNotificationFragment();
                fragmentList.add(fragment);
                notifyDataSetChanged();
            } else {
                ((OpenHABNotificationFragment) fragmentList.get(fragmentList.size() - 1)).refresh();
            }
        } else {
            OpenHABNotificationFragment fragment = getNewNotificationFragment();
            fragmentList.add(fragment);
            notifyDataSetChanged();
        }
    }

    private OpenHABNotificationFragment getNewNotificationFragment() {
        return OpenHABNotificationFragment.newInstance();
    }

    public void openPage(String pageUrl, String pageTitle) {
        Log.d(TAG, "openPage(" + pageUrl + ")");
        OpenHABWidgetListFragment fragment = OpenHABWidgetListFragment.withPage(pageUrl, pageTitle,
                fragmentList.size());
        fragmentList.add(fragment);
        notifyDataSetChanged();
    }

    public void openPage(OpenHABLinkedPage page, int position) {
        Log.d(TAG, "openPage(" + page.getLink() + ")");
        int oldColumnCount = getActualColumnsNumber();
        if (position < fragmentList.size()) {
            for (int i=fragmentList.size()-1; i>=position; i--) {
                fragmentList.remove(i);
                Log.d(TAG, String.format("Removing fragment at position %d", i));
            }
            notifyDataSetChanged();
        }
        OpenHABWidgetListFragment fragment = OpenHABWidgetListFragment.withPage(page.getLink(),
                page.getTitle(), position);
        fragmentList.add(fragment);
        Log.d(TAG, String.format("Old columns = %d, new columns = %d", oldColumnCount, getActualColumnsNumber()));

        Log.d(TAG, "Before notifyDataSetChanged");
        notifyDataSetChanged();
        Log.d(TAG, "After notifyDataSetChanged");
    }

    public void onPageScrolled(int i, float v, int i2) {

    }

    public void onPageSelected(int pageSelected) {
        Log.d(TAG, String.format("onPageSelected(%d)", pageSelected));
        mSelectedPage = pageSelected;
        int oldColumnCount = getActualColumnsNumber();
        if (pageSelected < fragmentList.size() - 1) {
            Log.d(TAG, "new position is less then current");
            if (columnsNumber > 1) { // In multicolumn we will modify fragment list immediately
                if (fragmentList.get(pageSelected) instanceof OpenHABWidgetListFragment)
                    ((OpenHABWidgetListFragment)fragmentList.get(pageSelected)).clearSelection();
                for(int i=fragmentList.size()-1; i>mSelectedPage; i--) {
                    Log.d(TAG, String.format("Removing page %d", i));
                    fragmentList.remove(i);
                }
                notifyDataSetChanged();
            } else { // In single column we will set a flag to do that after scroll finishes
                notifyDataSetChangedPending = true;
            }
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

    private void removeLastFragmentIfNotWidgetList() {
        while (!(fragmentList.get(fragmentList.size() - 1) instanceof OpenHABWidgetListFragment) &&
                fragmentList.size() > 0) {
            fragmentList.remove(fragmentList.size() - 1);
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        Log.d(TAG, String.format("getPageTitle(%d)", position));
        if (position > fragmentList.size() - 1) {
            return null;
        }
        if (fragmentList.get(position) instanceof OpenHABWidgetListFragment) {
            return ((OpenHABWidgetListFragment) fragmentList.get(position)).getTitle();
        }
        return null;
    }

    public int getColumnsNumber() {
        return columnsNumber;
    }

    public void setColumnsNumber(int columnsNumber) {
        this.columnsNumber = columnsNumber;
    }

    public String getSitemapRootUrl() {
        return sitemapRootUrl;
    }

    public void setSitemapRootUrl(String sitemapRootUrl) {
        this.sitemapRootUrl = sitemapRootUrl;
    }
}
