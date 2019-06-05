package org.openhab.habdroid.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import okhttp3.Call
import okhttp3.Headers
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.toItem
import org.openhab.habdroid.ui.widget.DividerItemDecoration
import org.openhab.habdroid.util.*

class ItemPickerActivity : AbstractBaseActivity(), SwipeRefreshLayout.OnRefreshListener,
        ItemPickerAdapter.ItemClickListener, SearchView.OnQueryTextListener {
    override val forceNonFullscreen = true

    private var requestHandle: Call? = null
    private var initialHightlightItemName: String? = null

    private lateinit var itemPickerAdapter: ItemPickerAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var swipeLayout: SwipeRefreshLayout
    private lateinit var emptyView: View
    private lateinit var emptyMessage: TextView
    private lateinit var retryButton: TextView

    private val suggestedCommandsFactory by lazy {
        SuggestedCommandsFactory(this, true)
    }
    private var isDisabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_item_picker)

        val toolbar = findViewById<Toolbar>(R.id.openhab_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        swipeLayout = findViewById(R.id.swipe_container)
        swipeLayout.setOnRefreshListener(this)
        swipeLayout.applyColors(R.attr.colorPrimary, R.attr.colorAccent)

        recyclerView = findViewById(android.R.id.list)
        emptyView = findViewById(android.R.id.empty)
        emptyMessage = findViewById(R.id.empty_message)
        retryButton = findViewById(R.id.retry_button)

        retryButton.setOnClickListener {
            if (isDisabled) {
                getPrefs().edit {
                    putBoolean(Constants.PREFERENCE_TASKER_PLUGIN_ENABLED, true)
                }
                isDisabled = false
                loadItems()
            }
        }

        itemPickerAdapter = ItemPickerAdapter(this, this)
        layoutManager = LinearLayoutManager(this)

        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(this))
        recyclerView.adapter = itemPickerAdapter

        val editItem = intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE)
        initialHightlightItemName = editItem?.getString(EXTRA_ITEM_NAME)

        if (!getPrefs().isTaskerPluginEnabled()) {
            isDisabled = true
            updateViewVisibility(false, false, true)
        }
    }

    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        loadItems()
    }

    public override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
        // Cancel request for items if there was any
        requestHandle?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.item_picker, menu)

        val searchItem = menu.findItem(R.id.app_bar_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")
        if (item.itemId == android.R.id.home) {
            finish(false, null, null)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadItems() {
        if (isDisabled) {
            return
        }

        val connection = ConnectionFactory.usableConnectionOrNull
        if (connection == null) {
            updateViewVisibility(false, true, false)
            return
        }

        itemPickerAdapter.clear()
        updateViewVisibility(true, false, false)

        val client = connection.asyncHttpClient
        requestHandle = client["rest/items", object : AsyncHttpClient.StringResponseHandler() {
            override fun onSuccess(response: String, headers: Headers) {
                try {
                    val items = JSONArray(response)
                            .map { obj -> obj.toItem() }
                            .filterNot { item -> item.readOnly }
                    Log.d(TAG, "Item request success, got " + items.size + " items")
                    itemPickerAdapter.setItems(items)
                    handleInitialHighlight()
                    updateViewVisibility(false, false, false)
                } catch (e: JSONException) {
                    Log.d(TAG, "Item response could not be parsed", e)
                    updateViewVisibility(false, true, false)
                }

            }

            override fun onFailure(request: Request, statusCode: Int, error: Throwable) {
                updateViewVisibility(false, true, false)
                Log.e(TAG, "Item request failure", error)
            }
        }]
    }

    override fun onItemClicked(item: Item) {
        val suggestedCommands = suggestedCommandsFactory.fill(item)
        val labels = suggestedCommands.labels
        val commands = suggestedCommands.commands

        if (suggestedCommands.shouldShowCustom) {
            labels.add(getString(R.string.item_picker_custom))
        }

        val labelArray = labels.toTypedArray()
        AlertDialog.Builder(this)
                .setTitle(R.string.item_picker_dialog_title)
                .setItems(labelArray) { _, which ->
                    if (which == labelArray.size - 1 && suggestedCommands.shouldShowCustom) {
                        val input = EditText(this)
                        input.inputType = suggestedCommands.inputTypeFlags
                        val customDialog = AlertDialog.Builder(this)
                                .setTitle(getString(R.string.item_picker_custom))
                                .setView(input)
                                .setPositiveButton(android.R.string.ok) { _, _ -> finish(true, item, input.text.toString()) }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        input.setOnFocusChangeListener { _, hasFocus ->
                            val mode = if (hasFocus)
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                            else
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                            customDialog.window?.setSoftInputMode(mode)
                        }
                    } else {
                        finish(true, item, commands[which])
                    }
                }
                .show()
    }

    private fun finish(success: Boolean, item: Item?, state: String?) {
        val intent = Intent()

        if (success) {
            val blurb = getString(R.string.item_picker_blurb, item!!.label, item.name, state)
            intent.putExtra(TaskerIntent.EXTRA_STRING_BLURB, blurb)

            intent.putExtra(TaskerIntent.EXTRA_BUNDLE, bundleOf(
                    EXTRA_ITEM_NAME to item.name,
                    EXTRA_ITEM_STATE to state
            ))
        }

        val resultCode = if (success) RESULT_OK else RESULT_CANCELED
        setResult(resultCode, intent)
        finish()
    }

    override fun onRefresh() {
        loadItems()
    }

    private fun handleInitialHighlight() {
        val highlightItem = initialHightlightItemName
        if (highlightItem.isNullOrEmpty()) {
            return
        }

        val position = itemPickerAdapter.findPositionForName(highlightItem)
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            recyclerView.postDelayed({ itemPickerAdapter.highlightItem(position) }, 600)
        }

        initialHightlightItemName = null
    }

    private fun updateViewVisibility(loading: Boolean, loadError: Boolean, isDisabled: Boolean) {
        val showEmpty = isDisabled || !loading && (itemPickerAdapter.itemCount == 0 || loadError)
        recyclerView.isVisible = !showEmpty
        emptyView.isVisible = showEmpty
        swipeLayout.isRefreshing = loading
        emptyMessage.setText(when {
            loadError -> R.string.item_picker_list_error
            isDisabled -> R.string.settings_tasker_plugin_summary
            else -> R.string.item_picker_list_empty
        })
        retryButton.setText(if (isDisabled) R.string.turn_on else R.string.try_again_button)
        retryButton.isVisible = loadError || isDisabled
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        itemPickerAdapter.filter(newText)
        return true
    }

    companion object {
        private val TAG = ItemPickerActivity::class.java.simpleName

        val EXTRA_ITEM_NAME = "itemName"
        val EXTRA_ITEM_STATE = "itemState"
    }
}
