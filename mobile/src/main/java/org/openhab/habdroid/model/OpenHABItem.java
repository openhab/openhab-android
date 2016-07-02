/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import android.graphics.Color;
import android.util.Log;

import com.crittercism.app.Crittercism;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a class to hold basic information about openHAB Item.
 */

public class OpenHABItem {
	private String name;
	private String type;
	private String groupType;
	private String state = "";
	private String link;
	private final static String TAG = OpenHABItem.class.getSimpleName();
	private final static Pattern HSB_PATTERN = Pattern.compile("^\\d+,\\d+,(\\d+)$");

	public OpenHABItem(Node startNode) {
		if (startNode.hasChildNodes()) {
			NodeList childNodes = startNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i ++) {
				Node childNode = childNodes.item(i);
				if (childNode.getNodeName().equals("type")) {
					this.setType(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("groupType")) {
					this.setGroupType(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("name")) {
					this.setName(childNode.getTextContent());
				} else if (childNode.getNodeName().equals("state")) {
					if (childNode.getTextContent().equals("Uninitialized")) {
						this.setState(null);
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
				if (jsonObject.has("groupType"))
					this.setGroupType(jsonObject.getString("groupType"));
                if (jsonObject.has("name"))
                    this.setName(jsonObject.getString("name"));
                if (jsonObject.has("state")) {
                    if (jsonObject.getString("state").equals("NULL") ||
                            jsonObject.getString("state").equals("UNDEF") ||
                            jsonObject.getString("state").equalsIgnoreCase("undefined")) {
                        this.setState(null);
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

	public String getGroupType() {
		return groupType;
	}

	public void setGroupType(String groupType) {
		this.groupType = groupType;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public boolean getStateAsBoolean() {
		// For uninitialized/null state return false
		if (state == null) {
			return false;
		}
		// If state is ON for switches return True
		if (state.equals("ON")) {
			return true;
		}

		Matcher hsbMatcher = HSB_PATTERN.matcher(state);
		if(hsbMatcher.find()) {
			String brightness = hsbMatcher.group(1);
			return isValueDecimalIntegerAndGreaterThanZero(brightness);
		}

		return isValueDecimalIntegerAndGreaterThanZero(state);

	}

	private Boolean isValueDecimalIntegerAndGreaterThanZero(String value) {
		try {
			int decimalValue = Integer.valueOf(value);
			return decimalValue > 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public Float getStateAsFloat() {
		Float result;
		// For uninitialized/null state return zero
		if (state == null) {
			result = 0f;
		} else if ("ON".equals(state)) {
			result = 100f;
		} else if ("OFF".equals(state)) {
			result = 0f;
		} else {
			try {
				result = Float.parseFloat(state);
			} catch (NumberFormatException e) {
				if (e != null) {
					Crittercism.logHandledException(e);
					Log.e(TAG, e.getMessage());
				}
				result = new Float(0);
			}
		}
		return result;
	}

	public float[] getStateAsHSV() {
		if (state == null) {
			float[] result = {0, 0, 0};
			return result;
		}
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
