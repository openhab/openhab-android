package org.openhab.habdroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.openhab.habdroid.service.OpenHABConnectionService;

public class BootBroadcastReceiver extends BroadcastReceiver {
    public BootBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, OpenHABConnectionService.class);
        context.startService(service);
    }
}
