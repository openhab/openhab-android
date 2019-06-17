/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model

import android.graphics.Color
import android.os.Parcelable

import kotlinx.android.parcel.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.forEach
import org.openhab.habdroid.util.map
import org.w3c.dom.Node

import java.util.ArrayList
import java.util.Locale

@Parcelize
data class Widget(
    val id: String,
    val parentId: String?,
    val label: String,
    val icon: String?,
    val iconPath: String?,
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
    val height: Int
) : Parcelable {
    val mappingsOrItemOptions get() =
        if (mappings.isEmpty() && item?.options != null) item.options else mappings

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

    companion object {
        @Throws(JSONException::class)
        fun updateFromEvent(source: Widget, eventPayload: JSONObject, iconFormat: String): Widget {
            val item = Item.updateFromEvent(source.item, eventPayload.getJSONObject("item"))
            val icon = eventPayload.optString("icon", source.icon)
            val iconPath = determineOH2IconPath(item, source.type, icon, iconFormat,
                source.mappings.isNotEmpty())
            return Widget(source.id, source.parentId,
                eventPayload.optString("label", source.label),
                sanitizeIcon(icon), iconPath,
                determineWidgetState(eventPayload.optString("state", null), item),
                source.type, source.url, item, source.linkedPage, source.mappings,
                source.encoding, source.iconColor,
                eventPayload.optString("labelcolor", source.labelColor),
                eventPayload.optString("valuecolor", source.valueColor),
                source.refresh, source.minValue, source.maxValue, source.step,
                source.period, source.service, source.legend,
                source.switchSupport, source.height)
        }

        internal fun sanitizeIcon(icon: String?) = if (icon == "none") null else icon
        internal fun sanitizeRefreshRate(refresh: Int) = if (refresh in 1..99) 100 else refresh
        internal fun sanitizePeriod(period: String?) = if (period.isNullOrEmpty()) "D" else period
        internal fun sanitizeMinMaxStep(min: Float, max: Float, step: Float) =
            Triple(min, Math.max(min, max), Math.abs(step))

        internal fun determineWidgetState(state: String?, item: Item?): ParsedState? {
            return state.toParsedState(item?.state?.asNumber?.format) ?: item?.state
        }

        internal fun determineOH2IconPath(
            item: Item?,
            type: Type,
            icon: String?,
            iconFormat: String,
            hasMappings: Boolean
        ): String {
            val itemState = item?.state
            var iconState = itemState?.asString.orEmpty()
            if (itemState != null) {
                if (item.isOfTypeOrGroupType(Item.Type.Color)) {
                    // For items that control a color item fetch the correct icon
                    if (type == Type.Slider || type == Type.Switch && !hasMappings) {
                        try {
                            iconState = itemState.asBrightness.toString()
                            if (type == Type.Switch) {
                                iconState = if (iconState == "0") "OFF" else "ON"
                            }
                        } catch (e: Exception) {
                            iconState = "OFF"
                        }
                    } else if (itemState.asHsv != null) {
                        val color = itemState.asHsv.toColor()
                        iconState = String.format(Locale.US, "#%02x%02x%02x",
                            Color.red(color), Color.green(color), Color.blue(color))
                    }
                } else if (type == Type.Switch && !hasMappings && !item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
                    // For switch items without mappings (just ON and OFF) that control a dimmer item
                    // and which are not ON or OFF already, set the state to "OFF" instead of 0
                    // or to "ON" to fetch the correct icon
                    iconState = if (itemState.asString == "0" || itemState.asString == "OFF") "OFF" else "ON"
                }
            }

            return "icon/$icon?state=$iconState&format=$iconFormat"
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

    val widget = Widget(finalId, parent?.id, label.orEmpty(),
        Widget.sanitizeIcon(icon), "images/$icon.png",
        item?.state, type, url, item, linkedPage, mappings, encoding, iconColor, labelColor, valueColor,
        Widget.sanitizeRefreshRate(refresh), actualMin, actualMax, actualStep,
        Widget.sanitizePeriod(period), service, null, switchSupport, height)
    val childWidgets = childWidgetNodes.map { node -> node.collectWidgets(widget) }.flatten()

    return listOf(widget) + childWidgets
}

@Throws(JSONException::class)
fun JSONObject.collectWidgets(parent: Widget?, iconFormat: String): List<Widget> {
    val mappings = if (has("mappings")) {
        getJSONArray("mappings").map { obj -> obj.toLabeledValue("command", "label") }
    } else {
        emptyList()
    }

    val item = optJSONObject("item")?.toItem()
    val type = getString("type").toWidgetType()
    val icon = optString("icon", null)
    val (minValue, maxValue, step) = Widget.sanitizeMinMaxStep(
        optDouble("minValue", 0.0).toFloat(),
        optDouble("maxValue", 100.0).toFloat(),
        optDouble("step", 1.0).toFloat()
    )

    val widget = Widget(
        getString("widgetId"),
        parent?.id,
        optString("label", ""),
        Widget.sanitizeIcon(icon),
        Widget.determineOH2IconPath(item, type, icon, iconFormat, mappings.isNotEmpty()),
        Widget.determineWidgetState(optString("state", null), item),
        type,
        optString("url", null),
        item,
        optJSONObject("linkedPage").toLinkedPage(),
        mappings,
        optString("encoding", null),
        optString("iconcolor", null),
        optString("labelcolor", null),
        optString("valuecolor", null),
        Widget.sanitizeRefreshRate(optInt("refresh")),
        minValue, maxValue, step,
        Widget.sanitizePeriod(optString("period")),
        optString("service", ""),
        if (has("legend")) getBoolean("legend") else null,
        if (has("switchSupport")) getBoolean("switchSupport") else false,
        optInt("height"))

    val result = arrayListOf(widget)
    val childWidgetJson = optJSONArray("widgets")
    childWidgetJson?.forEach { obj -> result.addAll(obj.collectWidgets(widget, iconFormat)) }
    return result
}
