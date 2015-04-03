package org.openhab.habdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.openhab.habdroid.service.MobileService;
import org.openhab.habdroid.service.MobileServiceBaseClient;
import org.openhab.habdroid.service.SitemapBaseValues;
import org.openhab.habdroid.util.SharedConstants;

public class WearMainActivity extends Activity implements MobileServiceBaseClient {

    private static final String TAG = WearMainActivity.class.getSimpleName();

    private TextView mTextView;
    private ImageView mImageView;
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
                mTextView.setVisibility(View.GONE);

                mImageView = (ImageView) stub.findViewById(R.id.openHabImage);
                mImageView.setImageResource(R.drawable.openhab_logo_square);
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
    public void sitemapBaseFound(SitemapBaseValues sitemapBaseValues) {
        Intent intent = new Intent(WearMainActivity.this, WidgetListActivity.class);
        intent.putExtra(SharedConstants.DataMapKey.SITEMAP_NAME.name(), sitemapBaseValues.getSitemapName());
        intent.putExtra(SharedConstants.DataMapKey.SITEMAP_LINK.name(), sitemapBaseValues.getSitemapUrl());
        startActivity(intent);
    }
}
