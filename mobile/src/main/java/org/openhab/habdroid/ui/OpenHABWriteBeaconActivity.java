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
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.loopj.android.http.SyncHttpClient;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacons;
import org.openhab.habdroid.util.BeaconHandler;
import org.openhab.habdroid.util.MySyncHttpClient;
import org.openhab.habdroid.util.Util;
import org.openhab.habdroid.util.writeBeacon.WriteBeaconHandleGroup;
import org.openhab.habdroid.util.writeBeacon.WriteBeaconHandleSitemap;

import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;

public class OpenHABWriteBeaconActivity extends Activity implements Observer{

	// Logging TAG
	private static final String TAG = OpenHABWriteBeaconActivity.class.getSimpleName();

	private String baseURL = "";

	private String intentSitemap;
	private WriteBeaconHandleSitemap sitemap;

	private String intentGroup;
	private WriteBeaconHandleGroup group;

	private TextView writeBeaconMessage;

	private OpenHABBeacons intentBeacon;
	private TextView beaconAddress;
	private TextView beaconAddressShow;
	private TextView beaconName;
	private EditText beaconNameEdit;

	private Button btnSave;
	private Button btnCancel;

	private BeaconHandler beaconHandler;

	private SyncHttpClient mSyncHttpClient;

	private boolean hide;

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

		mSyncHttpClient = new MySyncHttpClient(this);

		beaconHandler = BeaconHandler.getInstance(this);
		setContentView(R.layout.openhabwritebeacon);
		writeBeaconMessage = (TextView)findViewById(R.id.write_message_beacon_view);
		configureButton();
		configureBeaconView();
		configureCheckbox();

		sitemap = new WriteBeaconHandleSitemap(	this,
												((Spinner) findViewById(R.id.write_beacon_sitemap_choose)),
												((TextView) findViewById(R.id.write_beacon_sitemap)),
												mSyncHttpClient);
		sitemap.addObserver(this);
		group = new WriteBeaconHandleGroup(	this,
											((Spinner) findViewById(R.id.write_beacon_group_choose)),
											((TextView) findViewById(R.id.write_beacon_group)),
											mSyncHttpClient);
		group.addObserver(this);

