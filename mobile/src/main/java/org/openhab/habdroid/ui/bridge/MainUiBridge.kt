/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.openhab.habdroid.ui.bridge.MainUiBridgeProtocol.Incoming
import org.openhab.habdroid.util.mapString

/**
 * The app's end of the Main UI bridge: installs it in a web view, decodes what the page posts and tells the page
 * what to do. Older Main UI versions get the shim from the iOS app, which speaks the bridge on their behalf.
 *
 * Delivery follows the protocol: messages wait for the page's `ui.hello`, then are sent again until the page
 * answers them.
 */
@SuppressLint("RequiresFeature")
class MainUiBridge(private val webView: WebView, private val scope: CoroutineScope, private val callback: Callback) {
    interface Callback {
        /**
         * @param accepted What Main UI took over from the app's offer.
         */
        fun onPageReady(impl: PageImpl, accepted: List<String>)

        fun onNavChanged(state: NavState)

        fun onNavbarChanged(state: NavbarState)

        fun onMenuChanged(sections: List<MenuSection>)

        fun onConnectionStateChanged(sseConnected: Boolean)

        /**
         * @return User name and password for a reverse proxy in front of openHAB, or null if there are none.
         */
        fun getProxyCredentials(): Pair<String, String>?
    }

    private class Pending(val type: String, val json: String) {
        val queuedAt = SystemClock.elapsedRealtime()
        var attemptsLeft = MAX_ATTEMPTS
        var job: Job? = null
    }

    private val shimScript = loadShim(webView.context)
    private var startScript: ScriptHandler? = null
    private var listenerInstalled = false
    private var replyProxy: JavaScriptReplyProxy? = null
    private var page: PageImpl? = null
    private val pending = mutableMapOf<String, Pending>()
    private var nextId = 0

    /**
     * True once Main UI, or the shim for it, has said hello on the page now loaded.
     */
    val isMainUiReady get() = page == PageImpl.MAIN_UI || page == PageImpl.SHIM

