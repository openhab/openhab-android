package org.openhab.habdroid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.view.WearableListView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;

import org.openhab.habdroid.adapter.OpenHABWearWidgetAdapter;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.service.GetRemoteDataAsync;
import org.openhab.habdroid.service.GoogleApiService;
import org.openhab.habdroid.util.SharedConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class WearMainActivity extends Activity implements WearableListView.ClickListener, DataApi.DataListener, Observer {

    private static final String TAG = WearMainActivity.class.getSimpleName();

    private static String mSitemapName;

    private static String mSitemapLink;
    private static String mCurrentSitemapLinkToWaitFor;
    private TextView mTextView;
    private WearableListView mListView;
    private OpenHABWearWidgetAdapter mListAdapter;
    private OpenHABWidgetDataSource mOpenHABWidgetDataSource;
    private List<OpenHABWidget> mWidgetList = new ArrayList<OpenHABWidget>();
    private GoogleApiService mGoogleApiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        initGoogleApiClient();

        mOpenHABWidgetDataSource = new OpenHABWidgetDataSource();

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mListView = (WearableListView) stub.findViewById(R.id.listView);
                mListAdapter = new OpenHABWearWidgetAdapter(WearMainActivity.this, mWidgetList);
                mListView.setAdapter(mListAdapter);
                mListView.setClickListener(WearMainActivity.this);
            }
        });

        getDataFromMap();
    }

    private void initGoogleApiClient() {
        mGoogleApiService = new GoogleApiService(getApplicationContext());
    }

    private void getDataFromMap() {
        Log.d(TAG, "Getting data from map async");
        new GetDataAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mGoogleApiService.isConnected()) {
            mGoogleApiService.connect();
        }
        mGoogleApiService.addObserver(this);
    }

    private void processSitemap(String sitemap) {
        Log.d(TAG, "Processing sitemap");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(sitemap)));
            if (document != null) {
                Node rootNode = document.getFirstChild();
                mOpenHABWidgetDataSource.setSourceNode(rootNode);
                mWidgetList.clear();
                for (OpenHABWidget w : mOpenHABWidgetDataSource.getWidgets()) {
                    // Remove frame widgets with no label text
                    if (w.getType().equals("Frame") && TextUtils.isEmpty(w.getLabel()))
                        continue;
                    mWidgetList.add(w);
                }
            } else {
                Log.e(TAG, "Got a null response from openHAB");
            }
            mListAdapter.notifyDataSetChanged();
            mTextView.setVisibility(View.GONE);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "ParserConfig", e);
        } catch (SAXException e) {
            Log.e(TAG, "SAXException", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGoogleApiService.removeListener(this);
        mGoogleApiService.disconnect();
        mGoogleApiService.deleteObserver(this);
    }

    @Override
    public void update(Observable observable, Object data) {
        if (data instanceof String) {
            String what = (String) data;
            if ("CONNECTED".equals(what)) {
                mGoogleApiService.addListener(this);
            }
        }
    }

    @Override
    public void onClick(WearableListView.ViewHolder viewHolder) {
        Log.d(TAG, "Clicked an element at position " + viewHolder.getPosition());
        OpenHABWidget clickedWidget = mWidgetList.get(viewHolder.getPosition());
        Log.d(TAG, "Clicked the widget " + clickedWidget);
        if (clickedWidget.getType().equals("Frame") || clickedWidget.getType().equals("Group")) {
            Log.d(TAG, "Clicked on frame or group");
            OpenHABLinkedPage linkedPage = clickedWidget.getLinkedPage();
            if (linkedPage != null) {
                Log.d(TAG, "Linked page url " + linkedPage.getLink());
                checkDataForUrl(linkedPage.getLink());
            } else {
                Log.i(TAG, "Linked page on widget is null");
            }
        } else {
            Log.d(TAG, "Widget is of type " + clickedWidget.getType());
        }
    }

    private void checkDataForUrl(String url) {
        Log.d(TAG, "Checking if we have data for the url " + url);
        if (url != null) {
            new GetSiteDataAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
        } else {
            Log.d(TAG, "Error url must not be null");
        }
    }

    @Override
    public void onTopEmptyRegionClick() {
        Log.d(TAG, "Top Empty Region click");
    }

    /**
     * @param dataEvents
     */
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "Data changed");
        if (dataEvents != null) {
            for (DataEvent event : dataEvents) {
                final DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Log.d(TAG, "Changed for " + dataMapItem.getUri());
                if (event.getDataItem().getUri().getPath().endsWith(SharedConstants.DataMapUrl.SITEMAP_DETAILS.value())) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {

                        public void run() {
                            processDataMapItem(dataMapItem);
                        }
                    });
                } else if (event.getDataItem().getUri().toString().endsWith(SharedConstants.DataMapUrl.SITEMAP_BASE.value())) {
                    mSitemapName = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_NAME.name());
                    mSitemapLink = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
                }
            }
        }
    }

    private void processDataMapItem(DataMapItem dataMapItem) {
        DataMap map = dataMapItem.getDataMap();
        Log.d(TAG, "Got DataMapItem: " + map);
        String sitemapXML = map.getString(SharedConstants.DataMapKey.SITEMAP_XML.name());
        String thisSitemapLink = map.getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
        Log.d(TAG, "Got Sitemap XML for '" + thisSitemapLink + "'");
        if (thisSitemapLink.equals(mSitemapLink)) {
            processSitemap(sitemapXML);
        } else if (mCurrentSitemapLinkToWaitFor != null && thisSitemapLink.equals(mCurrentSitemapLinkToWaitFor)) {
            openSublist(sitemapXML);
        } else {
            Log.d(TAG, thisSitemapLink + " does not match " + mSitemapLink + " -> thus no setup");
        }
    }


    class GetDataAsync extends AsyncTask<Void, Void, DataMapItem> {

        @Override
        protected DataMapItem doInBackground(Void... params) {
            while (!mGoogleApiService.isConnected()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted");
                }
            }
            Log.d(TAG, "Connected -> now check data items");
            boolean foundBaseValues = false;
            List<Uri> uris = mGoogleApiService.getUriForDataItem(SharedConstants.DataMapUrl.SITEMAP_BASE.value());
            PendingResult<DataItemBuffer> pendingResult;
            int count;
            DataItemBuffer dataItem;
            for (Uri uri : uris) {
                pendingResult = mGoogleApiService.getDataItems(uri);
                dataItem = pendingResult.await(5, TimeUnit.SECONDS);
                count = dataItem.getCount();
                Log.d(TAG, "Found '" + count + "' items for sitemap_base");
                if (count > 0) {
                    for (int i = 0; i < dataItem.getCount(); i++) {
                        DataItem item = dataItem.get(i);
                        Log.d(TAG, "DataItemUri: " + item.getUri());
                        final DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
                        if (item.getUri().toString().endsWith(SharedConstants.DataMapUrl.SITEMAP_BASE.value())) {
                            Log.d(TAG, "Got base values");
                            mSitemapName = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_NAME.name());
                            mSitemapLink = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
                            foundBaseValues = true;
                            continue;
                        }
                    }
                }
            }
            if (!foundBaseValues) {
                pendingResult = mGoogleApiService.getDataItems();
                dataItem = pendingResult.await(5, TimeUnit.SECONDS);
                count = dataItem.getCount();
                for (int i = 0; i < count; i++) {
                    DataItem item = dataItem.get(i);
                    Log.d(TAG, "Item uri for base " + item.getUri());
                    if (item.getUri().toString().endsWith(SharedConstants.DataMapUrl.SITEMAP_BASE.value())) {
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
                        mSitemapName = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_NAME.name());
                        mSitemapLink = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
                        foundBaseValues = true;
                    }
                }
            }
            if (foundBaseValues) {
                uris = mGoogleApiService.getUriForDataItem("/" + mSitemapLink.hashCode() + SharedConstants.DataMapUrl.SITEMAP_DETAILS.value());
                for (Uri uri : uris) {
                    DataMapItem mapItemToReturn = mGoogleApiService.getDataItemForUri(uri, mSitemapLink);
                    if (mapItemToReturn != null) {
                        return mapItemToReturn;
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(DataMapItem dataMapItem) {
            if (dataMapItem == null) {
                mTextView.setText("Es wurde noch keine Sitemap geladen... bitte auf dem Handy korrekt einrichten");
                mTextView.setVisibility(View.VISIBLE);
            } else {
                processDataMapItem(dataMapItem);
            }
        }
    }

    class GetSiteDataAsync extends AsyncTask<String, Void, DataMapItem> {

        private String mCurrentLink;

        @Override
        protected DataMapItem doInBackground(String... params) {
            if (params.length > 0) {
                mCurrentLink = params[0];
            } else {
                return null;
            }
            String uriValueToCheck = "/" + mCurrentLink.hashCode() + SharedConstants.DataMapUrl.SITEMAP_DETAILS.value();
            Log.d(TAG, "Async checking for " + uriValueToCheck);
            List<Uri> uris = mGoogleApiService.getUriForDataItem(uriValueToCheck);
            for (Uri uri : uris) {
                DataMapItem mapItemToReturn = mGoogleApiService.getDataItemForUri(uri, mCurrentLink);
                if (mapItemToReturn != null) {
                    return mapItemToReturn;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(DataMapItem dataMapItem) {
            if (dataMapItem == null) {
                Log.d(TAG, "Do not have data for this link " + mCurrentLink);
                mCurrentSitemapLinkToWaitFor = mCurrentLink;
                new GetRemoteDataAsync(mGoogleApiService).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mCurrentLink);
            } else {
                Log.d(TAG, "Already have the data for the link " + mCurrentLink);
                String sitemapXml = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_XML.name());
                openSublist(sitemapXml);
            }
        }
    }

    private void openSublist(String sitemapXml) {
        Bundle data = new Bundle();
        data.putString(SharedConstants.DataMapKey.SITEMAP_XML.name(), sitemapXml);
        Intent intent = new Intent(WearMainActivity.this, SublistActivity.class);
        intent.putExtras(data);
        startActivity(intent);
    }
}
