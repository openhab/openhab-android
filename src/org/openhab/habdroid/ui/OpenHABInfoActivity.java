/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.ui;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Util;

/**
 * Created by belovictor on 8/20/13.
 */

public class OpenHABInfoActivity extends Activity {

    private static final String TAG = "OpenHABInfoActivity";
    private TextView mOpenHABVersionText;
    private TextView mOpenHABUUIDText;
    private TextView mOpenHABSecretText;
    private String mOpenHABBaseUrl;
    private String mUsername;
    private String mPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.openhabinfo);
        mOpenHABVersionText = (TextView)findViewById(R.id.openhab_version);
        mOpenHABUUIDText = (TextView)findViewById(R.id.openhab_uuid);
        mOpenHABSecretText = (TextView)findViewById(R.id.openhab_secret);
        if (getIntent().hasExtra("openHABBaseUrl")) {
            mOpenHABBaseUrl = getIntent().getStringExtra("openHABBaseUrl");
            mUsername = getIntent().getStringExtra("username");
            mPassword = getIntent().getStringExtra("password");
        } else {
            Log.e(TAG, "No openHABBaseURl parameter passed, can't fetch openHAB info from nowhere");
            finish();
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        mOpenHABVersionText.setText("1.3.0-SNAPSHOT");
        mOpenHABUUIDText.setText("Some UUID here");
        mOpenHABSecretText.setText("Some Secret here");
    }

    @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }
}
