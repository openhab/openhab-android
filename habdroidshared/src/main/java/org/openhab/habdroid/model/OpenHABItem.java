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

import android.graphics.Color;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is a class to hold basic information about openHAB Item.
 */

public class OpenHABItem {
	private String name;
	private String type;
	private String state = "";
	private String link;

	public OpenHABItem(Node startNode) {
		if (startNode.hasChildNodes()) {
			NodeList childNodes = startNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i ++) {
				Node childNode = childNodes.item(i);
				if (childNode.getNodeName().equals("type")) {
					this.setType(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("name")) {
					this.setName(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("state")) {
					if (childNode.getTextContent().equals("Uninitialized")) {
						this.setState("0");
					} else {
						this.setState(childNode.getTextContent());
					}
				} else if (childNode.getNodeName().equals("link")) {					
					this.setLink(childNode.getTextContent());
				}
			}
		}
	}

    public OpenHABItem(JSONObject jsonObject) {
            try {
                if (jsonObject.has("type"))
                    this.setType(jsonObject.getString("type"));
                if (jsonObject.has("name"))
                    this.setName(jsonObject.getString("name"));
                if (jsonObject.has("state")) {
                    if (jsonObject.getString("state").equals("Uninitialized")) {
                        this.setState("0");
                    } else {
                        this.setState(jsonObject.getString("state"));
                    }
                }
                if (jsonObject.has("link"))
                    this.setLink(jsonObject.getString("link"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
    }
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}
	
	public boolean getStateAsBoolean() {
		// If state is ON for switches return True
		if (state.equals("ON")) {
			return true;
		}
		// If decimal value and it is >0 return True
		try {
			int decimalValue = Integer.valueOf(state);
			if (decimalValue > 0)
				return true;
		} catch (NumberFormatException e) {
			return false;
		}
		// Else return False
		return false;
	}
	
	public Float getStateAsFloat() {
		return Float.parseFloat(state);
	}

	public float[] getStateAsHSV() {
		String[] stateSplit = state.split(",");
		if (stateSplit.length == 3) { // We need exactly 3 numbers to operate this
			float[] result = {Float.parseFloat(stateSplit[0]), Float.parseFloat(stateSplit[1])/100, Float.parseFloat(stateSplit[2])/100};
			return result;
		} else {
			float[] result = {0, 0, 0};
			return result;
		}
	}

	public int getStateAsColor() {
		return Color.HSVToColor(getStateAsHSV());
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}
	
}
