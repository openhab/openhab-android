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

/**
 * Created by belovictor on 23/05/15.
 */
public class OpenHABDiscoveryInbox {
    private String flag;
    private String label;
    private String thingUID;

    public OpenHABDiscoveryInbox(JSONObject jsonObject) {
        try {
            if (jsonObject.has("flag"))
                this.setFlag(jsonObject.getString("flag"));
            if (jsonObject.has("label"))
                this.setLabel(jsonObject.getString("label"));
            if (jsonObject.has("thingUID"))
                this.setThingUID(jsonObject.getString("thingUID"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getThingUID() {
        return thingUID;
    }

    public void setThingUID(String thingUID) {
        this.thingUID = thingUID;
    }
}
