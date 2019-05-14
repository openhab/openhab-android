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
data class Item internal constructor(val name: String, val label: String?, val type: Type,
                                     val groupType: Type?, val link: String?, val readOnly: Boolean,
                                     val members: List<Item>, val options: List<LabeledValue>?,
                                     val state: ParsedState?) : Parcelable {
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
        @Throws(JSONException::class)
        fun updateFromEvent(item: Item?, jsonObject: JSONObject?): Item? {
            if (jsonObject == null) {
                return item
            }
            val parsedItem = jsonObject.toItem()
            // Events don't contain the link property, so preserve that if previously present
            val link = if (item != null) item.link else parsedItem.link
            return Item(parsedItem.name, parsedItem.label, parsedItem.type, parsedItem.groupType,
                    link, parsedItem.readOnly, parsedItem.members, parsedItem.options,
                    parsedItem.state)
        }
     }
}

fun Node.toItem(): Item? {
    var name: String? = null
    var state: String? = null
    var link: String? = null
    var type = Item.Type.None
    var groupType = Item.Type.None
    if (hasChildNodes()) {
        for (i in 0 until childNodes.length) {
            with (childNodes.item(i)) {
                when (nodeName) {
                    "type" -> type = textContent.toItemType()
                    "groupType" -> groupType = textContent.toItemType()
                    "name" -> name = textContent
                    "state" -> state = textContent
                    "link" -> link = textContent
                }
            }
        }
    }

    val finalName = name ?: return null
    if (state == "Uninitialized" || state == "Undefined") {
        state = null
    }

    return Item(finalName, finalName, type, groupType, link, false,
            emptyList(), null, state.toParsedState())
}

@Throws(JSONException::class)
fun JSONObject.toItem(): Item {
    val name = getString("name")
    var state = optString("state", "")
    if (state == "NULL" || state == "UNDEF" || state.equals("undefined", ignoreCase = true)) {
        state = null
    }

    val stateDescription = optJSONObject("stateDescription")
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
    val membersJson = optJSONArray("members")
    if (membersJson != null) {
        for (i in 0 until membersJson.length()) {
            members.add(membersJson.getJSONObject(i).toItem())
        }
    }

    val numberPattern = stateDescription?.optString("pattern")
    return Item(name,
            optString("label", name),
            getString("type").toItemType(),
            optString("groupType").toItemType(),
            optString("link", null),
            readOnly,
            members,
            options,
            state.toParsedState(numberPattern))
}

fun String?.toItemType(): Item.Type {
    var type = this ?: return Item.Type.None

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
        return Item.Type.StringItem
    }
    if (type == "Number" && colonPos > 0) {
        return Item.Type.NumberWithDimension
    }
    try {
        return Item.Type.valueOf(type)
    } catch (e: IllegalArgumentException) {
        return Item.Type.None
    }
}