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

import android.os.Parcelable
import android.util.Log
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.HttpClient
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

@Parcelize
data class ServerProperties(val flags: Int, val sitemaps: List<Sitemap>) : Parcelable {
    fun hasJsonApi(): Boolean {
        return flags and SERVER_FLAG_JSON_REST_API != 0
    }

    fun hasSseSupport(): Boolean {
        return flags and SERVER_FLAG_SSE_SUPPORT != 0
    }

    fun hasHabPanelInstalled(): Boolean {
        return flags and SERVER_FLAG_HABPANEL_INSTALLED != 0
    }

    fun hasInvisibleWidgetSupport(): Boolean {
        return flags and SERVER_FLAG_SITEMAP_HAS_INVISIBLE_WIDGETS != 0
    }

    companion object {
        private val TAG = ServerProperties::class.java.simpleName

        const val SERVER_FLAG_JSON_REST_API = 1 shl 0
        const val SERVER_FLAG_SSE_SUPPORT = 1 shl 1
        const val SERVER_FLAG_ICON_FORMAT_SUPPORT = 1 shl 2
        const val SERVER_FLAG_CHART_SCALING_SUPPORT = 1 shl 3
        const val SERVER_FLAG_HABPANEL_INSTALLED = 1 shl 4
        const val SERVER_FLAG_SITEMAP_HAS_INVISIBLE_WIDGETS = 1 shl 5
        const val SERVER_FLAG_SUPPORTS_ANY_FORMAT_ICON = 1 shl 6

        class UpdateHandle internal constructor(internal val scope: CoroutineScope) {
            internal var job: Job? = null
            internal var flags: Int = 0
            internal var sitemaps: List<Sitemap> = emptyList()
            fun cancel() {
                job?.cancel()
                job = null
            }
        }

        fun updateSitemaps(
            scope: CoroutineScope,
            props: ServerProperties,
            connection: Connection,
            successCb: (ServerProperties) -> Unit,
            failureCb: (Request, Int, Throwable) -> Unit
        ): UpdateHandle {
            val handle = UpdateHandle(scope)
            handle.flags = props.flags
            fetchSitemaps(connection.httpClient, handle, successCb, failureCb)
            return handle
        }

        fun fetch(
            scope: CoroutineScope,
            connection: Connection,
            successCb: (ServerProperties) -> Unit,
            failureCb: (Request, Int, Throwable) -> Unit
        ): UpdateHandle {
            val handle = UpdateHandle(scope)
            fetchFlags(connection.httpClient, handle, successCb, failureCb)
            return handle
        }

        private fun fetchFlags(
            client: HttpClient,
            handle: UpdateHandle,
            successCb: (ServerProperties) -> Unit,
            failureCb: (Request, Int, Throwable) -> Unit
        ) {
            handle.job = handle.scope.launch {
                try {
                    val result = client.get("rest").asText()
                    try {
                        val resultJson = JSONObject(result.response)
                        // If this succeeded, we're talking to OH2
                        var flags = (SERVER_FLAG_JSON_REST_API
                            or SERVER_FLAG_ICON_FORMAT_SUPPORT
                            or SERVER_FLAG_CHART_SCALING_SUPPORT)
                        try {
                            val version = resultJson.getString("version").toInt()
                            // all versions that return a number here have full SSE support
                            flags = flags or SERVER_FLAG_SSE_SUPPORT
                            if (version >= 2) {
                                flags = flags or SERVER_FLAG_SITEMAP_HAS_INVISIBLE_WIDGETS
                            }
                            if (version >= 3) {
                                flags = flags or SERVER_FLAG_SUPPORTS_ANY_FORMAT_ICON
                            }
                        } catch (nfe: NumberFormatException) {
                            // ignored: older versions without SSE support didn't return a number
                        }

                        val linksJsonArray = resultJson.optJSONArray("links")
                        if (linksJsonArray == null) {
                            Log.e(TAG, "No 'links' array available")
                        } else {
                            for (i in 0 until linksJsonArray.length()) {
                                val extensionJson = linksJsonArray.getJSONObject(i)
                                if (extensionJson.getString("type") == "habpanel") {
                                    flags = flags or SERVER_FLAG_HABPANEL_INSTALLED
                                    break
                                }
                            }
                        }

                        handle.flags = flags
                        fetchSitemaps(client, handle, successCb, failureCb)
                    } catch (e: JSONException) {
                        if (result.response.startsWith("<?xml")) {
                            // We're talking to an OH1 instance
                            handle.flags = 0
                            fetchSitemaps(client, handle, successCb, failureCb)
                        } else {
                            failureCb(result.request, 200, e)
                        }
                    }
                } catch (e: HttpClient.HttpException) {
                    failureCb(e.request, e.statusCode, e)
                }
            }
        }

        private fun fetchSitemaps(
            client: HttpClient,
            handle: UpdateHandle,
            successCb: (ServerProperties) -> Unit,
            failureCb: (Request, Int, Throwable) -> Unit
        ) {
            handle.job = handle.scope.launch {
                try {
                    val result = client.get("rest/sitemaps").asText()
                    // OH1 returns XML, later versions return JSON
                    handle.sitemaps = if (handle.flags and SERVER_FLAG_JSON_REST_API != 0)
                        loadSitemapsFromJson(result.response) else loadSitemapsFromXml(result.response)

                    Log.d(TAG, "Server returned sitemaps: ${handle.sitemaps}")
                    successCb(ServerProperties(handle.flags, handle.sitemaps))
                } catch (e: HttpClient.HttpException) {
                    failureCb(e.request, e.statusCode, e)
                }
            }
        }

        private fun loadSitemapsFromXml(response: String): List<Sitemap> {
            val dbf = DocumentBuilderFactory.newInstance()
            try {
                val builder = dbf.newDocumentBuilder()
                val sitemapsXml = builder.parse(InputSource(StringReader(response)))
                return sitemapsXml.toSitemapList()
            } catch (e: ParserConfigurationException) {
                Log.e(TAG, "Failed parsing sitemap XML", e)
            } catch (e: SAXException) {
                Log.e(TAG, "Failed parsing sitemap XML", e)
            } catch (e: IOException) {
                Log.e(TAG, "Failed parsing sitemap XML", e)
            }
            return emptyList()
        }

        private fun loadSitemapsFromJson(response: String): List<Sitemap> {
            return try {
                val jsonArray = JSONArray(response)
                jsonArray.toSitemapList()
            } catch (e: JSONException) {
                Log.e(TAG, "Failed parsing sitemap JSON", e)
                emptyList()
            }
        }
    }
}
