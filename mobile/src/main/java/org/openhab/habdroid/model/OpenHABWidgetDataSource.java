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

/**
 * This class provides datasource for openHAB widgets from sitemap page.
 * It uses a sitemap page XML document to create a list of widgets
 */

public class OpenHABWidgetDataSource {
	private static final String TAG = OpenHABWidgetDataSource.class.getSimpleName();
	private final String iconFormat;
	private OpenHABWidget rootWidget;
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
		rootWidget = new OpenHAB1Widget();
		rootWidget.setType("root");
		if (rootNode.hasChildNodes()) {
			NodeList childNodes = rootNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i ++) {
				Node childNode = childNodes.item(i);
				if (childNode.getNodeName().equals("widget")) {
					OpenHAB1Widget.createOpenHABWidgetFromNode(rootWidget, childNode);
				} else if (childNode.getNodeName().equals("title")) {
					this.setTitle(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("id")) {
					this.setId(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("icon")) {
					this.setIcon(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("link")) {
					this.setLink(childNode.getTextContent());
				}
			}
		}
	}

    public void setSourceJson(JSONObject jsonObject) {
        Log.d(TAG, jsonObject.toString());
        if (!jsonObject.has("widgets"))
            return;
        rootWidget = new OpenHAB2Widget();
        rootWidget.setType("root");
        try {
            JSONArray jsonWidgetArray = jsonObject.getJSONArray("widgets");
            for (int i=0; i<jsonWidgetArray.length(); i++) {
                JSONObject widgetJson = jsonWidgetArray.getJSONObject(i);
                // Log.d(TAG, widgetJson.toString());
                OpenHAB2Widget.createOpenHABWidgetFromJson(rootWidget, widgetJson, iconFormat);
            }
            if (jsonObject.has("title"))
                this.setTitle(jsonObject.getString("title"));
            if (jsonObject.has("id"))
                this.setId(jsonObject.getString("id"));
            if (jsonObject.has("icon"))
                this.setIcon(jsonObject.getString("icon"));
            if (jsonObject.has("link"))
                this.setLink(jsonObject.getString("link"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
	
	public OpenHABWidget getRootWidget() {
		return this.rootWidget;
	}

	public OpenHABWidget getWidgetById(String widgetId) {
		ArrayList<OpenHABWidget> widgets = this.getWidgets();
		for (int i = 0; i < widgets.size(); i++) {
			if (widgets.get(i).getId().equals(widgetId))
				return widgets.get(i);
		}
		return null;
	}
	
	public ArrayList<OpenHABWidget> getWidgets() {
		ArrayList<OpenHABWidget> result = new ArrayList<OpenHABWidget>();
		if (rootWidget != null)
		if (this.rootWidget.hasChildren()) {
			for (int i = 0; i < rootWidget.getChildren().size(); i++) {
				OpenHABWidget openHABWidget = this.rootWidget.getChildren().get(i);
				result.add(openHABWidget);
				if (openHABWidget.hasChildren()) {
					for (int j = 0; j < openHABWidget.getChildren().size(); j++) {
						result.add(openHABWidget.getChildren().get(j));
					}
				}
			}
		}
		return result;
	}

    public ArrayList<OpenHABWidget> getLinkWidgets() {
        ArrayList<OpenHABWidget> result = new ArrayList<OpenHABWidget>();
        if (rootWidget != null)
            if (this.rootWidget.hasChildren()) {
                for (int i = 0; i < rootWidget.getChildren().size(); i++) {
                    OpenHABWidget openHABWidget = this.rootWidget.getChildren().get(i);
                    if (openHABWidget.hasLinkedPage() || openHABWidget.childrenHasLinkedPages())
                    result.add(openHABWidget);
                    if (openHABWidget.hasChildren()) {
                        for (int j = 0; j < openHABWidget.getChildren().size(); j++) {
                            if (openHABWidget.getChildren().get(j).hasLinkedPage())
                                result.add(openHABWidget.getChildren().get(j));
                        }
                    }
                }
            }
        return result;
    }

    public ArrayList<OpenHABWidget> getNonlinkWidgets() {
        ArrayList<OpenHABWidget> result = new ArrayList<OpenHABWidget>();
        if (rootWidget != null)
            if (this.rootWidget.hasChildren()) {
                for (int i = 0; i < rootWidget.getChildren().size(); i++) {
                    OpenHABWidget openHABWidget = this.rootWidget.getChildren().get(i);
                    if ((openHABWidget.getType().equals("Frame") && openHABWidget.childrenHasNonlinkedPages()) ||
                            (!openHABWidget.getType().equals("Frame") && !openHABWidget.hasLinkedPage()))
                        result.add(openHABWidget);
                    if (openHABWidget.hasChildren()) {
                        for (int j = 0; j < openHABWidget.getChildren().size(); j++) {
                            if (!openHABWidget.getChildren().get(j).hasLinkedPage())
                                result.add(openHABWidget.getChildren().get(j));
                        }
                    }
                }
            }
        return result;
    }


    public void logWidget(OpenHABWidget widget) {
		Log.i(TAG, "Widget <" + widget.getLabel() + "> (" + widget.getType() + ")");
		if (widget.hasChildren()) {
			for (int i = 0; i < widget.getChildren().size(); i++) {
				logWidget(widget.getChildren().get(i));
			}
		}
	}

	public String getTitle() {
		String[] splitString;
        if (title != null) {
    		splitString = title.split("\\[|\\]");
            if (splitString.length>0) {
                return splitString[0];
            } else {
                return title;
            }
        }
        return "";
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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
	
}
