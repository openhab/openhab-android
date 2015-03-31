package org.openhab.habdroid.service;

import org.openhab.habdroid.model.OpenHABWidget;

import java.util.List;

/**
 * Created by tobiasamon on 31.03.15.
 */
public interface MobileServiceClient {

    public void connected();

    public void connectionSuspended();

    public void sitemapBaseMissing();

    public void onSitemapLoaded(List<OpenHABWidget> widgetList, String sitemapLink);
}
