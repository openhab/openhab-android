package org.openhab.habdroid.model;

import android.os.Parcelable;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class OpenHABBeacon {
    public enum Type{
        eddystone,
        iBeacon
    }

    public abstract String name();
    public abstract String address();

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder address(String address);

        public abstract OpenHABBeacon build();
    }
}

