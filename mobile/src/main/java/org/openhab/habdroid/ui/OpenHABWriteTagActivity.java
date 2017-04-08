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
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

import com.google.android.gms.analytics.GoogleAnalytics;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

public class OpenHABWriteTagActivity extends Activity {

	// Logging TAG
	private static final String TAG = OpenHABWriteTagActivity.class.getSimpleName();
	private String sitemapPage = "";
	private String item = "";
	private String command = "";

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
		setContentView(R.layout.openhabwritetag);
		TextView writeTagMessage = (TextView)findViewById(R.id.write_tag_message);
    	if (!this.getPackageManager().hasSystemFeature("android.hardware.nfc")) {
    		writeTagMessage.setText(R.string.info_write_tag_unsupported);
    	} else if (NfcAdapter.getDefaultAdapter(this) != null) {
    		if (!NfcAdapter.getDefaultAdapter(this).isEnabled()) {
    			writeTagMessage.setText(R.string.info_write_tag_disabled);
    		}
    	}
		if (getIntent().hasExtra("sitemapPage")) {
			sitemapPage = getIntent().getExtras().getString("sitemapPage");
			Log.d(TAG, "Got sitemapPage = " + sitemapPage);
		}
		if (getIntent().hasExtra("item")) {
			item = getIntent().getExtras().getString("item");
			Log.d(TAG, "Got item = " + item);
		}
		if (getIntent().hasExtra("command")) {
			command = getIntent().getExtras().getString("command");
			Log.d(TAG, "Got command = " + command);
		}
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
		PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		if (NfcAdapter.getDefaultAdapter(this) != null)
			NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(this, pendingIntent, null, null);
	}

	@Override
	public void onPause() {
		Log.d(TAG, "onPause()");
		super.onPause();
		if(NfcAdapter.getDefaultAdapter(this) != null)
			NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);
	}

	public void onNewIntent(Intent intent) {
	    Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	    String openhabURI = "";
	    //do something with tagFromIntent
	    Log.d(TAG, "NFC TAG = " + tagFromIntent.toString());
	    Log.d(TAG, "Writing page " + sitemapPage + " to TAG");
	    TextView writeTagMessage = (TextView)findViewById(R.id.write_tag_message);
	    try {
			URI sitemapURI = new URI(sitemapPage);
			if (sitemapURI.getPath().startsWith("/rest/sitemaps")) {
				openhabURI = "openhab://sitemaps" + sitemapURI.getPath().substring(14, sitemapURI.getPath().length());
				if (!TextUtils.isEmpty(item)) {
					openhabURI = openhabURI + "?item=" + item;
				}
				if (!TextUtils.isEmpty(command)) {
					openhabURI = openhabURI + "&command=" + command;
				}
			}
			Log.d(TAG, "URI = " + openhabURI);
		    writeTagMessage.setText(R.string.info_write_tag_progress);
		    writeTag(tagFromIntent, openhabURI);
		} catch (URISyntaxException e) {
			Log.e(TAG, e.getMessage());
			writeTagMessage.setText(R.string.info_write_failed);
		}
	}

	public void writeTag(Tag tag, String openhabUri) {
		Log.d(TAG, "Creating tag object");
	    TextView writeTagMessage = (TextView)findViewById(R.id.write_tag_message);
        if (openhabUri == null) {
            writeTagMessage.setText(R.string.info_write_failed);
            return;
        }
        if (openhabUri.length() == 0) {
            writeTagMessage.setText(R.string.info_write_failed);
            return;
        }
		NdefRecord[] ndefRecords;
		ndefRecords = new NdefRecord[1];
		ndefRecords[0] = NdefRecord.createUri(openhabUri);
		NdefMessage message = new NdefMessage(ndefRecords);
		NdefFormatable ndefFormatable = NdefFormatable.get(tag);
		if (ndefFormatable != null) {
			Log.d(TAG, "Tag is uninitialized, formating");
			try {
				ndefFormatable.connect();
				ndefFormatable.format(message);
				ndefFormatable.close();
			    writeTagMessage.setText(R.string.info_write_tag_finished);
			    autoCloseActivity();
			} catch (IOException | FormatException e) {
				if (e.getMessage() != null)
					Log.e(TAG, e.getMessage());
				writeTagMessage.setText(R.string.info_write_failed);
			}
		} else {
			Log.d(TAG, "Tag is initialized, writing");
			Ndef ndef = Ndef.get(tag);
			if (ndef != null) {
				try {
					Log.d(TAG, "Connecting");
					ndef.connect();
					Log.d(TAG, "Writing");
					if (ndef.isWritable()) {
						ndef.writeNdefMessage(message);
					}
					Log.d(TAG, "Closing");
					ndef.close();
				    writeTagMessage.setText(R.string.info_write_tag_finished);
				    autoCloseActivity();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					if (e != null)
						Log.e(TAG, e.getClass().getCanonicalName());
                    writeTagMessage.setText(R.string.info_write_failed);
				} catch (FormatException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, e.getMessage());
                    writeTagMessage.setText(R.string.info_write_failed);
				}
			} else {
				Log.e(TAG, "Ndef == null");
				writeTagMessage.setText(R.string.info_write_failed);
			}
		}
	}
	
	@Override
	public void finish() {
		super.finish();
		Util.overridePendingTransition(this, true);
	}
	
	private void autoCloseActivity() {
		Timer autoCloseTimer = new Timer();
		autoCloseTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				OpenHABWriteTagActivity.this.runOnUiThread(new Runnable() {
					public void run() {
						OpenHABWriteTagActivity.this.finish();
					}
				});
				Log.d(TAG, "Autoclosing tag write activity");
			}
			
		}, 2000);
	}
}
