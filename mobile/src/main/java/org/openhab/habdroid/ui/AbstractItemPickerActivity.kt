/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.toItem
import org.openhab.habdroid.ui.widget.DividerItemDecoration
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.SuggestedCommandsFactory
import org.openhab.habdroid.util.map

abstract class AbstractItemPickerActivity : AbstractBaseActivity(), SwipeRefreshLayout.OnRefreshListener,
    ItemPickerAdapter.ItemClickListener, SearchView.OnQueryTextListener {
    override val forceNonFullscreen = true

    private var requestJob: Job? = null
    protected var initialHighlightItemName: String? = null

    private lateinit var itemPickerAdapter: ItemPickerAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var swipeLayout: SwipeRefreshLayout
    private lateinit var emptyView: View
    private lateinit var emptyMessage: TextView
    private lateinit var watermark: ImageView
    protected lateinit var retryButton: TextView
    protected abstract var hintMessageId: Int
    protected abstract var hintButtonMessageId: Int
    protected abstract var hintIconId: Int

    private val suggestedCommandsFactory by lazy {
        SuggestedCommandsFactory(this, true)
    }
    protected var needToShowHint: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_item_picker)
        setResult(RESULT_CANCELED)

        val toolbar = findViewById<Toolbar>(R.id.openhab_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        swipeLayout = findViewById(R.id.activity_content)
        swipeLayout.setOnRefreshListener(this)
        swipeLayout.applyColors(R.attr.colorPrimary, R.attr.colorAccent)

        recyclerView = findViewById(android.R.id.list)
        emptyView = findViewById(android.R.id.empty)
        emptyMessage = findViewById(R.id.empty_message)
        watermark = findViewById(R.id.watermark)
        retryButton = findViewById(R.id.retry_button)

        retryButton.setOnClickListener {
            loadItems()
        }

        itemPickerAdapter = ItemPickerAdapter(this, this)
        layoutManager = LinearLayoutManager(this)

        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(this))
        recyclerView.adapter = itemPickerAdapter
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        loadItems()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
        // Cancel request for items if there was any
        requestJob?.cancel()
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
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        input.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                    }
                    val customDialog = AlertDialog.Builder(this)
                        .setTitle(getString(R.string.item_picker_custom))
                        .setView(input)
                        .setPositiveButton(android.R.string.ok) { _, _ -> finish(item, input.text.toString()) }
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
                    finish(item, commands[which], labels[which])
                }
            }
            .show()
    }

    override fun onRefresh() {
        loadItems()
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        itemPickerAdapter.filter(newText)
        return true
    }

    protected fun loadItems() {
        Log.d(TAG, "loadItems()")
        if (needToShowHint) {
            Log.d(TAG, "Hint is shown")
            return
        }

        val connection = ConnectionFactory.usableConnectionOrNull
        if (connection == null) {
            updateViewVisibility(loading = false, loadError = true, showHint = false)
            return
        }

        itemPickerAdapter.clear()
        updateViewVisibility(loading = true, loadError = false, showHint = false)

        requestJob = launch {
            try {
                val result = connection.httpClient.get("rest/items").asText()
                val items = JSONArray(result.response)
                    .map { obj -> obj.toItem() }
                    .filterNot { item -> item.readOnly }
                Log.d(TAG, "Item request success, got ${items.size} items")
                itemPickerAdapter.setItems(items)
                handleInitialHighlight()
                updateViewVisibility(loading = false, loadError = false, showHint = false)
            } catch (e: JSONException) {
                Log.d(TAG, "Item response could not be parsed", e)
                updateViewVisibility(loading = false, loadError = true, showHint = false)
            } catch (e: HttpClient.HttpException) {
                updateViewVisibility(loading = false, loadError = true, showHint = false)
                Log.e(TAG, "Item request failure", e)
            }
        }
    }

    protected abstract fun finish(item: Item, state: String, mappedState: String)

    private fun finish(item: Item, state: String) {
        finish(item, state, state)
    }

    private fun handleInitialHighlight() {
        val highlightItem = initialHighlightItemName
        if (highlightItem.isNullOrEmpty()) {
            return
        }

        val position = itemPickerAdapter.findPositionForName(highlightItem)
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            recyclerView.postDelayed({ itemPickerAdapter.highlightItem(position) }, 600)
        }

        initialHighlightItemName = null
    }

    protected fun updateViewVisibility(loading: Boolean, loadError: Boolean, showHint: Boolean) {
        val showEmpty = showHint || !loading && (itemPickerAdapter.itemCount == 0 || loadError)
        recyclerView.isVisible = !showEmpty
        emptyView.isVisible = showEmpty
        swipeLayout.isRefreshing = loading
        emptyMessage.setText(when {
            loadError -> R.string.item_picker_list_error
            showHint -> hintMessageId
            else -> R.string.item_picker_list_empty
        })
        watermark.setImageResource(if (showHint) hintIconId else R.drawable.ic_connection_error)
        retryButton.setText(if (showHint) hintButtonMessageId else R.string.try_again_button)
        retryButton.isVisible = loadError || showHint
    }

    companion object {
        private val TAG = AbstractItemPickerActivity::class.java.simpleName
    }
}
