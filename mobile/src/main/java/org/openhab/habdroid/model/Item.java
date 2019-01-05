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

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a class to hold basic information about openHAB Item.
 */

@AutoValue
public abstract class Item implements Parcelable {
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
    public abstract List<Item> members();
    @Nullable
    public abstract List<LabeledValue> options();
    @Nullable
    public abstract ParsedState state();

    public boolean isOfTypeOrGroupType(Type type) {
        return type() == type || groupType() == type;
    }

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder label(String label);
        public abstract Builder type(Type type);
        public abstract Builder groupType(Type type);
        public abstract Builder state(@Nullable ParsedState state);
        public abstract Builder link(@Nullable String link);
        public abstract Builder readOnly(boolean readOnly);
        public abstract Builder members(List<Item> members);
        public abstract Builder options(@Nullable List<LabeledValue> options);
        public abstract Item build();
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

    public static Item fromXml(Node startNode) {
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
                    default: break;
                }
            }
        }

        if ("Uninitialized".equals(state) || "Undefined".equals(state)) {
            state = null;
        }

        return new AutoValue_Item.Builder()
                .type(type)
                .groupType(groupType)
                .name(name)
                .label(name)
                .state(ParsedState.from(state, null))
                .members(new ArrayList<>())
                .link(link)
                .readOnly(false)
                .build();
    }

    public static Item updateFromEvent(Item item, JSONObject jsonObject) throws JSONException {
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

    public static Item fromJson(JSONObject jsonObject) throws JSONException {
        if (jsonObject == null) {
            return null;
        }
        return parseFromJson(jsonObject).build();
    }

    private static Item.Builder parseFromJson(JSONObject jsonObject) throws JSONException {
        String name = jsonObject.getString("name");
        String state = jsonObject.optString("state", "");
        if ("NULL".equals(state) || "UNDEF".equals(state) || "undefined".equalsIgnoreCase(state)) {
            state = null;
        }

        JSONObject stateDescription = jsonObject.optJSONObject("stateDescription");
        boolean readOnly = stateDescription != null
                && stateDescription.optBoolean("readOnly", false);

        List<LabeledValue> options = null;
        if (stateDescription != null && stateDescription.has("options")) {
            JSONArray optionsJson = stateDescription.getJSONArray("options");
            options = new ArrayList<>();
            for (int i = 0; i < optionsJson.length(); i++) {
                JSONObject optionJson = optionsJson.getJSONObject(i);
                options.add(LabeledValue.newBuilder()
                        .value(optionJson.getString("value"))
                        .label(optionJson.getString("label"))
                        .build());
            }
        }

        List<Item> members = new ArrayList<>();
        JSONArray membersJson = jsonObject.optJSONArray("members");
        if (membersJson != null) {
            for (int i = 0; i < membersJson.length(); i++) {
                members.add(fromJson(membersJson.getJSONObject(i)));
            }
        }

        String numberPattern = stateDescription != null
                ? stateDescription.optString("pattern") : null;

        return new AutoValue_Item.Builder()
                .type(parseType(jsonObject.getString("type")))
                .groupType(parseType(jsonObject.optString("groupType")))
                .name(name)
                .label(jsonObject.optString("label", name))
                .link(jsonObject.optString("link", null))
                .members(members)
                .options(options)
                .state(ParsedState.from(state, numberPattern))
                .readOnly(readOnly);
    }
}
