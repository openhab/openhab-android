/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.model;

import java.util.ArrayList;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.util.Log;

/**
 * This class provides datasource for openHAB widgets from sitemap page.
 * It uses a sitemap page XML document to create a list of widgets
 * 
 * @author Victor Belov
 *
 */

public class OpenHABWidgetDataSource {
	private static final String TAG = "OpenHABWidgetDataSource";
	private OpenHABWidget rootWidget;
	private String title;
	private String id;
	private String icon;
	private String link;

	public OpenHABWidgetDataSource() {
		
	}
	
	public OpenHABWidgetDataSource(Node rootNode) {
		setSourceNode(rootNode);
	}
	
	public void setSourceNode(Node rootNode) {
		Log.i(TAG, "Loading new data");
		rootWidget = new OpenHABWidget();
		rootWidget.setType("root");
		if (rootNode.hasChildNodes()) {
			NodeList childNodes = rootNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i ++) {
				Node childNode = childNodes.item(i);
				if (childNode.getNodeName().equals("widget")) {
					new OpenHABWidget(rootWidget, childNode);
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
		splitString = title.split("\\[|\\]");
		return splitString[0];
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
