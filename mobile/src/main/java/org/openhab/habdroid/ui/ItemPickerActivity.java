package org.openhab.habdroid.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.TaskerIntent;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;

public class ItemPickerActivity extends AbstractBaseActivity
        implements SwipeRefreshLayout.OnRefreshListener, View.OnClickListener,
        ItemPickerAdapter.ItemClickListener {
    private static final String TAG = ItemPickerActivity.class.getSimpleName();

    public static final String EXTRA_ITEM_NAME = "itemName";
    public static final String EXTRA_ITEM_STATE = "itemState";

    private Call mRequestHandle;
    private String mInitialHightlightItemName;

    private ItemPickerAdapter mItemPickerAdapter;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private SwipeRefreshLayout mSwipeLayout;
    private View mEmptyView;
    private TextView mEmptyMessage;
    private View mRetryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        forceNonFullscreen();
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_item_picker);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mSwipeLayout = findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        Util.applySwipeLayoutColors(mSwipeLayout, R.attr.colorPrimary, R.attr.colorAccent);

        mRecyclerView = findViewById(android.R.id.list);
        mEmptyView = findViewById(android.R.id.empty);
        mEmptyMessage = findViewById(R.id.empty_message);
        mRetryButton = findViewById(R.id.retry_button);
        mRetryButton.setOnClickListener(this);

        mItemPickerAdapter = new ItemPickerAdapter(this, this);
        mLayoutManager = new LinearLayoutManager(this);

        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this));
        mRecyclerView.setAdapter(mItemPickerAdapter);

        Bundle editItem = getIntent().getBundleExtra(TaskerIntent.EXTRA_BUNDLE);
        if (editItem != null && !TextUtils.isEmpty(editItem.getString(EXTRA_ITEM_NAME))) {
            mInitialHightlightItemName = editItem.getString(EXTRA_ITEM_NAME);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        loadItems();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        // Cancel request for items if there was any
        if (mRequestHandle != null) {
            mRequestHandle.cancel();
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected()");
        if (item.getItemId() == android.R.id.home) {
            finish(false, null, null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadItems() {
        Connection connection;
        try {
            connection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            connection = null;
        }
        if (connection == null) {
            updateViewVisibility(false, true);
            return;
        }

        mItemPickerAdapter.clear();
        updateViewVisibility(true, false);

        final AsyncHttpClient client = connection.getAsyncHttpClient();
        mRequestHandle = client.get("rest/items", new AsyncHttpClient.StringResponseHandler() {
            @Override
            public void onSuccess(String responseBody, Headers headers) {
                try {
                    ArrayList<Item> items = new ArrayList<>();
                    JSONArray jsonArray = new JSONArray(responseBody);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject itemJson = jsonArray.getJSONObject(i);
                        Item item = Item.fromJson(itemJson);
                        if (!item.readOnly()) {
                            items.add(item);
                        }
                    }
                    Log.d(TAG, "Item request success, got " + items.size() + " items");
                    mItemPickerAdapter.addLoadedItems(items);
                    handleInitialHighlight();
                    updateViewVisibility(false, false);
                } catch (JSONException e) {
                    Log.d(TAG, "Item response could not be parsed", e);
                    updateViewVisibility(false, true);
                }
            }

            @Override
            public void onFailure(Request request, int statusCode, Throwable error) {
                updateViewVisibility(false, true);
                Log.e(TAG, "Item request failure", error);
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view == mRetryButton) {
            loadItems();
        }
    }

    @Override
    public void onItemClicked(Item item) {
        if (item == null) {
            return;
        }

        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> commands = new ArrayList<>();

        if (item.isOfTypeOrGroupType(Item.Type.Switch)) {
            labels.add(getString(R.string.nfc_action_on));
            commands.add("ON");
            labels.add(getString(R.string.nfc_action_off));
            commands.add("OFF");
            labels.add(getString(R.string.nfc_action_toggle));
            commands.add("TOGGLE");
        } else if (item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
            labels.add(getString(R.string.nfc_action_up));
            commands.add("UP");
            labels.add(getString(R.string.nfc_action_down));
            commands.add("DOWN");
            labels.add(getString(R.string.nfc_action_toggle));
            commands.add("TOGGLE");
        } else if (item.isOfTypeOrGroupType(Item.Type.Number)
                || item.isOfTypeOrGroupType(Item.Type.Dimmer)) {
            labels.add("0");
            commands.add("0");
            labels.add("100");
            commands.add("100");
        }

        labels.add(getString(R.string.item_picker_custom));

        final String[] labelArray = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.item_picker_dialog_title)
                .setItems(labelArray, (dialog, which) -> {
                    if (which < labelArray.length - 1) {
                        finish(true, item.name(), commands.get(which));
                    } else {
                        final EditText input = new EditText(this);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT);
                        input.setLayoutParams(lp);
                        new AlertDialog.Builder(this)
                                .setTitle(getString(R.string.item_picker_custom))
                                .setView(input)
                                .setPositiveButton(android.R.string.ok, (dialog1, which1) -> {
                                    finish(true, item.name(), input.getText().toString());
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                })
                .show();
    }

    private void finish(boolean success, String itemName, String state) {
        Intent intent = new Intent();

        String blurb = getString(R.string.item_picker_blurb, itemName, state);
        intent.putExtra(TaskerIntent.EXTRA_STRING_BLURB, blurb);

        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_ITEM_NAME, itemName);
        bundle.putString(EXTRA_ITEM_STATE, state);
        intent.putExtra(TaskerIntent.EXTRA_BUNDLE, bundle);

        int resultCode = success ? RESULT_OK : RESULT_CANCELED;
        setResult(resultCode, intent);
        finish();
    }

    @Override
    public void onRefresh() {
        loadItems();
    }

    private void handleInitialHighlight() {
        if (TextUtils.isEmpty(mInitialHightlightItemName)) {
            return;
        }

        final int position = mItemPickerAdapter.findPositionForName(mInitialHightlightItemName);
        if (position >= 0) {
            mLayoutManager.scrollToPositionWithOffset(position, 0);
            mRecyclerView.postDelayed(() -> mItemPickerAdapter.highlightItem(position), 600);
        }

        mInitialHightlightItemName = null;
    }

    private void updateViewVisibility(boolean loading, boolean loadError) {
        boolean showEmpty = !loading && (mItemPickerAdapter.getItemCount() == 0 || loadError);
        mRecyclerView.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        mSwipeLayout.setRefreshing(loading);
        mEmptyMessage.setText(
                loadError ? R.string.item_picker_list_error : R.string.item_picker_list_empty);
        mRetryButton.setVisibility(loadError ? View.VISIBLE : View.GONE);
    }
}
