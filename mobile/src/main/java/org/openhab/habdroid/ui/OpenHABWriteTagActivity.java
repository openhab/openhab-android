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
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class OpenHABWriteTagActivity extends AppCompatActivity {
    private static final String TAG = OpenHABWriteTagActivity.class.getSimpleName();

    private NfcAdapter mNfcAdapter;
    private String mSitemapPage;
    private String mItem;
    private String mCommand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.openhabwritetag);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        NfcManager manager = (NfcManager) getSystemService(Context.NFC_SERVICE);
        mNfcAdapter = manager.getDefaultAdapter();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.writenfc_container, getFragment())
                    .commit();
        }

        setResult(RESULT_OK);

        mSitemapPage = getIntent().getStringExtra("sitemapPage");
        Log.d(TAG, "Got sitemapPage = " + mSitemapPage);
        mItem = getIntent().getStringExtra("item");
        Log.d(TAG, "Got item = " + mItem);
        mCommand = getIntent().getStringExtra("command");
        Log.d(TAG, "Got command = " + mCommand);
    }

    private Fragment getFragment() {
        if (mNfcAdapter == null) {
            return new NFCUnsupportedFragment();
        } else if (!mNfcAdapter.isEnabled()) {
            return new NFCDisabledFragment();
        } else {
            return new NFCWriteTagFragment();
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

        if (mNfcAdapter != null) {
            Intent intent = new Intent(this, getClass())
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.writenfc_container, getFragment())
                .commit();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (mSitemapPage == null) {
            return;
        }

        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        //do something with tagFromIntent
        Log.d(TAG, "NFC TAG = " + tagFromIntent.toString());
        Log.d(TAG, "Writing page " + mSitemapPage + " to tag");

        TextView writeTagMessage = findViewById(R.id.write_tag_message);

        try {
            URI sitemapURI = new URI(mSitemapPage);
            if (!sitemapURI.getPath().startsWith("/rest/sitemaps")) {
                throw new URISyntaxException(mSitemapPage, "Expected a sitemap URL");
            }
            StringBuilder uriToWrite = new StringBuilder("openhab://sitemaps");
            uriToWrite.append(sitemapURI.getPath().substring(14));
            if (!TextUtils.isEmpty(mItem) && !TextUtils.isEmpty(mCommand)) {
                uriToWrite.append("?item=").append(mItem).append("&command=").append(mCommand);
            }
            writeTagMessage.setText(R.string.info_write_tag_progress);
            writeTag(tagFromIntent, uriToWrite.toString());
        } catch (URISyntaxException e) {
            Log.e(TAG, e.getMessage());
            writeTagMessage.setText(R.string.info_write_failed);
        }
    }

    private void writeTag(Tag tag, String uri) {
        Log.d(TAG, "Creating tag object for URI " + uri);
        TextView writeTagMessage = findViewById(R.id.write_tag_message);

        NdefRecord[] ndefRecords = new NdefRecord[] { NdefRecord.createUri(uri) };
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
                Log.e(TAG, "Writing to unformatted tag failed: " + e);
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
                } catch (IOException | FormatException e) {
                    Log.e(TAG, "Writing to formatted tag failed: " + e);
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
        new Handler().postDelayed(this::finish, 2000);
    }

    public static abstract class AbstractNFCFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            final View view = inflater.inflate(R.layout.fragment_writenfc, container, false);
            final ImageView watermark = view.findViewById(R.id.nfc_watermark);

            Drawable ic_nfc = getResources().getDrawable(R.drawable.ic_nfc_black_180dp);
            ic_nfc.setColorFilter(
                    ContextCompat.getColor(getActivity(), R.color.empty_list_text_color),
                    PorterDuff.Mode.SRC_IN);
            watermark.setImageDrawable(ic_nfc);

            return view;
        }

        protected TextView getMessageTextView(View view) {
            return view.findViewById(R.id.write_tag_message);
        }
    }

    public static class NFCUnsupportedFragment extends AbstractNFCFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);

            getMessageTextView(view).setText(R.string.info_write_tag_unsupported);
            return view;
        }
    }

    public static class NFCDisabledFragment extends AbstractNFCFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);

            getMessageTextView(view).setText(R.string.info_write_tag_disabled);

            TextView nfcActivate = view.findViewById(R.id.nfc_activate);
            nfcActivate.setVisibility(View.VISIBLE);
            nfcActivate.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                } else {
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                }
            });

            return view;
        }
    }

    public static class NFCWriteTagFragment extends AbstractNFCFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);

            view.findViewById(R.id.nfc_wait_progress).setVisibility(View.VISIBLE);

            return view;
        }
    }
}