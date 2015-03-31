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
        return null;
    }
}
