/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

public class OpenHABWriteTagActivity extends AppCompatActivity {

	// Logging TAG
	private static final String TAG = OpenHABWriteTagActivity.class.getSimpleName();
	private String sitemapPage = "";
	private String item = "";
	private String command = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Util.setActivityTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.openhabwritetag);

		Toolbar toolbar = (Toolbar) findViewById(R.id.openhab_toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.add(R.id.writenfc_container, new OpenHABWriteTagActivity.WriteNFCFragment())
					.commit();
		}

		setResult(RESULT_OK);

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
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
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

	public static class WriteNFCFragment extends Fragment {

		@Override
		public void onResume() {
			super.onResume();

			updateFragmentContents(getView());
		}

		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
			super.onCreateView(inflater, container, savedInstanceState);

			final View view = inflater.inflate(R.layout.fragment_writenfc, container, false);

			TextView writeTagMessage = view.findViewById(R.id.write_tag_message);
			Drawable ic_intro = getResources().getDrawable(R.drawable.ic_info_outline);
			ic_intro.setColorFilter(
					writeTagMessage.getCurrentTextColor(),
					PorterDuff.Mode.SRC_IN
			);
			int h = ic_intro.getIntrinsicHeight();
			int w = ic_intro.getIntrinsicWidth();
			ic_intro.setBounds( 0, 0, w, h );
			writeTagMessage.setCompoundDrawables(ic_intro, null, null, null);

			updateFragmentContents(view);

			return view;
		}

		private void updateFragmentContents(View view) {
			final TextView writeTagMessage = view.findViewById(R.id.write_tag_message);
			final TextView waitMessage = view.findViewById(R.id.nfc_wait_for_tag);
			final ProgressBar waitProgressBar = view.findViewById(R.id.nfc_wait_progress);
			final Button goToSettingsButton = view.findViewById(R.id.nfc_go_to_settings);

			NfcManager manager =
					(NfcManager) this.getActivity().getSystemService(Context.NFC_SERVICE);
			NfcAdapter adapter = manager.getDefaultAdapter();
			if (adapter == null) {
				writeTagMessage.setText(R.string.info_write_tag_unsupported);
			} else if (! adapter.isEnabled()) {
				writeTagMessage.setText(R.string.info_write_tag_disabled);
				waitMessage.setVisibility(View.GONE);
				waitProgressBar.setVisibility(View.GONE);
				goToSettingsButton.setVisibility(View.VISIBLE);
				goToSettingsButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
							startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
						} else {
							startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
						}
					}
				});
			} else {
				writeTagMessage.setText(R.string.info_write_tag);
				waitMessage.setVisibility(View.VISIBLE);
				waitProgressBar.setVisibility(View.VISIBLE);
				goToSettingsButton.setVisibility(View.GONE);
			}
		}
	}
}
