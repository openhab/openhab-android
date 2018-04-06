/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * This class provides datasource for openHAB widgets from sitemap page.
 * It uses a sitemap page XML document to create a list of widgets
 */

public class OpenHABWidgetDataSource {
    private static final String TAG = OpenHABWidgetDataSource.class.getSimpleName();

    private final String mIconFormat;
    private final List<OpenHABWidget> mAllWidgets = new ArrayList<>();
    private String mTitle;
    private String mId;
    private String mIcon;
    private String mLink;

    public OpenHABWidgetDataSource(String iconFormat) {
        mIconFormat = iconFormat;
    }

    public void setSourceNode(Node rootNode) {
        Log.i(TAG, "Loading new data");
        if (rootNode == null)
            return;
        if (rootNode.hasChildNodes()) {
            NodeList childNodes = rootNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                switch (childNode.getNodeName()) {
                    case "widget": OpenHABWidget.parseXml(mAllWidgets, null, childNode); break;
                    case "title": mTitle = childNode.getTextContent(); break;
                    case "id": mId = childNode.getTextContent(); break;
                    case "icon": mIcon = childNode.getTextContent(); break;
                    case "link": mLink = childNode.getTextContent(); break;
                }
            }
        }
    }

    public void setSourceJson(JSONObject jsonObject) {
        Log.d(TAG, jsonObject.toString());
        if (!jsonObject.has("widgets"))
            return;
        try {
            JSONArray jsonWidgetArray = jsonObject.getJSONArray("widgets");
            for (int i = 0; i < jsonWidgetArray.length(); i++) {
                JSONObject widgetJson = jsonWidgetArray.getJSONObject(i);
                OpenHABWidget.parseJson(mAllWidgets, null, widgetJson, mIconFormat);
            }
            mTitle = jsonObject.optString("title", null);
            mId = jsonObject.optString("id", null);
            mIcon = jsonObject.optString("icon", null);
            mLink = jsonObject.optString("link", null);
        } catch (JSONException e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public ArrayList<OpenHABWidget> getWidgets() {
        ArrayList<OpenHABWidget> result = new ArrayList<>();
        HashSet<String> firstLevelWidgetIds = new HashSet<>();
        for (OpenHABWidget widget : mAllWidgets) {
            if (widget.parentId() == null) {
                firstLevelWidgetIds.add(widget.id());
            }
        }
        for (OpenHABWidget widget : mAllWidgets) {
            String parentId = widget.parentId();
            if (parentId == null || firstLevelWidgetIds.contains(parentId)) {
                result.add(widget);
            }
        }
        return result;
    }

    public String getTitle() {
        if (mTitle != null) {
            String[] splitString = mTitle.split("\\[|\\]");
            return splitString.length > 0 ? splitString[0] : mTitle;
        }
        return "";
    }

    public String getId() {
        return mId;
    }

    public String getIcon() {
        return mIcon;
    }

    public String getLink() {
        return mLink;
    }
}
