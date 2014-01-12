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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.jmdns.ServiceInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import android.view.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABNFCActionList;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.util.AsyncServiceResolver;
import org.openhab.habdroid.util.AsyncServiceResolverListener;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.crittercism.app.Crittercism;
import com.google.analytics.tracking.android.EasyTracker;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.image.WebImageCache;

import android.Manifest.permission;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager.BadTokenException;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This class is apps' main activity which runs startup sequence and displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

public class OpenHABWidgetListActivity extends ListActivity implements AsyncServiceResolverListener {
	// Logging TAG
	private static final String TAG = "OpenHABWidgetListActivity";
	// Datasource, providing list of openHAB widgets
	private OpenHABWidgetDataSource openHABWidgetDataSource;
	// List adapter for list view of openHAB widgets
	private OpenHABWidgetAdapter openHABWidgetAdapter;
	// Url of current sitemap page displayed
	private String displayPageUrl ="";
	// sitemap root url
	private String sitemapRootUrl = "";
	// async http client
	private static AsyncHttpClient pageAsyncHttpClient;
	// openHAB base url
	private String openHABBaseUrl = "https://demo.openhab.org:8443/";
	// List of widgets to display
	private ArrayList<OpenHABWidget> widgetList = new ArrayList<OpenHABWidget>();
	// Username/password for authentication
	private String openHABUsername;
	private String openHABPassword;
	// openHAB Bonjour service name
	private String openHABServiceType;
	// openHAB page url from NFC tag
	private String nfcTagData = "";
	// Progress dialog
	private ProgressDialog progressDialog;
	// selected openhab widget
	private OpenHABWidget selectedOpenHABWidget;
	// widget Id which we got from nfc tag
	private String nfcWidgetId;
	// widget command which we got from nfc tag
	private String nfcCommand;
	// auto close app after nfc action is complete
	private boolean nfcAutoClose = false;
	// Service resolver for Bonjour
	private AsyncServiceResolver serviceResolver;
	// Enable/disable openHAB discovery
	private boolean serviceDiscoveryEnabled = true;

	/**
	 * Overriding onStart to enable Google Analytics stats collection
	 */
	@Override
	public void onStart() {
		super.onStart();
		// Start activity tracking via Google Analytics
		EasyTracker.getInstance().activityStart(this);
	}
	
	/**
	 * Overriding onStop to enable Google Analytics stats collection
	 */
	@Override
	public void onStop() {
		super.onStop();
		// Stop activity tracking via Google Analytics
		EasyTracker.getInstance().activityStop(this);
	}

