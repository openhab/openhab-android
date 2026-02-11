/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.connection.DemoConnection
import org.openhab.habdroid.databinding.ActivityItemPickerBinding
import org.openhab.habdroid.databinding.BottomSheetItemPickerCommandBinding
import org.openhab.habdroid.databinding.BottomSheetSelectionItemRadioButtonBinding
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.SuggestedCommandsFactory
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.parcelable
import org.openhab.habdroid.util.parcelableArrayList

abstract class AbstractItemPickerActivity :
    AbstractBaseActivity(),
    SwipeRefreshLayout.OnRefreshListener,
    ItemPickerAdapter.ItemClickListener,
    SearchView.OnQueryTextListener {
    override val forceNonFullscreen = true

    private var requestJob: Job? = null
    protected var initialHighlightItemName: String? = null

    protected lateinit var binding: ActivityItemPickerBinding
    private lateinit var itemPickerAdapter: ItemPickerAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var searchView: SearchView
    private var toolbarExtension: View? = null
    protected abstract var hintMessageId: Int
    protected abstract var hintButtonMessageId: Int
    protected abstract var hintIconId: Int
    protected var hideReadOnly = true

    private val suggestedCommandsFactory by lazy {
        SuggestedCommandsFactory(this, true)
    }
    protected var needToShowHint: Boolean = false
    protected open val forItemCommandOnly: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        binding.swiperefresh.apply {
            setOnRefreshListener(this@AbstractItemPickerActivity)
            applyColors()
        }

        binding.retryButton.setOnClickListener {
            loadItems()
        }

        toolbarExtension = inflateToolbarExtension(binding.appBar.toolbarExtensionStub)

        itemPickerAdapter = ItemPickerAdapter(this, this)
        layoutManager = LinearLayoutManager(this)

        binding.list.apply {
            layoutManager = this@AbstractItemPickerActivity.layoutManager
            adapter = itemPickerAdapter
        }

        binding.appBar.root.setLiftOnScrollTargetView(binding.list)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!searchView.isIconified) {
                    searchView.isIconified = true
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun inflateBinding(): CommonBinding {
        binding = ActivityItemPickerBinding.inflate(layoutInflater)
        return CommonBinding(binding.root, binding.appBar, binding.coordinator, binding.swiperefresh)
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
        searchView = searchItem.actionView as SearchView
        searchView.inputType = InputType.TYPE_CLASS_TEXT
        searchView.setOnQueryTextListener(this)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onItemClicked(item: Item) {
        val suggestedCommands = suggestedCommandsFactory.fill(item, !forItemCommandOnly)
        val entries = suggestedCommands.entries
            .map { entry -> CommandEntry(entry.command, entry.label) }
            .toMutableList()

        addAdditionalCommands(suggestedCommands, entries)

        val f = CommandChooserBottomSheet()
        f.arguments = f.createArguments(item, entries, suggestedCommands.shouldShowCustom)
        f.show(supportFragmentManager, "actionchooser")
    }

    protected open fun addAdditionalCommands(
        suggestedCommands: SuggestedCommandsFactory.SuggestedCommands,
        entries: MutableList<CommandEntry>
    ) {
        // no-op
    }

    protected open fun inflateToolbarExtension(stub: ViewStub): View? = null

    override fun onRefresh() {
        loadItems()
    }

    override fun onQueryTextSubmit(query: String): Boolean = false

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

        updateViewVisibility(loading = true, loadError = false, showHint = false)
        itemPickerAdapter.clear()

        requestJob = launch {
            ConnectionFactory.waitForInitialization()

            val connection = ConnectionFactory.primaryUsableConnection?.connection
            if (connection == null) {
                updateViewVisibility(loading = false, loadError = true, showHint = false)
                return@launch
            }

            if (connection is DemoConnection) {
                showSnackbar(
                    SNACKBAR_TAG_DEMO_MODE_ACTIVE,
                    R.string.info_demo_mode_short,
                    Snackbar.LENGTH_INDEFINITE,
                    R.string.turn_off
                ) {
                    getPrefs().edit {
                        putBoolean(PrefKeys.DEMO_MODE, false)
                    }
                    loadItems()
                }
            }

            try {
                var items = ItemClient.loadItems(connection)
                if (items == null) {
                    updateViewVisibility(loading = false, loadError = true, showHint = false)
                    Log.e(TAG, "Item request failure")
                    return@launch
                }
                if (hideReadOnly) {
                    items = items.filterNot { item -> item.readOnly }
                }

                if (forItemCommandOnly) {
                    // Contact Items cannot receive commands
                    items = items.filterNot { item -> item.type == Item.Type.Contact }
                }
                Log.d(TAG, "Item request success, got ${items.size} items")
                itemPickerAdapter.setItems(items)
                handleInitialHighlight()
                updateViewVisibility(loading = false, loadError = false, showHint = false)
                if (searchView.query.isNotEmpty()) {
                    itemPickerAdapter.filter(searchView.query.toString())
                }
            } catch (e: HttpClient.HttpException) {
                updateViewVisibility(loading = false, loadError = true, showHint = false)
                Log.e(TAG, "Item request failure", e)
            }
        }
    }

    protected abstract fun finish(item: Item, state: String?, mappedState: String? = state, tag: Any? = null)

    private fun handleInitialHighlight() {
        val highlightItem = initialHighlightItemName
        if (highlightItem.isNullOrEmpty()) {
            return
        }

        val position = itemPickerAdapter.findPositionForName(highlightItem)
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            binding.list.postDelayed({ itemPickerAdapter.highlightItem(position) }, 600)
        }

        initialHighlightItemName = null
    }

    protected fun updateViewVisibility(loading: Boolean, loadError: Boolean, showHint: Boolean) {
        val showEmpty = showHint || (!loading && (itemPickerAdapter.itemCount == 0 || loadError))
        binding.list.isVisible = !showEmpty
        toolbarExtension?.isGone = showEmpty
        binding.empty.isVisible = showEmpty
        binding.swiperefresh.isRefreshing = loading
        binding.emptyMessage.setText(
            when {
                loadError -> R.string.item_picker_list_error
                showHint -> hintMessageId
                else -> R.string.item_picker_list_empty
            }
        )
        binding.watermark.setImageResource(if (showHint) hintIconId else R.drawable.ic_connection_error)
        binding.retryButton.setText(if (showHint) hintButtonMessageId else R.string.try_again_button)
        binding.retryButton.isVisible = loadError || showHint
    }

    @Parcelize
    data class CommandEntry(val command: String?, val label: String, val tag: String? = null) : Parcelable

    companion object {
        private const val SNACKBAR_TAG_DEMO_MODE_ACTIVE = "demoModeActive"

        private val TAG = AbstractItemPickerActivity::class.java.simpleName
    }

    class CommandChooserBottomSheet :
        BottomSheetDialogFragment(),
        TextWatcher {
        private val item get() = requireArguments().parcelable<Item>("item")!!
        private val entries get() = requireArguments().parcelableArrayList<CommandEntry>("entries")!!
        private val showCustom get() = requireArguments().getBoolean("show_custom")

        private lateinit var binding: BottomSheetItemPickerCommandBinding

        fun createArguments(item: Item, entries: List<CommandEntry>, showCustom: Boolean) = bundleOf(
            "item" to item,
            "entries" to entries,
            "show_custom" to showCustom
        )

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = BottomSheetItemPickerCommandBinding.inflate(inflater, container, false)

            if (!showCustom) {
                binding.customEditorContainer.isGone = true
                // remove predefined 'custom' radio button
                binding.selectionGroup.removeAllViews()
            }

            entries.forEachIndexed { index, entry ->
                val button = BottomSheetSelectionItemRadioButtonBinding.inflate(
                    inflater,
                    binding.selectionGroup,
                    false
                ).root
                button.text = entry.label
                button.id = entry.hashCode()
                binding.selectionGroup.addView(button, index)
            }

            binding.customSave.setOnClickListener {
                val activity = requireActivity() as AbstractItemPickerActivity
                activity.finish(item, binding.customEditor.text.toString())
                dismissAllowingStateLoss()
            }

            binding.customEditor.addTextChangedListener(this)

            binding.selectionGroup.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.custom) {
                    binding.customEditor.requestFocus()
                } else {
                    val activity = requireActivity() as AbstractItemPickerActivity
                    val entry = entries.first { e -> e.hashCode() == checkedId }
                    activity.finish(item, entry.command, entry.label, entry.tag)
                    dismissAllowingStateLoss()
                }
            }
            binding.customEditor.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.selectionGroup.check(R.id.custom)
                }
            }

            return binding.root
        }

        override fun beforeTextChanged(s: CharSequence?, before: Int, count: Int, after: Int) {
            // no-op
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // no-op
        }

        override fun afterTextChanged(s: Editable?) {
            binding.customSave.isEnabled = s?.isNotEmpty() == true
        }
    }
}
