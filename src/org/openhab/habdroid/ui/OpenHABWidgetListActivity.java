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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABPage;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.image.WebImageCache;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This class provides app activity which displays list of openHAB
 * widgets from sitemap page
 * 
 * @author Victor Belov
 *
 */

public class OpenHABWidgetListActivity extends ListActivity {
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
	private AsyncHttpClient pageAsyncHttpClient;
	// Sitemap pages stack for digging in and getting back
	private ArrayList<OpenHABPage> pageStack = new ArrayList<OpenHABPage>();
	// openHAB base url
	private String openHABBaseUrl = "https://demo.openhab.org:8443/";
	// List of widgets to display
	private ArrayList<OpenHABWidget> widgetList = new ArrayList<OpenHABWidget>();
	// Username/password for authentication
	private String openHABUsername;
	private String openHABPassword;
	// Wiget list position
	private int widgetListPosition = -1;
	private NfcAdapter nfcAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i("OpenHABWidgetListActivity", "onCreate");
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		setProgressBarIndeterminateVisibility(true);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.openhabwidgetlist);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (settings.getBoolean("default_openhab_screentimeroff", false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		openHABUsername = settings.getString("default_openhab_username", null);
		openHABPassword = settings.getString("default_openhab_password", null);
		openHABWidgetDataSource = new OpenHABWidgetDataSource();
		openHABWidgetAdapter = new OpenHABWidgetAdapter(OpenHABWidgetListActivity.this,
				R.layout.openhabwidgetlist_genericitem, widgetList);
		getListView().setAdapter(openHABWidgetAdapter);
		openHABWidgetAdapter.setOpenHABUsername(openHABUsername);
		openHABWidgetAdapter.setOpenHABPassword(openHABPassword);
//		this.getActionBar().setDisplayHomeAsUpEnabled(true);
		this.getActionBar().setHomeButtonEnabled(true);
		// Check if we have openHAB page url in saved instance state?
		if (savedInstanceState != null) {
			displayPageUrl = savedInstanceState.getString("displayPageUrl");
			pageStack = savedInstanceState.getParcelableArrayList("pageStack");
			openHABBaseUrl = savedInstanceState.getString("openHABBaseUrl");
			sitemapRootUrl = savedInstanceState.getString("sitemapRootUrl");
			openHABWidgetAdapter.setOpenHABBaseUrl(openHABBaseUrl);
		}
		// If yes, then just show it
		if (displayPageUrl.length() > 0) {
			Log.i(TAG, "displayPageUrl = " + displayPageUrl);
			showPage(displayPageUrl, false);
		// Else check if we got openHAB base url through launch intent?
		} else if (getIntent().hasExtra("baseURL")) {
			openHABBaseUrl = getIntent().getExtras().getString("baseURL");
			if (openHABBaseUrl != null) {
				openHABWidgetAdapter.setOpenHABBaseUrl(openHABBaseUrl);
				selectSitemap(openHABBaseUrl, false);
			} else {
				Log.i(TAG, "No base URL!");
			}
		}
	}
	
	@Override
	public void onNewIntent(Intent newIntent) {
		Log.i(TAG, "New intent received = " + newIntent.toString());
	}
	
	@Override
	public void onResume() {
		super.onResume();
		PendingIntent pendingIntent = PendingIntent.getActivity(
				  this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		Log.i("OpenHABWidgetListActivity", "onSaveInstanceState");

	  // Save UI state changes to the savedInstanceState.
	  // This bundle will be passed to onCreate if the process is
	  // killed and restarted.
	  savedInstanceState.putString("displayPageUrl", displayPageUrl);
	  savedInstanceState.putParcelableArrayList("pageStack", pageStack);
	  savedInstanceState.putString("openHABBaseUrl", openHABBaseUrl);
	  savedInstanceState.putString("sitemapRootUrl", sitemapRootUrl);
	  super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy() for " + this.displayPageUrl);
		if (pageAsyncHttpClient != null)
			pageAsyncHttpClient.cancelRequests(this, true);
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
		if (longPolling)
			widgetListPosition = -1;
		if (pageAsyncHttpClient != null) {
			pageAsyncHttpClient.cancelRequests(this, true);
		}
		pageAsyncHttpClient = new MyAsyncHttpClient();
		// If authentication is needed
		pageAsyncHttpClient.setBasicAuthCredientidals(openHABUsername, openHABPassword);
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
		     public void onFailure(Throwable e) {
				Log.i(TAG, "http request failed");
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
				Log.i(TAG, "processContent: content == null");
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
			if (this.widgetListPosition >= 0)
				getListView().setSelection(this.widgetListPosition);
			getListView().setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position,
						long id) {
					Log.i(TAG, "Widget clicked " + String.valueOf(position));
					OpenHABWidget openHABWidget = openHABWidgetAdapter.getItem(position);
					if (openHABWidget.hasLinkedPage()) {
						// Widget have a page linked to it
						// Put current page and current widget list position into the stack and go to linked one
						pageStack.add(0, new OpenHABPage(displayPageUrl, OpenHABWidgetListActivity.this.getListView().getFirstVisiblePosition()));
						displayPageUrl = openHABWidget.getLinkedPage().getLink();
						widgetListPosition = 0;
						showPage(openHABWidget.getLinkedPage().getLink(), false);
					}
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
		this.widgetListPosition = 0;
		showPage(displayPageUrl, true);
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
    	case R.id.mainmenu_openhab_selectsitemap:
			SharedPreferences settings = 
			PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
			Editor preferencesEditor = settings.edit();
			preferencesEditor.putString("default_openhab_sitemap", "");
			preferencesEditor.commit();
    		selectSitemap(openHABBaseUrl, true);
        case android.R.id.home:
        	displayPageUrl = sitemapRootUrl;
        	// we are navigating to root page, so clear page stack to support regular 'back' behavior for root page
        	pageStack.clear();
            showPage(sitemapRootUrl, false);
            Log.i(TAG, "Home selected - " + sitemapRootUrl);
            return true;
        case R.id.mainmenu_openhab_clearcache:
			Log.i(TAG, "Restarting");
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
        default:
    		return super.onOptionsItemSelected(item);
    	}
    }

    /**
     * We run all openHAB browsing in a single activity, so we need to
     * intercept 'Back' key to get back to previous sitemap page.
     * If no pages in stack - simulate typical android app behaviour -
     * exit application.
     *
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	Log.i(TAG, "keyCode = " + keyCode);
    	if (keyCode == 4) {
    		Log.i(TAG, "This is 'back' key");
    		if (pageStack.size() > 0) {
    			displayPageUrl = pageStack.get(0).getPageUrl();
//    			OpenHABWidgetListActivity.this.setSelection(pageStack.get(0).getWidgetListPosition());
    			Log.i(TAG, String.format("onKeyDown: list position from the stack = %d", pageStack.get(0).getWidgetListPosition()));
    			widgetListPosition = pageStack.get(0).getWidgetListPosition();
    			pageStack.remove(0);
    			showPage(displayPageUrl, false);
    		} else {
    			Log.i(TAG, "No more pages left in stack, exiting");
    			finish();
    		}
    		return true;
    	} else {
    		return super.onKeyDown(keyCode, event);
    	}
    }

    /**
     * Get sitemaps from openHAB, if user already configured preffered sitemap
     * just open it. If no preffered sitemap is configured - let user select one.
     *
     * @param  baseUrl  an absolute base URL of openHAB to open
     * @return      void
     */

	private void selectSitemap(final String baseURL, final boolean forceSelect) {
		Log.i(TAG, "Loding sitemap list from " + baseURL + "rest/sitemaps");
	    AsyncHttpClient asyncHttpClient = new MyAsyncHttpClient();
		// If authentication is needed
	    asyncHttpClient.setBasicAuthCredientidals(openHABUsername, openHABPassword);
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
							Log.i(TAG, "Configured sitemap is on the list");
							OpenHABSitemap selectedSitemap = getSitemapByName(sitemapList, configuredSitemap);
							openSitemap(selectedSitemap.getHomepageLink());
						// Configured sitemap is not on the list we got!
						} else {
							Log.i(TAG, "Configured sitemap is not on the list");
							if (sitemapList.size() == 1) {
								Log.i(TAG, "Got only one sitemap");
								Editor preferencesEditor = settings.edit();
								preferencesEditor.putString("default_openhab_sitemap", sitemapList.get(0).getName());
									preferencesEditor.commit();
								openSitemap(sitemapList.get(0).getHomepageLink());								
							} else {
								Log.i(TAG, "Got multiply sitemaps, user have to select one");
								showSitemapSelectionDialog(sitemapList);
							}
						}
					// No sitemap is configured to use
					} else {
						// We got only one single sitemap from openHAB, use it
						if (sitemapList.size() == 1) {
							Log.i(TAG, "Got only one sitemap");
							Editor preferencesEditor = settings.edit();
							preferencesEditor.putString("default_openhab_sitemap", sitemapList.get(0).getName());
								preferencesEditor.commit();
							openSitemap(sitemapList.get(0).getHomepageLink());
						} else {
							Log.i(TAG, "Got multiply sitemaps, user have to select one");
							showSitemapSelectionDialog(sitemapList);
						}
					}
				}
			}
			@Override
	    	public void onFailure(Throwable e) {
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
		Log.i(TAG, "Opening sitemap selection dialog");
		final List<String> sitemapNameList = new ArrayList<String>();;
		for (int i=0; i<sitemapList.size(); i++) {
			sitemapNameList.add(sitemapList.get(i).getName());
		}
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(OpenHABWidgetListActivity.this);
		dialogBuilder.setTitle("Select sitemap");
		dialogBuilder.setItems(sitemapNameList.toArray(new CharSequence[sitemapNameList.size()]),
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int item) {
					Log.i(TAG, "Selected sitemap " + sitemapNameList.get(item));
					SharedPreferences settings = 
						PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
					Editor preferencesEditor = settings.edit();
					preferencesEditor.putString("default_openhab_sitemap", sitemapList.get(item).getName());
						preferencesEditor.commit();
					openSitemap(sitemapList.get(item).getHomepageLink());
				}
			}).show();
	}
	
	private void openSitemap(String sitemapUrl) {
		Log.i(TAG, "Opening sitemap at " + sitemapUrl);
		displayPageUrl = sitemapUrl;
		sitemapRootUrl = sitemapUrl;
		showPage(displayPageUrl, false);
    	OpenHABWidgetListActivity.this.getListView().setSelection(0);		
	}

}
