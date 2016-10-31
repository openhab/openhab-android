package org.openhab.habdroid.ui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;

import org.json.JSONArray;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.util.HomeWidgetUpdateJob;
import org.openhab.habdroid.util.HomeWidgetUtils;

import java.util.ArrayList;

public class HomeWidgetConfigurationActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener{
    private static final String TAG = HomeWidgetConfigurationActivity.class.getSimpleName();


    int mAppWidgetId = -1;
    Spinner itemName;
    EditText itemLabel;
    Spinner iconSpinner;
    CheckBox pinCheckbox;
    EditText pinText;


    ArrayList<OpenHABItem> itemList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setResult(RESULT_CANCELED);


        setContentView(R.layout.activity_home_widget_configuration);

        //http://homer:8080/rest/items?type=Switch&recursive=true



        //itemName = (EditText) findViewById(R.id.itemName);
        itemLabel = (EditText) findViewById(R.id.itemLabel);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }


        Button b = (Button) findViewById(R.id.buttonSaveWidget);
        b.setOnClickListener(this);

        itemName = (Spinner) findViewById(R.id.itemName);
        pinCheckbox = (CheckBox) findViewById(R.id.checkPin);
        pinCheckbox.setOnCheckedChangeListener(this);

        pinText = (EditText) findViewById(R.id.pinText);


        Log.d(TAG, "WIDGET ID: " + mAppWidgetId);


        iconSpinner = (Spinner) findViewById(R.id.iconSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.openhab_icons, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        iconSpinner.setAdapter(adapter);

        new DownloadJSON().execute();

    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.buttonSaveWidget:

                final Context context = HomeWidgetConfigurationActivity.this;

                OpenHABItem item = (OpenHABItem) itemName.getSelectedItem();

                HomeWidgetUtils.saveWidgetPrefs(context, mAppWidgetId, "name", item.getName());
                HomeWidgetUtils.saveWidgetPrefs(context, mAppWidgetId, "label", itemLabel.getText().toString());
                HomeWidgetUtils.saveWidgetPrefs(context, mAppWidgetId, "icon", (String) iconSpinner.getSelectedItem());
                HomeWidgetUtils.saveWidgetPrefs(context, mAppWidgetId, "pin", pinCheckbox.isEnabled() ? pinText.getText().toString() : null);

                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());

                new HomeWidgetUpdateJob(context, appWidgetManager, mAppWidgetId).execute();






                //TODO: save max widget id



                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                setResult(RESULT_OK, resultValue);



                finish();

                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        pinText.setEnabled(isChecked);
    }

    private class DownloadJSON extends AsyncTask<Void, Void, Void> {

        JSONArray jsonarray;

        @Override
        protected Void doInBackground(Void... params) {


            itemList = new ArrayList<OpenHABItem>();

            jsonarray = HomeWidgetUtils
                    .getJSONArrayFromURL(getApplicationContext(), "rest/items?type=Switch&recursive=true");

            try {

                for (int i = 0; i < jsonarray.length(); i++) {
                    itemList.add(new OpenHABItem(jsonarray.getJSONObject(i)));
                }
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void args) {
            // Locate the spinner in activity_main.xml
            Spinner mySpinner = (Spinner) findViewById(R.id.itemName);

            // Spinner adapter
            mySpinner
                    .setAdapter(new OpenhabItemListAdapter(getApplicationContext(),
                            itemList));

            // Spinner on item click listener
            mySpinner
                    .setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                        @Override
                        public void onItemSelected(AdapterView<?> arg0,
                                                   View arg1, int position, long arg3) {

                            EditText itemLabel = (EditText) findViewById(R.id.itemLabel);
                            itemLabel.setText(itemList.get(position).getName());



                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> arg0) {
                            // TODO Auto-generated method stub
                        }
                    });
        }
    }
}
