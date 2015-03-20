package org.openhab.habdroid.wear;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.loopj.android.http.SyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;

import org.apache.http.Header;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.util.SharedConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * This is a service to communicate with the wearable
 * <p/>
 * Created by tamon on 20.03.15.
 */
public class WearService implements GoogleApiClient.ConnectionCallbacks, MessageApi.MessageListener {

    private static final String TAG = WearService.class.getSimpleName();

    private static GoogleApiClient mGoogleApiClient;

    private String mOpenHABBaseUrl;

    private SyncHttpClient mSyncHttpClient = new SyncHttpClient();

    public WearService(Context context, String openhabBaseUrl) {
        mOpenHABBaseUrl = openhabBaseUrl;
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(context).addConnectionCallbacks(this)
                    .addApi(Wearable.API)
                    .build();
        }
    }

    /**
     * Delegates connect() to the internal googleapiclient
     */
    public void connect() {
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Connected");
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "Incoming message for " + messageEvent.getPath());
        if (messageEvent.getPath().equals(SharedConstants.MessagePath.LOAD_SITEMAP.value())) {
            final String url = new String(messageEvent.getData());

            Log.d(TAG, "Getting data from url " + url);

            mSyncHttpClient.get(url, new TextHttpResponseHandler() {

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseBody, Throwable error) {
                    Log.d(TAG, "Failed to load data for wearable", error);
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                    Log.d(TAG, "Successfully got data for wearable " + responseBody);
                    setDataForWearable(url, responseBody);
                }
            });
        }
    }

    private void setDataForWearable(String link, String content) {
        try {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/" + link.hashCode() + SharedConstants.DataMapUrl.SITEMAP_DETAILS.value());
            putDataMapRequest.getDataMap().putString(SharedConstants.DataMapKey.SITEMAP_XML.name(), content);
            putDataMapRequest.getDataMap().putString(SharedConstants.DataMapKey.SITEMAP_LINK.name(), link);
            putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        } catch (Exception e) {
            Log.e(TAG, "Exception occured");
        }
    }

    private void parseSitemapAndGetImages(String sitemapXML) {
        OpenHABWidgetDataSource openHABWidgetDataSource = new OpenHABWidgetDataSource();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(sitemapXML)));
            if (document != null) {
                Node rootNode = document.getFirstChild();
                openHABWidgetDataSource.setSourceNode(rootNode);
                for (OpenHABWidget w : openHABWidgetDataSource.getWidgets()) {
                    // Remove frame widgets with no label text
                    if (w.getType().equals("Frame") && TextUtils.isEmpty(w.getLabel())) {
                        continue;
                    } else {
                        String url = mOpenHABBaseUrl + "/images/" + w.getIcon() + ".png";
                        Log.d(TAG, "Getting image from url: " + url);
                    }
                }
            } else {
                Log.e(TAG, "Got a null response from openHAB");
            }
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSitemapForWearable(OpenHABSitemap openHABSitemap) {
        try {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(SharedConstants.DataMapUrl.SITEMAP_BASE.value());
            Log.d(TAG, "Sending sitemap to wearable:\nName: " + openHABSitemap.getName() + "\nLink: " + openHABSitemap.getHomepageLink());
            Log.d(TAG, "Sending to uri " + putDataMapRequest.getUri());

            putDataMapRequest.getDataMap().putString(SharedConstants.DataMapKey.SITEMAP_NAME.name(), openHABSitemap.getName());
            putDataMapRequest.getDataMap().putString(SharedConstants.DataMapKey.SITEMAP_LINK.name(), openHABSitemap.getHomepageLink());
            putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        } catch (Exception e) {
            Log.e(TAG, "Cannot send data to wearable", e);
        }
        new ClearDataApiAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, openHABSitemap);
    }

    public void sendDataToWearable(String pageUrl, String responseString) {
        try {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/" + pageUrl.hashCode() + SharedConstants.DataMapUrl.SITEMAP_DETAILS.value());
            Log.d(TAG, "HashCode for URI: " + pageUrl.hashCode());

            putDataMapRequest.getDataMap().putString(SharedConstants.DataMapKey.SITEMAP_XML.name(), responseString);
            putDataMapRequest.getDataMap().putString(SharedConstants.DataMapKey.SITEMAP_LINK.name(), pageUrl);
            putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
            Log.d(TAG, "Sending to uri : " + putDataMapRequest.getUri());
            Log.d(TAG, "Sending datamap: " + putDataMapRequest.getDataMap());
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        } catch (Exception e) {
            Log.e(TAG, "Cannot send data to wearable", e);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended");
    }

    public void disconnect() {
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    class ClearDataApiAsync extends AsyncTask<OpenHABSitemap, Void, OpenHABSitemap> {
        @Override
        protected OpenHABSitemap doInBackground(OpenHABSitemap... params) {
            /*PendingResult<DataItemBuffer> pendingResult = Wearable.DataApi.getDataItems(mGoogleApiClient);
            DataItemBuffer dataItem = pendingResult.await(5, TimeUnit.SECONDS);
            int count = dataItem.getCount();
            if (count > 0) {
                Log.d(TAG, "Now deleting '" + count + "' data items");
                for (int i = 0; i < dataItem.getCount(); i++) {
                    DataItem item = dataItem.get(i);
                    PendingResult<DataApi.DeleteDataItemsResult> pendingDelete = Wearable.DataApi.deleteDataItems(mGoogleApiClient, item.getUri());
                    pendingDelete.await(10, TimeUnit.MILLISECONDS);
                    Log.d(TAG, "Deleted data");
                }
            }*/
            return params[0];
        }

        @Override
        protected void onPostExecute(OpenHABSitemap openHABSitemap) {
            Log.d(TAG, "Now send new data");
        }
    }
}
