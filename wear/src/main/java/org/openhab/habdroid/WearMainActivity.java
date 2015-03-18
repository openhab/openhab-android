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

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.openhab.habdroid.adapter.OpenHABWearWidgetAdapter;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.util.SharedConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class WearMainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener, WearableListView.ClickListener {

    private static final String TAG = WearMainActivity.class.getSimpleName();

    private static String mSitemapName;

    private static String mSitemapLink;

    private TextView mTextView;

    private WearableListView mListView;

    private OpenHABWearWidgetAdapter mListAdapter;

    private GoogleApiClient mGoogleApiClient;

    private OpenHABWidgetDataSource mOpenHABWidgetDataSource;

    private List<OpenHABWidget> mWidgetList = new ArrayList<OpenHABWidget>();

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
        mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                .addApi(Wearable.API)
                .build();
    }

    private void getDataFromMap() {
        Log.d(TAG, "Getting data from map async");
        new GetDataAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected void onResume() {
        super.onStart();
        mGoogleApiClient.connect();
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
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Connected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
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
        } else {
            Log.d(TAG, thisSitemapLink + " does not match " + mSitemapLink + " -> thus no setup");
        }
    }

    private List<String> getLocalNodeId() {
        PendingResult<NodeApi.GetConnectedNodesResult> connectedNodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        NodeApi.GetConnectedNodesResult connectedNodesResult = connectedNodes.await(5, TimeUnit.SECONDS);
        List<String> nodeIds = new ArrayList<String>();
        for (com.google.android.gms.wearable.Node node : connectedNodesResult.getNodes()) {
            Log.d(TAG, "Found node " + node.getId() + " - " + node.getDisplayName());
            nodeIds.add(node.getId());
        }
        return nodeIds;
    }

    private List<Uri> getUriForDataItem(String path) {
        List<Uri> result = new ArrayList<Uri>();
        List<String> nodeIds = getLocalNodeId();
        for (String nodeId : nodeIds) {
            result.add(new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(nodeId).path(path).build());
        }
        return result;
    }

    class GetDataAsync extends AsyncTask<Void, Void, DataMapItem> {

        @Override
        protected DataMapItem doInBackground(Void... params) {
            while (!mGoogleApiClient.isConnected()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted");
                }
            }
            Log.d(TAG, "Connected -> now check data items");
            boolean foundBaseValues = false;
            List<Uri> uris = getUriForDataItem(SharedConstants.DataMapUrl.SITEMAP_BASE.value());
            PendingResult<DataItemBuffer> pendingResult;
            int count;
            DataItemBuffer dataItem;
            for (Uri uri : uris) {
                pendingResult = Wearable.DataApi.getDataItems(mGoogleApiClient, uri);
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
                pendingResult = Wearable.DataApi.getDataItems(mGoogleApiClient);
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
                uris = getUriForDataItem("/" + mSitemapLink.hashCode() + SharedConstants.DataMapUrl.SITEMAP_DETAILS.value());
                for (Uri uri : uris) {
                    pendingResult = Wearable.DataApi.getDataItems(mGoogleApiClient, uri);
                    dataItem = pendingResult.await(5, TimeUnit.SECONDS);
                    count = dataItem.getCount();
                    Log.d(TAG, "Found '" + count + "' sitemap detail items for uri " + uri);
                    if (count > 0) {
                        for (int i = 0; i < dataItem.getCount(); i++) {
                            DataItem item = dataItem.get(i);
                            Log.d(TAG, "DataItemUri: " + item.getUri());
                            final DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
                            if (item.getUri().toString().endsWith(mSitemapLink.hashCode() + SharedConstants.DataMapUrl.SITEMAP_DETAILS.value())) {
                                Log.d(TAG, "Got base sitemap for the correct uri");
                                return dataMapItem;
                            }
                        }
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
            List<Uri> uris = getUriForDataItem(uriValueToCheck);
            for (Uri uri : uris) {
                PendingResult<DataItemBuffer> pendingResult = Wearable.DataApi.getDataItems(mGoogleApiClient, uri);
                DataItemBuffer dataItem = pendingResult.await(5, TimeUnit.SECONDS);
                int count = dataItem.getCount();
                if (count > 0) {
                    for (int i = 0; i < dataItem.getCount(); i++) {
                        DataItem item = dataItem.get(i);
                        Log.d(TAG, "DataItemUri: " + item.getUri());
                        final DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
                        if (item.getUri().toString().endsWith("/" + mCurrentLink.hashCode() + SharedConstants.DataMapUrl.SITEMAP_DETAILS.value())) {
                            return dataMapItem;
                        } else {
                            Log.w(TAG, "Unknown URI: " + item.getUri());
                        }
                    }
                } else {
                    Log.d(TAG, "Did not find anything in the map so far");
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(DataMapItem dataMapItem) {
            if (dataMapItem == null) {
                // TODO get data from mobile app
                Log.d(TAG, "Do not have data for this link " + mCurrentLink);
            } else {
                // TODO forward to next subview list
                Log.d(TAG, "Already have the data for the link " + mCurrentLink);
                String sitemapXml = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_XML.name());
                Bundle data = new Bundle();
                data.putString(SharedConstants.DataMapKey.SITEMAP_XML.name(), sitemapXml);
                Intent intent = new Intent(WearMainActivity.this, SublistActivity.class);
                intent.putExtras(data);
                startActivity(intent);
            }
        }
    }
}
