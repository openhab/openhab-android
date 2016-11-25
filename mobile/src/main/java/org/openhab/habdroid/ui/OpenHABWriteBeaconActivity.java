/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacons;
import org.openhab.habdroid.util.BeaconHandler;
import org.openhab.habdroid.util.Util;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class OpenHABWriteBeaconActivity extends Activity {

	// Logging TAG
	private static final String TAG = OpenHABWriteBeaconActivity.class.getSimpleName();
	private String sitemap = "";
	private String group = "";
	private String message = "";
	private OpenHABBeacons beacon;

    private TextView writeBeaconMessage;
    private Button btnSave;

	private BeaconHandler beaconHandler;

    @Override
	public void onStart() {
		super.onStart();
        GoogleAnalytics.getInstance(this).reportActivityStart(this);
	}
	
	@Override
	public void onStop() {
		super.onStop();
        GoogleAnalytics.getInstance(this).reportActivityStop(this);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Util.setActivityTheme(this);
		super.onCreate(savedInstanceState);
		beaconHandler = BeaconHandler.getInstance(this);
		setContentView(R.layout.openhabwritebeacon);
		writeBeaconMessage = (TextView)findViewById(R.id.write_message_beacon);
        btnSave = (Button)findViewById(R.id.save_beacon);
    	checkBluetooth();
		intentsExtras();
		loadNearest();
        btnSave.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(beacon != null) saveBeacon();
                else refreshBeacon();
            }
        });
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public void onResume() {
		Log.d(TAG, "onResume()");
		super.onResume();
	}

	@Override
	public void onPause() {
		Log.d(TAG, "onPause()");
		super.onPause();
	}
	
	@Override
	public void finish() {
		super.finish();
		Util.overridePendingTransition(this, true);
	}

    private void saveBeacon(){
        String name = ((EditText)findViewById(R.id.write_beacon_name_edit)).getText().toString();
        if(name != null && !"".equals(name)) {
			writeBeaconMessage.setText(R.string.info_write_beacon_progress);
            beacon.setSitemap(sitemap);
            beacon.setGroup(group);
            beacon.setName(name);
			beaconHandler.addNewBeacon(beacon);
            if(beaconHandler.writeBeacons(getApplicationContext())){//FileHandler.writeBeacons(beacon, getApplicationContext())){
				writeBeaconMessage.setText(R.string.info_write_beacon_finished);
				autoCloseActivity();
			}
			else{
				hide();
				writeBeaconMessage.setText(R.string.info_write_beacon_failed);
			}
        }
        else{
            Toast.makeText(getApplicationContext(), "You have to enter a Beacon name!", Toast.LENGTH_LONG);
        }
    }

    private void refreshBeacon(){
		if(OpenHABMainActivity.bluetoothActivated){
			loadNearest();
			if (beacon != null) unhide();
		}
		else{
			writeBeaconMessage.setText(R.string.info_write_beacon_failed + ", please try again");
			autoCloseActivity();
		}
    }

    private void hide(){
        findViewById(R.id.write_beacon_name_edit).setVisibility(View.INVISIBLE);
        findViewById(R.id.write_beacon_name).setVisibility(View.INVISIBLE);
        findViewById(R.id.write_beacon_address).setVisibility(View.INVISIBLE);
        findViewById(R.id.write_beacon_message).setVisibility(View.INVISIBLE);
        writeBeaconMessage.setText(getString(R.string.info_write_nobeacon_near));
        btnSave.setText("refresh");
    }

    private void unhide(){
        findViewById(R.id.write_beacon_name_edit).setVisibility(View.VISIBLE);
        findViewById(R.id.write_beacon_name).setVisibility(View.VISIBLE);
        findViewById(R.id.write_beacon_address).setVisibility(View.VISIBLE);
        findViewById(R.id.write_beacon_message).setVisibility(View.VISIBLE);
        writeBeaconMessage.setText(message);
        btnSave.setText("save");
    }
	
	private void autoCloseActivity() {
		Timer autoCloseTimer = new Timer();
		autoCloseTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				OpenHABWriteBeaconActivity.this.runOnUiThread(new Runnable() {
					public void run() {
						OpenHABWriteBeaconActivity.this.finish();
					}
				});
				Log.d(TAG, "Autoclosing tag write activity");
			}
			
		}, 2000);
	}

	private void checkBluetooth(){
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			hide();
			writeBeaconMessage.setText(R.string.info_write_beacon_unsupported);
			findViewById(R.id.save_beacon).setVisibility(View.INVISIBLE);
		} else if (!OpenHABMainActivity.bluetoothActivated) {
			hide();
			writeBeaconMessage.setText(R.string.info_write_beacon_disabled);
			findViewById(R.id.save_beacon).setVisibility(View.INVISIBLE);
		}
	}

	private void intentsExtras(){
		if (getIntent().hasExtra("sitemapPage")) {
			String page = getIntent().getExtras().getString("sitemapPage");
			Log.d(TAG, "Got sitemapPage = " + page);
			sitemap = page.split("/")[2];
			group = page.split("/")[3];
			writeBeaconMessage.setText(writeBeaconMessage.getText().toString().replace("~sitemap~", sitemap));
			writeBeaconMessage.setText(writeBeaconMessage.getText().toString().replace("~group~", group));
			message = writeBeaconMessage.getText().toString();
		}
	}

	private void loadNearest(){
		beacon = beaconHandler.getNearRooms().get(0);
		if(beacon != null) {
			Log.d(TAG, "Got beacon = " + beacon.getName() + "(" + beacon.getAddress() + ")");
			((EditText) findViewById(R.id.write_beacon_name_edit)).setText(beacon.getName());
			((TextView) findViewById(R.id.write_beacon_address)).append(beacon.getAddress());
			((TextView) findViewById(R.id.write_beacon_message)).append(beacon.getBeaconMessage());
		}
		else{
			Log.d(TAG, "Got no beacon");
			hide();
		}
	}
}
