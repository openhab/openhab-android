package org.openhab.habdroid.service;

import android.content.Context;
import android.os.AsyncTask;

import java.util.List;

/**
 * Created by tobiasamon on 22.03.15.
 */
public class SendCommandAsync extends AsyncTask<String, Void, Void> {

    private GoogleApiService mGoogleApiService;

    public SendCommandAsync(Context context) {
        mGoogleApiService = new GoogleApiService(context);
    }

    @Override
    protected Void doInBackground(String... params) {
        String command = params[0];
        String link = params[1];
        List<String> nodes = mGoogleApiService.getNodeIdList();
        mGoogleApiService.sendCommand(nodes, command, link);
        return null;
    }
}
