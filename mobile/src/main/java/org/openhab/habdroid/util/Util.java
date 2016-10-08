/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.crittercism.app.Crittercism;
import com.crittercism.app.CrittercismConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHAB1Sitemap;
import org.openhab.habdroid.model.OpenHAB2Sitemap;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Util {

    private final static String TAG = Util.class.getSimpleName();

    public static void overridePendingTransition(Activity activity, boolean reverse) {
        if (!PreferenceManager.getDefaultSharedPreferences(activity).getString(Constants.PREFERENCE_ANIMATION, "android").equals("android")) {
            if (PreferenceManager.getDefaultSharedPreferences(activity).getString(Constants.PREFERENCE_ANIMATION, "android").equals("ios")) {
                if (reverse) {
                    activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                } else {
                    activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }
            } else {
                activity.overridePendingTransition(0, 0);
            }
        }
    }

    public static String normalizeUrl(String sourceUrl) {
        String normalizedUrl = "";
        try {
            URL url = new URL(sourceUrl);
            normalizedUrl = url.toString();
            normalizedUrl = normalizedUrl.replace("\n", "");
            normalizedUrl = normalizedUrl.replace(" ", "");
            if (!normalizedUrl.endsWith("/"))
                normalizedUrl = normalizedUrl + "/";
        } catch (MalformedURLException e) {
            Log.e(TAG, "normalizeUrl: invalid URL");
        }
        return normalizedUrl;
    }

    public static void initCrittercism(Context ctx, String appKey) {
        // Initialize crittercism reporting
        CrittercismConfig crittercismConfig = new CrittercismConfig();
        crittercismConfig.setLogcatReportingEnabled(true);
        Crittercism.initialize(ctx, appKey, crittercismConfig);
    }

    public static List<OpenHABSitemap> parseSitemapList(Document document) {
        List<OpenHABSitemap> sitemapList = new ArrayList<OpenHABSitemap>();
        NodeList sitemapNodes = document.getElementsByTagName("sitemap");
        if (sitemapNodes.getLength() > 0) {
            for (int i = 0; i < sitemapNodes.getLength(); i++) {
                Node sitemapNode = sitemapNodes.item(i);
                OpenHABSitemap openhabSitemap = new OpenHAB1Sitemap(sitemapNode);
                sitemapList.add(openhabSitemap);
            }
        }
        // Sort by sitename label
        Collections.sort(sitemapList, new Comparator<OpenHABSitemap>() {
            @Override
            public int compare(OpenHABSitemap sitemap1, OpenHABSitemap sitemap2) {
                if (sitemap1.getLabel() == null) {
                    return sitemap2.getLabel() == null ? 0 : -1;
                }
                if (sitemap2.getLabel() == null) {
                    return 1;
                }
                return  sitemap1.getLabel().compareTo(sitemap2.getLabel());
            }
        });

        return sitemapList;
    }

    public static List<OpenHABSitemap> parseSitemapList(JSONArray jsonArray) {
        List<OpenHABSitemap> sitemapList = new ArrayList<OpenHABSitemap>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject sitemapJson = jsonArray.getJSONObject(i);
                OpenHABSitemap openHABSitemap = new OpenHAB2Sitemap(sitemapJson);
                sitemapList.add(openHABSitemap);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return sitemapList;
    }

    public static boolean sitemapExists(List<OpenHABSitemap> sitemapList, String sitemapName) {
        for (int i = 0; i < sitemapList.size(); i++) {
            if (sitemapList.get(i).getName().equals(sitemapName))
                return true;
        }
        return false;
    }

    public static OpenHABSitemap getSitemapByName(List<OpenHABSitemap> sitemapList, String sitemapName) {
        for (int i = 0; i < sitemapList.size(); i++) {
            if (sitemapList.get(i).getName().equals(sitemapName))
                return sitemapList.get(i);
        }
        return null;
    }

    public static void setActivityTheme(@NonNull final Activity activity) {
        final String theme = PreferenceManager.getDefaultSharedPreferences(activity).getString(Constants.PREFERENCE_THEME, activity.getString(R.string.theme_value_dark));
        int themeRes;
        if (theme.equals(activity.getString(R.string.theme_value_light))) {
            themeRes = R.style.HABDroid_Light;
        } else if (theme.equals(activity.getString(R.string.theme_value_black))) {
            themeRes = R.style.HABDroid_Black;
        } else {
            themeRes = R.style.HABDroid_Dark;
        }
        activity.setTheme(themeRes);
    }
}
