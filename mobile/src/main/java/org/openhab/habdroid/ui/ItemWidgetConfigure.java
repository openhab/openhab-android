package org.openhab.habdroid.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABTracker;
import org.openhab.habdroid.core.OpenHABTrackerReceiver;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyAsyncHttpClient;

import java.util.ArrayList;
import java.util.Arrays;

public class ItemWidgetConfigure extends ActionBarActivity implements OpenHABTrackerReceiver {

    private static final String TAG = "ItemWidgetConfigure";
    public static final String OPENHAB_BASE_URL_EXTRA = "openHABBaseUrl";

    private String mOpenHABBaseUrl;
    private String selectedItem;
    private String selectedCommand;

    private MyAsyncHttpClient mAsyncHttpClient;

    private OpenHABTracker mOpenHABTracker;

    private ListView itemsListView ;
    private ArrayAdapter<String> listAdapter ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Called");

        //setContentView(R.layout.activity_item_widget_configure);
        setContentView(R.layout.fragment_item_widget_configure);

        itemsListView = (ListView) findViewById(R.id.listItems);

        initHttpClient();

        Button btn = (Button) findViewById(R.id.btn_create_widget);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RadioGroup rg = (RadioGroup) findViewById(R.id.cmd);
                RadioButton rb = (RadioButton) findViewById(rg.getCheckedRadioButtonId());

                selectedCommand = (String) rb.getText();
                Log.d(TAG, "Command=" + selectedCommand);

                if (selectedItem.length() == 0 || selectedCommand.length() == 0) {
                    Log.d(TAG, "Not all selections made");
                } else {
                    Log.d(TAG, "openhab://?item="+selectedItem+"&command="+selectedCommand);
                    ShortcutIcon();
                    finish();
                }
            }
        });
    }

    private void initHttpClient() {
        SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        String username = mSettings.getString(Constants.PREFERENCE_USERNAME, null);
        String password = mSettings.getString(Constants.PREFERENCE_PASSWORD, null);
        mAsyncHttpClient = new MyAsyncHttpClient(this);
        mAsyncHttpClient.setBasicAuth(username, password);

        if (mOpenHABTracker == null) {
            Log.d(TAG, "No openHABBaseUrl passed, starting OpenHABTracker");
            mOpenHABTracker = new OpenHABTracker(this, getString(R.string.openhab_service_type), false);
            mOpenHABTracker.start();
        }
    }

    @Override
    public void onOpenHABTracked(String baseUrl, String message) {
        Log.d(TAG, "onOpenHABTracked(): " + baseUrl);
        mOpenHABBaseUrl = baseUrl;

        // TODO: parse XML to display user's names and items
        String[] items = new String[] {"Heating_GF_Corridor", "Shutter_all"};

        // Create ArrayAdapter using the planet list.
        listAdapter = new ArrayAdapter<String>(this, R.layout.item , items);

        // Set the ArrayAdapter as the ListView's adapter.
        itemsListView.setAdapter( listAdapter );

        itemsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedItem = listAdapter.getItem(position).toString();
                Log.d(TAG, "CLICK: " + selectedItem);

                findViewById(R.id.linearLayout3).setVisibility(View.VISIBLE);
                findViewById(R.id.linearLayout4).setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onError(String error) {
        Log.d(TAG, "onError(): " + error);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (mOpenHABTracker != null) {
            mOpenHABTracker.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onBonjourDiscoveryStarted() {
        Log.d(TAG, "onBonjourDiscoveryStarted()");
    }

    @Override
    public void onBonjourDiscoveryFinished() {
        Log.d(TAG, "onBonjourDiscoveryFinished()");
    }

    private void ShortcutIcon() {
        Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("openhab://?item="+selectedItem+"&command="+selectedCommand));
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        shortcutIntent.addCategory("android.intent.category.DEFAULT");

        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, selectedItem);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(getApplicationContext(), R.drawable.openhabicon_light));
        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        getApplicationContext().sendBroadcast(addIntent);
    }
}
