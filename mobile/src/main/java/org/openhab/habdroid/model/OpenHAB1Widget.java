package org.openhab.habdroid.model;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;

public class OpenHAB1Widget extends OpenHABWidget {
    public OpenHAB1Widget() {
    }

    @Override
    public String getIconPath() {
        return String.format("images/%s.png", getIcon());
    }

    private OpenHAB1Widget(OpenHABWidget parent, Node startNode) {
        this.parent = parent;
        this.children = new ArrayList<OpenHABWidget>();
        this.mappings = new ArrayList<OpenHABWidgetMapping>();
        if (startNode.hasChildNodes()) {
            NodeList childNodes = startNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i ++) {
                Node childNode = childNodes.item(i);
                String childNodeName = childNode.getNodeName();
                String childNodeTextContent = childNode.getTextContent();
                if (childNodeName.equals("item")) {
                    this.setItem(new OpenHABItem(childNode));
                } else if (childNodeName.equals("linkedPage")) {
                    this.setLinkedPage(new OpenHABLinkedPage(childNode));
                } else if (childNodeName.equals("widget")) {
                    createOpenHABWidgetFromNode(this, childNode);
                } else {
                    if (childNodeName.equals("type")) {
                        this.setType(childNodeTextContent);
                    } else if (childNodeName.equals("widgetId")) {
                        this.setId(childNodeTextContent);
                    } else if (childNodeName.equals("label")) {
                        this.setLabel(childNodeTextContent);
                    } else if (childNodeName.equals("icon")) {
                        this.setIcon(childNodeTextContent);
                    } else if (childNodeName.equals("url")) {
                        this.setUrl(childNodeTextContent);
                    } else if (childNodeName.equals("minValue")) {
                        setMinValue(Float.valueOf(childNodeTextContent).floatValue());
                    } else if (childNodeName.equals("maxValue")) {
                        setMaxValue(Float.valueOf(childNodeTextContent).floatValue());
                    } else if (childNodeName.equals("step")) {
                        setStep(Float.valueOf(childNodeTextContent).floatValue());
                    } else if (childNodeName.equals("refresh")) {
                        setRefresh(Integer.valueOf(childNodeTextContent).intValue());
                    } else if (childNodeName.equals("period")) {
                        setPeriod(childNodeTextContent);
                    } else if (childNodeName.equals("service")) {
                        setService(childNodeTextContent);
                    } else if (childNodeName.equals("height")) {
                        setHeight(Integer.valueOf(childNodeTextContent));
                    } else if (childNodeName.equals("mapping")) {
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
                    } else if (childNodeName.equals("iconcolor")) {
                        setIconColor(childNodeTextContent);
                    } else if (childNodeName.equals("labelcolor")) {
                        setLabelColor(childNodeTextContent);
                    } else if (childNodeName.equals("valuecolor")) {
                        setValueColor(childNodeTextContent);
                    } else if (childNodeName.equals("encoding")) {
                        setEncoding(childNodeTextContent);
                    }
                 }
            }
        }
        this.parent.addChildWidget(this);
    }
    public static OpenHABWidget createOpenHABWidgetFromNode(OpenHABWidget parent, Node startNode) {
        return new OpenHAB1Widget(parent, startNode);
    }


}
