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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

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
	// async http client
	private AsyncHttpClient pageAsyncHttpClient;
	// Sitemap pages stack for digging in and getting back
	private ArrayList<String> pageUrlStack = new ArrayList<String>();
	// openHAB base url
	private String openHABBaseUrl = "http://demo.openhab.org:8080/";
	// List of widgets to display
	private ArrayList<OpenHABWidget> widgetList = new ArrayList<OpenHABWidget>();
	// Username/password for authentication
	private String openHABUsername;
	private String openHABPassword;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO: Make progress indicator active every time we load the page
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.openhabwidgetlist);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		openHABUsername = settings.getString("default_openhab_username", null);
		openHABPassword = settings.getString("default_openhab_password", null);
		openHABWidgetDataSource = new OpenHABWidgetDataSource();
		openHABWidgetAdapter = new OpenHABWidgetAdapter(OpenHABWidgetListActivity.this,
				R.layout.openhabwidgetlist_genericitem, widgetList);
		getListView().setAdapter(openHABWidgetAdapter);
		openHABWidgetAdapter.setOpenHABUsername(openHABUsername);
		openHABWidgetAdapter.setOpenHABPassword(openHABPassword);
		// Check if we have openHAB page url in saved instance state?
		if (savedInstanceState != null) {
			displayPageUrl = savedInstanceState.getString("displayPageUrl");
			pageUrlStack = savedInstanceState.getStringArrayList("pageUrlStack");
		}
		// If yes, then just show it
		if (displayPageUrl.length() > 0) {
			Log.i(TAG, "displayPageUrl = " + displayPageUrl);
			showPage(displayPageUrl, false);
		// Else check if we got openHAB base url through launch intent?
		} else  {
			openHABBaseUrl = getIntent().getExtras().getString("baseURL");
			if (openHABBaseUrl != null) {
				openHABWidgetAdapter.setOpenHABBaseUrl(openHABBaseUrl);
				startRootPage(openHABBaseUrl + "rest/sitemaps/");
			} else {
				Log.i(TAG, "No base URL!");
			}
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
	  // Save UI state changes to the savedInstanceState.
	  // This bundle will be passed to onCreate if the process is
	  // killed and restarted.
	  savedInstanceState.putString("displayPageUrl", displayPageUrl);
	  savedInstanceState.putStringArrayList("pageUrlStack", pageUrlStack);
	  super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy() for " + this.displayPageUrl);
		if (pageAsyncHttpClient != null)
			pageAsyncHttpClient.cancelRequests(this, true);
		// release multicast lock for mDNS
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
		if (pageAsyncHttpClient != null) {
			pageAsyncHttpClient.cancelRequests(this, true);
		}
		pageAsyncHttpClient = new AsyncHttpClient();
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
					Toast.makeText(getApplicationContext(), "Authentication failed",
							Toast.LENGTH_LONG).show();
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
			document = builder.parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
			Node rootNode = document.getFirstChild();
			openHABWidgetDataSource.setSourceNode(rootNode);
			widgetList.clear();
			for (OpenHABWidget w : openHABWidgetDataSource.getWidgets()) {
				widgetList.add(w);
			}
			openHABWidgetAdapter.notifyDataSetChanged();
			setTitle(openHABWidgetDataSource.getTitle());
			setProgressBarIndeterminateVisibility(false);
			getListView().setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position,
						long id) {
					Log.i(TAG, "Widget clicked " + String.valueOf(position));
					OpenHABWidget openHABWidget = openHABWidgetAdapter.getItem(position);
					if (openHABWidget.hasLinkedPage()) {
						// Widget have a page linked to it
						// Put current page into the stack and go to linked one
						pageUrlStack.add(0, displayPageUrl);
						displayPageUrl = openHABWidget.getLinkedPage().getLink();
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
    		if (pageUrlStack.size() > 0) {
    			displayPageUrl = pageUrlStack.get(0);
    			pageUrlStack.remove(0);
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
     * Get sitemaps from openHAB, select first sitemap and start
     * viewing it 
     *
     * @param  baseUrl  an absolute base URL of openHAB to open
     * @return      void
     */
    void startRootPage(String baseUrl) {
    	Log.i(TAG, "Starting root page for " + baseUrl);
    	AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
		// If authentication is needed
    	asyncHttpClient.setBasicAuthCredientidals(openHABUsername, openHABPassword);
    	asyncHttpClient.get(baseUrl, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String content) {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder;
				try {
					builder = factory.newDocumentBuilder();
					Document document;
					document = builder.parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
					NodeList homepageNodes = document.getElementsByTagName("homepage");
					if (homepageNodes.getLength() > 0) {
						NodeList homepageAttributeNodes = homepageNodes.item(0).getChildNodes();
						for (int i=0; i < homepageAttributeNodes.getLength(); i++) {
							if (homepageAttributeNodes.item(i).getNodeName().equals("link")) {
								String homepageUrl = homepageAttributeNodes.item(i).getTextContent();
								if (homepageUrl.length() > 0) {
									displayPageUrl = homepageUrl;
									showPage(homepageUrl, false);
								}
							}
						}
					}
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
			}
			@Override
		    public void onFailure(Throwable e) {
				Log.i(TAG, "http request failed");
				if (e.getMessage() != null) {
					Log.e(TAG, e.getMessage());
					if (e.getMessage().equals("Unauthorized")) {
					Toast.makeText(getApplicationContext(), "Authentication failed",
							Toast.LENGTH_LONG).show();
					}
				}
				stopProgressIndicator();
		    }
		});
    }
    
	private void stopProgressIndicator() {
		setProgressBarIndeterminateVisibility(false);
	}

}
