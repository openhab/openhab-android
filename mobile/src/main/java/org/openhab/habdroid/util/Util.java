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
import android.app.ActivityManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHAB1Sitemap;
import org.openhab.habdroid.model.OpenHAB2Sitemap;
import org.openhab.habdroid.model.OpenHABItem;
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

import okhttp3.Call;
import okhttp3.Headers;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

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

    public static String removeProtocolFromUrl(String url) {
        Uri uri = Uri.parse(url);
        return uri.getHost();
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
                return sitemap1.getLabel().compareTo(sitemap2.getLabel());
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
                if (!(openHABSitemap.getName().equals("_default") && jsonArray.length() != 1)) {
                    sitemapList.add(openHABSitemap);
                }
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
        setActivityTheme(activity, null);
    }

    public static void setActivityTheme(@NonNull final Activity activity, String theme) {
        if (theme == null) {
            theme = PreferenceManager.getDefaultSharedPreferences(activity).getString(Constants.PREFERENCE_THEME, activity.getString(R.string.theme_value_light));
        }
        int themeRes;
        if (theme.equals(activity.getString(R.string.theme_value_dark))) {
            themeRes = R.style.HABDroid_Dark;
        } else if (theme.equals(activity.getString(R.string.theme_value_black))) {
            themeRes = R.style.HABDroid_Black;
        } else if (theme.equals(activity.getString(R.string.theme_value_basic_ui))) {
            themeRes = R.style.HABDroid_Basic_ui;
        } else if (theme.equals(activity.getString(R.string.theme_value_basic_ui_dark))) {
            themeRes = R.style.HABDroid_Basic_ui_dark;
        } else {
            themeRes = R.style.HABDroid_Light;
        }
        activity.setTheme(themeRes);

        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            TypedValue typedValue = new TypedValue();
            activity.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
            activity.setTaskDescription(new ActivityManager.TaskDescription(
                    activity.getString(R.string.app_name),
                    BitmapFactory.decodeResource(activity.getResources(), R.mipmap.icon_round),
                    typedValue.data));
        }
    }

    public static boolean exceptionHasCause(Throwable error, Class<? extends Throwable> cause) {
        while (error != null) {
            if (error.getClass().equals(cause)) {
                return true;
            }
            error = error.getCause();
        }
        return false;
    }

    public static void sendItemCommand(MyAsyncHttpClient client, OpenHABItem item, String command) {
        if (item == null) {
            return;
        }
        sendItemCommand(client, item.getLink(), command);
    }

    public static void sendItemCommand(MyAsyncHttpClient client, String itemUrl, String command) {
        if (itemUrl == null || command == null) {
            return;
        }
        client.post(itemUrl, command, "text/plain;charset=UTF-8", new MyHttpClient.TextResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                Log.e(TAG, "Got command error " + error.getMessage());
                if (responseString != null)
                    Log.e(TAG, "Error response = " + responseString);
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                Log.d(TAG, "Command was sent successfully");
            }
        });
    }
}
