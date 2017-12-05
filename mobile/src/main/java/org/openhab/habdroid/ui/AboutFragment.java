/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AboutFragment extends Fragment {

    private static final String TAG = AboutFragment.class.getSimpleName();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.openhababout, container, false);

        TextView about = (TextView) view.findViewById(R.id.license_link);
        about.setText(Html.fromHtml(getString(R.string.about_license)));
        about.setMovementMethod(LinkMovementMethod.getInstance());
        TextView appVersion = (TextView) view.findViewById(R.id.app_version);
        appVersion.setText(getString(R.string.about_version_string,
                BuildConfig.VERSION_NAME,
                DateFormat.getDateTimeInstance().format(BuildConfig.buildTime)
        ));

        String year = new SimpleDateFormat("yyyy", Locale.US)
                .format(Calendar.getInstance().getTime());
        TextView copyright = (TextView)view.findViewById(R.id.copyright);
        copyright.setText(String.format(getString(R.string.about_copyright),year));
        copyright.setMovementMethod(LinkMovementMethod.getInstance());

        TextView links = (TextView)view.findViewById(R.id.links_list);
        links.setText(Html.fromHtml(getString(R.string.about_links_list)));
        links.setMovementMethod(LinkMovementMethod.getInstance());

        return view;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
    }
}
