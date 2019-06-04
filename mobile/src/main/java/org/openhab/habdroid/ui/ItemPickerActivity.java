package org.openhab.habdroid.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
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
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.SuggestedCommandsFactory;
import org.openhab.habdroid.util.TaskerIntent;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.List;

public class ItemPickerActivity extends AbstractBaseActivity
        implements SwipeRefreshLayout.OnRefreshListener, View.OnClickListener,
        ItemPickerAdapter.ItemClickListener, SearchView.OnQueryTextListener {
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
    private TextView mRetryButton;
    private SuggestedCommandsFactory mSuggestedCommandsFactory;
    private boolean mIsDisabled;
    private SharedPreferences mSharedPreferences;

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

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (!mSharedPreferences.getBoolean(Constants.PREFERENCE_TASKER_PLUGIN_ENABLED, false)) {
            mIsDisabled = true;
            updateViewVisibility(false, false, true);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.item_picker, menu);

        final MenuItem searchItem = menu.findItem(R.id.app_bar_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setOnQueryTextListener(this);

        return true;
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
        if (mIsDisabled) {
            return;
        }
        Connection connection;
        try {
            connection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            connection = null;
        }
        if (connection == null) {
            updateViewVisibility(false, true, false);
            return;
        }

        mItemPickerAdapter.clear();
        updateViewVisibility(true, false, false);

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
                    mItemPickerAdapter.setItems(items);
                    handleInitialHighlight();
                    updateViewVisibility(false, false, false);
                } catch (JSONException e) {
                    Log.d(TAG, "Item response could not be parsed", e);
                    updateViewVisibility(false, true, false);
                }
            }

            @Override
            public void onFailure(Request request, int statusCode, Throwable error) {
                updateViewVisibility(false, true, false);
                Log.e(TAG, "Item request failure", error);
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view == mRetryButton) {
            if (mIsDisabled) {
                mSharedPreferences
                        .edit()
                        .putBoolean(Constants.PREFERENCE_TASKER_PLUGIN_ENABLED, true)
                        .apply();
                mIsDisabled = false;
            }
            loadItems();
        }
    }

    @Override
    public void onItemClicked(Item item) {
        if (item == null) {
            return;
        }

        if (mSuggestedCommandsFactory == null) {
            mSuggestedCommandsFactory = new SuggestedCommandsFactory(this, true);
        }

        SuggestedCommandsFactory.SuggestedCommands suggestedCommands =
                mSuggestedCommandsFactory.fill(item);

        List<String> labels = suggestedCommands.labels;
        List<String> commands = suggestedCommands.commands;

        if (suggestedCommands.shouldShowCustom) {
            labels.add(getString(R.string.item_picker_custom));
        }

        final String[] labelArray = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.item_picker_dialog_title)
                .setItems(labelArray, (dialog, which) -> {
                    if (which == labelArray.length - 1 && suggestedCommands.shouldShowCustom) {
                        final EditText input = new EditText(this);
                        input.setInputType(suggestedCommands.inputTypeFlags);
                        AlertDialog customDialog = new AlertDialog.Builder(this)
                                .setTitle(getString(R.string.item_picker_custom))
                                .setView(input)
                                .setPositiveButton(android.R.string.ok, (dialog1, which1) -> {
                                    finish(true, item, input.getText().toString());
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        input.setOnFocusChangeListener((v, hasFocus) -> {
                            int mode = hasFocus
                                    ? WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                    : WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
                            customDialog.getWindow().setSoftInputMode(mode);
                        });
                    } else {
                        finish(true, item, commands.get(which));
                    }
                })
                .show();
    }

    private void finish(boolean success, Item item, String state) {
        Intent intent = new Intent();

        if (success) {
            String blurb = getString(R.string.item_picker_blurb, item.label(), item.name(), state);
            intent.putExtra(TaskerIntent.EXTRA_STRING_BLURB, blurb);

            Bundle bundle = new Bundle();
            bundle.putString(EXTRA_ITEM_NAME, item.name());
            bundle.putString(EXTRA_ITEM_STATE, state);
            intent.putExtra(TaskerIntent.EXTRA_BUNDLE, bundle);
        }

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

    private void updateViewVisibility(boolean loading, boolean loadError, boolean isDisabled) {
        boolean showEmpty =  isDisabled
                || (!loading && (mItemPickerAdapter.getItemCount() == 0 || loadError));
        mRecyclerView.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        mSwipeLayout.setRefreshing(loading);
        @StringRes int message;
        if (loadError) {
            message = R.string.item_picker_list_error;
        } else if (isDisabled) {
            message = R.string.settings_tasker_plugin_summary;
        } else {
            message = R.string.item_picker_list_empty;
        }
        mEmptyMessage.setText(message);
        mRetryButton.setText(isDisabled ? R.string.turn_on : R.string.try_again_button);
        mRetryButton.setVisibility(loadError || isDisabled ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mItemPickerAdapter.filter(newText);
        return true;
    }
}
