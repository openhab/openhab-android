/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Victor Belov
 *  @since 1.4.0
 *
 */

package org.openhab.habdroid.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * This is a class to provide preferences activity for application.
 */

public class OpenHABPreferencesActivity extends PreferenceActivity {
	@SuppressWarnings("deprecation")

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
	public void onCreate(Bundle savedInstanceState) {
		Util.setActivityTheme(this);
	    super.onCreate(savedInstanceState);
	    addPreferencesFromResource(R.xml.preferences);
	    Preference urlPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_URL);
	    Preference altUrlPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_ALTURL);
	    Preference usernamePreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_USERNAME);
	    Preference passwordPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_PASSWORD);
	    ListPreference themePreference = (ListPreference)getPreferenceScreen().findPreference(Constants.PREFERENCE_THEME);
	    Preference versionPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_APPVERSION);
	    urlPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				Log.d("OpenHABPreferencesActivity", "Validating new url = " + (String) newValue);
				String newUrl = (String)newValue;
				if (newUrl.length() == 0 || urlIsValid(newUrl)) {
					updateTextPreferenceSummary(preference, (String)newValue);
					return true;
				}
				showAlertDialog(getString(R.string.erorr_invalid_url));
				return false;
			}
	    });
	    updateTextPreferenceSummary(urlPreference, null);
	    altUrlPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String newUrl = (String)newValue;
				if (newUrl.length() == 0 || urlIsValid(newUrl)) {
					updateTextPreferenceSummary(preference, (String)newValue);
					return true;
				}
				showAlertDialog(getString(R.string.erorr_invalid_url));
				return false;
			}
	    });
	    updateTextPreferenceSummary(altUrlPreference, null);
	    usernamePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				updateTextPreferenceSummary(preference, (String)newValue);
				return true;
			}
	    });
	    updateTextPreferenceSummary(usernamePreference, null);
	    passwordPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				updatePasswordPreferenceSummary(preference, (String)newValue);
				return true;
			}
	    });
	    updatePasswordPreferenceSummary(passwordPreference, null);
	    themePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				ListPreference listPreference = (ListPreference)preference;
				listPreference.setSummary(listPreference.getEntries()[listPreference.findIndexOfValue((String)newValue)]);
				return true;
			}
	    });
	    themePreference.setSummary(themePreference.getEntry());
	    updateTextPreferenceSummary(versionPreference, null);

        //fullscreen is not supoorted in builds < 4.4
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getPreferenceScreen().removePreference(getPreferenceScreen().findPreference(Constants.PREFERENCE_FULLSCREEN));
        }

	    setResult(RESULT_OK);
	}
	
	private void updateTextPreferenceSummary(Preference textPreference, String newValue) {
		if (newValue == null) {
			if (textPreference.getSharedPreferences().getString(textPreference.getKey(), "").length() > 0)
		    	textPreference.setSummary(textPreference.getSharedPreferences().getString(textPreference.getKey(), ""));
		    else
		    	textPreference.setSummary(this.getResources().getString(R.string.info_not_set));
		} else {
			if (newValue.length() > 0)
				textPreference.setSummary(newValue);
			else
				textPreference.setSummary(this.getResources().getString(R.string.info_not_set));
		}
	}
	
	private void updatePasswordPreferenceSummary(Preference passwordPreference, String newValue) {
		if (newValue == null) {
			if (passwordPreference.getSharedPreferences().getString(passwordPreference.getKey(), "").length() > 0)
				passwordPreference.setSummary("******");
			else
				passwordPreference.setSummary(this.getResources().getString(R.string.info_not_set));
		} else {
			if (newValue.length() > 0)
				passwordPreference.setSummary("******");
			else
				passwordPreference.setSummary(this.getResources().getString(R.string.info_not_set));
		}
	}
	
	private boolean urlIsValid(String url) {
		// As we accept an empty URL, which means it is not configured, length==0 is ok
		if (url.length() == 0)
			return true;
		if (url.contains("\n") || url.contains(" "))
			return false;
		try {
			URL testURL = new URL(url);
		} catch (MalformedURLException e) {
			return false;
		}
		return true;
	}
		
	private void showAlertDialog(String alertMessage) {
		AlertDialog.Builder builder = new AlertDialog.Builder(OpenHABPreferencesActivity.this);
		builder.setMessage(alertMessage)
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
				}
		});
		AlertDialog alert = builder.create();
		alert.show();		
	}
	
	@Override
	public void finish() {
		super.finish();
		Util.overridePendingTransition(this, true);
	}
}
