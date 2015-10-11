package org.openhab.habdroid.service;

/**
 * Created by tobiasamon on 31.03.15.
 */
public class SitemapBaseValues {

    private final String mSitemapName;

    private final String mSitemapUrl;

    public SitemapBaseValues(String sitemapName, String sitemapUrl) {
        mSitemapName = sitemapName;
        mSitemapUrl = sitemapUrl;
    }

    public String getSitemapName() {
        return mSitemapName;
    }

    public String getSitemapUrl() {
        return mSitemapUrl;
    }
}
