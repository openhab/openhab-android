package org.openhab.habdroid.service;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.openhab.habdroid.util.SharedConstants;
import org.openhab.habdroid.widget.SwitchWidgetActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.TimeUnit;

/**
 * Created by tamon on 20.03.15.
 */
public class GoogleApiService extends Observable implements GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = GoogleApiService.class.getSimpleName();

    private static GoogleApiClient mGoogleApiClient;

    public GoogleApiService(Context context) {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(context).addConnectionCallbacks(this)
                    .addApi(Wearable.API)
                    .build();
        }
    }

    public void connect() {
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    public boolean isConnected() {
        return mGoogleApiClient.isConnected();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Connected");
        setChanged();
        notifyObservers("CONNECTED");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended");
        setChanged();
        notifyObservers("CONNECTION_SUSPENDED");
    }

    public void removeListener(DataApi.DataListener dataListener) {
        Wearable.DataApi.removeListener(mGoogleApiClient, dataListener);
    }

    public void addListener(DataApi.DataListener dataListener) {
        Wearable.DataApi.addListener(mGoogleApiClient, dataListener);
    }

    public void disconnect() {
        mGoogleApiClient.disconnect();
    }

    public List<String> getNodeIdList() {
        PendingResult<NodeApi.GetConnectedNodesResult> connectedNodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        NodeApi.GetConnectedNodesResult connectedNodesResult = connectedNodes.await(5, TimeUnit.SECONDS);
        List<String> nodeIds = new ArrayList<String>();
        for (com.google.android.gms.wearable.Node node : connectedNodesResult.getNodes()) {
            Log.d(TAG, "Found node " + node.getId() + " - " + node.getDisplayName());
            nodeIds.add(node.getId());
        }
        return nodeIds;
    }

    public List<Uri> getUriForDataItem(String path) {
        List<Uri> result = new ArrayList<Uri>();
        List<String> nodeIds = getNodeIdList();
        for (String nodeId : nodeIds) {
            result.add(new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(nodeId).path(path).build());
        }
        return result;
    }

    public void getDataFromMobileApp(String link, List<String> nodeIdList) {
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

    public DataMapItem getDataItemForUri(Uri uri, String currentLink) {
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

    public PendingResult<DataItemBuffer> getDataItems() {
        return Wearable.DataApi.getDataItems(mGoogleApiClient);
    }

    public PendingResult<DataItemBuffer> getDataItems(Uri uri) {
        return Wearable.DataApi.getDataItems(mGoogleApiClient, uri);
    }

    public void sendCommand(List<String> nodeIdList, String command, String link) {
        String firstNodeId;
        if (!nodeIdList.isEmpty()) {
            firstNodeId = nodeIdList.get(0);
            nodeIdList.remove(0);
            SendCommandResultCallBack callBack = new SendCommandResultCallBack(command, link, nodeIdList);
            String dataToSend = command + "::" + link;
            Wearable.MessageApi.sendMessage(mGoogleApiClient, firstNodeId, SharedConstants.MessagePath.SEND_TO_OPENHAB.value(), dataToSend.getBytes()).setResultCallback(callBack);
        }
    }

    public void addMessageListener(MessageApi.MessageListener messageListener) {
        Wearable.MessageApi.addListener(mGoogleApiClient, messageListener);
    }

    public void removeMessageListener(MessageApi.MessageListener messageListener) {
        Wearable.MessageApi.removeListener(mGoogleApiClient, messageListener);
    }

    class SendCommandResultCallBack implements ResultCallback<MessageApi.SendMessageResult> {
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

    class GetDataResultCallBack implements ResultCallback<MessageApi.SendMessageResult> {

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

}
