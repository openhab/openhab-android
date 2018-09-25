/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a class to hold basic information about openHAB Item.
 */

@AutoValue
public abstract class OpenHABItem implements Parcelable {
    public enum Type {
        None,
        Color,
        Contact,
        DateTime,
        Dimmer,
        Group,
        Image,
        Location,
        Number,
        Player,
        Rollershutter,
        StringItem,
        Switch
    }

    public abstract String name();
    public abstract String label();
    public abstract Type type();
    @Nullable
    public abstract Type groupType();
    @Nullable
    public abstract String link();
    public abstract boolean readOnly();
    public abstract List<OpenHABItem> members();
    @Nullable
    public abstract List<OpenHABLabeledValue> options();
    @Nullable
    public abstract String state();
    public abstract boolean stateAsBoolean();
    public abstract float stateAsFloat();
    @SuppressWarnings("mutable")
    @Nullable
    public abstract float[] stateAsHSV();
    @Nullable
    public abstract Integer stateAsBrightness();

    public boolean isOfTypeOrGroupType(Type type) {
        return type() == type || groupType() == type;
    }

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder label(String label);
        public abstract Builder type(Type type);
        public abstract Builder groupType(Type type);
        public abstract Builder state(@Nullable String state);
        public abstract Builder link(@Nullable String link);
        public abstract Builder readOnly(boolean readOnly);
        public abstract Builder members(List<OpenHABItem> members);
        public abstract Builder options(@Nullable List<OpenHABLabeledValue> options);

        public OpenHABItem build() {
            String state = state();
            return stateAsBoolean(parseAsBoolean(state))
                    .stateAsFloat(parseAsFloat(state))
                    .stateAsHSV(parseAsHSV(state))
                    .stateAsBrightness(parseAsBrightness(state))
                    .autoBuild();
        }

        abstract String state();
        abstract Builder stateAsBoolean(boolean state);
        abstract Builder stateAsFloat(float state);
        abstract Builder stateAsHSV(float[] hsv);
        abstract Builder stateAsBrightness(@Nullable Integer brightness);
        abstract OpenHABItem autoBuild();

        private static boolean parseAsBoolean(String state) {
            // For uninitialized/null state return false
            if (state == null) {
                return false;
            }
            // If state is ON for switches return True
            if (state.equals("ON")) {
                return true;
            }

            Integer brightness = parseAsBrightness(state);
            if (brightness != null) {
                return brightness != 0;
            }
            try {
                int decimalValue = Integer.valueOf(state);
                return decimalValue > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private static float parseAsFloat(String state) {
            // For uninitialized/null state return zero
            if (state == null) {
                return 0f;
            } else if ("ON".equals(state)) {
                return 100f;
            } else if ("OFF".equals(state)) {
                return 0f;
            } else {
                try {
                    return Float.parseFloat(state);
                } catch (NumberFormatException e) {
                    return 0f;
                }
            }
        }

        private static float[] parseAsHSV(String state) {
            if (state != null) {
                String[] stateSplit = state.split(",");
                if (stateSplit.length == 3) { // We need exactly 3 numbers to operate this
                    try {
                        return new float[]{
                                Float.parseFloat(stateSplit[0]),
                                Float.parseFloat(stateSplit[1]) / 100,
                                Float.parseFloat(stateSplit[2]) / 100
                        };
                    } catch (NumberFormatException e) {
                        // fall through to returning null
                    }
                }
            }
            return null;
        }

        public static Integer parseAsBrightness(String state) {
            if (state != null) {
                Matcher hsbMatcher = HSB_PATTERN.matcher(state);
                if (hsbMatcher.find()) {
                    try {
                        return Float.valueOf(hsbMatcher.group(3)).intValue();
                    } catch (NumberFormatException e) {
                        // fall through
                    }
                }
            }
            return null;
        }

        private final static Pattern HSB_PATTERN = Pattern.compile("^([0-9]*\\.?[0-9]+),([0-9]*\\.?[0-9]+),([0-9]*\\.?[0-9]+)$");
    }

    private static Type parseType(String type) {
        if (type == null) {
            return Type.None;
        }
        // Earlier OH2 versions returned e.g. 'Switch' as 'SwitchItem'
        if (type.endsWith("Item")) {
            type = type.substring(0, type.length() - 4);
        }
        if ("String".equals(type)) {
            return Type.StringItem;
        }
        try {
            return Type.valueOf(type);
        } catch (IllegalArgumentException e) {
            return Type.None;
        }
    }

    public static OpenHABItem fromXml(Node startNode) {
        String name = null, state = null, link = null;
        Type type = Type.None, groupType = Type.None;
        if (startNode.hasChildNodes()) {
            NodeList childNodes = startNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                switch (childNode.getNodeName()) {
                    case "type": type = parseType(childNode.getTextContent()); break;
                    case "groupType": groupType = parseType(childNode.getTextContent()); break;
                    case "name": name = childNode.getTextContent(); break;
                    case "state": state = childNode.getTextContent(); break;
                    case "link": link = childNode.getTextContent(); break;
                }
            }
        }

        return new AutoValue_OpenHABItem.Builder()
                .type(type)
                .groupType(groupType)
                .name(name)
                .label(name)
                .state("Unitialized".equals(state) ? null : state)
                .members(new ArrayList<OpenHABItem>())
                .link(link)
                .readOnly(false)
                .build();
    }

    public static OpenHABItem updateFromEvent(OpenHABItem item, JSONObject jsonObject)
            throws JSONException {
        if (jsonObject == null) {
            return item;
        }
        Builder builder = parseFromJson(jsonObject);
        // Events don't contain the link property, so preserve that if previously present
        if (item != null) {
            builder.link(item.link());
        }
        return builder.build();
    }

    public static OpenHABItem fromJson(JSONObject jsonObject) throws JSONException {
        if (jsonObject == null) {
            return null;
        }
        return parseFromJson(jsonObject).build();
    }

    private static OpenHABItem.Builder parseFromJson(JSONObject jsonObject) throws JSONException {
        String name = jsonObject.getString("name");
        String state = jsonObject.optString("state", "");
        if ("NULL".equals(state) || "UNDEF".equals(state) || "undefined".equalsIgnoreCase(state)) {
            state = null;
        }

        JSONObject stateDescription = jsonObject.optJSONObject("stateDescription");
        boolean readOnly = stateDescription != null
                ? stateDescription.optBoolean("readOnly", false)
                : false;

        List<OpenHABLabeledValue> options = null;
        if (stateDescription != null && stateDescription.has("options")) {
            JSONArray optionsJson = stateDescription.getJSONArray("options");
            options = new ArrayList<>();
            for (int i = 0; i < optionsJson.length(); i++) {
                JSONObject optionJson = optionsJson.getJSONObject(i);
                options.add(OpenHABLabeledValue.newBuilder()
                        .value(optionJson.getString("value"))
                        .label(optionJson.getString("label"))
                        .build());
            }
        }

        List<OpenHABItem> members = new ArrayList<>();
        JSONArray membersJson = jsonObject.optJSONArray("members");
        if (membersJson != null) {
            for (int i = 0; i < membersJson.length(); i++) {
                members.add(fromJson(membersJson.getJSONObject(i)));
            }
        }

        return new AutoValue_OpenHABItem.Builder()
                .type(parseType(jsonObject.getString("type")))
                .groupType(parseType(jsonObject.optString("groupType")))
                .name(name)
                .label(jsonObject.optString("label", name))
                .link(jsonObject.optString("link", null))
                .members(members)
                .options(options)
                .state(state)
                .readOnly(readOnly);
    }
}
