/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import android.graphics.Color;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.auto.value.AutoValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a class to hold basic information about openHAB widget.
 */

@AutoValue
public abstract class OpenHABWidget implements Parcelable {
    public abstract String id();
    @Nullable
    public abstract String parentId();
    @Nullable
    public abstract String label();
    @Nullable
    public abstract String icon();
    public abstract String iconPath();
    public abstract String type();
    @Nullable
    public abstract String url();
    @Nullable
    public abstract OpenHABItem item();
    @Nullable
    public abstract OpenHABLinkedPage linkedPage();
    public abstract List<OpenHABWidgetMapping> mappings();
    @Nullable
    public abstract String encoding();
    @Nullable
    public abstract Integer iconColor();
    @Nullable
    public abstract Integer labelColor();
    @Nullable
    public abstract Integer valueColor();
    public abstract int refresh();
    public abstract float minValue();
    public abstract float maxValue();
    public abstract float step();
    public abstract String period();
    public abstract String service();
    @Nullable
    public abstract Boolean legend();
    public abstract int height();

    public boolean hasMappings() {
        List<OpenHABWidgetMapping> mappings = mappings();
        return mappings != null && !mappings.isEmpty();
    }

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder id(String id);
        public abstract Builder parentId(@Nullable String parentId);
        public abstract Builder label(@Nullable String label);
        public abstract Builder icon(@Nullable String icon);
        public abstract Builder iconPath(String iconPath);
        public abstract Builder type(String type);
        public abstract Builder url(@Nullable String url);
        public abstract Builder item(@Nullable OpenHABItem item);
        public abstract Builder linkedPage(@Nullable OpenHABLinkedPage linkedPage);
        public abstract Builder mappings(List<OpenHABWidgetMapping> mappings);
        public abstract Builder encoding(@Nullable String encoding);
        public abstract Builder iconColor(@Nullable Integer iconColor);
        public abstract Builder labelColor(@Nullable Integer labelColor);
        public abstract Builder valueColor(@Nullable Integer valueColor);
        public abstract Builder refresh(int refresh);
        public abstract Builder minValue(float minValue);
        public abstract Builder maxValue(float maxValue);
        public abstract Builder step(float step);
        public abstract Builder period(String period);
        public abstract Builder service(String service);
        public abstract Builder legend(@Nullable Boolean legend);
        public abstract Builder height(int height);

        public OpenHABWidget build() {
            // Consider a minimal refresh rate of 100 ms
            refresh(Math.max(refresh(), 100));
            // Default period to 'D'
            if (period() == null || period().isEmpty()) {
                period("D");
            }
            return autoBuild();
        }

