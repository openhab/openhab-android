package org.openhab.habdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.openhab.habdroid.adapter.OpenHABWearWidgetAdapter;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.service.MobileService;
import org.openhab.habdroid.service.MobileServiceClient;
import org.openhab.habdroid.util.SharedConstants;

import java.util.ArrayList;
import java.util.List;

public class WearMainActivity extends Activity implements WearableListView.ClickListener, MobileServiceClient {

    private static final String TAG = WearMainActivity.class.getSimpleName();

    private TextView mTextView;
    private WearableListView mListView;
    private OpenHABWearWidgetAdapter mListAdapter;

    private List<OpenHABWidget> mWidgetList = new ArrayList<OpenHABWidget>();

    private String mSitemapToWaitFor;

    private MobileService mMobileService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        initMobileService();

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mListView = (WearableListView) stub.findViewById(R.id.listView);
                mListAdapter = new OpenHABWearWidgetAdapter(WearMainActivity.this, mWidgetList);
                mListView.setAdapter(mListAdapter);
                mListView.setClickListener(WearMainActivity.this);
            }
        });

    }

    private void initMobileService() {
        mMobileService = MobileService.getService(getApplicationContext());
        mMobileService.connect(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMobileService.addClient(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMobileService.removeClient(this);
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
                mSitemapToWaitFor = linkedPage.getLink();
                mMobileService.getSiteData(linkedPage.getLink());
            } else {
                Log.i(TAG, "Linked page on widget is null");
            }
        } else {
            Log.d(TAG, "Widget is of type " + clickedWidget.getType());
        }
    }

    @Override
    public void onTopEmptyRegionClick() {
        Log.d(TAG, "Top Empty Region click");
    }

    private void openSublist(String sitemapXml) {
        Bundle data = new Bundle();
        data.putString(SharedConstants.DataMapKey.SITEMAP_XML.name(), sitemapXml);
        Intent intent = new Intent(WearMainActivity.this, SublistActivity.class);
        intent.putExtras(data);
        startActivity(intent);
    }

    @Override
    public void connected() {
        mMobileService.getBaseSitemap();
    }

    @Override
    public void connectionSuspended() {
        Log.d(TAG, "Connection is suspended");
    }

    @Override
    public void sitemapBaseMissing() {
        mTextView.setText("Es wurde noch keine Sitemap gewählt. Bitte in der mobile App eine Sitemap wählen!");
        mTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSitemapLoaded(List<OpenHABWidget> widgetList, String sitemapLink) {
        Log.d(TAG, "Loaded a sitemap");
        if (sitemapLink.equals(mSitemapToWaitFor)) {
            openSublist(null);
        } else {
            mTextView.setVisibility(View.GONE);
            mWidgetList.clear();
            mWidgetList.addAll(widgetList);
            mListAdapter.notifyDataSetChanged();
        }
    }
}
