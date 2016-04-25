package org.openhab.habdroid.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OpenHAB1Sitemap extends OpenHABSitemap {
    public OpenHAB1Sitemap(Node startNode) {
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
    public static final Parcelable.Creator<OpenHABSitemap> CREATOR = new Parcelable.Creator<OpenHABSitemap>() {
        public OpenHABSitemap createFromParcel(Parcel in) {
            return new OpenHAB1Sitemap(in);
        }

        public OpenHABSitemap[] newArray(int size) {
            return new OpenHAB1Sitemap[size];
        }
    };

    public OpenHAB1Sitemap(Parcel in) {
        super(in);
    }

    @Override
    public String getIconPath() {
        return String.format("images/%s.png", getIcon());
    }
}
