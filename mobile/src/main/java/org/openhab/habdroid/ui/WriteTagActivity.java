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
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.openhab.habdroid.R;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class WriteTagActivity extends AbstractBaseActivity {
    private static final String TAG = WriteTagActivity.class.getSimpleName();
    public static final String QUERY_PARAMETER_ITEM_NAME = "i";
    public static final String QUERY_PARAMETER_STATE = "s";
    public static final String QUERY_PARAMETER_MAPPED_STATE = "m";
    public static final String QUERY_PARAMETER_ITEM_LABEL = "l";

    private NfcAdapter mNfcAdapter;
    private String mSitemapPage;
    private String mItem;
    private String mState;
    private String mMappedState;
    private String mLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_writetag);

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
        mItem = getIntent().getStringExtra(QUERY_PARAMETER_ITEM_NAME);
        mState = getIntent().getStringExtra(QUERY_PARAMETER_STATE);
        mMappedState = getIntent().getStringExtra(QUERY_PARAMETER_MAPPED_STATE);
        mLabel = getIntent().getStringExtra(QUERY_PARAMETER_ITEM_LABEL);
        Log.d(TAG, String.format(Locale.US,
                "Got sitemap '%s', item '%s', state '%s', mapped state '%s', label '%s'",
                mSitemapPage, mItem, mState, mMappedState, mLabel));
    }

    private Fragment getFragment() {
        if (mNfcAdapter == null) {
            return new NfcUnsupportedFragment();
        } else if (!mNfcAdapter.isEnabled()) {
            return new NfcDisabledFragment();
        } else {
            return new NfcWriteTagFragment();
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

        new AsyncTask<Void, Integer, Boolean>() {
            @Override
            protected void onPreExecute() {
                TextView writeTagMessage = findViewById(R.id.write_tag_message);
                writeTagMessage.setText(R.string.info_write_tag_progress);
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                Log.d(TAG, "NFC TAG = " + tag.toString());
                Log.d(TAG, "Writing page " + mSitemapPage + " to tag");

                URI sitemapUri;
                String longUri = "";
                String shortUri = "";

                try {
                    sitemapUri = new URI(mSitemapPage);
                    if (!TextUtils.isEmpty(mItem) && !TextUtils.isEmpty(mState)) {
                        Uri.Builder uriBuilder = new Uri.Builder()
                                .scheme("openhab")
                                .authority("")
                                .appendQueryParameter(QUERY_PARAMETER_ITEM_NAME, mItem)
                                .appendQueryParameter(QUERY_PARAMETER_STATE, mState);

                        shortUri = uriBuilder.toString();

                        longUri = uriBuilder
                                .appendQueryParameter(QUERY_PARAMETER_MAPPED_STATE, mMappedState)
                                .appendQueryParameter(QUERY_PARAMETER_ITEM_LABEL, mLabel)
                                .toString();
                    } else if (!sitemapUri.getPath().startsWith("/rest/sitemaps")) {
                        throw new URISyntaxException(mSitemapPage, "Expected a sitemap URL");
                    } else {
                        longUri = new Uri.Builder()
                                .scheme("openhab")
                                .authority("")
                                .appendEncodedPath(sitemapUri.getPath().substring(15))
                                .toString();
                    }
                } catch (URISyntaxException e) {
                    Log.e(TAG, e.getMessage());
                    return false;
                }

                Log.d(TAG, "Creating tag object for URI " + longUri);

                NdefMessage longMessage = getNdefMessage(longUri);
                NdefMessage shortMessage = getNdefMessage(shortUri);

                NdefFormatable ndefFormatable = NdefFormatable.get(tag);

                if (ndefFormatable != null) {
                    Log.d(TAG, "Tag is uninitialized, formating");
                    try {
                        ndefFormatable.connect();
                        try {
                            ndefFormatable.format(longMessage);
                        } catch (IOException e) {
                            if (shortMessage != null) {
                                Log.d(TAG, "Try with short uri");
                                ndefFormatable.format(shortMessage);
                            }
                        }
                        return true;
                    } catch (IOException | FormatException e) {
                        Log.e(TAG, "Writing to unformatted tag failed: " + e);
                    } finally {
                        try {
                            ndefFormatable.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Closing ndefFormatable failed", e);
                        }
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
                                try {
                                    ndef.writeNdefMessage(longMessage);
                                } catch (IOException e) {
                                    if (shortMessage != null) {
                                        Log.d(TAG, "Try with short uri");
                                        ndef.writeNdefMessage(shortMessage);
                                    }
                                }
                            }
                            return true;
                        } catch (IOException | FormatException e) {
                            Log.e(TAG, "Writing to formatted tag failed", e);
                        } finally {
                            try {
                                ndef.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Closing ndef failed", e);
                            }
                        }
                    } else {
                        Log.e(TAG, "Ndef == null");
                    }
                }

                return false;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                TextView writeTagMessage = findViewById(R.id.write_tag_message);

                if (success) {
                    ProgressBar progressBar = findViewById(R.id.nfc_wait_progress);
                    progressBar.setVisibility(View.INVISIBLE);

                    ImageView watermark = findViewById(R.id.nfc_watermark);
                    watermark.setImageDrawable(getDrawable(R.drawable.ic_nfc_black_180dp));

                    writeTagMessage.setText(R.string.info_write_tag_finished);
                    new Handler().postDelayed(WriteTagActivity.this::finish, 2000);
                } else {
                    writeTagMessage.setText(R.string.info_write_failed);
                }
            }
        }.execute();
    }

    private NdefMessage getNdefMessage(String uri) {
        if (TextUtils.isEmpty(uri)) {
            return null;
        }
        NdefRecord[] longNdefRecords = new NdefRecord[] { NdefRecord.createUri(uri) };
        return new NdefMessage(longNdefRecords);
    }

    public abstract static class AbstractNfcFragment extends Fragment {
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            final View view = inflater.inflate(R.layout.fragment_writenfc, container, false);
            final ImageView watermark = view.findViewById(R.id.nfc_watermark);

            Drawable nfcIcon = getResources().getDrawable(getWatermarkIcon());
            nfcIcon.setColorFilter(
                    ContextCompat.getColor(getActivity(), R.color.empty_list_text_color),
                    PorterDuff.Mode.SRC_IN);
            watermark.setImageDrawable(nfcIcon);

            return view;
        }

        protected TextView getMessageTextView(View view) {
            return view.findViewById(R.id.write_tag_message);
        }

        protected abstract @DrawableRes int getWatermarkIcon();
    }

    public static class NfcUnsupportedFragment extends AbstractNfcFragment {
        @Override
        public View onCreateView(LayoutInflater inflater,
                @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);

            getMessageTextView(view).setText(R.string.info_write_tag_unsupported);
            return view;
        }

        @Override
        protected @DrawableRes int getWatermarkIcon() {
            return R.drawable.ic_nfc_off_black_180dp;
        }
    }

    public static class NfcDisabledFragment extends AbstractNfcFragment {
        @Override
        public View onCreateView(LayoutInflater inflater,
                @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

        @Override
        protected @DrawableRes int getWatermarkIcon() {
            return R.drawable.ic_nfc_off_black_180dp;
        }
    }

    public static class NfcWriteTagFragment extends AbstractNfcFragment {
        @Override
        public View onCreateView(LayoutInflater inflater,
                @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);

            view.findViewById(R.id.nfc_wait_progress).setVisibility(View.VISIBLE);

            return view;
        }

        @Override
        protected @DrawableRes int getWatermarkIcon() {
            return R.drawable.ic_nfc_search_black_180dp;
        }
    }
}