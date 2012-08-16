package org.openhab.habdroid.model;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OpenHABSitemap {
	private String name;
	private String link;
	private String homepageLink;
	
	public OpenHABSitemap(Node startNode) {
		if (startNode.hasChildNodes()) {
			NodeList childNodes = startNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i ++) {
				Node childNode = childNodes.item(i);
				if (childNode.getNodeName().equals("name")) {
					this.setName(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("link")) {
					this.setLink(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("homepage")) {
					if (childNode.hasChildNodes()) {
						NodeList homepageNodes = childNode.getChildNodes();
						for (int j = 0; j < homepageNodes.getLength(); j++) {
							Node homepageChildNode = homepageNodes.item(j);
							if (homepageChildNode.getNodeName().equals("link")) {
								this.setHomepageLink(homepageChildNode.getTextContent());
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
	
}
