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

import android.net.Uri
import android.os.Parcelable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.appendQueryParameter
import org.openhab.habdroid.util.map

/**
 * Loads persisted states of Items from the REST API, used by [ChartWidgetActivity] and [StateHistoryActivity].
 */
class PersistenceDataLoader(
    private val connection: Connection,
    serverProperties: ServerProperties?,
    private val serviceId: String?
) {
    val serverTimeZoneId: ZoneId? = serverProperties?.timezoneId?.let { ZoneId.of(it) }

    fun now(): ZonedDateTime = ZonedDateTime.now().let { zdt ->
        if (serverTimeZoneId != null) zdt.withZoneSameInstant(serverTimeZoneId) else zdt
    }

    /**
     * @return The members of [item] if it's a Group Item, otherwise [item] itself
     */
    @Throws(HttpClient.HttpException::class)
    suspend fun resolveItems(item: Item): List<Item> = if (item.type == Item.Type.Group) {
        // sitemap response doesn't include member list, so re-fetch item to get members
        ItemClient.loadItem(connection, item.name)?.members ?: listOf(item)
    } else {
        listOf(item)
    }

    /**
     * Loads the states of [item] persisted since [startTime], including the current state of the Item.
     */
    @Throws(HttpClient.HttpException::class)
    suspend fun loadSeries(item: Item, startTime: ZonedDateTime): Series {
        val dataPoints = loadDataPoints(item.name, startTime, null, boundary = true, itemState = true)
        return Series.forItem(item, startTime.zone, dataPoints)
    }

    /**
     * Loads the states persisted between [startTime] and [endTime] for each of [templates].
     *
     * @return The loaded series and whether there are states persisted before [startTime]
     */
    @Throws(HttpClient.HttpException::class)
    suspend fun loadWindow(
        templates: List<Series>,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        progressCb: (itemName: String) -> Unit = {}
    ): Pair<List<Series>, Boolean> {
        val startInstant = startTime.toInstant().truncatedTo(ChronoUnit.MILLIS)
        val endInstant = endTime.toInstant().truncatedTo(ChronoUnit.MILLIS)
        var hasOlderData = false
        val allSeries = templates.map { series ->
            progressCb(series.name)
            val dataPoints = loadDataPoints(series.itemName, startTime, endTime, boundary = true)
            // With 'boundary', the server adds the last state before the start time at the start time if there is one,
            // so its presence tells whether older states exist. Both boundaries aren't real state changes, though.
            if (dataPoints.any { it.timestamp == startInstant }) {
                hasOlderData = true
            }
            series.copy(dataPoints = dataPoints.filter { it.timestamp > startInstant && it.timestamp < endInstant })
        }
        return allSeries to hasOlderData
    }

    @Throws(HttpClient.HttpException::class)
    private suspend fun loadDataPoints(
        itemName: String,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime?,
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

        companion object {
            fun forItem(item: Item, zoneId: ZoneId, dataPoints: List<DataPoint> = emptyList()) = Series(
                item.label ?: item.name,
                item.name,
                item.type,
                item.state?.asNumber,
                zoneId,
                dataPoints
            )
        }
    }

    companion object {
        /**
         * Item types whose states can never be plotted in a chart
         */
        private val NON_NUMERIC_ITEM_TYPES = listOf(
            Item.Type.Call,
            Item.Type.Color,
            Item.Type.DateTime,
            Item.Type.Image,
            Item.Type.Location,
            Item.Type.Player
        )

        /**
         * Whether the states of [item] are known to be non-numeric without loading them.
         * For other types (e.g. String Items, which may contain numbers), this can only be decided from the states.
         */
        fun isKnownNonNumeric(item: Item) = item.type in NON_NUMERIC_ITEM_TYPES ||
            (item.type == Item.Type.Group && item.groupType in NON_NUMERIC_ITEM_TYPES)

        private val UNDEFINED_STATES = listOf("NULL", "UNDEF")
        private val NUMERIC_ITEM_TYPES = listOf(
            Item.Type.Number,
            Item.Type.NumberWithDimension,
            Item.Type.Dimmer,
            Item.Type.Rollershutter,
            Item.Type.Switch,
            Item.Type.Contact
        )
    }
}
