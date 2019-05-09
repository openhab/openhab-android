package org.openhab.habdroid.model

import android.os.Parcelable
import android.util.Log

import kotlinx.android.parcel.Parcelize
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.AsyncHttpClient
import org.openhab.habdroid.util.Util
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

    fun hasHabpanelInstalled(): Boolean {
        return flags and SERVER_FLAG_HABPANEL_INSTALLED != 0
    }

    companion object {
        private val TAG = ServerProperties::class.java.simpleName

        val SERVER_FLAG_JSON_REST_API = 1 shl 0
        val SERVER_FLAG_SSE_SUPPORT = 1 shl 1
        val SERVER_FLAG_ICON_FORMAT_SUPPORT = 1 shl 2
        val SERVER_FLAG_CHART_SCALING_SUPPORT = 1 shl 3
        val SERVER_FLAG_HABPANEL_INSTALLED = 1 shl 4

        interface UpdateSuccessCallback {
            fun handleServerPropertyUpdate(props: ServerProperties)
        }

        interface UpdateFailureCallback {
            fun handleUpdateFailure(request: Request, statusCode: Int, error: Throwable?)
        }

        class UpdateHandle {
            internal var call: Call? = null
            internal var flags: Int = 0
            internal var sitemaps: List<Sitemap> = emptyList()
            fun cancel() {
                if (call != null) {
                    call!!.cancel()
                    call = null
                }
            }
        }

        fun updateSitemaps(props: ServerProperties, connection: Connection,
                           successCb: UpdateSuccessCallback, failureCb: UpdateFailureCallback): UpdateHandle {
            val handle = UpdateHandle()
            handle.flags = props.flags
            fetchSitemaps(connection.asyncHttpClient, handle, successCb, failureCb)
            return handle
        }

        fun fetch(connection: Connection,
                  successCb: UpdateSuccessCallback, failureCb: UpdateFailureCallback): UpdateHandle {
            val handle = UpdateHandle()
            fetchFlags(connection.asyncHttpClient, handle, successCb, failureCb)
            return handle
        }

        private fun fetchFlags(client: AsyncHttpClient, handle: UpdateHandle,
                               successCb: UpdateSuccessCallback, failureCb: UpdateFailureCallback) {
            handle.call = client["rest", object : AsyncHttpClient.StringResponseHandler() {
                override fun onFailure(request: Request, statusCode: Int, error: Throwable?) {
                    failureCb.handleUpdateFailure(request, statusCode, error)
                }

                override fun onSuccess(response: String, headers: Headers) {
                    try {
                        val result = JSONObject(response)
                        // If this succeeded, we're talking to OH2
                        var flags = (SERVER_FLAG_JSON_REST_API
                                or SERVER_FLAG_ICON_FORMAT_SUPPORT
                                or SERVER_FLAG_CHART_SCALING_SUPPORT)
                        try {
                            val versionString = result.getString("version")
                            val versionNumber = Integer.parseInt(versionString)
                            // all versions that return a number here have full SSE support
                            flags = flags or SERVER_FLAG_SSE_SUPPORT
                        } catch (nfe: NumberFormatException) {
                            // ignored: older versions without SSE support didn't return a number
                        }

                        val linksJsonArray = result.optJSONArray("links")
                        if (linksJsonArray == null) {
                            Log.e(TAG, "No 'links' array available")
                        } else {
                            for (i in 0 until linksJsonArray.length()) {
                                val extensionJson = linksJsonArray.getJSONObject(i)
                                if ("habpanel" == extensionJson.getString("type")) {
                                    flags = flags or SERVER_FLAG_HABPANEL_INSTALLED
                                    break
                                }
                            }
                        }

                        handle.flags = flags
                        fetchSitemaps(client, handle, successCb, failureCb)
                    } catch (e: JSONException) {
                        if (response.startsWith("<?xml")) {
                            // We're talking to an OH1 instance
                            handle.flags = 0
                            fetchSitemaps(client, handle, successCb, failureCb)
                        } else {
                            failureCb.handleUpdateFailure(handle.call!!.request(), 200, e)
                        }
                    }

                }
            }]
        }

        private fun fetchSitemaps(client: AsyncHttpClient, handle: UpdateHandle,
                                  successCb: UpdateSuccessCallback, failureCb: UpdateFailureCallback) {
            handle.call = client["rest/sitemaps", object : AsyncHttpClient.StringResponseHandler() {
                override fun onFailure(request: Request, statusCode: Int, error: Throwable?) {
                    failureCb.handleUpdateFailure(request, statusCode, error)
                }

                override fun onSuccess(response: String, headers: Headers) {
                    // OH1 returns XML, later versions return JSON
                    if (handle.flags and SERVER_FLAG_JSON_REST_API != 0) {
                        handle.sitemaps = loadSitemapsFromJson(response)
                    } else {
                        handle.sitemaps = loadSitemapsFromXml(response)
                    }

                    Log.d(TAG, "Server returned sitemaps: " + handle.sitemaps)
                    successCb.handleServerPropertyUpdate(ServerProperties(handle.flags, handle.sitemaps))
                }
            }]
        }

        private fun loadSitemapsFromXml(response: String): List<Sitemap> {
            val dbf = DocumentBuilderFactory.newInstance()
            try {
                val builder = dbf.newDocumentBuilder()
                val sitemapsXml = builder.parse(InputSource(StringReader(response)))
                return Util.parseSitemapList(sitemapsXml)
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
            try {
                val jsonArray = JSONArray(response)
                return Util.parseSitemapList(jsonArray)
            } catch (e: JSONException) {
                Log.e(TAG, "Failed parsing sitemap JSON", e)
                return emptyList()
            }
        }
    }
}
