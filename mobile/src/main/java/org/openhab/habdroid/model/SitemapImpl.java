package org.openhab.habdroid.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public class SitemapImpl implements Sitemap {
    private static final String TAG = SitemapImpl.class.getSimpleName();
    public static final Parcelable.Creator<Sitemap> CREATOR = new Parcelable.Creator<Sitemap>() {
        public Sitemap createFromParcel(Parcel in) {
            ObjectMapper mapper = new ObjectMapper();
            String jsonData = in.readString();

            try {
                return mapper.readValue(jsonData, SitemapImpl.class);
            } catch (IOException e) {
                Log.e(TAG, "Could not deserialize JSON to SitemapImpl. JSON: " + jsonData, e);
            }

            return null;
        }

        public Sitemap[] newArray(int size) {
            return new SitemapImpl[size];
        }
    };

    @Getter
    @JsonProperty
    private String name;
    @JsonProperty
    private String label;
    @Getter
    @JsonProperty
    private String link;
    @Getter
    @JsonProperty
    private String icon;
    @JsonProperty
    private Homepage homepage;
    @Getter
    @JsonProperty
    private boolean leaf;

    @Override
    @JsonIgnore
    public String getHomepageLink() {
        return homepage.getLink();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            dest.writeString(mapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Could not serialize sitemap to JSON.", e);
        }
    }

    @Override
    @JsonIgnore
    public String getIconPath() {
        return String.format("icon/%s", getIcon());
    }

    @NonNull
    @Override
    public String getLabel() {
        return label != null ? label : getName();
    }
}
