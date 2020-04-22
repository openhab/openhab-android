/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

package org.openhab.habdroid.model

import android.content.SharedPreferences
import android.net.Uri
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.appendQueryParameter
import org.openhab.habdroid.util.forEach
import org.openhab.habdroid.util.getChartScalingFactor
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.optStringOrFallback
import org.openhab.habdroid.util.optStringOrNull
import org.openhab.habdroid.util.shouldRequestHighResChart
import org.w3c.dom.Node
import java.util.ArrayList
import java.util.Random
import kotlin.math.abs
import kotlin.math.max

@Parcelize
data class Widget(
    val id: String,
    val parentId: String?,
    private val rawLabel: String,
    val icon: IconResource?,
    val state: ParsedState?,
    val type: Type,
    val url: String?,
    val item: Item?,
    val linkedPage: LinkedPage?,
    val mappings: List<LabeledValue>,
    val encoding: String?,
    val iconColor: String?,
    val labelColor: String?,
    val valueColor: String?,
    val refresh: Int,
    val minValue: Float,
    val maxValue: Float,
    val step: Float,
    val period: String,
    val service: String,
    val legend: Boolean?,
    val switchSupport: Boolean,
    val height: Int,
    val visibility: Boolean
) : Parcelable {
    val label get() = rawLabel.split("[", "]")[0]
    val stateFromLabel: String? get() = rawLabel.split("[", "]").getOrNull(1)

    val mappingsOrItemOptions get() =
        if (mappings.isEmpty() && item?.options != null) item.options else mappings

    @Suppress("unused")
    enum class Type {
        Chart,
        Colorpicker,
        Default,
        Frame,
        Group,
        Image,
        Mapview,
        Selection,
        Setpoint,
        Slider,
        Switch,
        Text,
        Video,
        Webview,
        Unknown
    }

    fun toChartUrl(
        prefs: SharedPreferences,
        random: Random,
        width: Int,
        height: Int = width / 2,
        chartTheme: CharSequence?,
        density: Int,
        forcedPeriod: String = period,
        forcedLegend: Boolean? = legend
    ): String? {
        item ?: return null

        val actualDensity = density.toFloat() / prefs.getChartScalingFactor()
        val resDivider = if (prefs.shouldRequestHighResChart()) 1 else 2

        val chartUrl = Uri.Builder()
            .path("chart")
            .appendQueryParameter(if (item.type === Item.Type.Group) "groups" else "items", item.name)
            .appendQueryParameter("dpi", actualDensity.toInt() / resDivider)
            .appendQueryParameter("period", forcedPeriod)
            .appendQueryParameter("random", random.nextInt())

        if (service.isNotEmpty()) {
            chartUrl.appendQueryParameter("service", service)
        }
        if (chartTheme != null) {
            chartUrl.appendQueryParameter("theme", chartTheme.toString())
        }
        if (forcedLegend != null) {
            chartUrl.appendQueryParameter("legend", forcedLegend)
        }
        if (width > 0) {
            chartUrl.appendQueryParameter("w", width / resDivider)
            chartUrl.appendQueryParameter("h", height / resDivider)
        }

        return chartUrl.toString()
    }

    companion object {
        @Throws(JSONException::class)
        fun updateFromEvent(source: Widget, eventPayload: JSONObject): Widget {
            val item = Item.updateFromEvent(source.item, eventPayload.optJSONObject("item"))
            val iconName = eventPayload.optStringOrFallback("icon", source.icon?.icon)
            val icon = iconName.toOH2WidgetIconResource(item, source.type, source.mappings.isNotEmpty())
            return Widget(source.id, source.parentId,
                eventPayload.optString("label", source.label),
                icon,
                determineWidgetState(eventPayload.optStringOrNull("state"), item),
                source.type, source.url, item, source.linkedPage, source.mappings,
                source.encoding, source.iconColor,
                eventPayload.optStringOrNull("labelcolor"),
                eventPayload.optStringOrNull("valuecolor"),
                source.refresh, source.minValue, source.maxValue, source.step,
                source.period, source.service, source.legend,
                source.switchSupport, source.height,
                eventPayload.optBoolean("visibility", source.visibility))
        }

        internal fun sanitizeRefreshRate(refresh: Int) = if (refresh in 1..99) 100 else refresh
        internal fun sanitizePeriod(period: String?) = if (period.isNullOrEmpty()) "D" else period
        internal fun sanitizeMinMaxStep(min: Float, max: Float, step: Float) =
            Triple(min, max(min, max), abs(step))

        internal fun determineWidgetState(state: String?, item: Item?): ParsedState? {
            return state.toParsedState(item?.state?.asNumber?.format) ?: item?.state
        }
    }
}

