/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Victor Belov
 *  @since 1.4.0
 *
 */

package org.openhab.habdroid.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OpenHABSitemap implements Parcelable {
	private String name;
    private String label;
	private String link;
    private String icon;
	private String homepageLink;
    private boolean leaf = false;
	
	public OpenHABSitemap(Node startNode) {
		if (startNode.hasChildNodes()) {
			NodeList childNodes = startNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i ++) {
				Node childNode = childNodes.item(i);
				if (childNode.getNodeName().equals("name")) {
					this.setName(childNode.getTextContent());
                } else if (childNode.getNodeName().equals("label")) {
                    this.setLabel(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("link")) {
					this.setLink(childNode.getTextContent());
                } else if (childNode.getNodeName().equals("icon")) {
                    this.setIcon(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("homepage")) {
					if (childNode.hasChildNodes()) {
						NodeList homepageNodes = childNode.getChildNodes();
						for (int j = 0; j < homepageNodes.getLength(); j++) {
							Node homepageChildNode = homepageNodes.item(j);
							if (homepageChildNode.getNodeName().equals("link")) {
								this.setHomepageLink(homepageChildNode.getTextContent());
							} else if (homepageChildNode.getNodeName().equals("leaf")) {
                                if (homepageChildNode.getTextContent().equals("true")) {
                                    setLeaf(true);
                                } else {
                                    setLeaf(false);
                                }
                            }
						}
					}
				}
			}
		}
	}

    public OpenHABSitemap(JSONObject jsonObject) {
        try {
            if (jsonObject.has("name"))
                this.setName(jsonObject.getString("name"));
            if (jsonObject.has("label"))
                this.setLabel(jsonObject.getString("label"));
            if (jsonObject.has("link"))
                this.setLink(jsonObject.getString("link"));
            if (jsonObject.has("icon"))
                this.setIcon(jsonObject.getString("icon"));
            if (jsonObject.has("homepage")) {
                JSONObject homepageObject = jsonObject.getJSONObject("homepage");
                this.setHomepageLink(homepageObject.getString("link"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private OpenHABSitemap(Parcel in) {
        this.name = in.readString();
        this.label = in.readString();
        this.link = in.readString();
        this.icon = in.readString();
        this.homepageLink = in.readString();
    }

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getLink() {
		return link;
	}
	public void setLink(String link) {
		this.link = link;
	}
	public String getHomepageLink() {
		return homepageLink;
	}
	public void setHomepageLink(String homepageLink) {
		this.homepageLink = homepageLink;
	}
    public String getIcon() {
        return icon;
    }
    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isLeaf() {
        return leaf;
    }

    public void setLeaf(boolean isLeaf) {
        leaf = isLeaf;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(label);
        dest.writeString(link);
        dest.writeString(icon);
        dest.writeString(homepageLink);
    }

    public static final Parcelable.Creator<OpenHABSitemap> CREATOR = new Parcelable.Creator<OpenHABSitemap>() {
        public OpenHABSitemap createFromParcel(Parcel in) {
            return new OpenHABSitemap(in);
        }

        public OpenHABSitemap[] newArray(int size) {
            return new OpenHABSitemap[size];
        }
    };

}
