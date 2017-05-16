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
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacons;

import java.util.ArrayList;

/**
 * Created by belovictor on 23/05/15.
 */
public class OpenHABNearRoomAdapter extends ArrayAdapter<OpenHABBeacons> {

    private static final String TAG = OpenHABNearRoomAdapter.class.getSimpleName();

    private int mResource;
    private String mOpenHABUsername;
    private String mOpenHABPassword;
    private String mOpenHABBaseUrl;

    public OpenHABNearRoomAdapter(Context context, int resource, ArrayList<OpenHABBeacons> objects) {
        super(context, resource, objects);
        mResource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        OpenHABBeacons beacon = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mResource, parent, false);
        }

        RelativeLayout rl = (RelativeLayout)convertView.findViewById(R.id.relative_beaconlist);

        String nameTXT = beacon.getNameForView();
        String idTXT = beacon.getBeaconMessage() + "\n";
        String roomTXT = beacon.getRoomForView();
        String infoTXT = beacon.getInfoForView();

        TextView nameView = (TextView)convertView.findViewById(R.id.beaconName);
        nameView.setText(nameTXT);

        TextView infoView = (TextView)convertView.findViewById(R.id.beaconInfo);
        if(roomTXT == null){
            infoView.setText(idTXT + "To add this Beacon to openHAB click here\n" + infoTXT);
        }
        else{
            infoView.setText(idTXT+roomTXT+infoTXT);
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

    public String getmOpenHABBaseUrl() {
        return mOpenHABBaseUrl;
    }

    public void setmOpenHABBaseUrl(String mOpenHABBaseUrl) {
        this.mOpenHABBaseUrl = mOpenHABBaseUrl;
    }
}
