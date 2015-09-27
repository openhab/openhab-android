package org.openhab.habdroid.service;

/**
 * Created by tobiasamon on 03.04.15.
 */
public interface MobileServiceBaseClient extends MobileServiceClient {

    public void sitemapBaseMissing();

    public void sitemapBaseFound(SitemapBaseValues baseValues);
}
