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


}
