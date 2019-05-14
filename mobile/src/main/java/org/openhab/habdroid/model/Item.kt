/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model

import android.os.Parcelable

import kotlinx.android.parcel.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.w3c.dom.Node

import java.util.ArrayList

@Parcelize
data class Item(val name: String, val label: String?, val type: Type, val groupType: Type?,
                val link: String?, val readOnly: Boolean, val members: List<Item>,
                val options: List<LabeledValue>?, val state: ParsedState?) : Parcelable {
    enum class Type {
        None,
        Color,
        Contact,
        DateTime,
        Dimmer,
        Group,
        Image,
        Location,
        Number,
        NumberWithDimension,
        Player,
        Rollershutter,
        StringItem,
        Switch
    }

    fun isOfTypeOrGroupType(type: Type): Boolean {
        return this.type == type || groupType == type
    }

    companion object {
        private fun parseType(typeString: String?): Type {
            var type: String = typeString ?: return Type.None
            // Earlier OH2 versions returned e.g. 'Switch' as 'SwitchItem'
            if (type.endsWith("Item")) {
                type = type.substring(0, type.length - 4)
            }
            // types can have subtypes (e.g. 'Number:Temperature'); split off those
            val colonPos = type.indexOf(':')
            if (colonPos > 0) {
                type = type.substring(0, colonPos)
            }

            if (type == "String") {
                return Type.StringItem
            }
            if (type == "Number" && colonPos > 0) {
                return Type.NumberWithDimension
            }
            try {
                return Type.valueOf(type)
            } catch (e: IllegalArgumentException) {
                return Type.None
            }
        }

        fun fromXml(startNode: Node): Item? {
            var name: String? = null
            var state: String? = null
            var link: String? = null
            var type = Type.None
            var groupType = Type.None
            if (startNode.hasChildNodes()) {
                val childNodes = startNode.childNodes
                for (i in 0 until childNodes.length) {
                    val childNode = childNodes.item(i)
                    when (childNode.nodeName) {
                        "type" -> type = parseType(childNode.textContent)
                        "groupType" -> groupType = parseType(childNode.textContent)
                        "name" -> name = childNode.textContent
                        "state" -> state = childNode.textContent
                        "link" -> link = childNode.textContent
                        else -> {
                        }
                    }
                }
            }

            if (name == null) {
                return null
            }
            if (state == "Uninitialized" || state == "Undefined") {
                state = null
            }

            return Item(name, name, type, groupType, link, false, emptyList(),
                    null, ParsedState.from(state, null))
        }

        @Throws(JSONException::class)
        fun updateFromEvent(item: Item?, jsonObject: JSONObject?): Item? {
            if (jsonObject == null) {
                return item
            }
            val parsedItem = parseFromJson(jsonObject)
            // Events don't contain the link property, so preserve that if previously present
            val link = if (item != null) item.link else parsedItem.link
            return Item(parsedItem.name, parsedItem.label, parsedItem.type, parsedItem.groupType,
                    link, parsedItem.readOnly, parsedItem.members, parsedItem.options,
                    parsedItem.state)
        }

        @Throws(JSONException::class)
        fun fromJson(jsonObject: JSONObject?): Item? {
            if (jsonObject == null) {
                return null
            }
            return parseFromJson(jsonObject)
        }

        @Throws(JSONException::class)
        private fun parseFromJson(jsonObject: JSONObject): Item {
            val name = jsonObject.getString("name")
            var state: String? = jsonObject.optString("state", "")
            if (state == "NULL" || state == "UNDEF" || state.equals("undefined", ignoreCase = true)) {
                state = null
            }

            val stateDescription = jsonObject.optJSONObject("stateDescription")
            val readOnly = stateDescription != null && stateDescription.optBoolean("readOnly", false)

            var options: MutableList<LabeledValue>? = null
            if (stateDescription != null && stateDescription.has("options")) {
                val optionsJson = stateDescription.getJSONArray("options")
                options = ArrayList()
                for (i in 0 until optionsJson.length()) {
                    val optionJson = optionsJson.getJSONObject(i)
                    options.add(LabeledValue(optionJson.getString("value"),
                            optionJson.getString("label")))
                }
            }

            val members = ArrayList<Item>()
            val membersJson = jsonObject.optJSONArray("members")
            if (membersJson != null) {
                for (i in 0 until membersJson.length()) {
                    val item = fromJson(membersJson.getJSONObject(i))
                    if (item != null) {
                        members.add(item)
                    }
                }
            }

            val numberPattern = stateDescription?.optString("pattern")
            return Item(name,
                    jsonObject.optString("label", name),
                    parseType(jsonObject.getString("type")),
                    parseType(jsonObject.optString("groupType")),
                    jsonObject.optString("link", null),
                    readOnly,
                    members,
                    options,
                    ParsedState.from(state, numberPattern))
        }
    }
}
