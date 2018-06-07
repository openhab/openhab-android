package org.openhab.habdroid.model;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;

@AutoValue
public abstract class OpenHABBeacon {
    public enum Type{
        EddystoneUrl,
        EddystoneUid,
        iBeacon,
        NotABeacon
    }

    //Common BLE beacon features. Must not be null for any type of beacon.
    public abstract String address();
    public abstract byte txPower();
    public abstract int rssi();
    public abstract double distance();
    public abstract Type type();
    @Nullable
    public abstract String name();

    //Eddystone URL specified value
    @Nullable
    public abstract String url();

    //Eddystone UID specified values
    @Nullable
    public abstract String nameSpace();
    @Nullable
    public abstract String instance();

    //iBeacon specified values
    @Nullable
    public abstract String uuid();
    @Nullable
    public abstract String major();
    @Nullable
    public abstract String minor();
    //Add more selective value here

    public static Builder builder(Type beaconType){
        return new AutoValue_OpenHABBeacon.Builder()
                .setType(beaconType);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setAddress(String address);
        public abstract Builder setTxPower(byte txPower);
        public abstract Builder setRssi(int rssi);

        public abstract Builder setName(@Nullable String name);
        public abstract Builder setUrl(@Nullable String url);
        public abstract Builder setNameSpace(@Nullable String nameSpace);
        public abstract Builder setInstance(@Nullable String instance);
        public abstract Builder setUuid(@Nullable String uuid);
        public abstract Builder setMajor(@Nullable String major);
        public abstract Builder setMinor(@Nullable String minor);
        //Nullable(selective) attribute go through here

        abstract Builder setType(Type type);
        abstract Builder setDistance(double distance);

        abstract int rssi();
        abstract byte txPower();

        abstract OpenHABBeacon autoBuild();

        public OpenHABBeacon build(){
            if ((rssi() & txPower()) != 0){
                setDistance(measureDistance(rssi(), txPower()));
            }
            return autoBuild();
        }

        private double measureDistance(int rssi, byte txPower){
            return Math.pow(10, ((double)txPower - rssi) / (10 * 2.5));
        }
    }
}

