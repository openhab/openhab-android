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

import javax.jmdns.ServiceInfo;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.AsyncServiceResolver;
import org.openhab.habdroid.util.AsyncServiceResolverListener;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;

/**
 * This class provides main app activity which performes startup sequence for
 * discovering openHAB or switching to alternative URL and then launches
 * OpenHABWidgetListActivity to display openHAB sitemap pages
 * 
 * @author Victor Belov
 *
 */

public class OpenHABStartupActivity extends Activity implements AsyncServiceResolverListener {
	private final static String TAG = "OpenHABStartupActivity";
	private static final String openHABServiceType = "_openhab-server._tcp.local.";
	private String openHABBaseUrl = "";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.openhabstartup);
		ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(
				Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		if (activeNetworkInfo != null) {
			Log.i(TAG, "Network is connected");
			if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI
					|| activeNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
				Log.i(TAG, "Network is WiFi or Ethernet");
				AsyncServiceResolver serviceResolver = new AsyncServiceResolver(this, openHABServiceType);
				serviceResolver.start();
			} else if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
				Log.i(TAG, "Network is Mobile (" + activeNetworkInfo.getSubtypeName() + ")");
				onAlternativeUrl();
			} else {
				Log.i(TAG, "Network type (" + activeNetworkInfo.getTypeName() + ") is unsupported");
			}
		} else {
			Log.i(TAG, "Network is not available");
			Toast.makeText(getApplicationContext(), "Network is not available",
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onServiceResolved(ServiceInfo serviceInfo) {
		Log.i(TAG, "Service resolved: "
                + serviceInfo.getHostAddresses()[0]
                + " port:" + serviceInfo.getPort());
		openHABBaseUrl = "http://" + serviceInfo.getHostAddresses()[0] + ":" +
				String.valueOf(serviceInfo.getPort()) + "/";
		AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
		asyncHttpClient.get(openHABBaseUrl + "static/uuid", new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String content) {
				Log.i(TAG, "Got openHAB UUID = " + content);
				SharedPreferences settings = 
						PreferenceManager.getDefaultSharedPreferences(OpenHABStartupActivity.this);
				if (settings.contains("openhab_uuid")) {
					String openHABUUID = settings.getString("openhab_uuid", "");
					if (openHABUUID.equals(content)) {
						Log.i(TAG, "openHAB UUID does match the saved one");
						startListActivity(openHABBaseUrl);
					} else {
						Log.i(TAG, "openHAB UUID doesn't match the saved one");
						// TODO: need to add some user prompt here
						Toast.makeText(getApplicationContext(), 
								"openHAB UUID doesn't match the saved one, connection failed",
								Toast.LENGTH_LONG).show();
					}
				} else {
					Log.i(TAG, "No recorded openHAB UUID, saving the new one");
					Editor preferencesEditor = settings.edit();
					preferencesEditor.putString("openhab_uuid", content);
					preferencesEditor.commit();
				}
			}
			@Override
		    public void onFailure(Throwable e) {
				Toast.makeText(getApplicationContext(), "Unable to get openHAB UUID, connection failed",
						Toast.LENGTH_LONG).show();
			}
		});
	}

	@Override
	public void onServiceResolveFailed() {
		Log.i(TAG, "Service resolve failed, switching to alt URL");
		onAlternativeUrl();
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.mainmenu_openhab_preferences:
            Intent myIntent = new Intent(this.getApplicationContext(), OpenHABPreferencesActivity.class);
            startActivityForResult(myIntent, 0);
    		return true;
    	default:
    		return super.onOptionsItemSelected(item);
    	}
    }
    
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult " + String.valueOf(requestCode) + " " + String.valueOf(resultCode));
		if (resultCode == -1) {
			// Right now only PreferencesActivity returns -1
			// Restart app after preferences
			Log.i(TAG, "Restarting");
			// Get launch intent for application
			Intent restartIntent = getBaseContext().getPackageManager()
		             .getLaunchIntentForPackage( getBaseContext().getPackageName() );
			restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			// Finish current activity
			finish();
			// Start launch activity
			startActivity(restartIntent);
		}
	}

	
	private void onAlternativeUrl() {
		SharedPreferences settings = 
				PreferenceManager.getDefaultSharedPreferences(this);
		String altUrl = settings.getString("default_openhab_alturl", "");
		if (altUrl.length() > 0) {
			Toast.makeText(getApplicationContext(), "Connecting to alternative URL",
					Toast.LENGTH_SHORT).show();
			Log.i(TAG, "Connecting to alternative URL " + altUrl);
			openHABBaseUrl = altUrl;
			startListActivity(openHABBaseUrl);
		} else {
			Toast.makeText(getApplicationContext(), "Please configure openHAB alternative URL to access it via Internet",
					Toast.LENGTH_LONG).show();		
			stopProgressIndicator();
		}
	}
	
	private void startListActivity(String baseURL) {
		Intent listActivityIntent = new Intent(this.getApplicationContext(), OpenHABWidgetListActivity.class);
		listActivityIntent.putExtra("baseURL", baseURL);
		finish();
		startActivity(listActivityIntent);
	}
	
	private void stopProgressIndicator() {
		setProgressBarIndeterminateVisibility(false);
	}
}
