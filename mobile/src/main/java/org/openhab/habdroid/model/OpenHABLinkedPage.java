/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is a class to hold information about openHAB linked page.
 */

public class OpenHABLinkedPage {
	private String id;
	private String title;
	private String icon;
	private String link;
	private static final String TAG = OpenHABLinkedPage.class.getSimpleName();
	
	public OpenHABLinkedPage(Node startNode) {
		if (startNode.hasChildNodes()) {
			NodeList childNodes = startNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i ++) {
				Node childNode = childNodes.item(i);
				if (childNode.getNodeName().equals("id")) {
					this.setId(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("title")) {
					this.setTitle(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("icon")) {
					this.setIcon(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("link")) {
					this.setLink(childNode.getTextContent());
				}
			}
		}
	}

    public OpenHABLinkedPage(JSONObject jsonObject) {
        try {
            if (jsonObject.has("id"))
                this.setId(jsonObject.getString("id"));
            if (jsonObject.has("title"))
                this.setTitle(jsonObject.getString("title"));
            if (jsonObject.has("icon"))
                this.setIcon(jsonObject.getString("icon"));
            if (jsonObject.has("link"))
                this.setLink(jsonObject.getString("link"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
        if (title.indexOf('[') > 0) {
            return title.substring(0, title.indexOf('['));
        }
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public static String getTag() {
		return TAG;
	}
	
}
