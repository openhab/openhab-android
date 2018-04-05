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
    private final String iconFormat;
    private List<OpenHABWidget> allWidgets = new ArrayList<>();
    private String title;
    private String id;
    private String icon;
    private String link;

    public OpenHABWidgetDataSource(String iconFormat) {
        this.iconFormat = iconFormat;
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
                    case "widget": OpenHABWidget.parseXml(allWidgets, null, childNode); break;
                    case "title": title = childNode.getTextContent(); break;
                    case "id": id = childNode.getTextContent(); break;
                    case "icon": icon = childNode.getTextContent(); break;
                    case "link": link = childNode.getTextContent(); break;
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
                OpenHABWidget.parseJson(allWidgets, null, widgetJson, iconFormat);
            }
            title = jsonObject.optString("title", null);
            id = jsonObject.optString("id", null);
            icon = jsonObject.optString("icon", null);
            link = jsonObject.optString("link", null);
        } catch (JSONException e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public ArrayList<OpenHABWidget> getWidgets() {
        ArrayList<OpenHABWidget> result = new ArrayList<>();
        HashSet<String> firstLevelWidgetIds = new HashSet<>();
        for (OpenHABWidget widget : allWidgets) {
            if (widget.parentId() == null) {
                firstLevelWidgetIds.add(widget.id());
            }
        }
        for (OpenHABWidget widget : allWidgets) {
            String parentId = widget.parentId();
            if (parentId == null || firstLevelWidgetIds.contains(parentId)) {
                result.add(widget);
            }
        }
        return result;
    }

    public String getTitle() {
        String[] splitString;
        if (title != null) {
            splitString = title.split("\\[|\\]");
            if (splitString.length > 0) {
                return splitString[0];
            } else {
                return title;
            }
        }
        return "";
    }

    public String getId() {
        return id;
    }

    public String getIcon() {
        return icon;
    }

    public String getLink() {
        return link;
    }
}