        abstract int refresh();
        abstract String period();
        abstract OpenHABWidget autoBuild();
    }

    public static void parseXml(List<OpenHABWidget> allWidgets, OpenHABWidget parent, Node startNode) {
        OpenHABItem item = null;
        OpenHABLinkedPage linkedPage = null;
        String id = null, type = null, label = null, icon = null, url = null;
        String period = "", service = "", encoding = null;
        float minValue = 0f, maxValue = 100f, step = 1f;
        int refresh = 0, height = 0;
        Integer iconColor = null, labelColor = null, valueColor = null;
        List<OpenHABWidgetMapping> mappings = new ArrayList<>();
        List<Node> childWidgetNodes = new ArrayList<>();

        if (startNode.hasChildNodes()) {
            NodeList childNodes = startNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                switch (childNode.getNodeName()) {
                    case "item": item = OpenHABItem.fromXml(childNode); break;
                    case "linkedPage": linkedPage = OpenHABLinkedPage.fromXml(childNode); break;
                    case "widget": childWidgetNodes.add(childNode); break;
                    case "type": type = childNode.getTextContent(); break;
                    case "widgetId": id = childNode.getTextContent(); break;
                    case "label": label = childNode.getTextContent(); break;
                    case "icon": icon = childNode.getTextContent(); break;
                    case "url": url = childNode.getTextContent(); break;
                    case "minValue": minValue = Float.valueOf(childNode.getTextContent()); break;
                    case "maxValue": maxValue = Float.valueOf(childNode.getTextContent()); break;
                    case "step": step = Float.valueOf(childNode.getTextContent()); break;
                    case "refresh": refresh = Integer.valueOf(childNode.getTextContent()); break;
                    case "period": period = childNode.getTextContent(); break;
                    case "service": service = childNode.getTextContent(); break;
                    case "height": height = Integer.valueOf(childNode.getTextContent()); break;
                    case "iconcolor": iconColor = parseColor(childNode.getTextContent()); break;
                    case "valuecolor": valueColor = parseColor(childNode.getTextContent()); break;
                    case "labelcolor": labelColor = parseColor(childNode.getTextContent()); break;
                    case "encoding": encoding = childNode.getTextContent(); break;
                    case "mapping":
                        NodeList mappingChildNodes = childNode.getChildNodes();
                        String mappingCommand = "";
                        String mappingLabel = "";
                        for (int k = 0; k < mappingChildNodes.getLength(); k++) {
                            Node mappingNode = mappingChildNodes.item(k);
                            switch (mappingNode.getNodeName()) {
                                case "command": mappingCommand = mappingNode.getTextContent(); break;
                                case "label": mappingLabel = mappingNode.getTextContent(); break;
                            }
                        }
                        OpenHABWidgetMapping mapping = OpenHABWidgetMapping.newBuilder()
                                .command(mappingCommand)
                                .label(mappingLabel)
                                .build();
                        mappings.add(mapping);
                        break;
                }
            }
        }

        OpenHABWidget widget = new AutoValue_OpenHABWidget.Builder()
                .id(id)
                .parentId(parent != null ? parent.id() : null)
                .type(type)
                .label(label)
                .icon(icon)
                .iconPath(String.format("images/%s.png", icon))
                .item(item)
                .linkedPage(linkedPage)
                .url(url)
                .minValue(minValue)
                .maxValue(maxValue)
                .step(step)
                .refresh(refresh)
                .period(period)
                .service(service)
                .height(height)
                .iconColor(iconColor)
                .labelColor(labelColor)
                .valueColor(valueColor)
                .encoding(encoding)
                .mappings(mappings)
                .build();
        allWidgets.add(widget);

        for (Node childNode : childWidgetNodes) {
            parseXml(allWidgets, widget, childNode);
        }
    }

    public static void parseJson(List<OpenHABWidget> allWidgets, OpenHABWidget parent,
            JSONObject widgetJson, String iconFormat) throws JSONException {
        List<OpenHABWidgetMapping> mappings = new ArrayList<>();
        if (widgetJson.has("mappings")) {
            JSONArray mappingsJsonArray = widgetJson.getJSONArray("mappings");
            for (int i = 0; i < mappingsJsonArray.length(); i++) {
                JSONObject mappingObject = mappingsJsonArray.getJSONObject(i);
                OpenHABWidgetMapping mapping = OpenHABWidgetMapping.newBuilder()
                        .command(mappingObject.getString("command"))
                        .label(mappingObject.getString("label"))
                        .build();
                mappings.add(mapping);
            }
        }

        OpenHABItem item = OpenHABItem.fromJson(widgetJson.optJSONObject("item"));
        String type = widgetJson.getString("type");
        String icon = widgetJson.optString("icon", null);

        OpenHABWidget widget = new AutoValue_OpenHABWidget.Builder()
                .id(widgetJson.getString("widgetId"))
                .parentId(parent != null ? parent.id() : null)
                .item(item)
                .linkedPage(OpenHABLinkedPage.fromJson(widgetJson.optJSONObject("linkedPage")))
                .mappings(mappings)
                .type(type)
                .label(widgetJson.optString("label", null))
                .icon(icon)
                .iconPath(determineOH2IconPath(item, type, icon, iconFormat, !mappings.isEmpty()))
                .url(widgetJson.optString("url", null))
                .minValue((float) widgetJson.optDouble("minValue", 0))
                .maxValue((float) widgetJson.optDouble("maxValue", 100))
                .step((float) widgetJson.optDouble("step", 1))
                .refresh(Math.max(widgetJson.optInt("refresh"), 100))
                .period(widgetJson.optString("period", "D"))
                .service(widgetJson.optString("service", ""))
                .legend(widgetJson.has("legend") ? widgetJson.getBoolean("legend") : null)
                .height(widgetJson.optInt("height"))
                .iconColor(parseColor(widgetJson.optString("iconcolor", null)))
                .labelColor(parseColor(widgetJson.optString("labelcolor", null)))
                .valueColor(parseColor(widgetJson.optString("valuecolor", null)))
                .encoding(widgetJson.optString("encoding", null))
                .build();

        allWidgets.add(widget);

        JSONArray childWidgetJson = widgetJson.optJSONArray("widgets");
        if (childWidgetJson != null) {
            for (int i = 0; i < childWidgetJson.length(); i++) {
                parseJson(allWidgets, widget, childWidgetJson.getJSONObject(i), iconFormat);
            }
        }
    }

    private static String determineOH2IconPath(OpenHABItem item, String type, String icon,
            String iconFormat, boolean hasMappings) {
        String itemState = item != null ? item.state() : null;
        if (itemState != null) {
            if (item.type().equals("Color") || (item.groupType() != null && item.groupType().equals("Color"))) {
                // For items that control a color item fetch the correct icon
                if (type.equals("Slider") || (type.equals("Switch") && !hasMappings)) {
                    try {
                        itemState = String.valueOf(item.stateAsBrightness());
                        if (type.equals("Switch")) {
                            if (itemState.equals("0")) {
                                itemState = "OFF";
                            } else {
                                itemState = "ON";
                            }
                        }
                    } catch (Exception e) {
                        itemState = "OFF";
                    }
                }
            } else if (type.equals("Switch") && !hasMappings &&
                    !(item.type().equals("Rollershutter") || (item.groupType() != null && item.groupType().equals("Rollershutter")))) {
                // For switch items without mappings (just ON and OFF) that control a dimmer item
                // set the state to "OFF" instead of 0 or to "ON" to fetch the correct icon
                try {
                    int itemStateNumber = Integer.valueOf(itemState);
                    if (itemStateNumber == 0) {
                        itemState = "OFF";
                    } else {
                        itemState = "ON";
                    }
                } catch (java.lang.NumberFormatException e) {
                    // Item state is not a number, not sure if that can happen, but good to catch
                }
            }
        }

        return String.format("icon/%s?state=%s&format=%s", icon, itemState, iconFormat);
    }

    private static Integer parseColor(String color) {
        if (color != null && !color.isEmpty()) {
            if (color.equals("orange")) {
                color = "#FFA500";
            }
            try {
                return new Integer(Color.parseColor(color));
            } catch (IllegalArgumentException e) {
                Log.e("OpenHABWidget", "Could not parse color '" + color + "'", e);
            }
        }
        return null;
    }
}
