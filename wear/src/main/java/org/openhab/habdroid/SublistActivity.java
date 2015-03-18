package org.openhab.habdroid;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.view.WearableListView;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.wearable.DataMapItem;

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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class SublistActivity extends Activity implements WearableListView.ClickListener {

    private static final String TAG = SublistActivity.class.getSimpleName();

    private WearableListView mListView;

    private OpenHABWearWidgetAdapter mListAdapter;

    private OpenHABWidgetDataSource mOpenHABWidgetDataSource;

    private List<OpenHABWidget> mWidgetList = new ArrayList<OpenHABWidget>();

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
    }

    @Override
    protected void onResume() {
        super.onStart();
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
            /*List<Uri> uris = getUriForDataItem(uriValueToCheck);
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
            }*/
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
                Log.d(TAG, "DataMapItem: " + dataMapItem.getDataMap());
            }
        }
    }
}
