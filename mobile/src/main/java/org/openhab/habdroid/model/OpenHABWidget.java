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

import com.google.auto.value.AutoValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * This is a class to hold basic information about openHAB widget.
 */

@AutoValue
public abstract class OpenHABWidget implements Parcelable {
    public enum Type {
        Chart,
        Colorpicker,
        Default,
        Frame,
        Group,
        Image,
        Mapview,
        Selection,
        Setpoint,
        Slider,
        Switch,
        Text,
        Video,
        Webview,
        Unknown
    }

    public abstract String id();
    @Nullable
    public abstract String parentId();
    @Nullable
    public abstract String label();
    @Nullable
    public abstract String icon();
    public abstract String iconPath();
    public abstract Type type();
    @Nullable
    public abstract String url();
    @Nullable
    public abstract OpenHABItem item();
    @Nullable
    public abstract OpenHABLinkedPage linkedPage();
    public abstract List<OpenHABLabeledValue> mappings();
    @Nullable
    public abstract String encoding();
    @Nullable
    public abstract String iconColor();
    @Nullable
    public abstract String labelColor();
    @Nullable
    public abstract String valueColor();
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
        return !mappings().isEmpty();
    }

    public boolean hasMappingsOrItemOptions() {
        return !getMappingsOrItemOptions().isEmpty();
    }

    public List<OpenHABLabeledValue> getMappingsOrItemOptions() {
        List<OpenHABLabeledValue> mappings = mappings();
        List<OpenHABLabeledValue> options = item() != null ? item().options() : null;
        return mappings.isEmpty() && options != null ? options : mappings;
    }

    abstract Builder toBuilder();

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder id(String id);
        public abstract Builder parentId(@Nullable String parentId);
        public abstract Builder label(@Nullable String label);
        public abstract Builder icon(@Nullable String icon);
        public abstract Builder iconPath(String iconPath);
        public abstract Builder type(Type type);
        public abstract Builder url(@Nullable String url);
        public abstract Builder item(@Nullable OpenHABItem item);
        public abstract Builder linkedPage(@Nullable OpenHABLinkedPage linkedPage);
        public abstract Builder mappings(List<OpenHABLabeledValue> mappings);
        public abstract Builder encoding(@Nullable String encoding);
        public abstract Builder iconColor(@Nullable String iconColor);
        public abstract Builder labelColor(@Nullable String labelColor);
        public abstract Builder valueColor(@Nullable String valueColor);
        public abstract Builder refresh(int refresh);
        public abstract Builder minValue(float minValue);
        public abstract Builder maxValue(float maxValue);
        public abstract Builder step(float step);
        public abstract Builder period(String period);
        public abstract Builder service(String service);
        public abstract Builder legend(@Nullable Boolean legend);
        public abstract Builder height(int height);

        public OpenHABWidget build() {
            // A 'none' icon equals no icon at all
            if ("none".equals(icon())) {
                icon(null);
            }
            // Consider a minimal refresh rate of 100 ms, but 0 is special and means 'no refresh'
            if (refresh() > 0 && refresh() < 100) {
                refresh(100);
            }
            // Default period to 'D'
            if (period() == null || period().isEmpty()) {
                period("D");
            }
            // Sanitize minValue, maxValue and step: min <= max, step >= 0
            maxValue(Math.max(minValue(), maxValue()));
            step(Math.abs(step()));

            return autoBuild();
        }

        abstract String icon();
        abstract int refresh();
        abstract String period();
        abstract float minValue();
        abstract float maxValue();
        abstract float step();
        abstract OpenHABWidget autoBuild();
    }

    public static void parseXml(List<OpenHABWidget> allWidgets, OpenHABWidget parent, Node startNode) {
        OpenHABItem item = null;
        OpenHABLinkedPage linkedPage = null;
        String id = null, label = null, icon = null, url = null;
        String period = "", service = "", encoding = null;
        String iconColor = null, labelColor = null, valueColor = null;
        Type type = Type.Unknown;
        float minValue = 0f, maxValue = 100f, step = 1f;
        int refresh = 0, height = 0;
        List<OpenHABLabeledValue> mappings = new ArrayList<>();
        List<Node> childWidgetNodes = new ArrayList<>();

        if (startNode.hasChildNodes()) {
            NodeList childNodes = startNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                switch (childNode.getNodeName()) {
                    case "item": item = OpenHABItem.fromXml(childNode); break;
                    case "linkedPage": linkedPage = OpenHABLinkedPage.fromXml(childNode); break;
                    case "widget": childWidgetNodes.add(childNode); break;
                    case "type": type = parseType(childNode.getTextContent()); break;
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
                    case "iconcolor": iconColor = childNode.getTextContent(); break;
                    case "valuecolor": valueColor = childNode.getTextContent(); break;
                    case "labelcolor": labelColor = childNode.getTextContent(); break;
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
                        OpenHABLabeledValue mapping = OpenHABLabeledValue.newBuilder()
                                .value(mappingCommand)
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
        List<OpenHABLabeledValue> mappings = new ArrayList<>();
        if (widgetJson.has("mappings")) {
            JSONArray mappingsJsonArray = widgetJson.getJSONArray("mappings");
            for (int i = 0; i < mappingsJsonArray.length(); i++) {
                JSONObject mappingObject = mappingsJsonArray.getJSONObject(i);
                OpenHABLabeledValue mapping = OpenHABLabeledValue.newBuilder()
                        .value(mappingObject.getString("command"))
                        .label(mappingObject.getString("label"))
                        .build();
                mappings.add(mapping);
            }
        }

        OpenHABItem item = OpenHABItem.fromJson(widgetJson.optJSONObject("item"));
        Type type = parseType(widgetJson.getString("type"));
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
                .refresh(widgetJson.optInt("refresh"))
                .period(widgetJson.optString("period", "D"))
                .service(widgetJson.optString("service", ""))
                .legend(widgetJson.has("legend") ? widgetJson.getBoolean("legend") : null)
                .height(widgetJson.optInt("height"))
                .iconColor(widgetJson.optString("iconcolor", null))
                .labelColor(widgetJson.optString("labelcolor", null))
                .valueColor(widgetJson.optString("valuecolor", null))
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

    public static OpenHABWidget updateFromEvent(OpenHABWidget source, JSONObject eventPayload,
            String iconFormat) throws JSONException {
        OpenHABItem item = OpenHABItem.updateFromEvent(
                source.item(), eventPayload.getJSONObject("item"));
        String iconPath = determineOH2IconPath(item, source.type(),
                source.icon(), iconFormat, !source.mappings().isEmpty());
        return source.toBuilder()
                .label(eventPayload.optString("label", source.label()))
                .item(item)
                .iconPath(iconPath)
                .build();
    }

    private static String determineOH2IconPath(OpenHABItem item, Type type, String icon,
            String iconFormat, boolean hasMappings) {
        String itemState = item != null ? item.state() : null;
        if (itemState != null) {
            if (item.isOfTypeOrGroupType(OpenHABItem.Type.Color)) {
                // For items that control a color item fetch the correct icon
                if (type == Type.Slider || (type == Type.Switch && !hasMappings)) {
                    try {
                        itemState = String.valueOf(item.stateAsBrightness());
                        if (type == Type.Switch) {
                            itemState = itemState.equals("0") ? "OFF" : "ON";
                        }
                    } catch (Exception e) {
                        itemState = "OFF";
                    }
                } else if (item.stateAsHSV() != null) {
                    int color = Color.HSVToColor(item.stateAsHSV());
                    itemState = String.format(Locale.US, "#%02x%02x%02x",
                            Color.red(color), Color.green(color), Color.blue(color));
                }
            } else if (type == Type.Switch && !hasMappings
                    && !item.isOfTypeOrGroupType(OpenHABItem.Type.Rollershutter)) {
                // For switch items without mappings (just ON and OFF) that control a dimmer item
                // and which are not ON or OFF already, set the state to "OFF" instead of 0
                // or to "ON" to fetch the correct icon
                itemState = itemState.equals("0") || itemState.equals("OFF") ? "OFF" : "ON";
            }
        }

        return String.format("icon/%s?state=%s&format=%s", icon, itemState, iconFormat);
    }

    private static Type parseType(String type) {
        if (type != null) {
            try {
                return Type.valueOf(type);
            } catch (IllegalArgumentException e) {
                // fall through
            }
        }
        return Type.Unknown;
    }
}
