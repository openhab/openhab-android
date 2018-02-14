/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import android.os.Parcelable;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class OpenHABWidgetMapping implements Parcelable {
    public abstract String command();
    public abstract String label();

    public static Builder newBuilder() {
        return new AutoValue_OpenHABWidgetMapping.Builder();
    }

    @AutoValue.Builder
    public static abstract class Builder {
        public abstract Builder command(String command);
        public abstract Builder label(String label);

        public abstract OpenHABWidgetMapping build();
    }
}
