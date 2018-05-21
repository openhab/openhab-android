package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import org.openhab.habdroid.util.BleBeaconConnector;
import org.openhab.habdroid.util.Util;

public class OpenHABBleActivity extends AppCompatActivity {

    BleBeaconConnector bleBeaconConnector;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        bleBeaconConnector = BleBeaconConnector.getInstance(this);
        bleBeaconConnector.scanLeServiceCompact();
    }

}
