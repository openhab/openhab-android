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

package org.openhab.habdroid.wear.util

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.WidgetDataSource
import org.openhab.habdroid.model.toItem
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.optStringOrNull

private val JSON_HEADERS = mapOf("Accept" to "application/json")

/**
 * Loads a sitemap page once, e.g. for a tile. The app uses SitemapPageRepository to keep pages up to date.
 */
@Throws(HttpClient.HttpException::class, JSONException::class)
suspend fun Connection.loadPageWidgets(url: String): List<Widget> {
    val response = httpClient.get(url, headers = JSON_HEADERS).asText().response
    return WidgetDataSource().apply { setSourceJson(JSONObject(response)) }.widgets
}

/**
 * @return The item and its state formatted by the server
 */
@Throws(HttpClient.HttpException::class, JSONException::class)
suspend fun Connection.loadItem(name: String): Pair<Item, String?> {
    val response = httpClient.get("rest/items/$name", headers = JSON_HEADERS).asText().response
    val json = JSONObject(response)
    return json.toItem() to json.optStringOrNull("displayState")
}

/**
 * @param groupName Only return members of this group, or all items if null
 * @return Name and label of the items
 */
@Throws(HttpClient.HttpException::class, JSONException::class)
suspend fun Connection.loadItemNames(groupName: String?): List<Pair<String, String>> {
    val url = if (groupName != null) "rest/items/$groupName" else "rest/items?fields=name,label,type"
    val response = httpClient.get(url, headers = JSON_HEADERS).asText().response
    val items = if (groupName != null) {
        JSONObject(response).optJSONArray("members") ?: JSONArray()
    } else {
        JSONArray(response)
    }
    return items
        .map { item ->
            item.getString("name") to
                (item.optStringOrNull("label")?.takeIf { it.isNotEmpty() } ?: item.getString("name"))
        }
        .sortedBy { (_, label) -> label.lowercase() }
}
