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

package org.openhab.habdroid.ui.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages of the Main UI bridge protocol v1, see docs/mainui-bridge in openhab-ios.
 */
data class BridgeHello(val impl: String, val version: String?, val accepted: List<String>, val features: List<String>) {
    companion object {
        fun fromJson(json: JSONObject) = BridgeHello(
            json.optString("impl", "other"),
            json.optString("version").ifEmpty { null },
            json.optJSONArray("accepted").toStringList(),
            json.optJSONArray("features").toStringList()
        )
    }
}

data class BridgeNavState(val path: String, val history: List<String>, val props: List<String>?, val modal: Boolean) {
    companion object {
        fun fromJson(json: JSONObject) = BridgeNavState(
            json.optString("path"),
            json.optJSONArray("history").toStringList(),
            json.optJSONArray("props")?.toStringList(),
            json.optBoolean("modal")
        )
    }
}

data class BridgeIcon(val name: String?, val md: String?, val svg: String?) {
    companion object {
        fun fromJson(json: JSONObject?): BridgeIcon? {
            json ?: return null
            return BridgeIcon(
                json.optString("name").ifEmpty { null },
                json.optString("md").ifEmpty { null },
                json.optString("svg").ifEmpty { null }
            )
        }
    }
}

data class BridgeNavbarAction(val id: String, val label: String, val icon: BridgeIcon?, val disabled: Boolean) {
    companion object {
        fun fromJson(json: JSONObject) = BridgeNavbarAction(
            json.optString("id"),
            json.optString("label"),
            BridgeIcon.fromJson(json.optJSONObject("icon")),
            json.optBoolean("disabled")
        )
    }
}

data class BridgeNavbarState(
    val title: String,
    val titleInContent: Boolean,
    val hidden: Boolean,
    val backLabel: String?,
    val hasBack: Boolean,
    val leading: List<BridgeNavbarAction>,
    val trailing: List<BridgeNavbarAction>
) {
    val actions get() = leading + trailing

    companion object {
        fun fromJson(json: JSONObject): BridgeNavbarState {
            val back = json.optJSONObject("back")
            return BridgeNavbarState(
                json.optString("title"),
                json.optBoolean("titleInContent"),
                json.optBoolean("hidden"),
                back?.optString("label")?.ifEmpty { null },
                back != null,
                json.optJSONArray("leading").toObjectList { BridgeNavbarAction.fromJson(it) },
                json.optJSONArray("trailing").toObjectList { BridgeNavbarAction.fromJson(it) }
            )
        }
    }
}

data class BridgeMenuItem(
    val id: String,
    val label: String,
    val footer: String?,
    val icon: BridgeIcon?,
    val path: String?,
    val active: Boolean,
    val children: List<BridgeMenuItem>,
    val more: List<BridgeMenuItem>
) {
    companion object {
        fun fromJson(json: JSONObject): BridgeMenuItem = BridgeMenuItem(
            json.optString("id"),
            json.optString("label"),
            json.optString("footer").ifEmpty { null },
            BridgeIcon.fromJson(json.optJSONObject("icon")),
            json.optString("path").ifEmpty { null },
            json.optBoolean("active"),
            json.optJSONArray("children").toObjectList { fromJson(it) },
            json.optJSONArray("more").toObjectList { fromJson(it) }
        )
    }
}

data class BridgeMenuSection(val id: String, val title: String?, val items: List<BridgeMenuItem>) {
    companion object {
        fun fromJson(json: JSONObject) = BridgeMenuSection(
            json.optString("id"),
            json.optString("title").ifEmpty { null },
            json.optJSONArray("items").toObjectList { BridgeMenuItem.fromJson(it) }
        )

        fun listFromJson(json: JSONObject) = json.optJSONArray("sections").toObjectList { fromJson(it) }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    this ?: return emptyList()
    return (0 until length()).map { optString(it) }
}

private fun <T> JSONArray?.toObjectList(map: (JSONObject) -> T): List<T> {
    this ?: return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }.map(map)
}
