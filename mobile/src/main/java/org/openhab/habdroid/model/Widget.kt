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
import org.w3c.dom.Node

import java.util.ArrayList
import java.util.Locale

@Parcelize
data class Widget(val id: String, val parentId: String?, val label: String,
                  val icon: String?, val iconPath: String?, val state: ParsedState?,
                  val type: Type, val url: String?, val item: Item?,
                  val linkedPage: LinkedPage?, val mappings: List<LabeledValue>,
                  val encoding: String?, val iconColor: String?, val labelColor: String?,
                  val valueColor: String?, val refresh: Int, val minValue: Float,
                  val maxValue: Float, val step: Float, val period: String,
                  val service: String, val legend: Boolean?,
                  val switchSupport: Boolean, val height: Int) : Parcelable {

    val mappingsOrItemOptions: List<LabeledValue>
        get() {
            return if (mappings.isEmpty() && item?.options != null) item.options else mappings
        }

    fun hasMappings(): Boolean {
        return !mappings.isEmpty()
    }

    fun hasMappingsOrItemOptions(): Boolean {
        return !mappingsOrItemOptions.isEmpty()
    }

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
        fun parseXml(allWidgets: MutableList<Widget>, parent: Widget?, startNode: Node) {
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
            var type = Type.Unknown
            var minValue = 0f
            var maxValue = 100f
            var step = 1f
            var refresh = 0
            var height = 0
            val mappings = ArrayList<LabeledValue>()
            val childWidgetNodes = ArrayList<Node>()

            if (startNode.hasChildNodes()) {
                val childNodes = startNode.childNodes
                for (i in 0 until childNodes.length) {
                    val childNode = childNodes.item(i)
                    when (childNode.nodeName) {
                        "item" -> item = Item.fromXml(childNode)
                        "linkedPage" -> linkedPage = LinkedPage.fromXml(childNode)
                        "widget" -> childWidgetNodes.add(childNode)
                        "type" -> type = parseType(childNode.textContent)
                        "widgetId" -> id = childNode.textContent
                        "label" -> label = childNode.textContent
                        "icon" -> icon = childNode.textContent
                        "url" -> url = childNode.textContent
                        "minValue" -> minValue = java.lang.Float.valueOf(childNode.textContent)
                        "maxValue" -> maxValue = java.lang.Float.valueOf(childNode.textContent)
                        "step" -> step = java.lang.Float.valueOf(childNode.textContent)
                        "refresh" -> refresh = Integer.valueOf(childNode.textContent)
                        "period" -> period = childNode.textContent
                        "service" -> service = childNode.textContent
                        "height" -> height = Integer.valueOf(childNode.textContent)
                        "iconcolor" -> iconColor = childNode.textContent
                        "valuecolor" -> valueColor = childNode.textContent
                        "labelcolor" -> labelColor = childNode.textContent
                        "encoding" -> encoding = childNode.textContent
                        "switchSupport" -> switchSupport = java.lang.Boolean.valueOf(childNode.textContent)
                        "mapping" -> {
                            val mappingChildNodes = childNode.childNodes
                            var mappingCommand = ""
                            var mappingLabel = ""
                            for (k in 0 until mappingChildNodes.length) {
                                val mappingNode = mappingChildNodes.item(k)
                                when (mappingNode.nodeName) {
                                    "command" -> mappingCommand = mappingNode.textContent
                                    "label" -> mappingLabel = mappingNode.textContent
                                    else -> {
                                    }
                                }
                            }
                            mappings.add(LabeledValue(mappingCommand, mappingLabel))
                        }
                        else -> { }
                    }
                }
            }

            if (id == null) {
                return
            }

            val widget = build(id, parent?.id, label ?: "", icon, String.format("images/%s.png", icon),
                    item?.state, type, url, item, linkedPage, mappings, encoding, iconColor,
                    labelColor, valueColor, refresh, minValue, maxValue, step, period,
                    service, null, switchSupport, height)
            allWidgets.add(widget)

            for (childNode in childWidgetNodes) {
                parseXml(allWidgets, widget, childNode)
            }
        }

        @Throws(JSONException::class)
        fun parseJson(allWidgets: MutableList<Widget>, parent: Widget?,
                      widgetJson: JSONObject, iconFormat: String) {
            val mappings = ArrayList<LabeledValue>()
            if (widgetJson.has("mappings")) {
                val mappingsJsonArray = widgetJson.getJSONArray("mappings")
                for (i in 0 until mappingsJsonArray.length()) {
                    val mappingObject = mappingsJsonArray.getJSONObject(i)
                    mappings.add(LabeledValue(
                            mappingObject.getString("command"),
                            mappingObject.getString("label")))
                }
            }

            val item = Item.fromJson(widgetJson.optJSONObject("item"))
            val type = parseType(widgetJson.getString("type"))
            val icon = widgetJson.optString("icon", null)

            val widget = build(widgetJson.getString("widgetId"), parent?.id,
                    widgetJson.optString("label", ""),
                    icon, determineOH2IconPath(item, type, icon, iconFormat, !mappings.isEmpty()),
                    determineWidgetState(widgetJson.optString("state", null), item),
                    type,
                    widgetJson.optString("url", null),
                    item,
                    LinkedPage.fromJson(widgetJson.optJSONObject("linkedPage")),
                    mappings,
                    widgetJson.optString("encoding", null),
                    widgetJson.optString("iconcolor", null),
                    widgetJson.optString("labelcolor", null),
                    widgetJson.optString("valuecolor", null),
                    widgetJson.optInt("refresh"),
                    widgetJson.optDouble("minValue", 0.0).toFloat(),
                    widgetJson.optDouble("maxValue", 100.0).toFloat(),
                    widgetJson.optDouble("step", 1.0).toFloat(),
                    widgetJson.optString("period", "D"),
                    widgetJson.optString("service", ""),
                    if (widgetJson.has("legend")) widgetJson.getBoolean("legend") else null,
                    if (widgetJson.has("switchSupport")) widgetJson.getBoolean("switchSupport") else false,
                    widgetJson.optInt("height"))

            allWidgets.add(widget)

            val childWidgetJson = widgetJson.optJSONArray("widgets")
            if (childWidgetJson != null) {
                for (i in 0 until childWidgetJson.length()) {
                    parseJson(allWidgets, widget, childWidgetJson.getJSONObject(i), iconFormat)
                }
            }
        }

        @Throws(JSONException::class)
        fun updateFromEvent(source: Widget, eventPayload: JSONObject, iconFormat: String): Widget {
            val item = Item.updateFromEvent(source.item, eventPayload.getJSONObject("item"))
            val iconPath = determineOH2IconPath(item, source.type,
                    source.icon, iconFormat, !source.mappings.isEmpty())
            return build(source.id, source.parentId,
                    eventPayload.optString("label", source.label),
                    source.icon, iconPath,
                    determineWidgetState(eventPayload.optString("state", null), item),
                    source.type, source.url, item, source.linkedPage, source.mappings,
                    source.encoding, source.iconColor, source.labelColor, source.valueColor,
                    source.refresh, source.minValue, source.maxValue, source.step,
                    source.period, source.service, source.legend,
                    source.switchSupport, source.height)
        }

        private fun build(id: String, parentId: String?, label: String, icon: String?,
                          iconPath: String?, state: ParsedState?, type: Type, url: String?,
                          item: Item?, linkedPage: LinkedPage?, mappings: List<LabeledValue>,
                          encoding: String?, iconColor: String?, labelColor: String?,
                          valueColor: String?, refresh: Int, minValue: Float, maxValue: Float,
                          step: Float, period: String, service: String, legend: Boolean?,
                          switchSupport: Boolean, height: Int) : Widget {
            // A 'none' icon equals no icon at all
            val actualIcon = if (icon == "none") null else icon
            // Consider a minimal refresh rate of 100 ms, but 0 is special and means 'no refresh'
            val actualRefresh = if (refresh > 0 && refresh < 100) 100 else refresh
            // Default period to 'D'
            val actualPeriod = if (period.isEmpty()) "D" else period
            // Sanitize minValue, maxValue and step: min <= max, step >= 0
            val actualMaxValue = Math.max(minValue, maxValue)
            val actualStep = Math.abs(step)

            return Widget(id, parentId, label, actualIcon, iconPath, state, type, url,
                    item, linkedPage, mappings, encoding, iconColor, labelColor, valueColor,
                    actualRefresh, minValue, actualMaxValue, actualStep, actualPeriod,
                    service, legend, switchSupport, height)
        }


        private fun determineWidgetState(state: String?, item: Item?): ParsedState? {
            return ParsedState.from(state, item?.state?.asNumber?.format) ?: item?.state
        }

        private fun determineOH2IconPath(item: Item?, type: Type, icon: String?,
                                         iconFormat: String, hasMappings: Boolean): String {
            val itemState = item?.state
            var iconState = itemState?.asString ?: ""
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
                        val color = Color.HSVToColor(itemState.asHsv)
                        iconState = String.format(Locale.US, "#%02x%02x%02x",
                                Color.red(color), Color.green(color), Color.blue(color))
                    }
                } else if (type == Type.Switch && !hasMappings
                        && !item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
                    // For switch items without mappings (just ON and OFF) that control a dimmer item
                    // and which are not ON or OFF already, set the state to "OFF" instead of 0
                    // or to "ON" to fetch the correct icon
                    iconState = if (itemState.asString == "0" || itemState.asString == "OFF") "OFF" else "ON"
                }
            }

            return String.format("icon/%s?state=%s&format=%s", icon, iconState, iconFormat)
        }

        private fun parseType(type: String?): Type {
            if (type != null) {
                try {
                    return Type.valueOf(type)
                } catch (e: IllegalArgumentException) {
                    // fall through
                }

            }
            return Type.Unknown
        }
    }
}
