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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import okhttp3.Headers;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.ParsedState;
import org.openhab.habdroid.model.Sitemap;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;

public class Util {
    private static final String TAG = Util.class.getSimpleName();

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

    public static void checkFullscreen(Activity activity) {
        checkFullscreen(activity, isFullscreenEnabled(activity));
    }

    public static void checkFullscreen(Activity activity, boolean isEnabled) {
        int uiOptions = activity.getWindow().getDecorView().getSystemUiVisibility();
        final int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (isEnabled) {
            uiOptions |= flags;
        } else {
            uiOptions &= ~flags;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }

    public static boolean isFullscreenEnabled(Context context) {
        // If we are 4.4 we can use fullscreen mode and Daydream features
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return false;
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Constants.PREFERENCE_FULLSCREEN, false);
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

    public static void sendItemCommand(AsyncHttpClient client, Item item,
            ParsedState.NumberState state) {
        if (item == null || state == null) {
            return;
        }
        if (item.isOfTypeOrGroupType(Item.Type.NumberWithDimension)) {
            // For number items, include unit (if present) in command
            sendItemCommand(client, item, state.toString(Locale.US));
        } else {
            // For all other items, send the plain value
            sendItemCommand(client, item, state.formatValue());
        }
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
                Log.e(TAG, "Sending command '" + command
                        + "' to " + itemUrl + " failed: status " + statusCode, error);
            }

            @Override
            public void onSuccess(String response, Headers headers) {
                Log.d(TAG, "Command '" + command + "' was sent successfully to " + itemUrl);
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
            theme.resolveAttribute(colorAttrIds[i], typedValue, true);
            colors[i] = typedValue.data;
        }
        swipeLayout.setColorSchemeColors(colors);
    }

    public static boolean isFlavorStable() {
        return BuildConfig.FLAVOR.toLowerCase().contains("stable");
    }

    public static boolean isFlavorBeta() {
        return !isFlavorStable();
    }

    public static boolean isFlavorFull() {
        return BuildConfig.FLAVOR.toLowerCase().contains("full");
    }

    public static boolean isFlavorFoss() {
        return !isFlavorFull();
    }

    public static void applyAuthentication(WebView webView, Connection connection, String url) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WebViewDatabase webViewDatabase = WebViewDatabase.getInstance(webView.getContext());
            webViewDatabase.setHttpAuthUsernamePassword(Util.getHostFromUrl(url), "",
                    connection.getUsername(), connection.getPassword());
        } else {
            webView.setHttpAuthUsernamePassword(Util.getHostFromUrl(url), "",
                    connection.getUsername(), connection.getPassword());
        }
    }

    /**
     * Returns vibration pattern for notifications that can be passed to
     * {@link androidx.core.app.NotificationCompat.Builder#setVibrate(long[] pattern}.
     *
     * @param context
     * @return Vibration pattern
     */
    public static long[] getNotificationVibrationPattern(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String vibration =
                prefs.getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION, "");
        if (context.getString(R.string.settings_notification_vibration_value_short).equals(vibration)) {
            return new long[] {0, 500, 500};
        } else if (context.getString(R.string.settings_notification_vibration_value_long).equals(vibration)) {
            return new long[] {0, 1000, 1000};
        } else if (context.getString(R.string.settings_notification_vibration_value_twice).equals(vibration)) {
            return new long[] {0, 1000, 1000, 1000, 1000};
        } else {
            return new long[] {0};
        }
    }

    public static CharSequence getHumanReadableErrorMessage(Context context, String url,
                                                            int statusCode, Throwable error) {
        CharSequence message;
        if (statusCode >= 400) {
            if (error.getMessage().equals("openHAB is offline")) {
                return context.getString(R.string.error_openhab_offline);
            } else {
                int resourceId;
                try {
                    resourceId = context.getResources().getIdentifier(
                            "error_http_code_" + statusCode,
                            "string", context.getPackageName());
                    return context.getString(resourceId);
                } catch (Resources.NotFoundException e) {
                    return context.getString(R.string.error_http_connection_failed, statusCode);
                }
            }
        } else if (error instanceof UnknownHostException) {
            Log.e(TAG, "Unable to resolve hostname");
            return context.getString(R.string.error_unable_to_resolve_hostname);
        } else if (error instanceof SSLException) {
            // if ssl exception, check for some common problems
            if (Util.exceptionHasCause(error, CertPathValidatorException.class)) {
                return context.getString(R.string.error_certificate_not_trusted);
            } else if (Util.exceptionHasCause(error, CertificateExpiredException.class)) {
                return context.getString(R.string.error_certificate_expired);
            } else if (Util.exceptionHasCause(error, CertificateNotYetValidException.class)) {
                return context.getString(R.string.error_certificate_not_valid_yet);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && Util.exceptionHasCause(error, CertificateRevokedException.class)) {
                return context.getString(R.string.error_certificate_revoked);
            } else if (Util.exceptionHasCause(error, SSLPeerUnverifiedException.class)) {
                return String.format(context.getString(R.string.error_certificate_wrong_host),
                        Util.getHostFromUrl(url));
            } else {
                return context.getString(R.string.error_connection_sslhandshake_failed);
            }
        } else if (error instanceof ConnectException || error instanceof SocketTimeoutException) {
            return context.getString(R.string.error_connection_failed);
        } else if (error instanceof IOException
                && Util.exceptionHasCause(error, EOFException.class)) {
            return context.getString(R.string.error_http_to_https_port);
        } else {
            Log.e(TAG, "REST call to " + url + " failed", error);
            return error.getMessage();
        }
    }
}
