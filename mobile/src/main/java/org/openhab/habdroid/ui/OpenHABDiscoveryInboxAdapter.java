/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABDiscoveryInbox;
import org.openhab.habdroid.model.thing.ThingType;

import java.util.ArrayList;

public class OpenHABDiscoveryInboxAdapter extends ArrayAdapter<OpenHABDiscoveryInbox> {
    private int mResource;
    private String mOpenHABUsername;
    private String mOpenHABPassword;
    private String mOpenHABBaseUrl;
    private ArrayList<ThingType> thingTypes;

    public OpenHABDiscoveryInboxAdapter(Context context, int resource, ArrayList<OpenHABDiscoveryInbox> objects) {
        super(context, resource, objects);
        mResource = resource;
        thingTypes = new ArrayList<ThingType>();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        OpenHABDiscoveryInbox inbox = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mResource, parent, false);
        }
        TextView labelView = (TextView)convertView.findViewById(R.id.deviceLabel);
        ThingType thingType = thingTypeByUid(inbox.getThingUID());
        if (thingType != null) {
            if (inbox.getLabel() != null && !inbox.getLabel().isEmpty()) {
                labelView.setText(thingType.getLabel() + ": " + inbox.getLabel());
            } else {
                labelView.setText(thingType.getLabel() + ": " + getContext().getString(R.string.app_discoveryinbox_deviceunnamed));
            }
        } else {
            if (inbox.getLabel() != null && !inbox.getLabel().isEmpty()) {
                labelView.setText(inbox.getLabel());
            } else {
                labelView.setText(R.string.app_discoveryinbox_deviceunnamed);
            }
        }
        return convertView;
    }

    public String getOpenHABUsername() {
        return mOpenHABUsername;
    }

    public void setOpenHABUsername(String openHABUsername) {
        this.mOpenHABUsername = openHABUsername;
    }

    public String getOpenHABPassword() {
        return mOpenHABPassword;
    }

    public void setOpenHABPassword(String openHABPassword) {
        this.mOpenHABPassword = openHABPassword;
    }

    public ArrayList<ThingType> getThingTypes() {
        return thingTypes;
    }

    public void setThingTypes(ArrayList<ThingType> thingTypes) {
        this.thingTypes = thingTypes;
    }

    private ThingType thingTypeByUid(String uid) {
        for (ThingType type : thingTypes) {
            if (uid.startsWith(type.getUID())) {
                return type;
            }
        }
        return null;
    }
}
