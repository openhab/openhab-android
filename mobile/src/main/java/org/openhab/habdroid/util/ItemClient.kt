/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

package org.openhab.habdroid.util

import android.util.Log
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.toItem
import org.xml.sax.InputSource
import org.xml.sax.SAXException

object ItemClient {
    private val TAG = ItemClient::class.java.simpleName

    suspend fun loadItems(connection: Connection): List<Item>? {
        val response = connection.httpClient.get("rest/items")
        val contentType = response.response.contentType()
        val content = response.asText().response

        if (contentType?.type == "application" && contentType.subtype == "json") {
            // JSON
            return try {
                JSONArray(content).map { it.toItem() }
            } catch (e: JSONException) {
                Log.e(TAG, "Failed parsing JSON result for items", e)
                null
            }
        } else {
            // XML
            return try {
                val dbf = DocumentBuilderFactory.newInstance()
                val builder = dbf.newDocumentBuilder()
                val document = builder.parse(InputSource(StringReader(content)))
                val nodes = document.childNodes
                val items = ArrayList<Item>(nodes.length)
                for (i in 0 until nodes.length) {
                    nodes.item(i).toItem()?.let { items.add(it) }
                }
                items
            } catch (e: ParserConfigurationException) {
                Log.e(TAG, "Failed parsing XML result for items", e)
                null
            } catch (e: SAXException) {
                Log.e(TAG, "Failed parsing XML result for items", e)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Failed parsing XML result for items", e)
                null
            }
        }
    }

    suspend fun loadItem(connection: Connection, itemName: String): Item? {
        val response = connection.httpClient.get("rest/items/$itemName")
        val contentType = response.response.contentType()
        val content = response.asText().response

        if (contentType?.type == "application" && contentType.subtype == "json") {
            // JSON
            return try {
                JSONObject(content).toItem()
            } catch (e: JSONException) {
                Log.e(TAG, "Failed parsing JSON result for item $itemName", e)
                null
            }
        } else {
            // XML
            return try {
                val dbf = DocumentBuilderFactory.newInstance()
                val builder = dbf.newDocumentBuilder()
                val document = builder.parse(InputSource(StringReader(content)))
                document.toItem()
            } catch (e: ParserConfigurationException) {
                Log.e(TAG, "Failed parsing XML result for item $itemName", e)
                null
            } catch (e: SAXException) {
                Log.e(TAG, "Failed parsing XML result for item $itemName", e)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Failed parsing XML result for item $itemName", e)
                null
            }
        }
    }
}
