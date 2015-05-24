package org.openhab.habdroid.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by belovictor on 22/05/15.
 * This class represents an openHAB2 binding
 */

public class OpenHABBinding {
    private String id;
    private String name;
    private String description;
    private String author;

    public OpenHABBinding(JSONObject jsonObject) {
        try {
            if (jsonObject.has("id"))
                this.setId(jsonObject.getString("id"));
            if (jsonObject.has("name"))
                this.setName(jsonObject.getString("name"));
            if (jsonObject.has("description"))
                this.setDescription(jsonObject.getString("description"));
            if (jsonObject.has("author"))
                this.setAuthor(jsonObject.getString("author"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
