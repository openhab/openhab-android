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

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.StateHistoryHeaderBinding
import org.openhab.habdroid.databinding.StateHistoryItemBinding
import org.openhab.habdroid.databinding.StateHistoryLoadingBinding
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.toParsedState

/**
 * Shows the persisted states of Items that can't be plotted in a chart (e.g. String or DateTime Items)
 * as a list of state changes, newest first and grouped by day.
 */
class StateHistoryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: List<Row> = emptyList()
    private var zoneId: ZoneId = ZoneId.systemDefault()
    private var showSeriesName = false

    val isEmpty get() = rows.none { it is Row.Entry }

    var isLoadingMore = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(rows.size) else notifyItemRemoved(rows.size)
        }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(data: ChartWidgetActivity.ChartData) {
        zoneId = data.timestamp.zone
        showSeriesName = data.data.size > 1
        val entries = data.data
            .flatMap { series ->
                // Persistence strategies like 'everyMinute' store the same state over and over,
                // only keep the points where the state actually changed
                val changes = series.dataPoints
                    .filterIndexed { index, dp -> index == 0 || series.dataPoints[index - 1].state != dp.state }
                changes.mapIndexed { index, dp ->
                    Row.Entry(dp.timestamp, changes.getOrNull(index + 1)?.timestamp, dp.state, series)
                }
            }
            .sortedByDescending { it.timestamp }

        val newRows = mutableListOf<Row>()
        var lastDate: LocalDate? = null
        entries.forEach { entry ->
            val date = entry.timestamp.atZone(zoneId).toLocalDate()
            if (date != lastDate) {
                newRows.add(Row.Header(date))
                lastDate = date
            }
            newRows.add(entry)
        }
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size + if (isLoadingMore) 1 else 0

    override fun getItemViewType(position: Int) = when (rows.getOrNull(position)) {
        is Row.Header -> TYPE_HEADER
        is Row.Entry -> TYPE_ENTRY
        null -> TYPE_LOADING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(StateHistoryHeaderBinding.inflate(inflater, parent, false))
            TYPE_ENTRY -> EntryViewHolder(StateHistoryItemBinding.inflate(inflater, parent, false))
            else -> LoadingViewHolder(StateHistoryLoadingBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val now = Instant.now()
        when (holder) {
            is HeaderViewHolder -> holder.bind(rows[position] as Row.Header, now, zoneId)
            is EntryViewHolder -> holder.bind(rows[position] as Row.Entry, now, zoneId, showSeriesName)
        }
    }

    private sealed class Row {
        data class Header(val date: LocalDate) : Row()

        /**
         * @param until Timestamp of the next state change, or null if this is the current state
         */
        data class Entry(
            val timestamp: Instant,
            val until: Instant?,
            val state: String,
            val series: ChartWidgetActivity.Series
        ) : Row()
    }

    private class HeaderViewHolder(val binding: StateHistoryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: Row.Header, now: Instant, zoneId: ZoneId) {
            val context = binding.root.context
            val today = now.atZone(zoneId).toLocalDate()
            binding.root.text = when (header.date) {
                today -> context.getString(R.string.chart_history_today)

                today.minusDays(1) -> context.getString(R.string.chart_history_yesterday)

                else -> {
                    val skeleton = if (header.date.year == today.year) "EEEEdMMMM" else "EEEEdMMMMyyyy"
                    DateTimeFormatter
                        .ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton))
                        .format(header.date)
                }
            }
        }
    }

    private class EntryViewHolder(val binding: StateHistoryItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: Row.Entry, now: Instant, zoneId: ZoneId, showSeriesName: Boolean) {
            val context = binding.root.context
            binding.state.text = entry.formattedState()

            val timeSkeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
            binding.time.text = DateTimeFormatter
                .ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), timeSkeleton))
                .format(entry.timestamp.atZone(zoneId))

            val age = Duration.between(entry.timestamp, now)
            binding.relativeTime.isVisible = age < RELATIVE_TIME_LIMIT
            binding.relativeTime.text = DateUtils.getRelativeTimeSpanString(
                entry.timestamp.toEpochMilli(),
                now.toEpochMilli(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

            val duration = Duration.between(entry.timestamp, entry.until ?: now).formatted(context)
            binding.duration.text = context.getString(
                if (entry.until == null) R.string.chart_history_duration_ongoing else R.string.chart_history_duration,
                duration
            )

            binding.seriesName.text = entry.series.name
            binding.seriesName.isVisible = showSeriesName
        }

        private fun Row.Entry.formattedState(): String {
            if (series.type == Item.Type.DateTime) {
                state.toParsedState()?.asDateTime?.let { return DATE_TIME_FORMATTER.format(it) }
            }
            return state
        }

        private fun Duration.formatted(context: Context): String = when {
            toMinutes() < 1 -> context.getString(R.string.chart_history_duration_seconds, seconds.coerceAtLeast(0))

            toHours() < 1 -> context.getString(R.string.chart_history_duration_minutes, toMinutes())

            toDays() < 1 && toMinutes() % 60 == 0L ->
                context.getString(R.string.chart_history_duration_hours_only, toHours())

            toDays() < 1 -> context.getString(R.string.chart_history_duration_hours, toHours(), toMinutes() % 60)

            toHours() % 24 == 0L -> context.getString(R.string.chart_history_duration_days_only, toDays())

            else -> context.getString(R.string.chart_history_duration_days, toDays(), toHours() % 24)
        }
    }

    private class LoadingViewHolder(binding: StateHistoryLoadingBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
        private const val TYPE_LOADING = 2

        private val RELATIVE_TIME_LIMIT = Duration.ofHours(6)
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    }
}
