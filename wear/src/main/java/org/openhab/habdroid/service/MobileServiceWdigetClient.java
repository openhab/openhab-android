package org.openhab.habdroid.service;

import org.openhab.habdroid.model.OpenHABWidget;

import java.util.List;

/**
 * Created by tobiasamon on 03.04.15.
 */
public interface MobileServiceWdigetClient extends MobileServiceClient {

    public void commandExecuted(boolean success);

}
