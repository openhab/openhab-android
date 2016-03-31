/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.model.thing.ThingType;

import java.util.ArrayList;

/**
 * Created by belovictor on 22/05/15.
 * This class represents an openHAB2 binding
 */

public class OpenHABBinding {
    private String id;
    private String name;
    private String description;
    private String author;
    private ArrayList<ThingType> thingTypes;

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

    public ArrayList<ThingType> getThingTypes() {
        return thingTypes;
    }

    public void setThingTypes(ArrayList<ThingType> thingTypes) {
        this.thingTypes = thingTypes;
    }
}
