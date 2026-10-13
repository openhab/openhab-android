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

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.format.DateFormat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.Locale
import kotlin.collections.map
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.nextUp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.databinding.ActivityChartBinding
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.appendQueryParameter
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelable
import org.openhab.habdroid.util.resolveThemedColor
import org.openhab.habdroid.util.resolveThemedColorArray
import org.openhab.habdroid.util.serializable

class ChartWidgetActivity : AbstractBaseActivity() {
    private lateinit var binding: ActivityChartBinding
    private lateinit var widget: Widget
    private lateinit var seriesColors: Array<Int>
    private val dataCacheFragment get() =
        supportFragmentManager.findFragmentByTag("cache") as? ChartDataCacheFragment
    private var period: TemporalAmount = Duration.ofDays(1)
    private val serverProperties: ServerProperties? get() = intent.parcelable(EXTRA_SERVER_PROPS)
    private val historyAdapter = StateHistoryAdapter()
    private var historyLoadJob: Job? = null
    private var historyLoadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widget = intent.parcelable<Widget>(EXTRA_WIDGET)
        if (widget == null) {
            finish()
            return
        } else {
            this.widget = widget
            parsePeriod(widget.period)?.let { period = it }
        }

        supportActionBar?.title = widget.label.orDefaultIfEmpty(getString(R.string.chart_activity_title))

        if (savedInstanceState != null) {
            savedInstanceState.serializable<Period>(PERIOD)?.let { period = it }
            savedInstanceState.serializable<Duration>(PERIOD)?.let { period = it }
        }

        if (dataCacheFragment == null) {
            supportFragmentManager.commitNow {
                add(ChartDataCacheFragment(), "cache")
            }
        }

