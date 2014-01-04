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

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OpenHABSitemap {
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
}
