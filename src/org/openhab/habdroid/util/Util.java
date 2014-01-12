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

package org.openhab.habdroid.util;

import android.content.Context;
import android.util.Log;
import com.crittercism.app.Crittercism;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;

import android.app.Activity;
import android.preference.PreferenceManager;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Util {

    private final static String TAG = "Util";

	public static void setActivityTheme(Activity activity) {
		if (PreferenceManager.getDefaultSharedPreferences(activity).getString("default_openhab_theme", "dark").equals("dark")) {
//			activity.setTheme(android.R.style.Theme_Holo);
			activity.setTheme(R.style.HABDroid_Dark);
		} else {
//			activity.setTheme(android.R.style.Theme_Holo_Light);
			activity.setTheme(R.style.HABDroid_Light);
		}
	}
	
	public static void overridePendingTransition(Activity activity, boolean reverse) {
		if (PreferenceManager.getDefaultSharedPreferences(activity).getString("default_openhab_animation", "android").equals("android")) {
		} else if (PreferenceManager.getDefaultSharedPreferences(activity).getString("default_openhab_animation", "android").equals("ios")) {
			if (reverse) {
				activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
			} else {
				activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);				
			}
		} else {
			activity.overridePendingTransition(0, 0);
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
        JSONObject crittercismConfig = new JSONObject();
        try {
            crittercismConfig.put("shouldCollectLogcat", true);
        } catch (JSONException e) {
            if (e.getMessage() != null)
                Log.e(TAG, e.getMessage());
            else
                Log.e(TAG, "Crittercism JSON exception");
        }
        Crittercism.init(ctx, appKey, crittercismConfig);
    }

    public static List<OpenHABSitemap> parseSitemapList(Document document) {
        List<OpenHABSitemap> sitemapList = new ArrayList<OpenHABSitemap>();
            NodeList sitemapNodes = document.getElementsByTagName("sitemap");
            if (sitemapNodes.getLength() > 0) {
                for (int i=0; i < sitemapNodes.getLength(); i++) {
                    Node sitemapNode = sitemapNodes.item(i);
                    OpenHABSitemap openhabSitemap = new OpenHABSitemap(sitemapNode);
                    sitemapList.add(openhabSitemap);
                }
            }
        return sitemapList;
    }

    public static boolean sitemapExists(List<OpenHABSitemap> sitemapList, String sitemapName) {
        for (int i=0; i<sitemapList.size(); i++) {
            if (sitemapList.get(i).getName().equals(sitemapName))
                return true;
        }
        return false;
    }

    public static OpenHABSitemap getSitemapByName(List<OpenHABSitemap> sitemapList, String sitemapName) {
        for (int i=0; i<sitemapList.size(); i++) {
            if (sitemapList.get(i).getName().equals(sitemapName))
                return sitemapList.get(i);
        }
        return null;
    }

}
