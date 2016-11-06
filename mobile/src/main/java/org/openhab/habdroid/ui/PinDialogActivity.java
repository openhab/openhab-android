package org.openhab.habdroid.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.PinDialogListener;
import org.openhab.habdroid.util.HomeWidgetSendCommandJob;
import org.openhab.habdroid.util.HomeWidgetUpdateJob;
import org.openhab.habdroid.util.HomeWidgetUtils;

public class PinDialogActivity extends Activity implements PinDialogListener {

    private PinDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_dialog);

        int appWidgetId = Integer.parseInt(getIntent().getData().getLastPathSegment());

        String pin = HomeWidgetUtils.loadWidgetPrefs(getApplicationContext(), appWidgetId, "pin");

        pd = new PinDialog(this, pin);
        pd.show();
    }

    @Override
    public void onPinEntered(String pin) {

        String item = getIntent().getStringExtra("item_name");
        String command = getIntent().getStringExtra("item_command");

        new HomeWidgetSendCommandJob(getApplicationContext(), item, command).execute();
        new HomeWidgetUpdateJob(getApplicationContext(), Integer.parseInt(getIntent().getData().getLastPathSegment())).execute();


        this.finish();
    }

    @Override
    public void onPinAborted() {
        this.finish();
    }
}
