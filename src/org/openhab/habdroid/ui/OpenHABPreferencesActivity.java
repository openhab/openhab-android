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

import java.net.MalformedURLException;
import java.net.URL;

import org.openhab.habdroid.R;

import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * This is a class to provide preferences activity for application.
 * 
 * @author Victor Belov
 *
 */

public class OpenHABPreferencesActivity extends PreferenceActivity {
	@SuppressWarnings("deprecation")

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    addPreferencesFromResource(R.xml.preferences);
	    Preference urlPreference = getPreferenceScreen().findPreference("default_openhab_url");
	    Preference altUrlPreference = getPreferenceScreen().findPreference("default_openhab_alturl");
	    Preference usernamePreference = getPreferenceScreen().findPreference("default_openhab_username");
	    Preference passwordPreference = getPreferenceScreen().findPreference("default_openhab_password");
	    Preference versionPreference = getPreferenceScreen().findPreference("default_openhab_appversion");
	    urlPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				Log.d("OpenHABPreferencesActivity", "Validating new url = " + (String)newValue);
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
	    updateTextPreferenceSummary(versionPreference, null);
	    setResult(RESULT_OK);
	}
	
	private void updateTextPreferenceSummary(Preference textPreference, String newValue) {
		if (newValue == null) {
			if (textPreference.getSharedPreferences().getString(textPreference.getKey(), "").length() > 0)
		    	textPreference.setSummary(textPreference.getSharedPreferences().getString(textPreference.getKey(), ""));
		    else
		    	textPreference.setSummary("Not set");
		} else {
			if (newValue.length() > 0)
				textPreference.setSummary(newValue);
			else
				textPreference.setSummary("Not set");
		}
	}
	
	private void updatePasswordPreferenceSummary(Preference passwordPreference, String newValue) {
		if (newValue == null) {
			if (passwordPreference.getSharedPreferences().getString(passwordPreference.getKey(), "").length() > 0)
				passwordPreference.setSummary("******");
			else
				passwordPreference.setSummary("Not set");
		} else {
			if (newValue.length() > 0)
				passwordPreference.setSummary("******");
			else
				passwordPreference.setSummary("Not set");
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
}
