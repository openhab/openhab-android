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

import com.google.auto.value.AutoValue;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

@AutoValue
public abstract class OpenHABNotification {
    public abstract String message();
    public abstract long createdTimestamp();
    public abstract String icon();
    public abstract String severity();

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder message(String message);
        abstract Builder createdTimestamp(long created);
        abstract Builder icon(String icon);
        abstract Builder severity(String severity);

        abstract OpenHABNotification build();
    }

    public static OpenHABNotification fromJson(JSONObject jsonObject) throws JSONException {
        long created = 0;
        if (jsonObject.has("created")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                created = format.parse(jsonObject.getString("created")).getTime();
            } catch (ParseException e) {
                // keep created at null
            }
        }

        return new AutoValue_OpenHABNotification.Builder()
                .icon(jsonObject.optString("icon", null))
                .severity(jsonObject.optString("severity", null))
                .message(jsonObject.optString("message", null))
                .createdTimestamp(created)
                .build();
    }
}

