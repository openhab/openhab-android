package org.openhab.habdroid.model;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class OpenHABBeacon {
    public enum Type{
        EddystoneUrl,
        EddystoneUid,
        iBeacon,
        NotABeacon
    }

    public abstract String name();
    public abstract String address();
    public abstract String url();
    public abstract byte txPower();

    public static Builder builder(){
        return new AutoValue_OpenHABBeacon.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder address(String address);
        public abstract Builder url(String url);
        public abstract Builder txPower(byte txPower);

        public abstract OpenHABBeacon build();
    }
}

