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

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Host side of the Main UI bridge (protocol v1, see docs/mainui-bridge in openhab-ios).
 *
 * The app installs `window.OHBridge` and the shim before the page's own scripts run, then
 * talks to the page with JSON strings. Messages to the page wait for its `ui.hello` and are
 * retried while the page answers `not_ready`.
 */
class MainUiBridge(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onBridgeHello(hello: BridgeHello)

        fun onBridgeNavChanged(state: BridgeNavState)

        fun onBridgeNavbarChanged(state: BridgeNavbarState)

        fun onBridgeMenuChanged(sections: List<BridgeMenuSection>)

        fun onBridgeConnectionChanged(sseConnected: Boolean)

        /** Basic auth credentials of a proxy in front of openHAB, null if there are none */
        fun bridgeCredentials(): Pair<String, String>?
    }

    private class Pending(val type: String, val json: String, val queuedAt: Long) {
        var attemptsLeft = MAX_ATTEMPTS
        var timer: Runnable? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pending = linkedMapOf<String, Pending>()
    private var nextId = 0
    private var replyProxy: JavaScriptReplyProxy? = null
    private var scriptHandler: ScriptHandler? = null
    private var listenerInstalled = false
    var hello: BridgeHello? = null
        private set

    /**
     * Must be called before the WebView loads the page. [initialHistory] and [initialProps] are
     * the pages to put back (route restore), oldest first.
     */
    fun install(webView: WebView, url: HttpUrl, initialHistory: List<String>?, initialProps: List<String>?) {
        val origin = buildOrigin(url)
        val origins = setOf(origin)
        if (!listenerInstalled) {
            val listener = WebViewCompat.WebMessageListener { _, message, source, mainFrame, proxy ->
                receive(message, source, mainFrame, origin, proxy)
            }
            WebViewCompat.addWebMessageListener(webView, JS_OBJECT_NAME, origins, listener)
            listenerInstalled = true
        }
        scriptHandler?.remove()
        scriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            buildBootstrapScript(url, initialHistory, initialProps) + loadShim(),
            origins
        )
        pageWillLoad()
    }

    /** A new page is loading: messages wait for its ui.hello again. */
    fun pageWillLoad() {
        hello = null
        pending.values.forEach { entry ->
            entry.timer?.let { handler.removeCallbacks(it) }
            entry.attemptsLeft = MAX_ATTEMPTS
        }
    }

    fun destroy() {
        pending.values.forEach { entry -> entry.timer?.let { handler.removeCallbacks(it) } }
        pending.clear()
        replyProxy = null
        scriptHandler?.remove()
        scriptHandler = null
    }

    fun navigate(path: String) = send("nav.navigate", JSONObject().put("path", path))

    fun back() = send("nav.back", JSONObject())

    fun reload() = send("ui.reload", JSONObject())

    fun activateNavbarAction(id: String) = send("navbar.activate", JSONObject().put("id", id))

    fun activateMenuItem(id: String) = send("menu.activate", JSONObject().put("id", id))

    fun closeModals() = send("nav.closeModals", JSONObject())

    private fun receive(
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        expectedOrigin: String,
        proxy: JavaScriptReplyProxy
    ) {
        // Anything else - a site the page went to, an iframe - could otherwise drive the app
        if (!isMainFrame || sourceOrigin.toString().trimEnd('/') != expectedOrigin) {
            Log.w(TAG, "Ignored a message from $sourceOrigin (main frame: $isMainFrame)")
            return
        }
        replyProxy = proxy
        val data = message.data ?: return
        val json = try {
            JSONObject(data)
        } catch (e: JSONException) {
            Log.w(TAG, "Dropped unparsable message: $data")
            return
        }
        val payload = json.optJSONObject("payload") ?: JSONObject()
        when (val type = json.optString("type")) {
            "reply" -> handleReply(json.optString("replyTo"), payload)

            "ui.hello" -> {
                val hello = BridgeHello.fromJson(payload)
                Log.d(TAG, "Page said hello: $hello")
                this.hello = hello
                listener.onBridgeHello(hello)
                pending.entries.toList().forEach { (id, entry) -> attempt(id, entry) }
            }

            "connection.state" -> listener.onBridgeConnectionChanged(payload.optBoolean("sseConnected"))

            "nav.changed" -> listener.onBridgeNavChanged(BridgeNavState.fromJson(payload))

            "navbar.state" -> listener.onBridgeNavbarChanged(BridgeNavbarState.fromJson(payload))

            "menu.state" -> {
                Log.d(TAG, "menu.state: ${data.take(MAX_LOGGED_CHARS)}")
                listener.onBridgeMenuChanged(BridgeMenuSection.listFromJson(payload))
            }

            "auth.getCredentials" -> {
                val id = json.optString("id")
                val credentials = listener.bridgeCredentials()
                val result = if (credentials == null) {
                    JSONObject.NULL
                } else {
                    JSONObject().put("username", credentials.first).put("password", credentials.second)
                }
                val reply = JSONObject().put("ok", true).put("result", result)
                deliver(JSONObject().put("type", "reply").put("replyTo", id).put("payload", reply))
            }

            else -> Log.d(TAG, "Unknown message type $type")
        }
    }

    private fun handleReply(replyTo: String, payload: JSONObject) {
        val entry = pending[replyTo] ?: return
        if (payload.optBoolean("ok")) {
            finish(replyTo)
            return
        }
        val code = payload.optJSONObject("error")?.optString("code")
        if (code == "not_ready") {
            schedule(replyTo, entry, RETRY_DELAY_MS)
        } else {
            Log.w(TAG, "${entry.type} failed: $code")
            finish(replyTo)
        }
    }

    private fun send(type: String, payload: JSONObject) {
        val id = "h${++nextId}"
        // A newer message of the same type replaces an older one still waiting: the user's last tap wins
        pending.entries.filter { (_, entry) -> entry.type == type }.map { it.key }.forEach { finish(it) }
        val json = JSONObject()
            .put("v", 1)
            .put("type", type)
            .put("id", id)
            .put("payload", payload)
            .toString()
        val entry = Pending(type, json, System.currentTimeMillis())
        pending[id] = entry
        attempt(id, entry)
    }

    private fun pageTakes(type: String): Boolean = when (hello?.impl) {
        "mainui", "shim" -> true
        "other" -> type == "layout.changed" || type == "ui.reload"
        else -> false
    }

    private fun attempt(id: String, entry: Pending) {
        if (pending[id] !== entry) {
            return
        }
        if (System.currentTimeMillis() - entry.queuedAt > MAX_WAIT_MS) {
            Log.w(TAG, "${entry.type} waited too long for a page, dropped")
            finish(id)
            return
        }
        // Waits for the next ui.hello, which calls this again
        if (!pageTakes(entry.type)) {
            return
        }
        if (entry.attemptsLeft <= 0) {
            Log.w(TAG, "${entry.type} not acknowledged, giving up")
            finish(id)
            return
        }
        entry.attemptsLeft--
        val proxy = replyProxy
        if (proxy == null) {
            schedule(id, entry, RETRY_DELAY_MS)
            return
        }
        proxy.postMessage(entry.json)
        // Delivered but unanswered: the page took it before it could reply. Try again.
        schedule(id, entry, ACKNOWLEDGEMENT_TIMEOUT_MS)
    }

    private fun deliver(json: JSONObject) {
        replyProxy?.postMessage(json.put("v", 1).toString())
    }

    private fun schedule(id: String, entry: Pending, delayMs: Long) {
        entry.timer?.let { handler.removeCallbacks(it) }
        val timer = Runnable { attempt(id, entry) }
        entry.timer = timer
        handler.postDelayed(timer, delayMs)
    }

    private fun finish(id: String) {
        pending.remove(id)?.timer?.let { handler.removeCallbacks(it) }
    }

    private fun buildBootstrapScript(url: HttpUrl, initialHistory: List<String>?, initialProps: List<String>?): String {
        val info = JSONObject()
            .put("protocol", 1)
            .put("platform", "android")
            .put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
            .put("features", JSONArray(FEATURES))
            .put("theme", "md")
            .put("layout", JSONObject().put("insets", JSONObject().put("top", 0).put("bottom", 0)))
        initialHistory?.takeIf { it.isNotEmpty() }?.let { info.put("initialHistory", JSONArray(it)) }
        initialProps?.takeIf { it.isNotEmpty() }?.let { info.put("initialProps", JSONArray(it)) }
        // Main UI's addresses hang off the base path, no trailing slash
        val basePath = url.encodedPath.trimEnd('/')
        return """
            (function () {
                var info = Object.freeze($info);
                window.OHBridgeInfo = info;
                window.OHBridgeShimConfig = { basePath: ${JSONObject.quote(basePath)} };
                if (window.$JS_OBJECT_NAME) {
                    try { window.$JS_OBJECT_NAME.info = info; } catch (e) {}
                }
            })();
        """.trimIndent() + "\n"
    }

    private fun loadShim(): String = context.assets.open(SHIM_ASSET).bufferedReader().use { it.readText() }

    private fun buildOrigin(url: HttpUrl): String {
        val defaultPort = HttpUrl.defaultPort(url.scheme)
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        return "${url.scheme}://${url.host}$port"
    }

    companion object {
        private val TAG = MainUiBridge::class.java.simpleName

        private const val JS_OBJECT_NAME = "OHBridge"
        private const val SHIM_ASSET = "oh-bridge-shim.js"
        private val FEATURES = listOf("navbar", "menu", "routeRestore")

        private const val MAX_ATTEMPTS = 6
        private const val RETRY_DELAY_MS = 300L
        private const val ACKNOWLEDGEMENT_TIMEOUT_MS = 750L
        private const val MAX_WAIT_MS = 30_000L
        private const val MAX_LOGGED_CHARS = 4000

        fun isSupported() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }
}
