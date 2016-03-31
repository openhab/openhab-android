/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * Created by belovictor on 03/04/15.
 * This class represents a my.openHAB notification
 */

package org.openhab.habdroid.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class OpenHABNotification {
    private String mMessage;
    private Date mCreated;
    private String mIcon;
    private String mSeverity;
    public OpenHABNotification(JSONObject jsonObject) {
        try {
            if (jsonObject.has("icon"))
                this.setIcon(jsonObject.getString("icon"));
            if (jsonObject.has("severity"))
                this.setSeverity(jsonObject.getString("severity"));
            if (jsonObject.has("message"))
                this.setMessage(jsonObject.getString("message"));
            if (jsonObject.has("created")) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                this.setCreated(format.parse(jsonObject.getString("created")));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String message) {
        this.mMessage = message;
    }

    public Date getCreated() {
        return mCreated;
    }

    public void setCreated(Date created) {
        this.mCreated = created;
    }

    public String getIcon() {
        return mIcon;
    }

    public void setIcon(String icon) {
        this.mIcon = icon;
    }

    public String getSeverity() {
        return mSeverity;
    }

    public void setSeverity(String severity) {
        this.mSeverity = severity;
    }
}

