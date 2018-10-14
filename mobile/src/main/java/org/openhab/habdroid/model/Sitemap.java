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
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@AutoValue
public abstract class Sitemap implements Parcelable {
    public abstract String name();
    public abstract String label();
    public abstract String link();
    @Nullable
    public abstract String icon();
    public abstract String iconPath();
    public abstract String homepageLink();

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder label(String label);
        public abstract Builder link(String link);
        public abstract Builder icon(@Nullable String icon);
        public abstract Builder iconPath(String iconPath);
        public abstract Builder homepageLink(String homepageLink);

        public abstract Sitemap build();
    }

    public static Sitemap fromXml(Node startNode) {
        String label = null, name = null, icon = null, link = null, homepageLink = null;

        if (startNode.hasChildNodes()) {
            NodeList childNodes = startNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                switch (childNode.getNodeName()) {
                    case "name": name = childNode.getTextContent(); break;
                    case "label": label = childNode.getTextContent(); break;
                    case "link": link = childNode.getTextContent(); break;
                    case "icon": icon = childNode.getTextContent(); break;
                    case "homepage":
                        if (childNode.hasChildNodes()) {
                            NodeList homepageNodes = childNode.getChildNodes();
                            for (int j = 0; j < homepageNodes.getLength(); j++) {
                                Node homepageChildNode = homepageNodes.item(j);
                                if (homepageChildNode.getNodeName().equals("link")) {
                                    homepageLink = homepageChildNode.getTextContent();
                                    break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        return new AutoValue_Sitemap.Builder()
                .name(name)
                .label(label != null ? label : name)
                .link(link)
                .homepageLink(homepageLink)
                .icon(icon)
                .iconPath(String.format("images/%s.png", icon))
                .build();
    }

    public static Sitemap fromJson(JSONObject jsonObject) {
        String name = jsonObject.optString("name", null);
        String label = jsonObject.optString("label", null);
        String icon = jsonObject.optString("icon", null);
        JSONObject homepageObject = jsonObject.optJSONObject("homepage");
        String homepageLink = homepageObject != null
                ? homepageObject.optString("link", null) : null;

        return new AutoValue_Sitemap.Builder()
                .name(name)
                .label(label != null ? label : name)
                .icon(icon)
                .iconPath(String.format("icon/%s", icon))
                .link(jsonObject.optString("link", null))
                .homepageLink(homepageLink)
                .build();
    }
}
