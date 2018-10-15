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

import com.google.auto.value.AutoValue;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is a class to hold information about openHAB linked page.
 */

@AutoValue
public abstract class LinkedPage implements Parcelable {
    public abstract String id();
    public abstract String title();
    public abstract String icon();
    public abstract String link();

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder id(String id);
        public abstract Builder title(String title);
        public abstract Builder icon(String icon);
        public abstract Builder link(String link);

        public LinkedPage build() {
            String title = title();
            if (title.indexOf('[') > 0) {
                title(title.substring(0, title.indexOf('[')));
            }
            return autoBuild();
        }

        abstract String title();
        abstract LinkedPage autoBuild();
    }

    public static LinkedPage fromXml(Node startNode) {
        String id = null, title = null, icon = null, link = null;

        if (startNode.hasChildNodes()) {
            NodeList childNodes = startNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                switch (childNode.getNodeName()) {
                    case "id": id = childNode.getTextContent(); break;
                    case "title": title = childNode.getTextContent(); break;
                    case "icon": icon = childNode.getTextContent(); break;
                    case "link": link = childNode.getTextContent(); break;
                    default: break;
                }
            }
        }

        return new AutoValue_LinkedPage.Builder()
                .id(id)
                .title(title)
                .icon(icon)
                .link(link)
                .build();
    }

    public static LinkedPage fromJson(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        return new AutoValue_LinkedPage.Builder()
                .id(jsonObject.optString("id", null))
                .title(jsonObject.optString("title", null))
                .icon(jsonObject.optString("icon", null))
                .link(jsonObject.optString("link", null))
                .build();
    }
}