        seriesColors = resolveThemedColorArray(R.attr.chartSeriesColors)
        configureChart()
        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = historyAdapter
        binding.historyRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Scroll callbacks may run during layout, where the adapter must not be changed
                recyclerView.post { maybeLoadMoreHistory() }
            }
        })
    }

    override fun inflateBinding(): CommonBinding {
        binding = ActivityChartBinding.inflate(layoutInflater)
        return CommonBinding(binding.root, binding.appBar, binding.coordinator, binding.activityContent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "onCreateOptionsMenu()")
        dataCacheFragment?.loadedData?.let { data ->
            menuInflater.inflate(R.menu.chart_menu, menu)
            menu.removeItem(R.id.show_legend)
            if (data.history != null) {
                // The history list loads older states while scrolling, so there's no need to select a period
                menu.removeItem(R.id.period)
                return true
            }

            // Make sure we don't fetch overly large data sets by removing periods that would yield too many items
            val dataPeriodHours = Duration.between(data.startTime, data.timestamp).toHours()
            val pointsPerHour = data.data.size * data.data[0].dataPoints.size / dataPeriodHours
            val now = LocalDateTime.now()
            DURATION_MENU_MAPPING.forEach { itemId, period ->
                val periodInHours = Duration.between(now.minus(period), now).toHours()
                menu.findItem(itemId).isVisible = pointsPerHour * periodInHours < DATA_POINT_LIMIT
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")
        return when {
            item.itemId == R.id.refresh -> {
                onRefresh()
                true
            }

            item.itemId == android.R.id.home -> {
                finish()
                super.onOptionsItemSelected(item)
            }

            // The dropdown menu is opened
            item.itemId == R.id.period -> {
                true
            }

            DURATION_MENU_MAPPING.containsKey(item.itemId) -> {
                period = DURATION_MENU_MAPPING[item.itemId]!!
                onRefresh()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        when (val p = period) {
            is Period -> outState.putSerializable(PERIOD, p)
            is Duration -> outState.putSerializable(PERIOD, p)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        val data = dataCacheFragment?.loadedData
        val loadExistingData = data?.let {
            val dataUsagePolicy = determineDataUsagePolicy(getConnectionFactory().currentActive?.conn?.connection)
            val now = Instant.now().atZone(data.timestamp.zone)
            val dataIsOutdated = widget.refresh > 0 &&
                Duration.between(data.timestamp, now).toMillis() > widget.refresh
            val mayDoAutoUpdate = dataUsagePolicy.canDoRefreshes &&
                (data.totalDataPointCount < 10000 || dataUsagePolicy.canDoLargeTransfers)
            !dataIsOutdated || !mayDoAutoUpdate
        }

        if (loadExistingData == true) {
            showData(data)
        } else {
            onRefresh()
        }
    }

    private fun onRefresh() {
        val previousData = dataCacheFragment?.loadedData
        if (previousData?.history != null) {
            startHistory(previousData)
        } else {
            loadChart()
        }
    }

    private fun loadChart() = lifecycleScope.launch {
        val connection = getConnectionFactory().currentActive?.conn?.connection
        val item = widget.item
        if (connection == null || item == null) {
            finish()
            return@launch
        }

        dataCacheFragment?.loadedData = null
        invalidateOptionsMenu()
        showLoadingIndicator()

        val data = try {
            loadData(connection, item, period, widget.service) { itemName ->
                binding.progressText.text = getString(R.string.chart_activity_loading_progress_item, itemName)
            }
        } catch (e: HttpClient.HttpException) {
            showLoadError(e)
            return@launch
        }

        dataCacheFragment?.loadedData = data
        invalidateOptionsMenu()
        showData(data)
    }

    private fun showLoadError(e: HttpClient.HttpException) {
        Log.w(TAG, "Could not load chart data", e)
        if (e.statusCode == 401 || e.statusCode == 403) {
            showError(
                getString(R.string.chart_error_authentication),
                getString(R.string.chart_error_retry_button_chart_image)
            ) {
                goToChartImageActivity()
            }
        } else {
            showError(
                getString(R.string.chart_error_generic, e.statusCode),
                getString(R.string.chart_error_retry_button_retry)
            ) {
                retryLoad()
            }
        }
    }

    private fun showData(data: ChartData) {
        when {
            data.history != null -> {
                historyAdapter.submit(data)
                showHistory()
                // Check whether the list fills the screen once the new rows have been laid out
                binding.historyRecycler.doOnNextLayout { it.post { maybeLoadMoreHistory() } }
            }

            data.isNumeric -> {
                configureChartForData(data)
                showChart()
            }

            // States can't be plotted (e.g. String or DateTime Items), so show a list of state changes instead
            else -> startHistory(data)
        }
    }

    /**
     * Loads the most recent states of the Items in [template] for the state history list.
     * Older states are loaded on demand by [loadMoreHistory].
     */
    private fun startHistory(template: ChartData) {
        historyLoadJob?.cancel()
        historyLoadJob = lifecycleScope.launch {
            val connection = getConnectionFactory().currentActive?.conn?.connection
            if (connection == null) {
                finish()
                return@launch
            }

            dataCacheFragment?.loadedData = null
            historyLoadFailed = false
            invalidateOptionsMenu()
            showLoadingIndicator()

            val timestamp = ZonedDateTime.now(template.timestamp.zone)
            val startTime = timestamp.minus(INITIAL_HISTORY_WINDOW)
            val data = try {
                val (allSeries, hasOlderData) = loadHistoryWindow(connection, template.data, startTime, timestamp) {
                    binding.progressText.text = getString(R.string.chart_activity_loading_progress_item, it)
                }
                ChartData(
                    allSeries,
                    timestamp,
                    startTime,
                    HistoryInfo(hasOlderData, INITIAL_HISTORY_WINDOW, allSeries.sumOf { it.dataPoints.size })
                )
            } catch (e: HttpClient.HttpException) {
                showLoadError(e)
                return@launch
            }

            dataCacheFragment?.loadedData = data
            invalidateOptionsMenu()
            showData(data)
        }
    }

    private fun maybeLoadMoreHistory() {
        val data = dataCacheFragment?.loadedData ?: return
        if (data.history?.hasOlderData != true || historyLoadFailed || historyLoadJob?.isActive == true) {
            return
        }
        val layoutManager = binding.historyRecycler.layoutManager as LinearLayoutManager
        if (layoutManager.findLastVisibleItemPosition() >= historyAdapter.itemCount - LOAD_MORE_THRESHOLD) {
            loadMoreHistory(data)
        }
    }

    private fun loadMoreHistory(data: ChartData) {
        val history = data.history ?: return
        historyLoadJob = lifecycleScope.launch {
            val connection = getConnectionFactory().currentActive?.conn?.connection ?: return@launch
            historyAdapter.isLoadingMore = true

            // Widen the time window if the previous one contained only a few data points
            val window = if (history.lastWindowDataPointCount < HISTORY_TARGET_DATA_POINTS) {
                minOf(history.lastWindow.multipliedBy(2), MAX_HISTORY_WINDOW)
            } else {
                history.lastWindow
            }
            val endTime = data.startTime
            val startTime = endTime.minus(window)

            val newData = try {
                val (olderSeries, hasOlderData) = loadHistoryWindow(connection, data.data, startTime, endTime)
                data.copy(
                    data = olderSeries.zip(data.data) { older, newer ->
                        newer.copy(dataPoints = older.dataPoints + newer.dataPoints)
                    },
                    startTime = startTime,
                    history = HistoryInfo(hasOlderData, window, olderSeries.sumOf { it.dataPoints.size })
                )
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Could not load older states", e)
                historyAdapter.isLoadingMore = false
                historyLoadFailed = true
                showSnackbar(
                    SNACKBAR_TAG_HISTORY_LOAD_ERROR,
                    R.string.chart_history_load_more_error,
                    actionResId = R.string.chart_error_retry_button_retry
                ) {
                    historyLoadFailed = false
                    maybeLoadMoreHistory()
                }
                return@launch
            }

            dataCacheFragment?.loadedData = newData
            historyAdapter.isLoadingMore = false
            showData(newData)
        }
    }

    /**
     * Loads the states persisted between [startTime] and [endTime] for each of [templates].
     *
     * @return The loaded series and whether there are states persisted before [startTime]
     */
    @Throws(HttpClient.HttpException::class)
    private suspend fun loadHistoryWindow(
        connection: Connection,
        templates: List<Series>,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        progressCb: (itemName: String) -> Unit = {}
    ): Pair<List<Series>, Boolean> {
        val serverTimeZoneId = serverProperties?.timezoneId?.let { ZoneId.of(it) }
        val startInstant = startTime.toInstant().truncatedTo(ChronoUnit.MILLIS)
        val endInstant = endTime.toInstant().truncatedTo(ChronoUnit.MILLIS)
        var hasOlderData = false
        val allSeries = templates.map { series ->
            progressCb(series.name)
            val dataPoints = loadDataPoints(
                connection,
                series.itemName,
                startTime,
                endTime,
                serverTimeZoneId,
                widget.service,
                boundary = true
            )
            // With 'boundary', the server adds the last state before the start time at the start time if there is one,
            // so its presence tells whether older states exist. Both boundaries aren't real state changes, though.
            if (dataPoints.any { it.timestamp == startInstant }) {
                hasOlderData = true
            }
            series.copy(dataPoints = dataPoints.filter { it.timestamp > startInstant && it.timestamp < endInstant })
        }
        return allSeries to hasOlderData
    }

    private fun showLoadingIndicator() {
        binding.chart.isVisible = false
        binding.historyList.isVisible = false
        binding.progressContainer.isVisible = true
        binding.errorContainer.isVisible = false
        binding.progressText.text = ""
    }

    private fun showChart() {
        binding.progressContainer.isVisible = false
        binding.errorContainer.isVisible = false
        binding.historyList.isVisible = false
        binding.chart.isVisible = true
    }

    private fun showHistory() {
        binding.progressContainer.isVisible = false
        binding.errorContainer.isVisible = false
        binding.chart.isVisible = false
        binding.historyList.isVisible = true
        binding.historyEmpty.isVisible = historyAdapter.isEmpty
    }

    private fun showError(message: CharSequence, retryButtonText: CharSequence, retryAction: (() -> Unit)? = null) {
        binding.chart.isVisible = false
        binding.historyList.isVisible = false
        binding.progressContainer.isVisible = false
        binding.errorContainer.isVisible = true
        binding.errorMessage.text = message
        binding.retryButton.isVisible = retryAction != null
        binding.retryButton.text = retryButtonText
        retryAction?.let { binding.retryButton.setOnClickListener { it() } }
    }

    private fun goToChartImageActivity() {
        val intent = Intent(this, ChartImageActivity::class.java)
            .putExtra(ChartImageActivity.EXTRA_WIDGET, widget)
            .putExtra(ChartImageActivity.EXTRA_SERVER_FLAGS, serverProperties?.flags ?: 0)
        finish()
        startActivity(intent)
    }

    private fun retryLoad() {
        onRefresh()
    }

    @Throws(HttpClient.HttpException::class)
    private suspend fun loadData(
        connection: Connection,
        item: Item,
        period: TemporalAmount,
        serviceId: String?,
        progressCb: (itemName: String) -> Unit
    ): ChartData {
        val itemsForChart = if (item.type == Item.Type.Group) {
            // sitemap response doesn't include member list, so re-fetch item to get members
            ItemClient.loadItem(connection, item.name)?.members ?: listOf(item)
        } else {
            listOf(item)
        }
        val serverTimeZoneId = serverProperties?.timezoneId?.let { ZoneId.of(it) }
        val timestamp = ZonedDateTime.now().let { zdt ->
            if (serverTimeZoneId != null) zdt.withZoneSameInstant(serverTimeZoneId) else zdt
        }
        val startTime = timestamp.minus(period)

        val allSeries = itemsForChart.map { item ->
            progressCb(item.label ?: item.name)
            loadSeriesForItem(connection, item, startTime, serverTimeZoneId, serviceId)
        }
        return ChartData(allSeries, timestamp, startTime)
    }

    private fun configureChart() = with(binding.chart) {
        val foregroundColor = resolveThemedColor(R.attr.colorOnSurface)
        val padding = 4F // dp

        setTouchEnabled(true)
        setDragDecelerationFrictionCoef(0.9f)
        setDragEnabled(true)
        isScaleXEnabled = true
        isScaleYEnabled = false

        setDrawBorders(true)
        setDrawGridBackground(false)
        setBorderColor(foregroundColor)

        marker = ValueMarkerView(this, seriesColors)
        isHighlightPerTapEnabled = true
        isHighlightPerDragEnabled = true

        minOffset = padding
        extraBottomOffset = padding

        with(legend) {
            textColor = foregroundColor
            form = Legend.LegendForm.CIRCLE
            yOffset = padding
            isWordWrapEnabled = true
        }
        with(description) {
            isEnabled = false
        }

        with(xAxis) {
            textColor = foregroundColor
            labelCount = resources.getInteger(R.integer.chart_x_label_count)
            position = XAxis.XAxisPosition.BOTTOM
        }

        with(axisLeft) {
            textColor = foregroundColor
        }
        with(axisRight) {
            isEnabled = false
        }
    }

    private fun configureChartForData(data: ChartData) {
        val dataSets = data.data.mapIndexed { index, series ->
            val values = series.dataPoints.filter { it.value != null }.map {
                val dpTimestamp = ZonedDateTime.ofInstant(it.timestamp, data.startTime.zone)
                val entryData = DataPointWithSeries(it, series)
                // Entry x and y values are floats only (not double), so we use increments of 100ms as X axis
                // scale to avoid imprecisions due to large values
                Entry(
                    Duration.between(data.startTime, dpTimestamp).toMillis().toFloat().div(100),
                    it.value!!.toFloat(),
                    entryData
                )
            }
            LineDataSet(values, series.name).apply {
                setDrawCircles(false)
                setColor(seriesColors[index % seriesColors.size])
                setDrawCircleHole(false)
                setDrawValues(false)
                lineWidth = 1F
                mode = if (widget.interpolation == Widget.Interpolation.Step) {
                    LineDataSet.Mode.STEPPED
                } else {
                    LineDataSet.Mode.LINEAR
                }
            }
        }

        val viewportHandler = binding.chart.viewPortHandler
        with(binding.chart) {
            setXAxisRenderer(
                TimeXAxisRenderer(
                    viewportHandler,
                    xAxis,
                    rendererXAxis.transformer,
                    data.startTime
                )
            )
            xAxis.valueFormatter = TimeXAxisValueFormatter(
                data.startTime,
                viewportHandler,
                Locale.getDefault()
            )
        }

        // Make sure at least 5 seconds stay visible on screen
        val minX = dataSets.minByOrNull { it.xMin }?.xMin
        val maxX = dataSets.maxByOrNull { it.xMax }?.xMax
        if (minX != null && maxX != null) {
            viewportHandler.setMaximumScaleX((maxX - minX) / 50)
        }

        with(binding.chart.axisLeft) {
            val seriesUsedForAxisFormatting = data.data
                .firstOrNull { it.canBeUsedForAxisFormatting() }
                ?: data.data[0]

            if (data.data.all { it.type in listOf(Item.Type.Switch, Item.Type.Contact) }) {
                // states only
                axisMinimum = -0.05F
                axisMaximum = 1.05F
                specificPositions = FloatArray(2) { index -> index.toFloat() }
                isShowSpecificPositions = true
            } else {
                // numeric values
                resetAxisMinimum()
                resetAxisMaximum()
                isShowSpecificPositions = false
                isGranularityEnabled = false

                seriesUsedForAxisFormatting.state?.format?.let { format ->
                    if (format.contains("%d")) {
                        granularity = 1F
                    } else {
                        // Try to extract number of decimal digits from a format like '%.3f %unit%
                        ".*%\\.(\\d+)f.*".toRegex().matchEntire(format)?.let { match ->
                            granularity = 10.0.pow(-match.groupValues[1].toInt()).toFloat()
                        }
                    }
                }
                valueFormatter = object : IAxisValueFormatter {
                    override fun getFormattedValue(value: Float, axis: AxisBase?) =
                        seriesUsedForAxisFormatting.formatValue(value, this@ChartWidgetActivity)
                }
            }
        }

        binding.chart.apply {
            legend.isEnabled = data.data.size > 1
            this.data = LineData(dataSets)
            fitScreen()
        }
    }

    @Throws(HttpClient.HttpException::class)
    private suspend fun loadSeriesForItem(
        connection: Connection,
        item: Item,
        startTime: ZonedDateTime,
        serverTimeZoneId: ZoneId?,
        serviceId: String?
    ): Series {
        val dataPoints = loadDataPoints(
            connection,
            item.name,
            startTime,
            null,
            serverTimeZoneId,
            serviceId,
            boundary = true,
            itemState = true
        )
        return Series(
            item.label ?: item.name,
            item.name,
            item.type,
            item.state?.asNumber,
            startTime.zone,
            dataPoints
        )
    }

    @Throws(HttpClient.HttpException::class)
    private suspend fun loadDataPoints(
        connection: Connection,
        itemName: String,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime?,
        serverTimeZoneId: ZoneId?,
        serviceId: String?,
        boundary: Boolean = false,
        itemState: Boolean = false
    ): List<DataPoint> {
        // Timing of 'server provides its own time zone' and 'server understands ZDT format' luckily coincides
        fun ZonedDateTime.toTimeParameter() = if (serverTimeZoneId != null) {
            // OH 5.x: time parser understands ZonedDateTime format
            toString()
        } else {
            // OH up to 4.x: time parser only understands LocalDateTime format
            toLocalDateTime().toString()
        }

        val uriBuilder = Uri.Builder()
            .path("rest/persistence/items/$itemName")
            .appendQueryParameter("starttime", startTime.toTimeParameter())
            .appendQueryParameter("boundary", boundary)
            .appendQueryParameter("itemState", itemState)
        if (endTime != null) {
            uriBuilder.appendQueryParameter("endtime", endTime.toTimeParameter())
        }
        if (!serviceId.isNullOrEmpty()) {
            uriBuilder.appendQueryParameter("serviceId", serviceId)
        }

        return connection.httpClient
            .get(uriBuilder.build().toString())
            .asText()
            .let { resp ->
                withContext(Dispatchers.IO) {
                    val json = JSONObject(resp.response)
                    json.getJSONArray("data").map { dpjson ->
                        val timestamp = Instant.ofEpochMilli(dpjson.getLong("time"))
                        val state = dpjson.getString("state")
                        val value = when (val numericPart = state.substringBefore(' ')) {
                            "ON", "OPEN" -> 1.0
                            "OFF", "CLOSED" -> 0.0
                            else -> numericPart.toDoubleOrNull()
                        }
                        DataPoint(timestamp, state, value)
                    }
                }
            }
    }

    private fun parsePeriod(period: String): TemporalAmount? {
        if (period.isEmpty() || period.last() !in listOf('h', 'H', 'D', 'W', 'M', 'Y')) {
            return null
        }
        val periodAsIso8601 = when {
            period.startsWith('P') -> period
            period == "h" || period == "H" -> "PT1H"
            period.length == 1 -> "P1$period"
            period.endsWith("H", ignoreCase = true) -> "PT${period.substring(0, period.length - 1)}H"
            else -> "P$period"
        }
        return try {
            Period.parse(periodAsIso8601)
        } catch (_: DateTimeParseException) {
            try {
                Duration.parse(periodAsIso8601)
            } catch (e: DateTimeParseException) {
                Log.e(TAG, "Could not parse period specification '$period'", e)
                null
            }
        }
    }

    @Parcelize
    data class DataPoint(val timestamp: Instant, val state: String, val value: Double?) : Parcelable

    @Parcelize
    data class Series(
        val name: String,
        val itemName: String,
        val type: Item.Type,
        val state: ParsedState.NumberState?,
        val zoneId: ZoneId,
        val dataPoints: List<DataPoint>
    ) : Parcelable {
        val isNumeric get() = if (dataPoints.any { it.value != null }) {
            dataPoints.all { it.value != null || it.state in UNDEFINED_STATES }
        } else {
            // Nothing to decide on, e.g. if only an undefined state has been persisted, so go by the Item type
            type in NUMERIC_ITEM_TYPES
        }

        fun canBeUsedForAxisFormatting() = when (type) {
            Item.Type.Switch, Item.Type.Contact -> true
            else -> state != null
        }

        fun formatValue(value: Float, context: Context) = when (type) {
            Item.Type.Switch -> context.getString(
                if (value.roundToInt() > 0) R.string.nfc_action_on else R.string.nfc_action_off
            )

            Item.Type.Contact -> context.getString(
                if (value.roundToInt() > 0) R.string.nfc_action_open else R.string.nfc_action_closed
            )

            else -> state.withValue(value).toString()
        }
    }

    class TimeXAxisValueFormatter(
        private val startTime: ZonedDateTime,
        private val viewPortHandler: ViewPortHandler,
        locale: Locale
    ) : IAxisValueFormatter {
        private val formatHoursAndMinutes =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "JJmm"))
        private val formatHoursAndMinutesWithDate =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "d JJmm"))
        private val formatHoursAndMinutesWithSeconds =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "JJmmss"))
        private val formatHoursAndMinutesWithSecondsAndDate =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "d JJmmss"))
        private val formatDate =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dM"))

        private var lastFormattedValue = Float.MAX_VALUE

        override fun getFormattedValue(value: Float, axis: AxisBase?): String? {
            val axisRangeSeconds = axis?.mAxisRange?.div(10)?.div(viewPortHandler.scaleX)?.toInt() ?: -1
            val labelCount = axis?.mEntryCount ?: 5
            val needsDate = if (value < lastFormattedValue) {
                // Since values are formatted in order, if our value is smaller than the one before, it's the first one
                true
            } else {
                value.toTime().hour < lastFormattedValue.toTime().hour
            }

            val format = when (axisRangeSeconds) {
                in 0..120 ->
                    if (needsDate) formatHoursAndMinutesWithSecondsAndDate else formatHoursAndMinutesWithSeconds

                in labelCount * 24 * 3600..Int.MAX_VALUE -> formatDate

                else -> if (needsDate) formatHoursAndMinutesWithDate else formatHoursAndMinutes
            }
            lastFormattedValue = value
            return format.format(value.toTime())
        }

        private fun Float.toTime() = startTime.plus(toLong() * 100, ChronoUnit.MILLIS)
    }

    class TimeXAxisRenderer(
        viewPortHandler: ViewPortHandler,
        axis: XAxis,
        transformer: Transformer,
        startTime: ZonedDateTime
    ) : XAxisRenderer(viewPortHandler, axis, transformer) {
        private val offset = startTime.toInstant().toEpochMilli() / 100

        override fun computeAxisValues(min: Float, max: Float) {
            super.computeAxisValues(min, max)

            val labelCount = mAxis.labelCount
            val fullRangeDeciSeconds = (max - min).toDouble()
            val axisScaleDeciSeconds = when (fullRangeDeciSeconds.div(10)) {
                in 0.0..120.0 -> 1F * 10F
                in 120.0..2.0 * 3600 -> 60F * 10F
                in 2.0 * 3600..6.0 * 3600 -> 600F * 10F
                in 6.0 * 3600..12.0 * 3600 -> 1800F * 10F
                in 12.0 * 3600..24.0 * 3600 -> 3600F * 10F
                in 24.0 * 3600..5.0 * 24 * 3600 -> 3F * 3600F * 10F
                else -> 24F * 3600F * 10F
            }

            // Find out how much spacing (in y value space) between axis values
            val rawInterval = fullRangeDeciSeconds / labelCount / axisScaleDeciSeconds
            var interval = Utils.roundToNextSignificant(rawInterval)

            // Normalize interval
            val intervalMagnitude = Utils.roundToNextSignificant(10.0.pow(log10(interval).toInt()))
            val intervalSigDigit = (interval / intervalMagnitude).toInt()
            if (intervalSigDigit > 5) {
                // Use one order of magnitude higher, to avoid intervals like 0.9 or 90
                interval = floor(10 * intervalMagnitude)
            }

            interval *= axisScaleDeciSeconds

            val snapInDelta = (interval / 2).toDouble()
            val first = ceil((min.toDouble() + offset) / snapInDelta) * snapInDelta - offset
            val last = (floor((max.toDouble() + offset) / snapInDelta) * snapInDelta - offset).nextUp()
            val n = ceil((last - first) / interval).toInt()

            mAxis.mEntryCount = n
            if (mAxis.mEntries.size < n) {
                mAxis.mEntries = FloatArray(n)
            }
            (0 until n).forEach { i ->
                mAxis.mEntries[i] = (first + interval * i).toFloat()
            }

            mAxis.mDecimals = if (interval < 1) {
                ceil(-log10(interval)).toInt()
            } else {
                0
            }
        }
    }

    @Parcelize
    data class ChartData(
        val data: List<Series>,
        val timestamp: ZonedDateTime,
        val startTime: ZonedDateTime,
        val history: HistoryInfo? = null
    ) : Parcelable {
        val totalDataPointCount get() = data.sumOf { series -> series.dataPoints.size }
        val isNumeric get() = data.all { series -> series.isNumeric }
    }

    /**
     * @param hasOlderData Whether there are persisted states older than the ones loaded so far
     * @param lastWindow Length of the time window loaded last
     * @param lastWindowDataPointCount Number of data points in the time window loaded last
     */
    @Parcelize
    data class HistoryInfo(val hasOlderData: Boolean, val lastWindow: Duration, val lastWindowDataPointCount: Int) :
        Parcelable

    data class DataPointWithSeries(val dp: DataPoint, val series: Series)

    private class ValueMarkerView(chartView: LineChart, seriesColors: Array<Int>) :
        MarkerView(chartView.context, R.layout.chart_marker) {
        private val text = getChildAt(0) as TextView
        private val dataSetColors: List<ColorStateList>
        private val background = MaterialShapeDrawable(
            ShapeAppearanceModel.Builder()
                .setAllCornerSizes(RelativeCornerSize(0.15F))
                .build()
        )
        private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

        init {
            setChartView(chartView)
            val backgroundColor = chartView.context.resolveThemedColor(R.attr.colorSurface)
            dataSetColors = seriesColors.map { color ->
                val fg = ColorUtils.setAlphaComponent(color, 96)
                ColorStateList.valueOf(ColorUtils.compositeColors(fg, backgroundColor))
            }
            text.background = background
            text.setTextColor(chartView.context.resolveThemedColor(R.attr.colorOnSurface))
        }

        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            val data = e?.data as? DataPointWithSeries
            text.isVisible = data != null
            if (data != null && data.dp.value != null) {
                val value = data.series.formatValue(data.dp.value.toFloat(), text.context)
                val formattedTimestamp = formatter.format(
                    LocalDateTime.ofInstant(data.dp.timestamp, data.series.zoneId)
                )
                text.text = "${formattedTimestamp}\n${data.series.name}: $value"
                highlight?.let { background.setFillColor(dataSetColors[it.dataSetIndex % dataSetColors.size]) }
            }
            super.refreshContent(e, highlight)
        }
    }

    @Suppress("DEPRECATION")
    class ChartDataCacheFragment : Fragment() {
        var loadedData: ChartData? = null
        init {
            retainInstance = true
        }
    }

    companion object {
        private val TAG = ChartWidgetActivity::class.java.simpleName

        private val DURATION_MENU_MAPPING: Map<Int, TemporalAmount> = mapOf(
            R.id.period_h to Duration.ofHours(1),
            R.id.period_4h to Duration.ofHours(4),
            R.id.period_8h to Duration.ofHours(8),
            R.id.period_12h to Duration.ofHours(12),
            R.id.period_d to Duration.ofDays(1),
            R.id.period_2d to Duration.ofDays(2),
            R.id.period_3d to Duration.ofDays(3),
            R.id.period_w to Period.ofWeeks(1),
            R.id.period_2w to Period.ofWeeks(2),
            R.id.period_m to Period.ofMonths(1),
            R.id.period_2m to Period.ofMonths(2),
            R.id.period_4m to Period.ofMonths(4),
            R.id.period_y to Period.ofYears(1)
        )

        private const val DATA_POINT_LIMIT = 500000
        private val UNDEFINED_STATES = listOf("NULL", "UNDEF")
        private val NUMERIC_ITEM_TYPES = listOf(
            Item.Type.Number,
            Item.Type.NumberWithDimension,
            Item.Type.Dimmer,
            Item.Type.Rollershutter,
            Item.Type.Switch,
            Item.Type.Contact
        )

        private val INITIAL_HISTORY_WINDOW: Duration = Duration.ofDays(1)
        private val MAX_HISTORY_WINDOW: Duration = Duration.ofDays(365)
        private const val HISTORY_TARGET_DATA_POINTS = 5000
        private const val LOAD_MORE_THRESHOLD = 10
        private const val SNACKBAR_TAG_HISTORY_LOAD_ERROR = "historyLoadError"

        private const val PERIOD = "period"
        const val EXTRA_WIDGET = "widget"
        const val EXTRA_SERVER_PROPS = "server_properties"
    }
}
