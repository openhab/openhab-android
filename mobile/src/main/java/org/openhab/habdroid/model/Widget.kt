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

package org.openhab.habdroid.model

import android.content.SharedPreferences
import android.net.Uri
import android.os.Parcelable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.appendQueryParameter
import org.openhab.habdroid.util.forEach
import org.openhab.habdroid.util.getChartScalingFactor
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.optBooleanOrNull
import org.openhab.habdroid.util.optFloatOrNull
import org.openhab.habdroid.util.optIntOrNull
import org.openhab.habdroid.util.optStringOrFallback
import org.openhab.habdroid.util.optStringOrNull
import org.openhab.habdroid.util.shouldRequestHighResChart

@Parcelize
data class Widget(
    val id: String,
    val parentId: String?,
    private val rawLabel: String,
    val labelSource: LabelSource,
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
    private val rawMinValue: Float?,
    private val rawMaxValue: Float?,
    private val rawStep: Float?,
    val row: Int?,
    val column: Int?,
    val command: String?,
    val releaseCommand: String?,
    val stateless: Boolean?,
    val period: String,
    val service: String,
    val legend: Boolean?,
    val forceAsItem: Boolean,
    val yAxisDecimalPattern: String?,
    val switchSupport: Boolean,
    val releaseOnly: Boolean?,
    val height: Int,
    val visibility: Boolean,
    val rawInputHint: InputTypeHint?
) : Parcelable {
    val label get() = rawLabel.split("[", "]")[0].trim()
    val stateFromLabel: String? get() {
        val value = rawLabel.split("[", "]").getOrNull(1)?.trim()
        val optionLabel = mappingsOrItemOptions.find { it.value == value }?.label
        return optionLabel ?: value
    }

    val mappingsOrItemOptions get() = if (mappings.isEmpty() && item?.options != null) item.options else mappings

    val minValue get() = min(configuredMinValue, configuredMaxValue)
    val maxValue get() = max(configuredMinValue, configuredMaxValue)
    val step get() = abs(configuredStep)

    val inputHint: InputTypeHint get() {
        if (rawInputHint != null) {
            return rawInputHint
        }
        if (item?.isOfTypeOrGroupType(Item.Type.DateTime) == true) {
            return InputTypeHint.Datetime
        }
        return InputTypeHint.Text
    }

    private val configuredMinValue get() = when {
        rawMinValue != null -> rawMinValue
        item?.minimum != null && item.type != Item.Type.Dimmer -> item.minimum
        else -> 0f
    }
    private val configuredMaxValue get() = when {
        rawMaxValue != null -> rawMaxValue
        item?.maximum != null && item.type != Item.Type.Dimmer -> item.maximum
        else -> 100f
    }
    private val configuredStep get() = when {
        rawStep != null -> rawStep
        item?.step != null && item.type != Item.Type.Dimmer -> item.step
        else -> 1f
    }

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
        Input,
        Buttongrid,
        Button,
        Unknown
    }

    enum class InputTypeHint {
        Text,
        Number,
        Date,
        Time,
        Datetime
    }

    enum class LabelSource {
        Unknown,
        ItemLabel,
        ItemName,
        SitemapDefinition
    }

    fun toChartUrl(
        prefs: SharedPreferences,
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
            .appendQueryParameter(
                if (item.type === Item.Type.Group && !forceAsItem) "groups" else "items",
                item.name
            )
            .appendQueryParameter("dpi", actualDensity.toInt() / resDivider)
            .appendQueryParameter("period", forcedPeriod)

        if (service.isNotEmpty()) {
            chartUrl.appendQueryParameter("service", service)
        }
        chartTheme?.let { chartUrl.appendQueryParameter("theme", it.toString()) }
        forcedLegend?.let { chartUrl.appendQueryParameter("legend", it) }
        yAxisDecimalPattern?.let { chartUrl.appendQueryParameter("yAxisDecimalPattern", it) }

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
            val state = determineWidgetState(eventPayload.optStringOrNull("state"), item)
            val iconName = eventPayload.optStringOrFallback("icon", source.icon?.icon)
            val staticIcon = source.icon?.customState?.isEmpty() == true
            val hasMappings = source.mappings.isNotEmpty()
            val icon = iconName.toWidgetIconResource(item, source.type, hasMappings, !staticIcon)
            return Widget(
                id = source.id,
                parentId = source.parentId,
                rawLabel = eventPayload.optString("label", source.label),
                labelSource = eventPayload.optStringOrNull("labelSource").toLabelSource(),
                icon = icon,
                state = state,
                type = source.type,
                url = source.url,
                item = item,
                linkedPage = source.linkedPage,
                mappings = source.mappings,
                encoding = source.encoding,
                iconColor = eventPayload.optStringOrNull("iconcolor"),
                labelColor = eventPayload.optStringOrNull("labelcolor"),
                valueColor = eventPayload.optStringOrNull("valuecolor"),
                refresh = source.refresh,
                rawMinValue = source.rawMinValue,
                rawMaxValue = source.rawMaxValue,
                rawStep = source.rawStep,
                row = source.row,
                column = source.column,
                command = source.command,
                releaseCommand = source.releaseCommand,
                stateless = source.stateless,
                period = source.period,
                service = source.service,
                legend = source.legend,
                forceAsItem = source.forceAsItem,
                switchSupport = source.switchSupport,
                releaseOnly = source.releaseOnly,
                yAxisDecimalPattern = source.yAxisDecimalPattern,
                height = source.height,
                visibility = eventPayload.optBoolean("visibility", source.visibility),
                rawInputHint = source.rawInputHint
            )
        }

        internal fun sanitizeRefreshRate(refresh: Int) = if (refresh in 1..99) 100 else refresh

        internal fun sanitizePeriod(period: String?) = if (period.isNullOrEmpty()) "D" else period

        internal fun determineWidgetState(state: String?, item: Item?): ParsedState? = when {
            state == null -> item?.state
            item?.isOfTypeOrGroupType(Item.Type.Number) == true ||
                item?.isOfTypeOrGroupType(Item.Type.NumberWithDimension) == true ->
                state.toParsedState(item.state?.asNumber?.format)
            else -> state.toParsedState()
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

fun String?.toInputHint(): Widget.InputTypeHint? = this?.let { value ->
    try {
        return Widget.InputTypeHint.valueOf(value.lowercase().replaceFirstChar { c -> c.uppercase() })
    } catch (e: IllegalArgumentException) {
        return null
    }
}

fun String?.toLabelSource(): Widget.LabelSource = when (this) {
    "SITEMAP_WIDGET" -> Widget.LabelSource.SitemapDefinition
    "ITEM_LABEL" -> Widget.LabelSource.ItemLabel
    "ITEM_NAME" -> Widget.LabelSource.ItemName
    else -> Widget.LabelSource.Unknown
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
    val staticIcon = optBoolean("staticIcon", false)

    val widget = Widget(
        id = getString("widgetId"),
        parentId = parent?.id,
        rawLabel = optString("label", ""),
        labelSource = optStringOrNull("labelSource").toLabelSource(),
        icon = icon.toWidgetIconResource(item, type, mappings.isNotEmpty(), !staticIcon),
        state = Widget.determineWidgetState(optStringOrNull("state"), item),
        type = type,
        url = optStringOrNull("url"),
        item = item,
        linkedPage = optJSONObject("linkedPage").toLinkedPage(),
        mappings = mappings,
        encoding = optStringOrNull("encoding"),
        iconColor = optStringOrNull("iconcolor"),
        labelColor = optStringOrNull("labelcolor"),
        valueColor = optStringOrNull("valuecolor"),
        refresh = Widget.sanitizeRefreshRate(optInt("refresh")),
        rawMinValue = optFloatOrNull("minValue"),
        rawMaxValue = optFloatOrNull("maxValue"),
        rawStep = optFloatOrNull("step"),
        row = optIntOrNull("row"),
        column = optIntOrNull("column"),
        command = optStringOrNull("command"),
        releaseCommand = optStringOrNull("releaseCommand"),
        stateless = optBooleanOrNull("stateless"),
        period = Widget.sanitizePeriod(optString("period")),
        service = optString("service", ""),
        legend = optBooleanOrNull("legend"),
        forceAsItem = optBoolean("forceAsItem", false),
        yAxisDecimalPattern = optString("yAxisDecimalPattern"),
        switchSupport = optBoolean("switchSupport", false),
        releaseOnly = optBooleanOrNull("releaseOnly"),
        height = optInt("height"),
        rawInputHint = optStringOrNull("inputHint").toInputHint(),
        visibility = optBoolean("visibility", true)
    )

    val result = arrayListOf(widget)
    val childWidgetJson = optJSONArray("widgets")
    childWidgetJson?.forEach { obj -> result.addAll(obj.collectWidgets(widget)) }
    return result
}
