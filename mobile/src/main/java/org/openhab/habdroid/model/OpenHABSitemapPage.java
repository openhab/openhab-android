/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;

public class OpenHABSitemapPage {

    private ArrayList<OpenHABWidget> mWidgets;
    private String mTitle;
    private String mPageId;
    private String mIcon;
    private String mLink;
    OpenHABWidget mRootWidget;

    public OpenHABSitemapPage(Document document) {
        Node rootNode = document.getFirstChild();
        if (rootNode == null)
            return;
        mRootWidget = new OpenHAB1Widget();
        mRootWidget.setType("root");
        if (rootNode.hasChildNodes()) {
            NodeList childNodes = rootNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i ++) {
                Node childNode = childNodes.item(i);
                if (childNode.getNodeName().equals("widget")) {
                    OpenHABWidget newOpenHABWidget = OpenHAB1Widget.createOpenHABWidgetFromNode(mRootWidget, childNode);
                    mWidgets.add(newOpenHABWidget);
                } else if (childNode.getNodeName().equals("title")) {
                    this.setTitle(childNode.getTextContent());
                } else if (childNode.getNodeName().equals("id")) {
                    this.setPageId(childNode.getTextContent());
                } else if (childNode.getNodeName().equals("icon")) {
                    this.setIcon(childNode.getTextContent());
                } else if (childNode.getNodeName().equals("link")) {
                    this.setLink(childNode.getTextContent());
                }
            }
        }

    }

    public OpenHABSitemapPage(JSONObject document) {
    }

    public ArrayList<OpenHABWidget> getWidgets() {
        ArrayList<OpenHABWidget> result = new ArrayList<OpenHABWidget>();
        if (mRootWidget != null)
            if (this.mRootWidget.hasChildren()) {
                for (int i = 0; i < mRootWidget.getChildren().size(); i++) {
                    OpenHABWidget openHABWidget = this.mRootWidget.getChildren().get(i);
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

    public void setWidgets(ArrayList<OpenHABWidget> mWidgets) {
        this.mWidgets = mWidgets;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    public String getPageId() {
        return mPageId;
    }

    public void setPageId(String mPageId) {
        this.mPageId = mPageId;
    }

    public String getIcon() {
        return mIcon;
    }

    public void setIcon(String mIcon) {
        this.mIcon = mIcon;
    }

    public String getLink() {
        return mLink;
    }

    public void setLink(String mLink) {
        this.mLink = mLink;
    }
}
