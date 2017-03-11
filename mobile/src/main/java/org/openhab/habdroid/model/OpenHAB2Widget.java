package org.openhab.habdroid.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class OpenHAB2Widget extends OpenHABWidget {

    private String iconFormat;

    public OpenHAB2Widget() {
        iconFormat="null";
    }

    @Override
    public String getIconPath() {
        OpenHABItem widgetItem = getItem();
        String itemState = (widgetItem != null) ? widgetItem.getState() : null;
        return String.format("icon/%s?state=%s&format=%s", getIcon(), itemState, iconFormat);
    }

    private OpenHAB2Widget(OpenHABWidget parent, JSONObject widgetJson, String iconFormat) {
        this.iconFormat = iconFormat;
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
                    createOpenHABWidgetFromJson(this, childWidgetJsonArray.getJSONObject(i), iconFormat);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        this.parent.addChildWidget(this);
    }

    public static OpenHABWidget createOpenHABWidgetFromJson(OpenHABWidget parent, JSONObject widgetJson, String iconFormat) {
        return new OpenHAB2Widget(parent, widgetJson, iconFormat);
    }
}
