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

package org.openhab.habdroid.util

import android.util.Log
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
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

    @Throws(HttpClient.HttpException::class)
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

    @Throws(HttpClient.HttpException::class)
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

    // Emits pairs of 'item name' - 'item state'
    @OptIn(ExperimentalCoroutinesApi::class)
    fun listenForItemChange(scope: CoroutineScope, connection: Connection, itemName: String?) = scope.produce {
        while (scope.isActive) {
            val subscription = connection.httpClient.makeSse(
                // Support for both the "openhab" and the older "smarthome" root topic by using a wildcard
                connection.httpClient.buildUrl("rest/events?topics=*/items/${itemName ?: "*"}/command")
            )

            while (scope.isActive) {
                try {
                    // ALIVE event is sent every 10 seconds, so use a timeout somewhat larger than that
                    val event = withTimeout(30.seconds) {
                        JSONObject(subscription.getNextEvent())
                    }
                    if (event.optString("type") == "ALIVE") {
                        Log.d(TAG, "Got ALIVE event for item $itemName")
                        continue
                    }
                    val topic = event.getString("topic")
                    val topicPath = topic.split('/')
                    // Possible formats:
                    // - openhab/items/<item>/statechanged
                    // - openhab/items/<group item>/<item>/statechanged
                    // When an update for a group is sent, there's also one for the individual item.
                    // Therefore always take the element on index two.
                    if (topicPath.size !in 4..5) {
                        throw JSONException("Unexpected topic path $topic")
                    }
                    val payload = JSONObject(event.getString("payload"))
                    Log.d(TAG, "Got payload: $payload")
                    send(topicPath[2] to payload.getString("value"))
                } catch (e: JSONException) {
                    Log.e(TAG, "Failed parsing JSON of state change event for item $itemName", e)
                } catch (e: HttpClient.SseFailureException) {
                    Log.e(TAG, "SSE failure for item $itemName", e)
                    break // restart subscription
                } catch (e: TimeoutCancellationException) {
                    Log.d(TAG, "No events received for item $itemName, restarting subscription $scope")
                    break // restart subscription
                }
            }

            subscription.cancel()
            delay(5.seconds)
        }
    }
}
