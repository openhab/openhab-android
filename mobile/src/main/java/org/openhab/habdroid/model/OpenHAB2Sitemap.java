package org.openhab.habdroid.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public class OpenHAB2Sitemap extends OpenHABSitemap {
    public OpenHAB2Sitemap(JSONObject jsonObject) {
        try {
            if (jsonObject.has("name"))
                this.setName(jsonObject.getString("name"));
            if (jsonObject.has("label"))
                this.setLabel(jsonObject.getString("label"));
            if (jsonObject.has("link"))
                this.setLink(jsonObject.getString("link"));
            if (jsonObject.has("icon"))
                this.setIcon(jsonObject.getString("icon"));
            if (jsonObject.has("homepage")) {
                JSONObject homepageObject = jsonObject.getJSONObject("homepage");
                this.setHomepageLink(homepageObject.getString("link"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static final Parcelable.Creator<OpenHABSitemap> CREATOR = new Parcelable.Creator<OpenHABSitemap>() {
        public OpenHABSitemap createFromParcel(Parcel in) {
            return new OpenHAB2Sitemap(in);
        }

        public OpenHABSitemap[] newArray(int size) {
            return new OpenHAB2Sitemap[size];
        }
    };

    public OpenHAB2Sitemap(Parcel in) {
        super(in);
    }

    @Override
    public String getIconPath() {
        return String.format("icon/%s", getIcon());
    }
}
