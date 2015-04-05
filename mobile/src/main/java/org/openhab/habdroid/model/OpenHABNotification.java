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

public class OpenHABNotification {
    private String mMessage;
    private Date mCreated;
    public OpenHABNotification(JSONObject jsonObject) {
        try {
            if (jsonObject.has("message"))
                this.setMessage(jsonObject.getString("message"));
            if (jsonObject.has("created")) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
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
}