fun String?.toWidgetType(): Widget.Type {
    if (this != null) {
        try {
            return Widget.Type.valueOf(this)
        } catch (e: IllegalArgumentException) {
            // fall through
        }
    }
    return Widget.Type.Unknown
}

fun Node.collectWidgets(parent: Widget?): List<Widget> {
    var item: Item? = null
    var linkedPage: LinkedPage? = null
    var id: String? = null
    var label: String? = null
    var icon: String? = null
    var url: String? = null
    var period = ""
    var service = ""
    var encoding: String? = null
    var iconColor: String? = null
    var labelColor: String? = null
    var valueColor: String? = null
    var switchSupport = false
    var type = Widget.Type.Unknown
    var minValue = 0f
    var maxValue = 100f
    var step = 1f
    var refresh = 0
    var height = 0
    val mappings = ArrayList<LabeledValue>()
    val childWidgetNodes = ArrayList<Node>()

    childNodes.forEach { node ->
        when (node.nodeName) {
            "item" -> item = node.toItem()
            "linkedPage" -> linkedPage = node.toLinkedPage()
            "widget" -> childWidgetNodes.add(node)
            "type" -> type = node.textContent.toWidgetType()
            "widgetId" -> id = node.textContent
            "label" -> label = node.textContent
            "icon" -> icon = node.textContent
            "url" -> url = node.textContent
            "minValue" -> minValue = node.textContent.toFloat()
            "maxValue" -> maxValue = node.textContent.toFloat()
            "step" -> step = node.textContent.toFloat()
            "refresh" -> refresh = node.textContent.toInt()
            "period" -> period = node.textContent
            "service" -> service = node.textContent
            "height" -> height = node.textContent.toInt()
            "iconcolor" -> iconColor = node.textContent
            "valuecolor" -> valueColor = node.textContent
            "labelcolor" -> labelColor = node.textContent
            "encoding" -> encoding = node.textContent
            "switchSupport" -> switchSupport = node.textContent?.toBoolean() == true
            "mapping" -> {
                var mappingCommand = ""
                var mappingLabel = ""
                node.childNodes.forEach { childNode ->
                    when (childNode.nodeName) {
                        "command" -> mappingCommand = childNode.textContent
                        "label" -> mappingLabel = childNode.textContent
                    }
                }
                mappings.add(LabeledValue(mappingCommand, mappingLabel))
            }
            else -> {}
        }
    }

    val finalId = id ?: return emptyList()
    val (actualMin, actualMax, actualStep) = Widget.sanitizeMinMaxStep(minValue, maxValue, step)

    val widget = Widget(finalId, parent?.id, label.orEmpty(), icon.toOH1IconResource(),
        item?.state, type, url, item, linkedPage, mappings, encoding, iconColor, labelColor, valueColor,
        Widget.sanitizeRefreshRate(refresh), actualMin, actualMax, actualStep,
        Widget.sanitizePeriod(period), service, null, switchSupport, height, true)
    val childWidgets = childWidgetNodes.map { node -> node.collectWidgets(widget) }.flatten()

    return listOf(widget) + childWidgets
}

@Throws(JSONException::class)
fun JSONObject.collectWidgets(parent: Widget?): List<Widget> {
    val mappings = if (has("mappings")) {
        getJSONArray("mappings").map { obj -> obj.toLabeledValue("command", "label") }
    } else {
        emptyList()
    }

    val item = optJSONObject("item")?.toItem()
    val type = getString("type").toWidgetType()
    val icon = optStringOrNull("icon")
    val (minValue, maxValue, step) = Widget.sanitizeMinMaxStep(
        optDouble("minValue", 0.0).toFloat(),
        optDouble("maxValue", 100.0).toFloat(),
        optDouble("step", 1.0).toFloat()
    )

    val widget = Widget(
        getString("widgetId"),
        parent?.id,
        optString("label", ""),
        icon.toOH2WidgetIconResource(item, type, mappings.isNotEmpty()),
        Widget.determineWidgetState(optStringOrNull("state"), item),
        type,
        optStringOrNull("url"),
        item,
        optJSONObject("linkedPage").toLinkedPage(),
        mappings,
        optStringOrNull("encoding"),
        optStringOrNull("iconcolor"),
        optStringOrNull("labelcolor"),
        optStringOrNull("valuecolor"),
        Widget.sanitizeRefreshRate(optInt("refresh")),
        minValue, maxValue, step,
        Widget.sanitizePeriod(optString("period")),
        optString("service", ""),
        if (has("legend")) getBoolean("legend") else null,
        if (has("switchSupport")) getBoolean("switchSupport") else false,
        optInt("height"),
        optBoolean("visibility", true))

    val result = arrayListOf(widget)
    val childWidgetJson = optJSONArray("widgets")
    childWidgetJson?.forEach { obj -> result.addAll(obj.collectWidgets(widget)) }
    return result
}