	/**
	 * This is called when activity is created. Initializes the state, performs network
	 * state based selection for app initialization and starts the widget list
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d("OpenHABWidgetListActivity", "onCreate");
		// Set default values, false means do it one time during the very first launch
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		// Set non-persistant HABDroid version preference to current version from application package
		try {
			PreferenceManager.getDefaultSharedPreferences(this).edit().putString("default_openhab_appversion",
					getPackageManager().getPackageInfo(getPackageName(), 0).versionName).commit();
		} catch (NameNotFoundException e1) {
            if (e1 != null)
                Log.d(TAG, e1.getMessage());
		}
		// Set the theme to one from preferences
		Util.setActivityTheme(this);
		// Fetch openHAB service type name from strings.xml
		openHABServiceType = getString(R.string.openhab_service_type);
		// Enable progress ring bar
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		setProgressBarIndeterminateVisibility(true);
		// Initialize crittercism reporting
		JSONObject crittercismConfig = new JSONObject();
		try {
			crittercismConfig.put("shouldCollectLogcat", true);
		} catch (JSONException e) {
			if (e.getMessage() != null)
				Log.e(TAG, e.getMessage());
			else
				Log.e(TAG, "Crittercism JSON exception");
		}
		Crittercism.init(getApplicationContext(), "5117659f59e1bd4ba9000004", crittercismConfig);
		// Initialize activity view
		super.onCreate(savedInstanceState);
		setContentView(R.layout.openhabwidgetlist);
		// Disable screen timeout if set in preferences
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (settings.getBoolean("default_openhab_screentimeroff", false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		// Check if we got all needed permissions
		PackageManager pm = getPackageManager();
		if (!(pm.checkPermission(permission.CHANGE_WIFI_MULTICAST_STATE, getPackageName()) == PackageManager.PERMISSION_GRANTED)) {
			showAlertDialog(getString(R.string.erorr_no_wifi_mcast_permission));
			serviceDiscoveryEnabled = false;
		}
		if (!(pm.checkPermission(permission.ACCESS_WIFI_STATE, getPackageName()) == PackageManager.PERMISSION_GRANTED)) {
			showAlertDialog(getString(R.string.erorr_no_wifi_state_permission));
			serviceDiscoveryEnabled = false;
		}
		// Get username/password from preferences
		openHABUsername = settings.getString("default_openhab_username", null);
		openHABPassword = settings.getString("default_openhab_password", null);
		// Create new data source and adapter and set it to list view
		openHABWidgetDataSource = new OpenHABWidgetDataSource();
		openHABWidgetAdapter = new OpenHABWidgetAdapter(OpenHABWidgetListActivity.this,
				R.layout.openhabwidgetlist_genericitem, widgetList);
		getListView().setAdapter(openHABWidgetAdapter);
		// Set adapter parameters
		openHABWidgetAdapter.setOpenHABUsername(openHABUsername);
		openHABWidgetAdapter.setOpenHABPassword(openHABPassword);
		// Enable app logo as home button
		this.getActionBar().setHomeButtonEnabled(true);
		// Check if we have openHAB page url in saved instance state?
		if (savedInstanceState != null) {
			displayPageUrl = savedInstanceState.getString("displayPageUrl");
			openHABBaseUrl = savedInstanceState.getString("openHABBaseUrl");
			sitemapRootUrl = savedInstanceState.getString("sitemapRootUrl");
			openHABWidgetAdapter.setOpenHABBaseUrl(openHABBaseUrl);
		}
		// Check if this is a launch from myself (drill down navigation)
		if (getIntent() != null) {
            if (getIntent().getAction() != null) {
                if (getIntent().getAction().equals("org.openhab.habdroid.ui.OpwnHABWidgetListActivity")) {
                    displayPageUrl = getIntent().getExtras().getString("displayPageUrl");
                    openHABBaseUrl = getIntent().getExtras().getString("openHABBaseUrl");
                    sitemapRootUrl = getIntent().getExtras().getString("sitemapRootUrl");
                    this.setTitle(getIntent().getExtras().getString("pageTitle"));
                    openHABWidgetAdapter.setOpenHABBaseUrl(openHABBaseUrl);
                }
            }
		}
		// If yes, then just go to it (means restore activity from it's saved state)
		if (displayPageUrl.length() > 0) {
			Log.d(TAG, "displayPageUrl = " + displayPageUrl);
			showPage(displayPageUrl, false);
		// If not means it is a clean start
		} else {
			if (getIntent() != null) {
				Log.i(TAG, "Launch intent = " + getIntent().getAction());
				// If this is a launch through NFC tag reading
                if (getIntent().getAction() != null) {
                    if (getIntent().getAction().equals("android.nfc.action.NDEF_DISCOVERED")) {
                        // Save url which we got from NFC tag
                        nfcTagData = getIntent().getDataString();
                    }
                }
			}
			// If we are in demo mode, ignore all settings and use demo url from strings
			if (settings.getBoolean("default_openhab_demomode", false)) {
				openHABBaseUrl = getString(R.string.openhab_demo_url);
				Log.i(TAG, "Demo mode, connecting to " + openHABBaseUrl);
				Toast.makeText(getApplicationContext(), getString(R.string.info_demo_mode),
					Toast.LENGTH_LONG).show();
				showTime();
			} else {
				openHABBaseUrl = normalizeUrl(settings.getString("default_openhab_url", ""));
				// Check if we have a direct URL in preferences, if yes - use it
				if (openHABBaseUrl.length() > 0) {
					Log.i(TAG, "Connecting to configured URL = " + openHABBaseUrl);
					Toast.makeText(getApplicationContext(), getString(R.string.info_conn_url),
							Toast.LENGTH_SHORT).show();
					showTime();
				} else {
					// Get current network information
					ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(
							Context.CONNECTIVITY_SERVICE);
					NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
					if (activeNetworkInfo != null) {
						Log.i(TAG, "Network is connected");
						// If network is mobile, try to use remote URL
						if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE || serviceDiscoveryEnabled == false) {
							if (!serviceDiscoveryEnabled) {
								Log.i(TAG, "openHAB discovery is disabled");
							} else {
								Log.i(TAG, "Network is Mobile (" + activeNetworkInfo.getSubtypeName() + ")");
							}
							openHABBaseUrl = normalizeUrl(settings.getString("default_openhab_alturl", ""));
							// If remote URL is configured
							if (openHABBaseUrl.length() > 0) {
								Toast.makeText(getApplicationContext(), getString(R.string.info_conn_rem_url),
										Toast.LENGTH_SHORT).show();
								Log.i(TAG, "Connecting to remote URL " + openHABBaseUrl);
								showTime();
							} else {
								Toast.makeText(getApplicationContext(), getString(R.string.error_no_url),
										Toast.LENGTH_LONG).show();		
							}
						// If network is WiFi or Ethernet
						} if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI
								|| activeNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
							Log.i(TAG, "Network is WiFi or Ethernet");
							// Start service discovery
							this.serviceResolver = new AsyncServiceResolver(this, openHABServiceType);
							progressDialog = ProgressDialog.show(this, "", 
			                        "Discovering openHAB. Please wait...", true);
							this.serviceResolver.start();
						// We don't know how to handle this network type
						} else {
							Log.i(TAG, "Network type (" + activeNetworkInfo.getTypeName() + ") is unsupported");
						}
					// Network is not available
					} else {
						Log.i(TAG, "Network is not available");
						Toast.makeText(getApplicationContext(), getString(R.string.error_network_not_available),
								Toast.LENGTH_LONG).show();						
					}
				}
			}
		}
	}
	
	// Start openHAB browsing!
	public void showTime() {
		if (openHABBaseUrl != null) {
			openHABWidgetAdapter.setOpenHABBaseUrl(openHABBaseUrl);
			if (nfcTagData.length() > 0) {
				Log.d(TAG, "We have NFC tag data");
				onNfcTag(nfcTagData, false);
			} else {
				selectSitemap(openHABBaseUrl, false);
			}
		} else {
			Log.e(TAG, "No base URL!");
		}
	}

	/**
	 * This method is called by AsyncServiceResolver in case of successful service discovery
	 * to start connection with local openHAB instance
	 */
	public void onServiceResolved(ServiceInfo serviceInfo) {
		Log.i(TAG, "Service resolved: "
                + serviceInfo.getHostAddresses()[0]
                + " port:" + serviceInfo.getPort());
		openHABBaseUrl = "https://" + serviceInfo.getHostAddresses()[0] + ":" +
				String.valueOf(serviceInfo.getPort()) + "/";
//		progressDialog.hide();
		progressDialog.dismiss();
		AsyncHttpClient asyncHttpClient = new MyAsyncHttpClient(this);
		asyncHttpClient.get(openHABBaseUrl + "static/uuid", new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String content) {
				Log.i(TAG, "Got openHAB UUID = " + content);
				SharedPreferences settings = 
						PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
				if (settings.contains("openhab_uuid")) {
					String openHABUUID = settings.getString("openhab_uuid", "");
					if (openHABUUID.equals(content)) {
						Log.i(TAG, "openHAB UUID does match the saved one");
						showTime();
					} else {
						Log.i(TAG, "openHAB UUID doesn't match the saved one");
						// TODO: need to add some user prompt here
/*						Toast.makeText(getApplicationContext(), 
								"openHAB UUID doesn't match the saved one!",
								Toast.LENGTH_LONG).show();*/
						showTime();
					}
				} else {
					Log.i(TAG, "No recorded openHAB UUID, saving the new one");
					Editor preferencesEditor = settings.edit();
					preferencesEditor.putString("openhab_uuid", content);
					preferencesEditor.commit();
					showTime();
				}
			}
			@Override
		    public void onFailure(Throwable e, String errorResponse) {
				Toast.makeText(getApplicationContext(), getString(R.string.error_no_uuid),
						Toast.LENGTH_LONG).show();
			}
		});
	}

	/**
	 * This method is called by AsyncServiceResolver in case of discovery failure
	 * to start alternate connection through remote url (if configured)
	 */
	public void onServiceResolveFailed() {
		if (progressDialog.isShowing())
			progressDialog.dismiss();
		Log.i(TAG, "Service resolve failed, switching to remote URL");
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		openHABBaseUrl = normalizeUrl(settings.getString("default_openhab_alturl", ""));
		// If remote URL is configured
		if (openHABBaseUrl.length() > 0) {
			Toast.makeText(getApplicationContext(), getString(R.string.info_conn_rem_url),
					Toast.LENGTH_SHORT).show();
			Log.i(TAG, "Connecting to remote URL " + openHABBaseUrl);
			showTime();
		} else {
			Toast.makeText(getApplicationContext(), getString(R.string.error_no_url),
					Toast.LENGTH_LONG).show();		
		}
	}
	
	/**
	 * This method is called when activity receives a new intent while running
	 */
	@Override
	public void onNewIntent(Intent newIntent) {
		Log.d(TAG, "New intent received = " + newIntent.toString());
		if (newIntent.getDataString() != null) {
			onNfcTag(newIntent.getDataString(), true);
		}
	}
	
	/**
	 * This method processes new intents generated by NFC subsystem
	 * @param nfcData - a data which NFC subsystem got from the NFC tag
	 * @param pushCurrentToStack
	 */
	public void onNfcTag(String nfcData, boolean pushCurrentToStack) {
		Uri openHABURI = Uri.parse(nfcData);
		Log.d(TAG, openHABURI.getScheme());
		Log.d(TAG, openHABURI.getHost());
		Log.d(TAG, openHABURI.getPath());
		if (openHABURI.getHost().equals("sitemaps")) {
			Log.d(TAG, "Tag indicates a sitemap link");
			String newPageUrl = this.openHABBaseUrl + "rest/sitemaps" + openHABURI.getPath();
			String widgetId = openHABURI.getQueryParameter("widget");
			String command = openHABURI.getQueryParameter("command");
			Log.d(TAG, "widgetId = " + widgetId);
			Log.d(TAG, "command = " + command);
			if (widgetId != null && command != null) {
				this.nfcWidgetId = widgetId;
				this.nfcCommand = command;
				// If we have widget+command and not pushing current page to stack
				// this means we started through NFC read when HABDroid was not running
				// so we need to put a flag to automatically close activity after we
				// finish nfc action
				if (!pushCurrentToStack)
					this.nfcAutoClose = true;
			}
			Log.d(TAG, "Should go to " + newPageUrl);
			if (pushCurrentToStack)
				navigateToPage(newPageUrl, "");
			else
				openSitemap(newPageUrl);
		}		
	}

	/**
	 * This method is called when activity is being resumed after onPause()
	 */
	@Override
	public void onResume() {
		Log.d(TAG, "onResume()");
		Log.d(TAG, "displayPageUrl = " + this.displayPageUrl);
		super.onResume();
		PendingIntent pendingIntent = PendingIntent.getActivity(
				  this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		if (NfcAdapter.getDefaultAdapter(this) != null)
			NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(this, pendingIntent, null, null);
		if (this.displayPageUrl.length() > 0) {
			Log.d(TAG, "displayPageUrl > 0, resuming");
			showPage(displayPageUrl, false);
		}
	}

	/**
	 * This method is called when activity is paused
	 */
	@Override
	public void onPause() {
		Log.d(TAG, "onPause()");
		super.onPause();
        if (NfcAdapter.getDefaultAdapter(this) != null)
            NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);
		openHABWidgetAdapter.stopVideoWidgets();
		openHABWidgetAdapter.stopImageRefresh();
		if(NfcAdapter.getDefaultAdapter(this) != null)
			NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);
		if (pageAsyncHttpClient != null)
			pageAsyncHttpClient.cancelRequests(this, true);
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		Log.d("OpenHABWidgetListActivity", "onSaveInstanceState");

	  // Save UI state changes to the savedInstanceState.
	  // This bundle will be passed to onCreate if the process is
	  // killed and restarted.
	  savedInstanceState.putString("displayPageUrl", displayPageUrl);
	  savedInstanceState.putString("openHABBaseUrl", openHABBaseUrl);
	  savedInstanceState.putString("sitemapRootUrl", sitemapRootUrl);
	  super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy() for " + this.displayPageUrl);
		if (this.progressDialog != null)
			this.progressDialog.dismiss();
		if (this.serviceResolver != null)
			this.serviceResolver.interrupt();
		if (pageAsyncHttpClient != null)
			pageAsyncHttpClient.cancelRequests(this, true);
	}
	
	/**
	 * This method is called when activity is finished
	 * We need to intercept it to override transition animation according to application
	 * settings
	 */
	@Override
	public void finish() {
		super.finish();
		Util.overridePendingTransition(this, true);		
	}
	
	/**
	 * This method is called when a daugther activity is finished
	 * we need to analyze return code
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "onActivityResult " + String.valueOf(requestCode) + " " + String.valueOf(resultCode));
		if (resultCode == -1) {
			// Right now only PreferencesActivity returns -1
			// Restart app after preferences
			Log.d(TAG, "Restarting");
			// Get launch intent for application
			Intent restartIntent = getBaseContext().getPackageManager()
		             .getLaunchIntentForPackage( getBaseContext().getPackageName() );
			restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			// Finish current activity
			finish();
			// Start launch activity
			startActivity(restartIntent);
		}
	}

	/**
     * Loads data from sitemap page URL and passes it to processContent
     *
     * @param  pageUrl  an absolute base URL of openHAB sitemap page
     * @param  longPolling  enable long polling when loading page
     * @return      void
     */
	public void showPage(String pageUrl, boolean longPolling) {
		Log.i(TAG, "showPage for " + pageUrl + " longPolling = " + longPolling);
		// Cancel any existing http request to openHAB (typically ongoing long poll)
		if (!longPolling)
			setProgressBarIndeterminateVisibility(true);
		if (pageAsyncHttpClient != null) {
			pageAsyncHttpClient.cancelRequests(this, true);
		}
		if (!longPolling) {
			pageAsyncHttpClient = null;
			pageAsyncHttpClient = new MyAsyncHttpClient(this);
		}
		// If authentication is needed
		pageAsyncHttpClient.setBasicAuth(openHABUsername, openHABPassword);
		// If long-polling is needed
		if (longPolling) {
			// Add corresponding fields to header to make openHAB know we need long-polling
			pageAsyncHttpClient.addHeader("X-Atmosphere-Transport", "long-polling");
			pageAsyncHttpClient.addHeader("Accept", "application/xml");
			pageAsyncHttpClient.setTimeout(30000);
		}
		pageAsyncHttpClient.get(this, pageUrl, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String content) {
				processContent(content);
			}
			@Override
		     public void onFailure(Throwable e, String errorResponse) {
				Log.e(TAG, "http request failed");
				if (e.getMessage() != null) {
					Log.e(TAG, e.getMessage());
					if (e.getMessage().equals("Unauthorized")) {
						showAlertDialog(getString(R.string.error_authentication_failed));
					}
				}
				stopProgressIndicator();
		     }
		});
	}

	/**
     * Parse XML sitemap page and show it
     *
     * @param  content	XML as a text
     * @return      void
     */
	public void processContent(String content) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document;
			openHABWidgetAdapter.stopVideoWidgets();
			openHABWidgetAdapter.stopImageRefresh();
			// TODO: fix crash with null content
			if (content != null) {
				document = builder.parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
			} else {
				Log.e(TAG, "processContent: content == null");
				return;
			}
			Node rootNode = document.getFirstChild();
			openHABWidgetDataSource.setSourceNode(rootNode);
			widgetList.clear();
			// As we change the page we need to stop all videos on current page
			// before going to the new page. This is quite dirty, but is the only
			// way to do that...
			for (OpenHABWidget w : openHABWidgetDataSource.getWidgets()) {
				widgetList.add(w);
			}
			openHABWidgetAdapter.notifyDataSetChanged();
			setTitle(openHABWidgetDataSource.getTitle());
			setProgressBarIndeterminateVisibility(false);
			// Set widget list index to saved or zero position
			// This would mean we got widget and command from nfc tag, so we need to do some automatic actions!
			if (this.nfcWidgetId != null && this.nfcCommand != null) {
				Log.d(TAG, "Have widget and command, NFC action!");
				OpenHABWidget nfcWidget = this.openHABWidgetDataSource.getWidgetById(this.nfcWidgetId);
				OpenHABItem nfcItem = nfcWidget.getItem();
				// Found widget with id from nfc tag and it has an item
				if (nfcWidget != null && nfcItem != null) {
					// TODO: Perform nfc widget action here
					if (this.nfcCommand.equals("TOGGLE")) {
						if (nfcItem.getType().equals("RollershutterItem")) {
							if (nfcItem.getStateAsBoolean())
								this.openHABWidgetAdapter.sendItemCommand(nfcItem, "UP");
							else
								this.openHABWidgetAdapter.sendItemCommand(nfcItem, "DOWN");							
						} else {
							if (nfcItem.getStateAsBoolean())
								this.openHABWidgetAdapter.sendItemCommand(nfcItem, "OFF");
							else
								this.openHABWidgetAdapter.sendItemCommand(nfcItem, "ON");
						}
					} else {
						this.openHABWidgetAdapter.sendItemCommand(nfcItem, this.nfcCommand);
					}
				}
				this.nfcWidgetId = null;
				this.nfcCommand = null;
				if (this.nfcAutoClose) {
					finish();
				}
			}
			getListView().setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View view, int position,
						long id) {
					Log.d(TAG, "Widget clicked " + String.valueOf(position));
					OpenHABWidget openHABWidget = openHABWidgetAdapter.getItem(position);
					if (openHABWidget.hasLinkedPage()) {
						// Widget have a page linked to it
                        String[] splitString;
                        splitString = openHABWidget.getLinkedPage().getTitle().split("\\[|\\]");
                        navigateToPage(openHABWidget.getLinkedPage().getLink(), splitString[0]);
					}
				}
				
			});
			getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
				public boolean onItemLongClick(AdapterView<?> parent, View view,
						int position, long id) {
					Log.d(TAG, "Widget long-clicked " + String.valueOf(position));
					OpenHABWidget openHABWidget = openHABWidgetAdapter.getItem(position);
					Log.d(TAG, "Widget type = " + openHABWidget.getType());
					if (openHABWidget.getType().equals("Switch") || openHABWidget.getType().equals("Selection") ||
							openHABWidget.getType().equals("Colorpicker")) {
						OpenHABWidgetListActivity.this.selectedOpenHABWidget = openHABWidget;
						AlertDialog.Builder builder = new AlertDialog.Builder(OpenHABWidgetListActivity.this);
						builder.setTitle(R.string.nfc_dialog_title);
						OpenHABNFCActionList nfcActionList = new OpenHABNFCActionList(OpenHABWidgetListActivity.this.selectedOpenHABWidget);
						builder.setItems(nfcActionList.getNames(), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
					            Intent writeTagIntent = new Intent(OpenHABWidgetListActivity.this.getApplicationContext(),
					            		OpenHABWriteTagActivity.class);
					            writeTagIntent.putExtra("sitemapPage", OpenHABWidgetListActivity.this.displayPageUrl);
					            writeTagIntent.putExtra("widget", OpenHABWidgetListActivity.this.selectedOpenHABWidget.getId());
					            OpenHABNFCActionList nfcActionList = 
					            	new OpenHABNFCActionList(OpenHABWidgetListActivity.this.selectedOpenHABWidget);
					            writeTagIntent.putExtra("command", nfcActionList.getCommands()[which]);
					            startActivityForResult(writeTagIntent, 0);
					            Util.overridePendingTransition(OpenHABWidgetListActivity.this, false);
					            OpenHABWidgetListActivity.this.selectedOpenHABWidget = null;
							}
						});
						builder.show();
						return true;
					}
					return true;
				}				
			});
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		showPage(displayPageUrl, true);
	}

	/*
	 * Put current page and current widget list position into the stack and go to new page
	 */
	private void navigateToPage(String pageLink, String pageTitle) {
		// We don't want to put current page to stack if navigateToPage is trying to go to the same page
		if (!pageLink.equals(displayPageUrl)) {
            Intent drillDownIntent = new Intent(OpenHABWidgetListActivity.this.getApplicationContext(),
            		OpenHABWidgetListActivity.class);
            drillDownIntent.setAction("org.openhab.habdroid.ui.OpwnHABWidgetListActivity");
            drillDownIntent.putExtra("displayPageUrl", pageLink);
            drillDownIntent.putExtra("openHABBaseUrl", openHABBaseUrl);
            drillDownIntent.putExtra("sitemapRootUrl", sitemapRootUrl);
            drillDownIntent.putExtra("pageTitle", pageTitle);
            startActivityForResult(drillDownIntent, 0);
            Util.overridePendingTransition(this, false);
		}
	}

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Log.d(TAG, event.toString());
        Log.d(TAG, String.format("event action = %d", event.getAction()));
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        Log.d(TAG, "keyCode = " + String.format("%d", keyCode));
        Log.d(TAG, "event = " + event.toString());
        if (keyCode == 4) {
            return true;
        } else {
            return super.onKeyLongPress(keyCode, event);
        }
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
            Intent settingsIntent = new Intent(this.getApplicationContext(), OpenHABPreferencesActivity.class);
            Util.overridePendingTransition(this, false);
    		return true;
    	case R.id.mainmenu_openhab_selectsitemap:
			SharedPreferences settings = 
			PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
			Editor preferencesEditor = settings.edit();
			preferencesEditor.putString("default_openhab_sitemap", "");
			preferencesEditor.commit();
    		selectSitemap(openHABBaseUrl, true);
    		return true;
        case android.R.id.home:
            Log.d(TAG, "Home selected - " + sitemapRootUrl);
			// Get launch intent for application
			Intent homeIntent = getBaseContext().getPackageManager()
		             .getLaunchIntentForPackage( getBaseContext().getPackageName() );
			homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			homeIntent.setAction("org.openhab.habdroid.ui.OpwnHABWidgetListActivity");
			homeIntent.putExtra("displayPageUrl", sitemapRootUrl);
			homeIntent.putExtra("openHABBaseUrl", openHABBaseUrl);
			homeIntent.putExtra("sitemapRootUrl", sitemapRootUrl);
			// Finish current activity
			finish();
			// Start launch activity
			startActivity(homeIntent);
			Util.overridePendingTransition(this, true);		
            return true;
        case R.id.mainmenu_openhab_clearcache:
			Log.d(TAG, "Restarting");
			// Get launch intent for application
			Intent restartIntent = getBaseContext().getPackageManager()
		             .getLaunchIntentForPackage( getBaseContext().getPackageName() );
			restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			// Finish current activity
			finish();
        	WebImageCache cache = new WebImageCache(getBaseContext());
        	cache.clear();
			// Start launch activity
			startActivity(restartIntent);
			// Start launch activity
			return true;
        case R.id.mainmenu_openhab_writetag:
            Intent writeTagIntent = new Intent(this.getApplicationContext(), OpenHABWriteTagActivity.class);
            writeTagIntent.putExtra("sitemapPage", this.displayPageUrl);
            startActivityForResult(writeTagIntent, 0);
            Util.overridePendingTransition(this, false);
    		return true;
        default:
    		return super.onOptionsItemSelected(item);
    	}
    }

    /**
     * Get sitemaps from openHAB, if user already configured preffered sitemap
     * just open it. If no preffered sitemap is configured - let user select one.
     *
     * @param  baseURL  an absolute base URL of openHAB to open
     * @return      void
     */

	private void selectSitemap(final String baseURL, final boolean forceSelect) {
		Log.d(TAG, "Loding sitemap list from " + baseURL + "rest/sitemaps");
	    AsyncHttpClient asyncHttpClient = new MyAsyncHttpClient(this);
		// If authentication is needed
	    asyncHttpClient.setBasicAuth(openHABUsername, openHABPassword);
	    asyncHttpClient.get(baseURL + "rest/sitemaps", new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String content) {
				List<OpenHABSitemap> sitemapList = parseSitemapList(content);
				if (sitemapList.size() == 0) {
					// Got an empty sitemap list!
					showAlertDialog(getString(R.string.error_empty_sitemap_list));
					return;
				}
				// If we are forced to do selection, just open selection dialog
				if (forceSelect) {
					showSitemapSelectionDialog(sitemapList);
				} else {
					// Check if we have a sitemap configured to use
					SharedPreferences settings = 
							PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
					String configuredSitemap = settings.getString("default_openhab_sitemap", "");
					// If we have sitemap configured
					if (configuredSitemap.length() > 0) {
						// Configured sitemap is on the list we got, open it!
						if (sitemapExists(sitemapList, configuredSitemap)) {
							Log.d(TAG, "Configured sitemap is on the list");
							OpenHABSitemap selectedSitemap = getSitemapByName(sitemapList, configuredSitemap);
							openSitemap(selectedSitemap.getHomepageLink());
						// Configured sitemap is not on the list we got!
						} else {
							Log.d(TAG, "Configured sitemap is not on the list");
							if (sitemapList.size() == 1) {
								Log.d(TAG, "Got only one sitemap");
								Editor preferencesEditor = settings.edit();
								preferencesEditor.putString("default_openhab_sitemap", sitemapList.get(0).getName());
									preferencesEditor.commit();
								openSitemap(sitemapList.get(0).getHomepageLink());								
							} else {
								Log.d(TAG, "Got multiply sitemaps, user have to select one");
								showSitemapSelectionDialog(sitemapList);
							}
						}
					// No sitemap is configured to use
					} else {
						// We got only one single sitemap from openHAB, use it
						if (sitemapList.size() == 1) {
							Log.d(TAG, "Got only one sitemap");
							Editor preferencesEditor = settings.edit();
							preferencesEditor.putString("default_openhab_sitemap", sitemapList.get(0).getName());
								preferencesEditor.commit();
							openSitemap(sitemapList.get(0).getHomepageLink());
						} else {
							Log.d(TAG, "Got multiply sitemaps, user have to select one");
							showSitemapSelectionDialog(sitemapList);
						}
					}
				}
			}
			@Override
	    	public void onFailure(Throwable e, String errorResponse) {
				if (e.getMessage() != null) {
					if (e.getMessage().equals("Unauthorized")) {
						showAlertDialog(getString(R.string.error_authentication_failed));
					} else {
						showAlertDialog("ERROR: " + e.getMessage());
					}
				} else {
					// TODO: carefully handle errors without message
//					showAlertDialog("ERROR: Http error, no details");
				}
			}
	    });
	}

	private void stopProgressIndicator() {
		setProgressBarIndeterminateVisibility(false);
	}

	private boolean sitemapExists(List<OpenHABSitemap> sitemapList, String sitemapName) {
		for (int i=0; i<sitemapList.size(); i++) {
			if (sitemapList.get(i).getName().equals(sitemapName))
				return true;
		}
		return false;
	}
	
	private OpenHABSitemap getSitemapByName(List<OpenHABSitemap> sitemapList, String sitemapName) {
		for (int i=0; i<sitemapList.size(); i++) {
			if (sitemapList.get(i).getName().equals(sitemapName))
				return sitemapList.get(i);
		}
		return null;
	}
	
	private List<OpenHABSitemap> parseSitemapList(String xmlContent) {
		List<OpenHABSitemap> sitemapList = new ArrayList<OpenHABSitemap>();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		Crittercism.leaveBreadcrumb("parseSitemapList");
		try {
			builder = factory.newDocumentBuilder();
			Document document;
			document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));
			NodeList sitemapNodes = document.getElementsByTagName("sitemap");
			if (sitemapNodes.getLength() > 0) {
				for (int i=0; i < sitemapNodes.getLength(); i++) {
					Node sitemapNode = sitemapNodes.item(i);
					OpenHABSitemap openhabSitemap = new OpenHABSitemap(sitemapNode);
					sitemapList.add(openhabSitemap);
				}
			}
		} catch (ParserConfigurationException e) {
			Log.e(TAG, e.getMessage());
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, e.getMessage());
		} catch (SAXException e) {
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		return sitemapList;
	}
	
	private void showAlertDialog(String alertMessage) {
		AlertDialog.Builder builder = new AlertDialog.Builder(OpenHABWidgetListActivity.this);
		builder.setMessage(alertMessage)
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
				}
		});
		AlertDialog alert = builder.create();
		alert.show();		
	}
	
	private void showSitemapSelectionDialog(final List<OpenHABSitemap> sitemapList) {
		Log.d(TAG, "Opening sitemap selection dialog");
		final List<String> sitemapNameList = new ArrayList<String>();;
		for (int i=0; i<sitemapList.size(); i++) {
			sitemapNameList.add(sitemapList.get(i).getName());
		}
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(OpenHABWidgetListActivity.this);
		dialogBuilder.setTitle("Select sitemap");
		try {
		dialogBuilder.setItems(sitemapNameList.toArray(new CharSequence[sitemapNameList.size()]),
			new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					Log.d(TAG, "Selected sitemap " + sitemapNameList.get(item));
					SharedPreferences settings = 
						PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
					Editor preferencesEditor = settings.edit();
					preferencesEditor.putString("default_openhab_sitemap", sitemapList.get(item).getName());
						preferencesEditor.commit();
					openSitemap(sitemapList.get(item).getHomepageLink());
				}
			}).show();
		} catch (BadTokenException e) {
			Crittercism.logHandledException(e);
		}
	}
	
	private void openSitemap(String sitemapUrl) {
		Log.i(TAG, "Opening sitemap at " + sitemapUrl);
		displayPageUrl = sitemapUrl;
		sitemapRootUrl = sitemapUrl;
		showPage(displayPageUrl, false);
    	OpenHABWidgetListActivity.this.getListView().setSelection(0);		
	}

	private String normalizeUrl(String sourceUrl) {
		String normalizedUrl = "";
		try {
			URL url = new URL(sourceUrl);
			normalizedUrl = url.toString();
			normalizedUrl = normalizedUrl.replace("\n", "");
			normalizedUrl = normalizedUrl.replace(" ", "");
			if (!normalizedUrl.endsWith("/"))
				normalizedUrl = normalizedUrl + "/";
		} catch (MalformedURLException e) {
			Log.d(TAG, "normalizeUrl: invalid URL");
		}
		return normalizedUrl;
	}
	
}
