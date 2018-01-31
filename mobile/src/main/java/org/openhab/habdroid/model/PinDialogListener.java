package org.openhab.habdroid.model;

/**
 * Created by redeye on 13.08.16.
 */
public interface PinDialogListener {
    public void onPinEntered(String pin);
    public void onPinAborted();
}
