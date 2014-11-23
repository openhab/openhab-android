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
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;

/**
 * This is a class to hold basic information about openHAB widget.
 */

public class OpenHABWidget {
	private String id;
	private String label;
	private String icon;
	private String type;
	private String url;
	private String period = "";
    private String service = "";
	private float minValue =0;
	private float maxValue = 100;
	private float step = 1;
	private int refresh = 0;
	private int height = 0;
	private OpenHABWidget parent;
	private OpenHABItem item;
	private OpenHABLinkedPage linkedPage;
	private ArrayList<OpenHABWidget> children;
	private ArrayList<OpenHABWidgetMapping> mappings;
    private boolean mChildrenHasLinkedPages = false;
    private Integer iconcolor;
    private Integer labelcolor;
    private Integer valuecolor;
    private String encoding;

	public OpenHABWidget() {
		this.children = new ArrayList<OpenHABWidget>();
		this.mappings = new ArrayList<OpenHABWidgetMapping>();
	}
	
	public OpenHABWidget(OpenHABWidget parent, Node startNode) {
		this.parent = parent;
		this.children = new ArrayList<OpenHABWidget>();
		this.mappings = new ArrayList<OpenHABWidgetMapping>();
		if (startNode.hasChildNodes()) {
			NodeList childNodes = startNode.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i ++) {
				Node childNode = childNodes.item(i);
				if (childNode.getNodeName().equals("item")) {
					this.setItem(new OpenHABItem(childNode));
				} else if (childNode.getNodeName().equals("linkedPage")) {					
					this.setLinkedPage(new OpenHABLinkedPage(childNode));
				} else if (childNode.getNodeName().equals("widget")) {
					new OpenHABWidget(this, childNode);
				} else {
					if (childNode.getNodeName().equals("type")) {
						this.setType(childNode.getTextContent());
					} else if (childNode.getNodeName().equals("widgetId")) {
						this.setId(childNode.getTextContent());
					} else if (childNode.getNodeName().equals("label")) {
						this.setLabel(childNode.getTextContent());
					} else if (childNode.getNodeName().equals("icon")) {
						this.setIcon(childNode.getTextContent());
					} else if (childNode.getNodeName().equals("url")) {
						this.setUrl(childNode.getTextContent());
					} else if (childNode.getNodeName().equals("minValue")) {
						setMinValue(Float.valueOf(childNode.getTextContent()).floatValue());
					} else if (childNode.getNodeName().equals("maxValue")) {
						setMaxValue(Float.valueOf(childNode.getTextContent()).floatValue());
					} else if (childNode.getNodeName().equals("step")) {
						setStep(Float.valueOf(childNode.getTextContent()).floatValue());
					} else if (childNode.getNodeName().equals("refresh")) {
						setRefresh(Integer.valueOf(childNode.getTextContent()).intValue());
					} else if (childNode.getNodeName().equals("period")) {
						setPeriod(childNode.getTextContent());
                    } else if (childNode.getNodeName().equals("service")) {
                        setService(childNode.getTextContent());
                    } else if (childNode.getNodeName().equals("height")) {
						setHeight(Integer.valueOf(childNode.getTextContent()));
					} else if (childNode.getNodeName().equals("mapping")) {
						NodeList mappingChildNodes = childNode.getChildNodes();
						String mappingCommand = "";
						String mappingLabel = "";
						for (int k = 0; k < mappingChildNodes.getLength(); k++) {
							if (mappingChildNodes.item(k).getNodeName().equals("command"))
								mappingCommand = mappingChildNodes.item(k).getTextContent();
							if (mappingChildNodes.item(k).getNodeName().equals("label"))
								mappingLabel = mappingChildNodes.item(k).getTextContent();
						}
						OpenHABWidgetMapping mapping = new OpenHABWidgetMapping(mappingCommand, mappingLabel);
						mappings.add(mapping);
					} else if (childNode.getNodeName().equals("iconcolor")) {
                        setIconColor(childNode.getTextContent());
                    } else if (childNode.getNodeName().equals("labelcolor")) {
                        setLabelColor(childNode.getTextContent());
                    } else if (childNode.getNodeName().equals("valuecolor")) {
                        setValueColor(childNode.getTextContent());
                    } else if (childNode.getNodeName().equals("encoding")) {
                        setEncoding(childNode.getTextContent());
                    }
				}
			}
		}
		this.parent.addChildWidget(this);
	}

    public OpenHABWidget(OpenHABWidget parent, JSONObject widgetJson) {
        this.parent = parent;
        this.children = new ArrayList<OpenHABWidget>();
        this.mappings = new ArrayList<OpenHABWidgetMapping>();
        try {
            if (widgetJson.has("item")) {
                this.setItem(new OpenHABItem(widgetJson.getJSONObject("item")));
            }
            if (widgetJson.has("linkedPage")) {
                this.setLinkedPage(new OpenHABLinkedPage(widgetJson.getJSONObject("linkedPage")));
            }
            if (widgetJson.has("mappings")) {
                JSONArray mappingsJsonArray = widgetJson.getJSONArray("mappings");
                for (int i=0; i<mappingsJsonArray.length(); i++) {
                    JSONObject mappingObject = mappingsJsonArray.getJSONObject(i);
                    OpenHABWidgetMapping mapping = new OpenHABWidgetMapping(mappingObject.getString("command"),
                            mappingObject.getString("label"));
                    mappings.add(mapping);
                }
            }
            if (widgetJson.has("type"))
                this.setType(widgetJson.getString("type"));
            if (widgetJson.has("widgetId"))
                this.setId(widgetJson.getString("widgetId"));
            if (widgetJson.has("label"))
                this.setLabel(widgetJson.getString("label"));
            if (widgetJson.has("icon"))
                this.setIcon(widgetJson.getString("icon"));
            if (widgetJson.has("url"))
                this.setUrl(widgetJson.getString("url"));
            if (widgetJson.has("minValue"))
                this.setMinValue((float)widgetJson.getDouble("minValue"));
            if (widgetJson.has("maxValue"))
                this.setMaxValue((float)widgetJson.getDouble("maxValue"));
            if (widgetJson.has("step"))
                this.setStep((float)widgetJson.getDouble("step"));
            if (widgetJson.has("refresh"))
                this.setRefresh(widgetJson.getInt("refresh"));
            if (widgetJson.has("period"))
                this.setPeriod(widgetJson.getString("period"));
            if (widgetJson.has("service"))
                this.setService(widgetJson.getString("service"));
            if (widgetJson.has("height"))
                this.setHeight(widgetJson.getInt("height"));
            if (widgetJson.has("iconcolor"))
                this.setIconColor(widgetJson.getString("iconcolor"));
            if (widgetJson.has("labelcolor"))
                this.setLabelColor(widgetJson.getString("labelcolor"));
            if (widgetJson.has("valuecolor"))
                this.setValueColor(widgetJson.getString("valuecolor"));
            if (widgetJson.has("encoding"))
                this.setEncoding(widgetJson.getString("encoding"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (widgetJson.has("widgets")) {
            try {
                JSONArray childWidgetJsonArray = widgetJson.getJSONArray("widgets");
                for (int i=0; i<childWidgetJsonArray.length(); i++) {
                    new OpenHABWidget(this, childWidgetJsonArray.getJSONObject(i));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        this.parent.addChildWidget(this);
    }
	
	public void addChildWidget(OpenHABWidget child) {
		if (child != null) {
			this.children.add(child);
		}
	}

	public boolean hasChildren() {
		if (this.children.size() > 0) {
			return true;
		} else {
			return false;
		}
	}
	
	public ArrayList<OpenHABWidget> getChildren() {
		return this.children;
	}
	
	public boolean hasItem() {
		if (this.getItem() != null) {
			return true;
		} else {
			return false;
		}
	}
	
	public boolean hasLinkedPage() {
		if (this.linkedPage != null) {
			return true;
		} else {
			return false;
		}
	}
	
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public OpenHABItem getItem() {
		return item;
	}

	public void setItem(OpenHABItem item) {
		this.item = item;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public OpenHABLinkedPage getLinkedPage() {
		return linkedPage;
	}

	public void setLinkedPage(OpenHABLinkedPage linkedPage) {
		this.linkedPage = linkedPage;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
	
	public boolean hasMappings() {
		if (mappings.size() > 0) {
			return true;
		}
		return false;
	}
	
	public OpenHABWidgetMapping getMapping(int index) {
		return mappings.get(index);
	}
	
	public ArrayList<OpenHABWidgetMapping> getMappings() {
		return mappings;
	}

	public float getMinValue() {
		return minValue;
	}

	public void setMinValue(float minValue) {
		this.minValue = minValue;
	}

	public float getMaxValue() {
		return maxValue;
	}

	public void setMaxValue(float maxValue) {
		this.maxValue = maxValue;
	}

	public float getStep() {
		return step;
	}

	public void setStep(float step) {
		this.step = step;
	}

	public int getRefresh() {
		return refresh;
	}

	public void setRefresh(int refresh) {
		this.refresh = refresh;
	}

	public String getPeriod() {
		if (period.length() == 0) {
			return "D";
		}
		return period;
	}

	public void setPeriod(String period) {
		this.period = period;
	}

    public String getService() { return service; }

    public void setService(String service) { this.service = service; }

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

    public boolean childrenHasLinkedPages() {
        if (this.hasChildren()) {
            for (OpenHABWidget w : this.getChildren()) {
                if (w.hasLinkedPage())
                    return true;
            }
        }
        return false;
    }

    public boolean childrenHasNonlinkedPages() {
        if (this.hasChildren()) {
            for (OpenHABWidget w : this.getChildren()) {
                if (!w.hasLinkedPage())
                    return true;
            }
        }
        return false;
    }

    public Integer getLabelColor() {
        return labelcolor;
    }

    public void setLabelColor(String color) {
        try {
            this.labelcolor = new Integer(Color.parseColor(fixColorName(color)));
        } catch(IllegalArgumentException e) {
            Log.e("OpenHABWidget", "Color was " + color);
            Log.e("OpenHABWidget", e.getMessage());
            this.labelcolor = null;
        }
    }

    public Integer getValueColor() {
        return valuecolor;
    }

    public void setValueColor(String color) {
        try {
            this.valuecolor = new Integer(Color.parseColor(fixColorName(color)));
        } catch(IllegalArgumentException e) {
            Log.e("OpenHABWidget", "Color was " + color);
            Log.e("OpenHABWidget", e.getMessage());
            this.valuecolor = null;
        }
    }

    public Integer getIconColor() {
        return iconcolor;
    }

    public void setIconColor(String color) {
        try {
            this.iconcolor = new Integer(Color.parseColor(fixColorName(color)));
        } catch(IllegalArgumentException e) {
            Log.e("OpenHABWidget", "Color was " + color);
            Log.e("OpenHABWidget", e.getMessage());
            this.iconcolor = null;
        }
    }

    private String fixColorName(String colorName) {
        if (colorName.equals("orange"))
            return "#FFA500";
        return colorName;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}
