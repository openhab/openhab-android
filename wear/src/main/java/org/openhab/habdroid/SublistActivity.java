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
import org.openhab.habdroid.widget.SwitchWidgetActivity;
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

public class SublistActivity extends Activity implements WearableListView.ClickListener, Observer, DataApi.DataListener {

    private static final String TAG = SublistActivity.class.getSimpleName();

    private WearableListView mListView;

    private OpenHABWearWidgetAdapter mListAdapter;

    private OpenHABWidgetDataSource mOpenHABWidgetDataSource;

    private List<OpenHABWidget> mWidgetList = new ArrayList<OpenHABWidget>();

    private GoogleApiService mGoogleApiService;

    private static String mCurrentSitemapLinkToWaitFor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        mOpenHABWidgetDataSource = new OpenHABWidgetDataSource();

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                Log.d(TAG, "Layout inflated");
                mListView = (WearableListView) stub.findViewById(R.id.listView);
                mListAdapter = new OpenHABWearWidgetAdapter(SublistActivity.this, mWidgetList);
                mListView.setAdapter(mListAdapter);
                mListView.setClickListener(SublistActivity.this);

                String xml = getIntent().getStringExtra(SharedConstants.DataMapKey.SITEMAP_XML.name());
                processSitemap(xml);
            }
        });

        mGoogleApiService = new GoogleApiService(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mGoogleApiService.isConnected()) {
            mGoogleApiService.connect();
        }
        mGoogleApiService.addObserver(this);
    }

    @Override
    protected void onPause() {
        mGoogleApiService.removeListener(this);
        mGoogleApiService.deleteObserver(this);
        super.onPause();
    }

    private void processSitemap(String sitemap) {
        Log.d(TAG, "Processing sitemap " + sitemap);
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
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "ParserConfig", e);
        } catch (SAXException e) {
            Log.e(TAG, "SAXException", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
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
            Log.d(TAG, "Widget item " + clickedWidget.getItem());
            Intent intent = new Intent(getApplicationContext(), SwitchWidgetActivity.class);
            intent.putExtra(SwitchWidgetActivity.STATE, clickedWidget.getItem().getStateAsBoolean());
            intent.putExtra(SwitchWidgetActivity.WIDGET_LINK, clickedWidget.getItem().getLink());
            startActivity(intent);
        }
    }

    private void forwardToSwitch() {

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
    public void update(Observable observable, Object data) {
        if (data instanceof String) {
            String what = (String) data;
            if ("CONNECTED".equals(what)) {
                mGoogleApiService.addListener(this);
            }
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
        if (mCurrentSitemapLinkToWaitFor != null && thisSitemapLink.equals(mCurrentSitemapLinkToWaitFor)) {
            openSublist(sitemapXML);
        }
    }

    private void openSublist(String sitemapXml) {
        Bundle data = new Bundle();
        data.putString(SharedConstants.DataMapKey.SITEMAP_XML.name(), sitemapXml);
        Intent intent = new Intent(this, SublistActivity.class);
        intent.putExtras(data);
        startActivity(intent);
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
                PendingResult<DataItemBuffer> pendingResult = mGoogleApiService.getDataItems(uri);
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
}
