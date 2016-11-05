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
import org.openhab.habdroid.util.HomeWidgetUtils;

public class PinDialogActivity extends Activity implements PinDialogListener {

    private PinDialog pd;

    //private Intent intent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_dialog);

        int appWidgetId = Integer.parseInt(getIntent().getData().getLastPathSegment());

        String pin = HomeWidgetUtils.loadWidgetPrefs(getApplicationContext(), appWidgetId, "name");

        pd = new PinDialog(this, pin);
        pd.show();
    }

    @Override
    public void onPinEntered(String pin) {

        Intent originIntent = getIntent();

        Intent active = new Intent().setClass(getApplicationContext(), HomeWidgetProvider.class);
        active.setAction(HomeWidgetProvider.ACTION_BUTTON_CLICKED);

        Uri data = originIntent.getData();
        active.setData(data);

        active.putExtra("item_name", originIntent.getStringExtra("item_name"));
        active.putExtra("item_command", originIntent.getStringExtra("item_command"));
        sendBroadcast(active);

        this.finish();
    }

    @Override
    public void onPinAborted() {
        this.finish();
    }
}
