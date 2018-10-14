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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import androidx.annotation.AnimRes;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import okhttp3.Headers;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.Sitemap;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Util {
    private static final String TAG = Util.class.getSimpleName();

    public static void overridePendingTransition(Activity activity, boolean reverse) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!prefs.getString(Constants.PREFERENCE_ANIMATION, "android").equals("android")) {
            if (prefs.getString(Constants.PREFERENCE_ANIMATION, "android").equals("ios")) {
                @AnimRes int enterAnim = reverse ? R.anim.slide_in_left : R.anim.slide_in_right;
                @AnimRes int exitAnim = reverse ? R.anim.slide_out_right : R.anim.slide_out_left;
                activity.overridePendingTransition(enterAnim, exitAnim);
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
            if (!normalizedUrl.endsWith("/")) {
                normalizedUrl = normalizedUrl + "/";
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "normalizeUrl: invalid URL");
        }
        return normalizedUrl;
    }

    public static String getHostFromUrl(String url) {
        Uri uri = Uri.parse(url);
        return uri.getHost();
    }

    public static List<Sitemap> parseSitemapList(Document document) {
        List<Sitemap> sitemapList = new ArrayList<>();
        NodeList sitemapNodes = document.getElementsByTagName("sitemap");
        if (sitemapNodes.getLength() > 0) {
            for (int i = 0; i < sitemapNodes.getLength(); i++) {
                sitemapList.add(Sitemap.fromXml(sitemapNodes.item(i)));
            }
        }
        return sitemapList;
    }

    public static List<Sitemap> parseSitemapList(JSONArray jsonArray) {
        List<Sitemap> sitemapList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject sitemapJson = jsonArray.getJSONObject(i);
                Sitemap sitemap = Sitemap.fromJson(sitemapJson);
                if (!(sitemap.name().equals("_default") && jsonArray.length() != 1)) {
                    sitemapList.add(sitemap);
                }
            } catch (JSONException e) {
                Log.d(TAG, "Error while parsing sitemap", e);
            }
        }
        return sitemapList;
    }

    public static void sortSitemapList(List<Sitemap> sitemapList, String defaultSitemapName) {
        // Sort by sitename label, the default sitemap should be the first one
        Collections.sort(sitemapList, (lhs, rhs) -> {
            if (lhs.name().equals(defaultSitemapName)) {
                return -1;
            }
            if (rhs.name().equals(defaultSitemapName)) {
                return 1;
            }
            return lhs.label().compareToIgnoreCase(rhs.label());
        });
    }

    public static boolean sitemapExists(List<Sitemap> sitemapList, String sitemapName) {
        for (int i = 0; i < sitemapList.size(); i++) {
            if (sitemapList.get(i).name().equals(sitemapName)) {
                return true;
            }
        }
        return false;
    }

    public static Sitemap getSitemapByName(List<Sitemap> sitemapList, String sitemapName) {
        for (int i = 0; i < sitemapList.size(); i++) {
            if (sitemapList.get(i).name().equals(sitemapName)) {
                return sitemapList.get(i);
            }
        }
        return null;
    }

    public static void setActivityTheme(@NonNull final Activity activity) {
        setActivityTheme(activity, null);
    }

    public static void setActivityTheme(@NonNull final Activity activity, String theme) {
        activity.setTheme(getActivityThemeId(activity, theme));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TypedValue typedValue = new TypedValue();
            activity.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
            activity.setTaskDescription(new ActivityManager.TaskDescription(
                    activity.getString(R.string.app_name),
                    BitmapFactory.decodeResource(activity.getResources(), R.mipmap.icon_round),
                    typedValue.data));
        }
    }

    public static @StyleRes int getActivityThemeId(@NonNull final Activity activity) {
        return getActivityThemeId(activity, null);
    }

    public static @StyleRes int getActivityThemeId(@NonNull final Activity activity, String theme) {
        if (theme == null) {
            theme = PreferenceManager.getDefaultSharedPreferences(activity).getString(
                    Constants.PREFERENCE_THEME, activity.getString(R.string.theme_value_light));
        }

        if (theme.equals(activity.getString(R.string.theme_value_dark))) {
            return R.style.HABDroid_Dark;
        }
        if (theme.equals(activity.getString(R.string.theme_value_black))) {
            return R.style.HABDroid_Black;
        }
        if (theme.equals(activity.getString(R.string.theme_value_basic_ui))) {
            return R.style.HABDroid_Basic_ui;
        }
        if (theme.equals(activity.getString(R.string.theme_value_basic_ui_dark))) {
            return R.style.HABDroid_Basic_ui_dark;
        }

        return R.style.HABDroid_Light;
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

    public static void sendItemCommand(AsyncHttpClient client, Item item, String command) {
        if (item == null) {
            return;
        }
        sendItemCommand(client, item.link(), command);
    }

    public static void sendItemCommand(AsyncHttpClient client, String itemUrl, String command) {
        if (itemUrl == null || command == null) {
            return;
        }
        client.post(itemUrl, command, "text/plain;charset=UTF-8",
                new AsyncHttpClient.StringResponseHandler() {
            @Override
            public void onFailure(Request request, int statusCode, Throwable error) {
                Log.e(TAG, "Got command error " + error.getMessage());
            }

            @Override
            public void onSuccess(String response, Headers headers) {
                Log.d(TAG, "Command was sent successfully");
            }
        });
    }

    /**
     * Replaces everything after the first 3 chars with asterisks
     * @param string to obfuscate
     * @return obfuscated string
     */
    public static String obfuscateString(String string) {
        return obfuscateString(string, 3);
    }

    /**
     * Replaces everything after the first clearTextCharCount chars with asterisks
     * @param string to obfuscate
     * @param clearTextCharCount leave the first clearTextCharCount in clear text
     * @return obfuscated string
     */
    public static String obfuscateString(String string, int clearTextCharCount) {
        clearTextCharCount = Math.min(string.length(), clearTextCharCount);
        return string.substring(0, clearTextCharCount)
                + string.substring(clearTextCharCount).replaceAll(".", "*");
    }

    /**
     * Sets {@link SwipeRefreshLayout} color scheme from
     * a list of attributes pointing to color resources
     *
     * @param colorAttrIds color attributes to create color scheme from
     */
    public static void applySwipeLayoutColors(SwipeRefreshLayout swipeLayout,
            @AttrRes int... colorAttrIds) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = swipeLayout.getContext().getTheme();
        int[] colors = new int[colorAttrIds.length];

        for (int i = 0; i < colorAttrIds.length; i++) {
            theme.resolveAttribute(R.attr.colorPrimary, typedValue, true);
            colors[i] = typedValue.data;
        }
        swipeLayout.setColorSchemeColors(colors);
    }
}
