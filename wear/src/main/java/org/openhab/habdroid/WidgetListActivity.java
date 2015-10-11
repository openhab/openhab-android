package org.openhab.habdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.view.WearableListView;
import android.util.Log;

import org.openhab.habdroid.adapter.OpenHABWearWidgetAdapter;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.service.MobileService;
import org.openhab.habdroid.service.MobileServiceWdigetListClient;
import org.openhab.habdroid.util.SharedConstants;
import org.openhab.habdroid.widget.RollerShutterWidgetActivity;
import org.openhab.habdroid.widget.SwitchWidgetActivity;

import java.util.ArrayList;
import java.util.List;

public class WidgetListActivity extends Activity implements WearableListView.ClickListener, MobileServiceWdigetListClient {

    private static final String TAG = WidgetListActivity.class.getSimpleName();

    private WearableListView mListView;

    private OpenHABWearWidgetAdapter mListAdapter;

    private OpenHABWidgetDataSource mOpenHABWidgetDataSource;

    private List<OpenHABWidget> mWidgetList = new ArrayList<OpenHABWidget>();

    private MobileService mMobileService;

    private String mCurrentSitemapLinkToWaitFor;
    private String mSitemapName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_list);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        mCurrentSitemapLinkToWaitFor = getIntent().getStringExtra(SharedConstants.DataMapKey.SITEMAP_LINK.name());
        mSitemapName = getIntent().getStringExtra(SharedConstants.DataMapKey.SITEMAP_NAME.name());

        mOpenHABWidgetDataSource = new OpenHABWidgetDataSource();

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                Log.d(TAG, "Layout inflated");
                mListView = (WearableListView) stub.findViewById(R.id.listView);
                mListAdapter = new OpenHABWearWidgetAdapter(WidgetListActivity.this, mWidgetList);
                if (mListView != null && mListAdapter != null) {
                    mListView.setAdapter(mListAdapter);
                    mListView.setClickListener(WidgetListActivity.this);
                }
            }
        });

        initMobileService();
    }

    private void initMobileService() {
        mMobileService = MobileService.getService(getApplicationContext());
        mMobileService.connect(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMobileService.connect(this);
    }

    @Override
    protected void onPause() {
        mMobileService.removeClient(this);
        super.onPause();
    }

    @Override
    public void onClick(WearableListView.ViewHolder viewHolder) {
        Log.d(TAG, "Clicked an element at position " + viewHolder.getPosition());
        OpenHABWidget clickedWidget = mWidgetList.get(viewHolder.getPosition());
        Log.d(TAG, "Clicked the widget " + clickedWidget);
        Log.d(TAG, "Widget is of type " + clickedWidget.getType());
        Log.d(TAG, "Widget item " + clickedWidget.getItem());
        OpenHABItem widgetItem = clickedWidget.getItem();
        String typeToCheck;
        if (widgetItem != null) {
            typeToCheck = widgetItem.getType();
        } else if (clickedWidget.getType().equals("Text")) {
            Log.d(TAG, "Widget is of type text...");
            typeToCheck = "TextItem";
        } else {
            typeToCheck = "";
        }
        Log.d(TAG, "Widget item type " + typeToCheck);
        if (typeToCheck.equals("FrameItem") || typeToCheck.equals("GroupItem") || typeToCheck.equals("TextItem")) {
            Log.d(TAG, "Clicked on frame or group");
            OpenHABLinkedPage linkedPage = clickedWidget.getLinkedPage();
            if (linkedPage != null) {
                Log.d(TAG, "Linked page url " + linkedPage.getLink());
                Intent intent = new Intent(WidgetListActivity.this, WidgetListActivity.class);
                intent.putExtra(SharedConstants.DataMapKey.SITEMAP_NAME.name(), linkedPage.getTitle());
                intent.putExtra(SharedConstants.DataMapKey.SITEMAP_LINK.name(), linkedPage.getLink());
                startActivity(intent);
            } else {
                Log.i(TAG, "Linked page on widget is null");
            }
        } else if (typeToCheck.equals("SwitchItem")) {
            Intent intent = new Intent(getApplicationContext(), SwitchWidgetActivity.class);
            intent.putExtra(SwitchWidgetActivity.STATE, clickedWidget.getItem().getStateAsBoolean());
            intent.putExtra(SwitchWidgetActivity.WIDGET_LINK, clickedWidget.getItem().getLink());
            intent.putExtra(SwitchWidgetActivity.WIDGET_NAME, clickedWidget.getLabel());
            startActivity(intent);
        } else if (typeToCheck.equals("RollershutterItem")) {
            Intent intent = new Intent(getApplicationContext(), RollerShutterWidgetActivity.class);
            intent.putExtra(RollerShutterWidgetActivity.WIDGET_LINK, clickedWidget.getItem().getLink());
            intent.putExtra(RollerShutterWidgetActivity.WIDGET_NAME, clickedWidget.getLabel());
            intent.putExtra(RollerShutterWidgetActivity.ITEM_NAME, clickedWidget.getItem().getName());
            startActivity(intent);
        } else {
        }
    }

    @Override
    public void onTopEmptyRegionClick() {
        Log.d(TAG, "Top Empty Region click");
    }

    @Override
    public void onSitemapLoaded(List<OpenHABWidget> widgetList, String sitemapLink) {
        if (sitemapLink.equals(mCurrentSitemapLinkToWaitFor)) {
            mWidgetList.clear();
            mWidgetList.addAll(widgetList);
            mListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void connected() {
        mMobileService.getSiteData(mCurrentSitemapLinkToWaitFor);
    }

    @Override
    public void connectionSuspended() {
        Log.i(TAG, "Google Api Connection suspended");
    }
}
