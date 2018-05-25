package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.openhab.habdroid.util.bleBeaconUtil.BleBeaconConnector;
import org.openhab.habdroid.util.Util;

public class OpenHABBleActivity extends AppCompatActivity {

    BleBeaconConnector bleBeaconConnector;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        bleBeaconConnector = BleBeaconConnector.getInstance(this);
        if (bleBeaconConnector.isNotSupport()){
            return;
        }

        bleBeaconConnector.scanLeServiceCompact();
    }

}
