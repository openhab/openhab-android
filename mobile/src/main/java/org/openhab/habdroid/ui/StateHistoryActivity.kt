/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.ActivityStateHistoryBinding
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.PersistenceDataLoader.Series
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelable

/**
 * Shows the persisted states of Items that can't be plotted in a chart (e.g. String or DateTime Items)
 * as a list of state changes. Older states are loaded in growing time windows while scrolling.
 */
class StateHistoryActivity : AbstractBaseActivity() {
    private lateinit var binding: ActivityStateHistoryBinding
    private lateinit var widget: Widget
    private val dataCacheFragment get() =
        supportFragmentManager.findFragmentByTag("cache") as? HistoryDataCacheFragment
    private val serverProperties: ServerProperties? get() = intent.parcelable(EXTRA_SERVER_PROPS)
    private val adapter = StateHistoryAdapter()
    private var loadJob: Job? = null
    private var loadMoreFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widget = intent.parcelable<Widget>(EXTRA_WIDGET)
        if (widget == null) {
            finish()
            return
        }
        this.widget = widget

        supportActionBar?.title = widget.label.orDefaultIfEmpty(getString(R.string.chart_activity_title))

        if (dataCacheFragment == null) {
            supportFragmentManager.commitNow {
                add(HistoryDataCacheFragment(), "cache")
            }
        }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Scroll callbacks may run during layout, where the adapter must not be changed
                recyclerView.post { maybeLoadMore() }
            }
        })
        binding.retryButton.setOnClickListener { onRefresh() }
    }

    override fun inflateBinding(): CommonBinding {
        binding = ActivityStateHistoryBinding.inflate(layoutInflater)
        return CommonBinding(binding.root, binding.appBar, binding.coordinator, binding.activityContent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.state_history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.refresh -> {
            onRefresh()
            true
        }

        android.R.id.home -> {
            finish()
            super.onOptionsItemSelected(item)
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        val data = dataCacheFragment?.loadedData
        val loadExistingData = data?.let {
            val dataUsagePolicy = determineDataUsagePolicy(getConnectionFactory().currentActive?.conn?.connection)
            val now = Instant.now().atZone(data.timestamp.zone)
            val dataIsOutdated = widget.refresh > 0 &&
                Duration.between(data.timestamp, now).toMillis() > widget.refresh
            !dataIsOutdated || !dataUsagePolicy.canDoRefreshes
        }

        if (loadExistingData == true) {
            showData(data)
        } else {
            onRefresh()
        }
    }

    /**
     * Loads the most recent states. Older states are loaded on demand by [loadMore].
     */
    private fun onRefresh() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val connection = getConnectionFactory().currentActive?.conn?.connection
            val item = widget.item
            if (connection == null || item == null) {
                finish()
                return@launch
            }

            dataCacheFragment?.loadedData = null
            loadMoreFailed = false
            showLoadingIndicator()

            val loader = PersistenceDataLoader(connection, serverProperties, widget.service)
            val data = try {
                val timestamp = loader.now()
                val startTime = timestamp.minus(INITIAL_WINDOW)
                val templates = loader.resolveItems(item).map { Series.forItem(it, timestamp.zone) }
                val (allSeries, hasOlderData) = loader.loadWindow(templates, startTime, timestamp) {
                    binding.progressText.text = getString(R.string.chart_activity_loading_progress_item, it)
                }
                HistoryData(allSeries, timestamp, startTime, hasOlderData, INITIAL_WINDOW, allSeries.dataPointCount)
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Could not load states", e)
                showError(getString(R.string.chart_error_generic, e.statusCode))
                return@launch
            }

            dataCacheFragment?.loadedData = data
            showData(data)
        }
    }

    private fun maybeLoadMore() {
        val data = dataCacheFragment?.loadedData ?: return
        if (!data.hasOlderData || loadMoreFailed || loadJob?.isActive == true) {
            return
        }
        val layoutManager = binding.recycler.layoutManager as LinearLayoutManager
        if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
            loadMore(data)
        }
    }

    private fun loadMore(data: HistoryData) {
        loadJob = lifecycleScope.launch {
            val connection = getConnectionFactory().currentActive?.conn?.connection ?: return@launch
            adapter.isLoadingMore = true

            // Widen the time window if the previous one contained only a few data points
            val window = if (data.lastWindowDataPointCount < TARGET_DATA_POINTS) {
                minOf(data.lastWindow.multipliedBy(2), MAX_WINDOW)
            } else {
                data.lastWindow
            }
            val endTime = data.startTime
            val startTime = endTime.minus(window)

            val loader = PersistenceDataLoader(connection, serverProperties, widget.service)
            val newData = try {
                val (olderSeries, hasOlderData) = loader.loadWindow(data.series, startTime, endTime)
                HistoryData(
                    series = olderSeries.zip(data.series) { older, newer ->
                        newer.copy(dataPoints = older.dataPoints + newer.dataPoints)
                    },
                    timestamp = data.timestamp,
                    startTime = startTime,
                    hasOlderData = hasOlderData,
                    lastWindow = window,
                    lastWindowDataPointCount = olderSeries.dataPointCount
                )
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Could not load older states", e)
                adapter.isLoadingMore = false
                loadMoreFailed = true
                showSnackbar(
                    SNACKBAR_TAG_LOAD_MORE_ERROR,
                    R.string.chart_history_load_more_error,
                    actionResId = R.string.chart_error_retry_button_retry
                ) {
                    loadMoreFailed = false
                    maybeLoadMore()
                }
                return@launch
            }

            dataCacheFragment?.loadedData = newData
            adapter.isLoadingMore = false
            showData(newData)
        }
    }

    private fun showData(data: HistoryData) {
        adapter.submit(data.series, data.timestamp.zone)
        binding.progressContainer.isVisible = false
        binding.errorContainer.isVisible = false
        binding.recycler.isVisible = true
        binding.empty.isVisible = adapter.isEmpty
        // Check whether the list fills the screen once the new rows have been laid out
        binding.recycler.doOnNextLayout { it.post { maybeLoadMore() } }
    }

    private fun showLoadingIndicator() {
        binding.recycler.isVisible = false
        binding.empty.isVisible = false
        binding.errorContainer.isVisible = false
        binding.progressContainer.isVisible = true
        binding.progressText.text = ""
    }

    private fun showError(message: CharSequence) {
        binding.recycler.isVisible = false
        binding.empty.isVisible = false
        binding.progressContainer.isVisible = false
        binding.errorContainer.isVisible = true
        binding.errorMessage.text = message
    }

    private val List<Series>.dataPointCount get() = sumOf { it.dataPoints.size }

    /**
     * @param startTime Start of the time window loaded so far
     * @param hasOlderData Whether there are persisted states older than [startTime]
     * @param lastWindow Length of the time window loaded last
     * @param lastWindowDataPointCount Number of data points in the time window loaded last
     */
    data class HistoryData(
        val series: List<Series>,
        val timestamp: ZonedDateTime,
        val startTime: ZonedDateTime,
        val hasOlderData: Boolean,
        val lastWindow: Duration,
        val lastWindowDataPointCount: Int
    )

    @Suppress("DEPRECATION")
    class HistoryDataCacheFragment : Fragment() {
        var loadedData: HistoryData? = null
        init {
            retainInstance = true
        }
    }

    companion object {
        private val TAG = StateHistoryActivity::class.java.simpleName

        private val INITIAL_WINDOW: Duration = Duration.ofDays(1)
        private val MAX_WINDOW: Duration = Duration.ofDays(365)
        private const val TARGET_DATA_POINTS = 5000
        private const val LOAD_MORE_THRESHOLD = 10
        private const val SNACKBAR_TAG_LOAD_MORE_ERROR = "loadMoreError"

        const val EXTRA_WIDGET = "widget"
        const val EXTRA_SERVER_PROPS = "server_properties"
    }
}
