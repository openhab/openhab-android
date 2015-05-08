package org.openhab.habdroid.service;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

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

/**
 * Created by tobiasamon on 31.03.15.
 */
public class MobileService implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener, MessageApi.MessageListener {

    private static final String TAG = MobileService.class.getSimpleName();
    private static MobileService instance;
    private GoogleApiClient mGoogleApiClient;
    private List<MobileServiceClient> clients;

    private MobileService() {
    }

    public static MobileService getService(Context context) {
        if (instance == null) {
            instance = new MobileService();
            instance.mGoogleApiClient = new GoogleApiClient.Builder(context).addConnectionCallbacks(instance)
                    .addApi(Wearable.API)
                    .build();
            instance.clients = new ArrayList<MobileServiceClient>();
        }
        return instance;
    }

    public void connect(MobileServiceClient client) {
        addClient(client);
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        } else {
            client.connected();
        }
    }

    public void addClient(MobileServiceClient client) {
        if (client != null && !clients.contains(client)) {
            clients.add(client);
        }
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    public void removeClient(MobileServiceClient client) {
        if (client != null && clients.contains(client)) {
            clients.remove(client);
        }
        if (clients.isEmpty()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        for (MobileServiceClient client : clients) {
            client.connected();
        }
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        for (MobileServiceClient client : clients) {
            client.connectionSuspended();
        }
    }

    private List<Uri> getUriForDataItem(String path) {
        List<Uri> result = new ArrayList<Uri>();
        List<String> nodeIds = getNodeIdList();
        for (String nodeId : nodeIds) {
            result.add(new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(nodeId).path(path).build());
        }
        Log.d(TAG, "Got '" + result.size() + "' uris for the path " + path);
        return result;
    }

    private List<String> getNodeIdList() {
        PendingResult<NodeApi.GetConnectedNodesResult> connectedNodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        NodeApi.GetConnectedNodesResult connectedNodesResult = connectedNodes.await(5, TimeUnit.SECONDS);
        List<String> nodeIds = new ArrayList<String>();
        for (com.google.android.gms.wearable.Node node : connectedNodesResult.getNodes()) {
            Log.d(TAG, "Found node " + node.getId() + " - " + node.getDisplayName());
            nodeIds.add(node.getId());
        }
        return nodeIds;
    }

    private PendingResult<DataItemBuffer> getDataItems() {
        return Wearable.DataApi.getDataItems(mGoogleApiClient);
    }

    private PendingResult<DataItemBuffer> getDataItems(Uri uri) {
        return Wearable.DataApi.getDataItems(mGoogleApiClient, uri);
    }

    private DataMapItem getDataItemForUri(Uri uri, String currentLink) {
        PendingResult<DataItemBuffer> pendingResult = Wearable.DataApi.getDataItems(mGoogleApiClient, uri);
        DataItemBuffer dataItem = pendingResult.await(5, TimeUnit.SECONDS);
        int count = dataItem.getCount();
        if (count > 0) {
            for (int i = 0; i < dataItem.getCount(); i++) {
                DataItem item = dataItem.get(i);
                Log.d(TAG, "DataItemUri: " + item.getUri());
                final DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
                if (item.getUri().toString().endsWith("/" + currentLink.hashCode() + SharedConstants.DataMapUrl.SITEMAP_DETAILS.value())) {
                    return dataMapItem;
                } else {
                    Log.w(TAG, "Unknown URI: " + item.getUri());
                }
            }
        }
        return null;
    }

    private void getDataFromMobileApp(String link, List<String> nodeIdList) {
        String firstNodeId;
        if (!nodeIdList.isEmpty()) {
            firstNodeId = nodeIdList.get(0);
            nodeIdList.remove(0);
            GetDataResultCallBack resultCallBack = new GetDataResultCallBack(link, nodeIdList);
            Wearable.MessageApi.sendMessage(mGoogleApiClient, firstNodeId, SharedConstants.MessagePath.LOAD_SITEMAP.value(), link.getBytes()).setResultCallback(resultCallBack);
        } else {
            Log.d(TAG, "Can not get data from any remote node");
        }
    }

    private void parseXmlAndNotifyClients(String sitemap, String thisSitemapLink) {
        Log.d(TAG, "Processing sitemap");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        OpenHABWidgetDataSource openHABWidgetDataSource = new OpenHABWidgetDataSource();
        List<OpenHABWidget> widgetList = new ArrayList<OpenHABWidget>();
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(sitemap)));
            if (document != null) {
                Node rootNode = document.getFirstChild();
                openHABWidgetDataSource.setSourceNode(rootNode);
                widgetList.clear();
                for (OpenHABWidget w : openHABWidgetDataSource.getWidgets()) {
                    // Remove frame widgets with no label text
                    Log.d(TAG, "Found widget with type " + w.getType() + " and label " + w.getLabel());
                    if (w.getType().toLowerCase().equals("frame")) {
                        Log.d(TAG, "Remove it ... its a frame");
                        continue;
                    }
                    widgetList.add(w);
                }
            } else {
                Log.e(TAG, "Got a null response from openHAB");
            }
            for (MobileServiceClient client : clients) {
                if (client instanceof MobileServiceWdigetListClient) {
                    ((MobileServiceWdigetListClient) client).onSitemapLoaded(widgetList, thisSitemapLink);
                }
            }
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "ParserConfig", e);
        } catch (SAXException e) {
            Log.e(TAG, "SAXException", e);
            Log.d(TAG, "Sitemap to parse: " + sitemap);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }
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
                    //mSitemapName = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_NAME.name());
                    //mSitemapLink = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
                }
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        boolean success = messageEvent.getPath().endsWith(SharedConstants.MessagePath.SUCCESS.value());
        for (MobileServiceClient client : clients) {
            if (client instanceof MobileServiceWdigetClient) {
                ((MobileServiceWdigetClient) client).commandExecuted(success);
            }
        }
    }

    private void processDataMapItem(DataMapItem dataMapItem) {
        DataMap map = dataMapItem.getDataMap();
        Log.d(TAG, "Got DataMapItem: " + map);
        String sitemapXML = map.getString(SharedConstants.DataMapKey.SITEMAP_XML.name());
        String thisSitemapLink = map.getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
        Log.d(TAG, "Got Sitemap XML for '" + thisSitemapLink + "'");
        parseXmlAndNotifyClients(sitemapXML, thisSitemapLink);
    }

    public void getSiteData(String link) {
        new GetSiteDataAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, link);
    }

    public void getBaseSitemap() {
        Log.d(TAG, "Getting base sitemap");
        new GetDataAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void sendCommand(String command, String link) {
        new SendCommandAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, command, link);
    }

    private void sendCommand(List<String> nodeIdList, String command, String link) {
        String firstNodeId;
        if (!nodeIdList.isEmpty()) {
            firstNodeId = nodeIdList.get(0);
            nodeIdList.remove(0);
            SendCommandResultCallBack callBack = new SendCommandResultCallBack(command, link, nodeIdList);
            String dataToSend = command + "::" + link;
            Wearable.MessageApi.sendMessage(mGoogleApiClient, firstNodeId, SharedConstants.MessagePath.SEND_TO_OPENHAB.value(), dataToSend.getBytes()).setResultCallback(callBack);
        }
    }

    private final class SendCommandResultCallBack implements ResultCallback<MessageApi.SendMessageResult> {
        private List<String> mNodeIds;

        private String mLink;

        private String mCommand;

        public SendCommandResultCallBack(String command, String link, List<String> nodeIds) {
            mNodeIds = nodeIds;
            mLink = link;
            mCommand = command;
        }

        @Override
        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
            if (!sendMessageResult.getStatus().isSuccess()) {
                sendCommand(mNodeIds, mCommand, mLink);
            } else {
                Log.d(TAG, "Successfully sent command to backend");
            }
        }
    }

    private final class GetDataAsync extends AsyncTask<Void, Void, SitemapBaseValues> {

        @Override
        protected SitemapBaseValues doInBackground(Void... params) {
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
            String sitemapName;
            String sitemapLink;
            for (Uri uri : uris) {
                pendingResult = getDataItems(uri);
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
                            sitemapName = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_NAME.name());
                            sitemapLink = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
                            return new SitemapBaseValues(sitemapName, sitemapLink);
                        }
                    }
                }
            }
            if (!foundBaseValues) {
                Log.d(TAG, "Did not find the items, yet... try again");
                pendingResult = getDataItems();
                dataItem = pendingResult.await(5, TimeUnit.SECONDS);
                count = dataItem.getCount();
                for (int i = 0; i < count; i++) {
                    DataItem item = dataItem.get(i);
                    Log.d(TAG, "Item uri for base " + item.getUri());
                    if (item.getUri().toString().endsWith(SharedConstants.DataMapUrl.SITEMAP_BASE.value())) {
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
                        sitemapName = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_NAME.name());
                        sitemapLink = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
                        return new SitemapBaseValues(sitemapName, sitemapLink);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(SitemapBaseValues sitemapBaseValues) {
            if (sitemapBaseValues == null) {
                for (MobileServiceClient client : clients) {
                    if (client instanceof MobileServiceBaseClient) {
                        ((MobileServiceBaseClient) client).sitemapBaseMissing();
                    }
                }
            } else {
                for (MobileServiceClient client : clients) {
                    if (client instanceof MobileServiceBaseClient) {
                        ((MobileServiceBaseClient) client).sitemapBaseFound(sitemapBaseValues);
                    }
                }
            }
        }
    }

    private final class GetSiteDataAsync extends AsyncTask<String, Void, DataMapItem> {

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
                DataMapItem mapItemToReturn = getDataItemForUri(uri, mCurrentLink);
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
                new GetRemoteDataAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mCurrentLink);
            } else {
                Log.d(TAG, "Already have the data for the link " + mCurrentLink);
                String sitemapXml = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_XML.name());
                String sitemapLink = dataMapItem.getDataMap().getString(SharedConstants.DataMapKey.SITEMAP_LINK.name());
                parseXmlAndNotifyClients(sitemapXml, sitemapLink);
            }
        }
    }

    private final class GetRemoteDataAsync extends AsyncTask<String, Void, Void> {


        @Override
        protected Void doInBackground(String... params) {
            if (params.length > 0) {
                String link = params[0];
                List<String> nodeIdList = getNodeIdList();
                getDataFromMobileApp(link, nodeIdList);
            }
            return null;
        }
    }

    private final class GetDataResultCallBack implements ResultCallback<MessageApi.SendMessageResult> {

        private List<String> mNodeIds;

        private String mLink;

        public GetDataResultCallBack(String link, List<String> nodeIds) {
            mNodeIds = nodeIds;
            mLink = link;
        }

        @Override
        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
            if (!sendMessageResult.getStatus().isSuccess()) {
                getDataFromMobileApp(mLink, mNodeIds);
            } else {
                Log.d(TAG, "Successfully sent message to remote node");
            }
        }
    }

    private final class SendCommandAsync extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            String command = params[0];
            String link = params[1];
            List<String> nodes = getNodeIdList();
            sendCommand(nodes, command, link);
            return null;
        }
    }

}
