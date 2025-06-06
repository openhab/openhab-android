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
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.appendQueryParameter
import org.openhab.habdroid.util.compress
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.extractParcelable
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.parcelable
import org.openhab.habdroid.util.resolveThemedColor
import org.openhab.habdroid.util.resolveThemedColorArray
import org.openhab.habdroid.util.serializable
import org.openhab.habdroid.util.toByteArray
import org.openhab.habdroid.util.uncompress

class ChartWidgetActivity : AbstractBaseActivity() {
    private lateinit var widget: Widget
    private lateinit var chart: LineChart
    private lateinit var progressContainer: View
    private lateinit var progressText: TextView
    private lateinit var errorContainer: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var seriesColors: Array<Int>
    private var period: TemporalAmount = Duration.ofDays(1)
    private var serverFlags: Int = 0
    private var loadedChartData: ChartData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chart)

        val widget = intent.parcelable<Widget>(EXTRA_WIDGET)
        if (widget == null) {
            finish()
            return
        } else {
            this.widget = widget
            parsePeriod(widget.period)?.let { period = it }
        }
        serverFlags = intent.getIntExtra(EXTRA_SERVER_FLAGS, 0)

        if (savedInstanceState != null) {
            savedInstanceState.serializable<Period>(PERIOD)?.let { period = it }
            savedInstanceState.serializable<Duration>(PERIOD)?.let { period = it }
            loadedChartData = savedInstanceState.getByteArray(DATA)?.uncompress()?.extractParcelable()
        }

        seriesColors = resolveThemedColorArray(R.attr.chartSeriesColors)
        progressContainer = findViewById(R.id.progress_container)
        progressText = findViewById(R.id.progress_text)
        errorContainer = findViewById(R.id.error_container)
        errorText = findViewById(R.id.error_message)
        retryButton = findViewById(R.id.retry_button)
        chart = findViewById<LineChart>(R.id.chart).also {
            configureChart(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "onCreateOptionsMenu()")
        loadedChartData?.let { data ->
            menuInflater.inflate(R.menu.chart_menu, menu)
            menu.removeItem(R.id.show_legend)

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
        outState.putByteArray(DATA, loadedChartData?.toByteArray()?.compress())
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        val data = loadedChartData
        val loadExistingData = data?.let {
            val dataUsagePolicy = determineDataUsagePolicy(ConnectionFactory.activeUsableConnection?.connection)
            val now = Instant.now().atZone(data.timestamp.zone)
            val dataIsOutdated = widget.refresh > 0 &&
                Duration.between(data.timestamp, now).toMillis() > widget.refresh
            val mayDoAutoUpdate = dataUsagePolicy.canDoRefreshes &&
                (data.totalDataPointCount < 10000 || dataUsagePolicy.canDoLargeTransfers)
            !dataIsOutdated || !mayDoAutoUpdate
        }

        if (loadExistingData == true) {
            configureChartForData(data)
            showChart()
        } else {
            onRefresh()
        }
    }

    private fun onRefresh() = lifecycleScope.launch {
        val connection = ConnectionFactory.activeUsableConnection?.connection
        val item = widget.item
        if (connection == null || item == null) {
            finish()
            return@launch
        }

        loadedChartData = null
        invalidateOptionsMenu()
        showLoadingIndicator()

        val data = try {
            loadData(connection, item, period, widget.service) { itemName ->
                progressText.text = getString(R.string.chart_activity_loading_progress_item, itemName)
            }
        } catch (e: HttpClient.HttpException) {
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
            return@launch
        } catch (e: StateParsingException) {
            Log.w(TAG, "Could not load chart data", e)
            showError(getString(R.string.chart_error_state_format, e.item.label ?: e.item.name), "")
            return@launch
        }

        configureChartForData(data)
        loadedChartData = data
        invalidateOptionsMenu()
        showChart()
    }

    private fun showLoadingIndicator() {
        chart.isVisible = false
        progressContainer.isVisible = true
        errorContainer.isVisible = false
        progressText.text = ""
    }

    private fun showChart() {
        progressContainer.isVisible = false
        errorContainer.isVisible = false
        chart.isVisible = true
    }

    private fun showError(message: CharSequence, retryButtonText: CharSequence, retryAction: (() -> Unit)? = null) {
        chart.isVisible = false
        progressContainer.isVisible = false
        errorContainer.isVisible = true
        errorText.text = message
        retryButton.isVisible = retryAction != null
        retryButton.text = retryButtonText
        retryAction?.let { retryButton.setOnClickListener { it() } }
    }

    private fun goToChartImageActivity() {
        val intent = Intent(this, ChartImageActivity::class.java)
            .putExtra(ChartImageActivity.EXTRA_WIDGET, widget)
            .putExtra(ChartImageActivity.EXTRA_SERVER_FLAGS, serverFlags)
        finish()
        startActivity(intent)
    }

    private fun retryLoad() {
        onRefresh()
    }

    @Throws(HttpClient.HttpException::class, StateParsingException::class)
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
        val timestamp = ZonedDateTime.now()
        val startTime = timestamp.minus(period)
        val allSeries = itemsForChart.map { item ->
            progressCb(item.label ?: item.name)
            try {
                loadSeriesForItem(connection, item, startTime, serviceId)
            } catch (e: NumberFormatException) {
                throw StateParsingException(e, item)
            }
        }
        return ChartData(allSeries, timestamp, startTime)
    }

    private fun configureChart(chart: LineChart) = with(chart) {
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

        marker = ValueMarkerView(chart, seriesColors)
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

        val locale = Locale.getDefault()
        val formatHoursAndMinutes = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "JJmm"))
        val formatHoursAndMinutesWithSeconds =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "JJmmss"))
        val formatDateAndTime = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "d JJmm"))
        val formatDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dM"))

        with(xAxis) {
            textColor = foregroundColor
            valueFormatter = object : IAxisValueFormatter {
                override fun getFormattedValue(value: Float, axis: AxisBase?) = loadedChartData?.let { data ->
                    val axisRangeSeconds = axis?.mEntries?.let {
                        val deltaSeconds = (it[it.size - 1] - it[0]) / 1000
                        deltaSeconds.toInt()
                    } ?: -1
                    val format = when (axisRangeSeconds) {
                        in 0..120 -> formatHoursAndMinutesWithSeconds
                        in 24 * 3600..5 * 24 * 3600 -> formatDateAndTime
                        in 5 * 24 * 3600..Int.MAX_VALUE -> formatDate
                        else -> formatHoursAndMinutes
                    }
                    data.startTime.plus(value.toLong(), ChronoUnit.MILLIS)
                        .let { format.format(it) }
                }
            }
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
            val values = series.dataPoints.map {
                val dpTimestamp = ZonedDateTime.ofInstant(it.timestamp, data.startTime.zone)
                val entryData = DataPointWithSeries(it, series)
                Entry(
                    Duration.between(data.startTime, dpTimestamp).toMillis().toFloat(),
                    it.value.toFloat(),
                    entryData
                )
            }
            LineDataSet(values, series.name).apply {
                setDrawCircles(false)
                setColor(seriesColors[index % seriesColors.size])
                setDrawCircleHole(false)
                setDrawValues(false)
                lineWidth = 1F
                mode = LineDataSet.Mode.STEPPED
            }
        }
        with(chart.axisLeft) {
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
            }
            valueFormatter = object : IAxisValueFormatter {
                override fun getFormattedValue(value: Float, axis: AxisBase?) =
                    data.data[0].formatValue(value, this@ChartWidgetActivity)
            }
        }

        chart.legend.isEnabled = data.data.size > 1
        chart.data = LineData(dataSets)
        chart.fitScreen()
    }

    @Throws(HttpClient.HttpException::class, NumberFormatException::class)
    private suspend fun loadSeriesForItem(
        connection: Connection,
        item: Item,
        startTime: ZonedDateTime,
        serviceId: String?
    ): Series {
        val uriBuilder = Uri.Builder()
            .path("rest/persistence/items/${item.name}")
            .appendQueryParameter("starttime", startTime.toString())
            .appendQueryParameter("boundary", true)
            .appendQueryParameter("itemState", true)
        if (!serviceId.isNullOrEmpty()) {
            uriBuilder.appendQueryParameter("serviceId", serviceId)
        }

        return connection.httpClient
            .get(uriBuilder.build().toString())
            .asText()
            .let { resp ->
                withContext(Dispatchers.IO) {
                    val json = JSONObject(resp.response)
                    val dataPoints = json.getJSONArray("data").map { dpjson ->
                        val timestamp = Instant.ofEpochMilli(dpjson.getLong("time"))
                        val state = when (val state = dpjson.getString("state").substringBefore(' ')) {
                            "ON", "OPEN" -> 1.0
                            "OFF", "CLOSED" -> 0.0
                            else -> state.toDouble()
                        }
                        DataPoint(timestamp, state)
                    }
                    Series(
                        item.label ?: item.name,
                        item.type,
                        item.state?.asNumber,
                        startTime.zone,
                        dataPoints
                    )
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
    data class DataPoint(val timestamp: Instant, val value: Double) : Parcelable

    @Parcelize
    data class Series(
        val name: String,
        val type: Item.Type,
        val state: ParsedState.NumberState?,
        val zoneId: ZoneId,
        val dataPoints: List<DataPoint>
    ) : Parcelable {
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

    @Parcelize
    data class ChartData(val data: List<Series>, val timestamp: ZonedDateTime, val startTime: ZonedDateTime) :
        Parcelable {
        val totalDataPointCount get() = data.sumOf { series -> series.dataPoints.size }
    }

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
            if (data != null) {
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

    private class StateParsingException(cause: Throwable, val item: Item) : Exception(cause)

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

        private const val PERIOD = "period"
        private const val DATA = "data"
        const val EXTRA_WIDGET = "widget"
        const val EXTRA_SERVER_FLAGS = "server_flags"
    }
}