		intentsExtras();
		beaconNameEdit.setText(intentBeacon.getName());
		beaconAddressShow.setText(intentBeacon.getAddress());
		new Thread(new Runnable() {
			@Override
			public void run() {
				sitemap.loadSitemapList(baseURL);
			}
		}).start();
	}

	private void configureButton(){
		btnSave = (Button)findViewById(R.id.save_beacon);
		btnSave.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				saveBeacon();
			}
		});
		btnCancel = (Button)findViewById(R.id.cancel_beacon);
		btnCancel.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				closeActivity();
			}
		});
	}

	private void configureBeaconView(){
		beaconAddress = (TextView)findViewById(R.id.write_beacon_address);
		beaconAddressShow = (TextView)findViewById(R.id.write_beacon_address_show);
		beaconName = (TextView)findViewById(R.id.write_beacon_name);
		beaconNameEdit = (EditText)findViewById(R.id.write_beacon_name_edit);
	}

	private void configureCheckbox(){
		findViewById(R.id.show_beacon_infos).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//actually there is only the Beacon message as further information
				//in future there could be more further informations
				findViewById(R.id.beacon_message).setVisibility(View.VISIBLE);
				findViewById(R.id.beacon_message_show).setVisibility(View.VISIBLE);
				((TextView)findViewById(R.id.beacon_message_show)).setText(intentBeacon.getBeaconMessage());
				((LinearLayout)v.getParent()).removeView(v);
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
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
		Log.d(TAG, "saveBeacon: ");
		OpenHABBeacons saveBeacon = new OpenHABBeacons();
		String name = beaconNameEdit.getText().toString();
        if(name != null && !"".equals(name)) {
			writeBeaconMessage.setText(R.string.info_write_beacon_progress);
            saveBeacon.setSitemap(sitemap.getChosenSitemap());
			saveBeacon.setGroup(group.getChosenGroup());
			saveBeacon.setName(name);
			saveBeacon.setAddress(intentBeacon.getAddress());
			saveBeacon.setBeaconMessage(intentBeacon.getBeaconMessage());
			if(saveBeacon.isCorrect() && beaconHandler.addBeacon(saveBeacon, getApplicationContext())){
				writeBeaconMessage.setText(R.string.info_write_beacon_finished);
				Log.d(TAG, "saveBeacon: successful");
				autoCloseActivity(true);
			}
			else{
				hide();
				Log.d(TAG, "saveBeacon: failure");
				writeBeaconMessage.setText(R.string.info_write_beacon_failed);
			}
        }
        else{
			Log.d(TAG, "saveBeacon: no Name");
            Toast.makeText(getApplicationContext(), "You have to enter a Beacon name!", Toast.LENGTH_LONG);
        }
    }

    private void hide(){
		hide = true;
		sitemap.hide();
		group.hide();
		findViewById(R.id.write_message_beacon_beacon).setVisibility(View.INVISIBLE);
		beaconAddress.setVisibility(View.INVISIBLE);
		beaconAddressShow.setVisibility(View.INVISIBLE);
		beaconName.setVisibility(View.INVISIBLE);
		beaconNameEdit.setVisibility(View.INVISIBLE);
		findViewById(R.id.show_beacon_infos).setVisibility(View.INVISIBLE);
		writeBeaconMessage.setText(getString(R.string.info_write_nobeacon_near));
		((LinearLayout)btnSave.getParent()).removeView(btnSave);
    }
	
	private void autoCloseActivity(final boolean save) {
		Timer autoCloseTimer = new Timer();
		autoCloseTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				OpenHABWriteBeaconActivity.this.runOnUiThread(new Runnable() {
					public void run() {
						OpenHABWriteBeaconActivity.this.setResult((save)?1:-1);
						OpenHABWriteBeaconActivity.this.finish();
					}
				});
				Log.d(TAG, "Autoclosing tag write activity");
			}
			
		}, 2000);
	}

	private boolean checkBluetooth(){
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			hide();
			writeBeaconMessage.setText(R.string.info_write_beacon_unsupported);
			return false;
		} else if (!OpenHABMainActivity.bluetoothActivated) {
			hide();
			writeBeaconMessage.setText(R.string.info_write_beacon_bt_disabled);
			return false;
		}
		return true;
	}

	private boolean checkLocator(){
		if(!OpenHABMainActivity.isLocate()){
			hide();
			writeBeaconMessage.setText(R.string.info_write_beacon_locator_disabled);
			return false;
		}
		return true;
	}

	private void intentsExtras(){
		if(getIntent().hasExtra("openHABBaseURL")){
			baseURL = getIntent().getStringExtra("openHABBaseURL");
		}
		if (getIntent().hasExtra("setBeacon")) {
			intentBeacon = getIntent().getParcelableExtra("setBeacon");
			if(intentBeacon.getSitemap() != null)
				intentSitemap = intentBeacon.getSitemap();
			if(intentBeacon.getGroup() != null)
				intentGroup = intentBeacon.getGroup();
		}
		else {
			checkBluetooth();
			checkLocator();
			return;
		}
		if (getIntent().hasExtra("sitemapPage")) {
			String[] pageSplit = getIntent().getStringExtra("sitemapPage").split("/");
			Log.d(TAG, "Got sitemapPage = " + getIntent().getStringExtra("sitemapPage"));
			intentSitemap = pageSplit[2];
			intentGroup = pageSplit[3];
		}
	}

	private void closeActivity() {
		OpenHABWriteBeaconActivity.this.finish();
	}

	private void setPointer() {
		sitemap.setSitemapPointer(intentSitemap);
		group.setGroupLabelsSitemap(sitemap.getChosenSitemap());
		group.setGroupPointer(intentGroup);
	}

	@Override
		public void update(Observable observable, Object data) {
		if(data == null){
			return;
		}
		if(observable instanceof WriteBeaconHandleSitemap){
			if(data instanceof List){
				if(Thread.currentThread() instanceof UiThread) {
					sitemap.sitemapAdapterNotifyDataSetChanged();
				}
				else{
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							sitemap.sitemapAdapterNotifyDataSetChanged();
						}
					});
				}
				group.loadGroupLabelList((List<String>)data, baseURL);
			}
			else if(data instanceof String) {
				group.setGroupLabelsSitemap((String) data);
				group.setGroupPointer(0);
			}
		}
		else if(observable instanceof WriteBeaconHandleGroup){
			if(Thread.currentThread() instanceof UiThread) {
				group.groupAdapterNotifyDataSetChanged();
				if (!hide) setPointer();
			}
			else{
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						group.groupAdapterNotifyDataSetChanged();
						if (!hide) setPointer();
					}
				});
			}
		}
	}
}
