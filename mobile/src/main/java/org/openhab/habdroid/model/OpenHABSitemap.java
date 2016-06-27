/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class OpenHABSitemap implements Parcelable {
	private String name;
    private String label;
	private String link;
    private String icon;
	private String homepageLink;
    private boolean leaf = false;

    OpenHABSitemap(Parcel in) {
        this.name = in.readString();
        this.label = in.readString();
        this.link = in.readString();
        this.icon = in.readString();
        this.homepageLink = in.readString();
    }

    protected OpenHABSitemap() {
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

    public abstract String getIconPath();

}
