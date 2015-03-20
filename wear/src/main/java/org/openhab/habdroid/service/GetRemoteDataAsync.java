package org.openhab.habdroid.service;

import android.os.AsyncTask;

import java.util.List;

public class GetRemoteDataAsync extends AsyncTask<String, Void, Void> {

    private GoogleApiService mGoogleApiService;

    public GetRemoteDataAsync(GoogleApiService googleApiService) {
        mGoogleApiService = googleApiService;
    }

    @Override
    protected Void doInBackground(String... params) {
        if (params.length > 0) {
            String link = params[0];
            List<String> nodeIdList = mGoogleApiService.getNodeIdList();
            mGoogleApiService.getDataFromMobileApp(link, nodeIdList);
        }
        return null;
    }
}