package org.openhab.habdroid.util.writeBeacon;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.loopj.android.http.BaseJsonHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;

import org.json.JSONArray;
import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;

import cz.msebera.android.httpclient.Header;

/**
 * Created by fspiekermann on 01.02.17.
 */

public class WriteBeaconHandleGroup extends Observable implements AdapterView.OnItemSelectedListener{

    // Logging TAG
    private static final String TAG = WriteBeaconHandleGroup.class.getSimpleName();

    private Map<String, List<String>> groupLabelsSitemapMap;
    private List<String> groupLabelsSitemap;
    private Spinner groupSpinner;
    private ArrayAdapter<String> groupAdapter;
    private int groupPointer;
    private SyncHttpClient mSyncHttpClient;
    private TextView groupText;

    public WriteBeaconHandleGroup(Context c, Spinner groupSpinner, TextView groupText, SyncHttpClient mSyncHttpClient){
        this.mSyncHttpClient = mSyncHttpClient;
        groupPointer = 0;
        groupLabelsSitemap = new ArrayList<>();
        groupLabelsSitemapMap = new HashMap<>();
        groupAdapter = new ArrayAdapter<>(c, R.layout.openhabwritebeaconspinneritem, groupLabelsSitemap);
        this.groupSpinner = groupSpinner;
        groupSpinner.setAdapter(groupAdapter);
        groupSpinner.setOnItemSelectedListener(this);
        this.groupText = groupText;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(groupPointer != position) {
            groupPointer = position;
        }
    }
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        groupSpinner.setSelection(groupPointer);
    }

    public void setGroupPointer(int groupPointer){
        this.groupPointer = groupPointer;
        setGroupSelection();
    }

    public void setGroupPointer(String group) {
        if (groupLabelsSitemap.contains(group))
            this.groupPointer = groupLabelsSitemap.indexOf(group);
        else
            this.groupPointer = 0;
        setGroupSelection();
    }

    public String getChosenGroup(){
        return groupLabelsSitemap.get(groupPointer);
    }

    public void setGroupSelection(){
        groupSpinner.setSelection(groupPointer);
    }

    public void hide(){
        groupText.setVisibility(View.INVISIBLE);
        groupSpinner.setVisibility(View.INVISIBLE);
    }

    public void loadGroupLabelList(final List<String> sitemapList, final String baseURL) {
        Log.d(TAG, "Loading Group Label list from " + baseURL + "rest/items?recursive=false");
        mSyncHttpClient.get(baseURL + "rest/items?recursive=false", new BaseJsonHttpResponseHandler<JSONArray>() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, String rawJsonResponse, JSONArray response) {
                mapGroupsToSitemaps(Util.parseGroupLabels(response), sitemapList, baseURL);
                setChanged();
                notifyObservers("done");
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, String rawJsonData, JSONArray errorResponse) {
                Log.d(TAG, "Group request failure: " + throwable.getMessage());
            }

            @Override
            protected JSONArray parseResponse(String rawJsonData, boolean isFailure) throws Throwable {
                return new JSONArray(rawJsonData);
            }
        });
    }

    private void mapGroupsToSitemaps(final List<String> groupLabels, final List<String> sitemapList, String baseURL){
        Log.d(TAG, "Loading Sitemap informations from " + baseURL + "rest/sitemaps/{sitemapname}");
        groupLabelsSitemapMap.clear();
        for(final String actSitemap : sitemapList) {
            mSyncHttpClient.get(baseURL + "rest/sitemaps/" + actSitemap, new TextHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, String responseString) {
                    List<String> groupsOfSitemap = new ArrayList<>();
                    for(String groups : groupLabels){
                        if (responseString.contains(groups))
                            groupsOfSitemap.add(groups);
                    }
                    groupLabelsSitemapMap.put(actSitemap, groupsOfSitemap);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, Throwable error) {
                    Log.d(TAG, "Sitemap request failure: " + error.getMessage());
                    groupLabelsSitemapMap.clear();
                }
            });
        }
    }

    public void setGroupLabelsSitemap(String choosenSitemap){
        groupLabelsSitemap.clear();
        if (choosenSitemap != null) {
            groupLabelsSitemap.addAll(groupLabelsSitemapMap.get(choosenSitemap));
            groupAdapter.notifyDataSetChanged();

        }
    }

    public void groupAdapterNotifyDataSetChanged(){
        groupAdapter.notifyDataSetChanged();
    }
}
