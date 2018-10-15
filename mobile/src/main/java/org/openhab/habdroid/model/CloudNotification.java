/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

@AutoValue
public abstract class CloudNotification {
    public abstract String id();
    public abstract String message();
    public abstract long createdTimestamp();
    @Nullable
    public abstract String icon();
    @Nullable
    public abstract String severity();

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder id(String id);
        abstract Builder message(String message);
        abstract Builder createdTimestamp(long created);
        abstract Builder icon(@Nullable String icon);
        abstract Builder severity(@Nullable String severity);

        abstract CloudNotification build();
    }

    public static CloudNotification fromJson(JSONObject jsonObject) throws JSONException {
        long created = 0;
        if (jsonObject.has("created")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                created = format.parse(jsonObject.getString("created")).getTime();
            } catch (ParseException e) {
                // keep created at 0
            }
        }

        return new AutoValue_CloudNotification.Builder()
                .id(jsonObject.getString("_id"))
                .icon(jsonObject.optString("icon", null))
                .severity(jsonObject.optString("severity", null))
                .message(jsonObject.getString("message"))
                .createdTimestamp(created)
                .build();
    }
}

