package org.openhab.habdroid.util.writeBeacon;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.loopj.android.http.SyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

import cz.msebera.android.httpclient.Header;

/**
 * Created by fspiekermann on 01.02.17.
 */

public class WriteBeaconHandleSitemap extends Observable implements AdapterView.OnItemSelectedListener{

    // Logging TAG
    private static final String TAG = WriteBeaconHandleSitemap.class.getSimpleName();

    private List<String> sitemapList;
    private Spinner sitemapSpinner;
    private ArrayAdapter<String> sitemapAdapter;
    private int sitemapPointer;
    private SyncHttpClient mSyncHttpClient;
    private TextView sitemapText;

    public WriteBeaconHandleSitemap(Context c, Spinner sitemapSpinner, TextView sitemapText, SyncHttpClient mSyncHttpClient){
        this.mSyncHttpClient = mSyncHttpClient;
        sitemapPointer = 0;
        sitemapList = new ArrayList<>();
        sitemapAdapter = new ArrayAdapter<>(c, R.layout.openhabwritebeaconspinneritem, sitemapList);
        this.sitemapSpinner = sitemapSpinner;
        sitemapSpinner.setAdapter(sitemapAdapter);
        sitemapSpinner.setOnItemSelectedListener(this);
        this.sitemapText = sitemapText;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(sitemapPointer != position) {
            sitemapPointer = position;
            setChanged();
            notifyObservers(getChosenSitemap());
        }
    }
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        sitemapSpinner.setSelection(sitemapPointer);
    }

    public void setSitemapPointer(String sitemap){
        if(sitemapList.contains(sitemap))
            this.sitemapPointer = sitemapList.indexOf(sitemap);
        else
            this.sitemapPointer = 0;
        setSitemapSelection();
    }

    public String getChosenSitemap(){
        Log.d(TAG, "getChosenSitemap: " + sitemapList.size());
        try {
            return sitemapList.get(sitemapPointer);
        } catch (Exception e){
            return null;
        }
    }

    public void setSitemapSelection(){
        sitemapSpinner.setSelection(sitemapPointer);
    }

    public void hide(){
        sitemapText.setVisibility(View.INVISIBLE);
        sitemapSpinner.setVisibility(View.INVISIBLE);
    }

    public List<String> getSitemapList(){
        return this.sitemapList;
    }

    public void loadSitemapList(String baseURL) {
        Log.d(TAG, "Loading sitemap list from " + baseURL + "rest/sitemaps");
        mSyncHttpClient.get(baseURL + "rest/sitemaps", new TextHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                Log.d(TAG, "onSuccess: " + Thread.currentThread().toString());
                sitemapList.clear();
                try {
                    JSONArray jsonArray = new JSONArray(responseBody);
                    for(OpenHABSitemap sitemap : Util.parseSitemapList(jsonArray)){
                        sitemapList.add(sitemap.getName());
                    }
                    setChanged();
                    notifyObservers(getSitemapList());
                } catch (JSONException e) {
                    e.printStackTrace();
                    sitemapList.clear();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseBody, Throwable error) {
                Log.d(TAG, "Sitemap request failure: " + error.getMessage());
                error.printStackTrace();
                sitemapList.clear();
            }
        });
    }

    public void sitemapAdapterNotifyDataSetChanged(){
        sitemapAdapter.notifyDataSetChanged();
    }
}
