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
import org.openhab.habdroid.model.OpenHABBinding;

import java.util.ArrayList;

/**
 * Created by belovictor on 23/05/15.
 */
public class OpenHABBindingAdapter extends ArrayAdapter<OpenHABBinding> {
    private int mResource;
    private String mOpenHABUsername;
    private String mOpenHABPassword;
    private String mOpenHABBaseUrl;

    public OpenHABBindingAdapter(Context context, int resource, ArrayList<OpenHABBinding> objects) {
        super(context, resource, objects);
        mResource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        OpenHABBinding binding = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mResource, parent, false);
        }
        TextView nameView = (TextView)convertView.findViewById(R.id.bindingName);
        TextView descriptionView = (TextView)convertView.findViewById(R.id.bindingDescription);
        TextView authorView = (TextView)convertView.findViewById(R.id.bindingAuthor);
        nameView.setText(binding.getName());
        descriptionView.setText(binding.getDescription());
        authorView.setText(binding.getAuthor());
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


}