    /**
     * Listens to messages from the main frame of pages loaded from [origin]. Call before loading a page.
     *
     * @return false if the bridge can't be used for this origin.
     */
    fun install(origin: String): Boolean {
        if (listenerInstalled) {
            WebViewCompat.removeWebMessageListener(webView, OBJECT_NAME)
            listenerInstalled = false
        }
        try {
            WebViewCompat.addWebMessageListener(webView, OBJECT_NAME, setOf(origin)) {
                    _,
                    message,
                    _,
                    isMainFrame,
                    proxy
                ->
                if (isMainFrame) {
                    message.data?.let { receive(it, proxy) }
                } else {
                    Log.w(TAG, "Ignored a message from a frame")
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Can't listen to origin $origin", e)
            return false
        }
        listenerInstalled = true
        return true
    }

    /**
     * Sets what the next page starts with. [info] is the protocol's `HostInfo`.
     */
    fun setStartupInfo(info: JSONObject) {
        startScript?.remove()
        // Runs on every origin, because it also provides OHApp. The bridge part only runs where the listener above
        // put window.OHBridge.
        startScript = WebViewCompat.addDocumentStartJavaScript(webView, buildStartScript(info), setOf("*"))
    }

    /**
     * A new page is loading. Messages wait for its `ui.hello`, including any that were part way through their
     * retries on the page that is going away.
     */
    fun onPageStarted() {
        page = null
        replyProxy = null
        pending.toList().forEach { (id, entry) ->
            entry.attemptsLeft = MAX_ATTEMPTS
            attempt(id, entry)
        }
    }

    /**
     * Drops all messages still waiting. Call when the web view goes away.
     */
    fun destroy() {
        pending.keys.toList().forEach { finish(it) }
        replyProxy = null
        page = null
    }

    /**
     * Runs a router command in Main UI's old `handleCommand` string form.
     *
     * @return false if there is no bridge message for it.
     */
    fun run(command: String): Boolean {
        when (val parsed = MainUiCommand.parse(command)) {
            is MainUiCommand.Navigate -> navigate(parsed.path)

            is MainUiCommand.OpenModal -> send(
                "nav.openModal",
                JSONObject().put("kind", parsed.kind).put("target", parsed.target)
            )

            MainUiCommand.CloseModals -> closeModals()

            MainUiCommand.Back -> back()

            MainUiCommand.Reload -> reload()

            null -> return false
        }
        return true
    }

    fun navigate(path: String) = send("nav.navigate", JSONObject().put("path", path))

    fun back() = send("nav.back")

    fun closeModals() = send("nav.closeModals")

    fun reload() = send("ui.reload")

    /**
     * A button of Main UI's navbar, shown in the app's bar, was selected.
     */
    fun activateNavbarAction(id: String) = send("navbar.activate", JSONObject().put("id", id))

    /**
     * An entry of Main UI's sidebar without a path, shown in the app's menu, was selected.
     */
    fun activateMenuEntry(id: String) = send("menu.activate", JSONObject().put("id", id))

    private fun receive(json: String, proxy: JavaScriptReplyProxy) {
        when (val message = MainUiBridgeProtocol.parseIncoming(json)) {
            is Incoming.Hello -> {
                Log.d(TAG, "Page is ${message.impl}, took ${message.accepted}")
                page = message.impl
                replyProxy = proxy
                callback.onPageReady(message.impl, message.accepted)
                pending.toList().forEach { (id, entry) -> attempt(id, entry) }
            }

            is Incoming.Reply -> handleReply(message)

            is Incoming.NavChanged -> callback.onNavChanged(message.state)

            is Incoming.NavbarChanged -> callback.onNavbarChanged(message.state)

            is Incoming.MenuChanged -> callback.onMenuChanged(message.sections)

            is Incoming.ConnectionState -> callback.onConnectionStateChanged(message.sseConnected)

            // The listener only gets messages from the connection's origin, so the credentials stay with the server
            // they belong to.
            is Incoming.CredentialsRequest -> {
                val credentials = callback.getProxyCredentials()
                    ?.let { (user, password) -> MainUiBridgeProtocol.buildCredentials(user, password) }
                proxy.postMessage(MainUiBridgeProtocol.buildReply(message.id, credentials))
            }

            is Incoming.Other -> Log.d(TAG, "Ignoring ${message.type}")

            null -> Log.e(TAG, "Unreadable message")
        }
    }

    private fun handleReply(reply: Incoming.Reply) {
        val entry = pending[reply.replyTo] ?: return
        when {
            reply.ok -> finish(reply.replyTo)

            reply.errorCode == MainUiBridgeProtocol.ERROR_NOT_READY -> schedule(reply.replyTo, entry, RETRY_DELAY)

            else -> {
                Log.i(TAG, "${entry.type} refused: ${reply.errorCode}")
                finish(reply.replyTo)
            }
        }
    }

    /**
     * Sends a message the page must acknowledge. A newer message of the same type replaces one still waiting.
     */
    private fun send(type: String, payload: JSONObject = JSONObject()) {
        pending.filterValues { it.type == type }.keys.forEach { finish(it) }
        val id = "h${++nextId}"
        val entry = Pending(type, MainUiBridgeProtocol.buildRequest(type, id, payload))
        pending[id] = entry
        attempt(id, entry)
    }

    private fun attempt(id: String, entry: Pending) {
        if (pending[id] !== entry) {
            return
        }
        val waited = (SystemClock.elapsedRealtime() - entry.queuedAt).milliseconds
        if (waited >= MAX_WAIT) {
            Log.w(TAG, "${entry.type} waited too long for a page, dropped")
            finish(id)
            return
        }
        val proxy = replyProxy
        if (proxy == null || !pageTakes(entry.type)) {
            // Waits for the next ui.hello, which calls this again
            schedule(id, entry, MAX_WAIT - waited)
            return
        }
        if (entry.attemptsLeft <= 0) {
            Log.w(TAG, "${entry.type} not acknowledged, giving up")
            finish(id)
            return
        }
        entry.attemptsLeft--
        proxy.postMessage(entry.json)
        schedule(id, entry, ACKNOWLEDGEMENT_TIMEOUT)
    }

    /**
     * Main UI (or the shim) takes everything. A page that isn't Main UI only takes what makes sense on any page;
     * navigation waits for the next page that is.
     */
    private fun pageTakes(type: String) = when (page) {
        PageImpl.MAIN_UI, PageImpl.SHIM -> true
        PageImpl.OTHER -> type == "layout.changed" || type == "ui.reload"
        null -> false
    }

    private fun schedule(id: String, entry: Pending, after: Duration) {
        entry.job?.cancel()
        entry.job = scope.launch {
            delay(after)
            attempt(id, entry)
        }
    }

    private fun finish(id: String) {
        pending.remove(id)?.job?.cancel()
    }

    private fun buildStartScript(info: JSONObject): String {
        // When the app draws Main UI's navbar, the app's bar and drawer already offer to pin a shortcut and to go
        // back to the app, so Main UI shouldn't show buttons for these.
        val drawsNavbar = info.optJSONArray("features")?.mapString { it }?.contains(MainUiBridgeProtocol.FEATURE_NAVBAR)
        val appMethods = if (drawsNavbar == true) OHAPP_METHODS - setOf("exitToApp", "pinToHome") else OHAPP_METHODS
        val appMethodsJs = JSONArray(appMethods).toString()
        // Main UI and the shim extend OHApp, which they can't do to the object addJavascriptInterface() creates.
        // So the app's interface goes in under another name and OHApp is a plain object that calls it.
        return """
            (function () {
                var app = window.$NATIVE_INTERFACE_NAME;
                if (app && !window.OHApp) {
                    var wrapper = {};
                    $appMethodsJs.forEach(function (name) {
                        if (typeof app[name] === 'function') {
                            wrapper[name] = function () { return app[name].apply(app, arguments); };
                        }
                    });
                    window.OHApp = wrapper;
                }
                if (window !== window.top || !window.$OBJECT_NAME) return;
                window.OHBridgeInfo = Object.freeze($info);
                window.OHBridgeShimConfig = { basePath: '' };
                try {
                    window.$OBJECT_NAME.info = window.OHBridgeInfo;
                } catch (e) {}
            })();
            if (window === window.top) {
            $shimScript
            }
        """.trimIndent()
    }

    companion object {
        private val TAG = MainUiBridge::class.java.simpleName

        private const val OBJECT_NAME = "OHBridge"
        const val NATIVE_INTERFACE_NAME = "OHAppNative"
        private val OHAPP_METHODS = listOf("preferTheme", "preferDarkMode", "exitToApp", "goFullscreen", "pinToHome")

        /** No reply within this long means the page wasn't listening yet. */
        private val ACKNOWLEDGEMENT_TIMEOUT = 750.milliseconds
        private val RETRY_DELAY = 300.milliseconds
        private const val MAX_ATTEMPTS = 6

        /**
         * A message still waiting for a page this long after it was sent is dropped, so a command from a
         * notification can't fire long after the user has moved on.
         */
        private val MAX_WAIT = 30.seconds

        private var shim: String? = null

        fun isSupported() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

        private fun loadShim(context: Context): String = shim
            ?: context.assets.open("oh-bridge-shim.js").bufferedReader().use { it.readText() }.also { shim = it }
    }
}
