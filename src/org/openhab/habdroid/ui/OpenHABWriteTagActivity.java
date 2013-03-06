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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.openhab.habdroid.R;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class OpenHABWriteTagActivity extends Activity {

	// Logging TAG
	private static final String TAG = "OpenHABWriteTagActivity";
	private String sitemapPage = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.openhabwritetag);
		if (getIntent().hasExtra("sitemapPage")) {
			sitemapPage = getIntent().getExtras().getString("sitemapPage");
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
		Log.i(TAG, "onResume()");
		super.onResume();
		PendingIntent pendingIntent = PendingIntent.getActivity(
				  this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		if (NfcAdapter.getDefaultAdapter(this) != null)
			NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(this, pendingIntent, null, null);
	}

	@Override
	public void onPause() {
		Log.i(TAG, "onPause()");
		super.onPause();
		if(NfcAdapter.getDefaultAdapter(this) != null)
			NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);
	}

	public void onNewIntent(Intent intent) {
	    Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	    String openhabURI = "";
	    //do something with tagFromIntent
	    Log.i(TAG, "TAG = " + tagFromIntent.toString());
	    Log.i(TAG, "Writing page " + sitemapPage + " to TAG");
	    TextView writeTagMessage = (TextView)findViewById(R.id.write_tag_message);
	    try {
			URI sitemapURI = new URI(sitemapPage);
			if (sitemapURI.getPath().startsWith("/rest/sitemaps")) {
				openhabURI = "openhab://sitemaps" + sitemapURI.getPath().substring(14, sitemapURI.getPath().length());
			}
			Log.i(TAG, "URI = " + openhabURI);
		    writeTagMessage.setText(R.string.info_write_tag_progress);
		    writeTag(tagFromIntent, openhabURI);
		    writeTagMessage.setText(R.string.info_write_tag_finished);	    
		} catch (URISyntaxException e) {
			Log.e(TAG, e.getMessage());
			writeTagMessage.setText(R.string.info_write_failed);
		}
	}

	public void writeTag(Tag tag, String openhabUri) {
		Log.i(TAG, "Creating tag object");
		NdefRecord[] ndefRecords;
		ndefRecords = new NdefRecord[1];
		ndefRecords[0] = NdefRecord.createUri(openhabUri);
		NdefMessage message = new NdefMessage(ndefRecords);
		NdefFormatable ndefFormatable = NdefFormatable.get(tag);
		if (ndefFormatable != null) {
			Log.i(TAG, "Tag is uninitialized, formating");
			try {
				ndefFormatable.connect();
				ndefFormatable.format(message);
				ndefFormatable.close();				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (FormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			Log.i(TAG, "Tag is initialized, writing");
			Ndef ndef = Ndef.get(tag);
			if (ndef != null) {
				try {
					Log.i(TAG, "Connecting");
					ndef.connect();
					Log.i(TAG, "Writing");
					if (ndef.isWritable()) {
						ndef.writeNdefMessage(message);
					}
					Log.i(TAG, "Closing");
					ndef.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (FormatException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
