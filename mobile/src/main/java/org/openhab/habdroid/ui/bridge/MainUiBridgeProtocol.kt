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

import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.mapString
import org.openhab.habdroid.util.optStringOrNull

/**
 * Messages of the Main UI bridge, protocol v1. The schema is docs/mainui-bridge/protocol.d.ts in
 * https://github.com/openhab/openhab-ios/pull/1354.
 */
object MainUiBridgeProtocol {
    const val VERSION = 1

    const val FEATURE_NAVBAR = "navbar"
    const val FEATURE_MENU = "menu"
    const val FEATURE_ROUTE_RESTORE = "routeRestore"

    const val ERROR_NOT_READY = "not_ready"

    fun buildRequest(type: String, id: String, payload: JSONObject = JSONObject()): String = JSONObject()
        .put("v", VERSION)
        .put("type", type)
        .put("id", id)
        .put("payload", payload)
        .toString()

    fun buildReply(replyTo: String, result: Any?): String = JSONObject()
        .put("v", VERSION)
        .put("type", "reply")
        .put("replyTo", replyTo)
        .put("payload", JSONObject().put("ok", true).put("result", result ?: JSONObject.NULL))
        .toString()

    /**
     * What Main UI reads before it draws anything, see `HostInfo` in the protocol.
     *
     * @param restore Pages to put back when Main UI starts on its front page.
     */
    fun buildHostInfo(
        appVersion: String,
        features: List<String>,
        theme: String,
        darkMode: Boolean,
        restore: NavState?
    ): JSONObject {
        val info = JSONObject()
            .put("protocol", VERSION)
            .put("platform", "android")
            .put("appVersion", appVersion)
            .put("features", JSONArray(features))
            .put("theme", theme)
            .put("darkMode", if (darkMode) "dark" else "light")
            .put("layout", buildLayout(drawsNavbar = FEATURE_NAVBAR in features))
        if (restore != null && restore.history.isNotEmpty()) {
            info.put("initialHistory", JSONArray(restore.history))
            restore.props?.let { props -> info.put("initialProps", JSONArray(props.map { JSONObject(it) })) }
        }
        return info
    }

    /**
     * See `LayoutInfo` in the protocol. The app's bars sit outside of the web view, so Main UI needs no room for
     * them. A navbar height of 0 tells it that the app shows the page title above the web view.
     */
    private fun buildLayout(drawsNavbar: Boolean): JSONObject {
        val layout = JSONObject().put("insets", JSONObject().put("top", 0).put("bottom", 0))
        if (drawsNavbar) {
            layout.put("navbarHeight", 0)
        }
        return layout
    }

    fun buildCredentials(username: String, password: String): JSONObject = JSONObject()
        .put("username", username)
        .put("password", password)

    /**
     * The origin of [url] in the form `WebViewCompat.addWebMessageListener()` expects.
     */
    fun originOf(url: HttpUrl): String {
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
        return "${url.scheme}://$host$port"
    }

    fun parseIncoming(json: String): Incoming? = try {
        val message = JSONObject(json)
        val payload = message.optJSONObject("payload") ?: JSONObject()
        when (val type = message.getString("type")) {
            "reply" -> Incoming.Reply(
                replyTo = message.getString("replyTo"),
                ok = payload.optBoolean("ok"),
                errorCode = payload.optJSONObject("error")?.optString("code")
            )

            "ui.hello" -> Incoming.Hello(
                impl = PageImpl.fromString(payload.optString("impl")),
                accepted = payload.optJSONArray("accepted")?.mapString { it }.orEmpty()
            )

            "nav.changed" -> Incoming.NavChanged(payload.toNavState())

            "connection.state" -> Incoming.ConnectionState(payload.getBoolean("sseConnected"))

            "navbar.state" -> Incoming.NavbarChanged(payload.toNavbarState())

            "menu.state" -> Incoming.MenuChanged(
                payload.getJSONArray("sections").map { it.toMenuSection() }
            )

            "auth.getCredentials" -> Incoming.CredentialsRequest(message.getString("id"))

            else -> Incoming.Other(type)
        }
    } catch (e: JSONException) {
        null
    }

    fun buildNavState(state: NavState): String {
        val json = JSONObject()
            .put("path", state.path)
            .put("history", JSONArray(state.history))
            .put("modal", state.modal)
        state.props?.let { json.put("props", JSONArray(it.map { p -> JSONObject(p) })) }
        return json.toString()
    }

    fun parseNavState(json: String): NavState? = try {
        JSONObject(json).toNavState()
    } catch (e: JSONException) {
        null
    }

    sealed class Incoming {
        data class Reply(val replyTo: String, val ok: Boolean, val errorCode: String?) : Incoming()

        data class Hello(val impl: PageImpl, val accepted: List<String>) : Incoming()

        data class NavChanged(val state: NavState) : Incoming()

        data class ConnectionState(val sseConnected: Boolean) : Incoming()

        data class NavbarChanged(val state: NavbarState) : Incoming()

        data class MenuChanged(val sections: List<MenuSection>) : Incoming()

        data class CredentialsRequest(val id: String) : Incoming()

        data class Other(val type: String) : Incoming()
    }
}

/**
 * What the page said it is in its `ui.hello`.
 */
enum class PageImpl {
    MAIN_UI,
    SHIM,
    OTHER;

    companion object {
        fun fromString(impl: String) = when (impl) {
            "mainui" -> MAIN_UI
            "shim" -> SHIM
            else -> OTHER
        }
    }
}

@Throws(JSONException::class)
private fun JSONObject.toNavState(): NavState {
    val history = getJSONArray("history").mapString { it }
    val props = optJSONArray("props")
        ?.map { it.toString() }
        ?.takeIf { it.size == history.size }
    return NavState(getString("path"), history, props, optBoolean("modal"))
}

private fun JSONObject.toBridgeIcon(): BridgeIcon? {
    fun opt(key: String) = optStringOrNull(key)?.takeIf { it.isNotEmpty() }
    val icon = BridgeIcon(opt("name"), opt("md"), opt("svg"))
    return icon.takeIf { it.androidName != null || it.svg != null }
}

@Throws(JSONException::class)
private fun JSONObject.toNavbarState() = NavbarState(
    title = optString("title"),
    hidden = optBoolean("hidden"),
    back = optJSONObject("back")?.optString("label"),
    leading = optJSONArray("leading")?.map { it.toNavbarAction() }.orEmpty(),
    trailing = optJSONArray("trailing")?.map { it.toNavbarAction() }.orEmpty()
)

@Throws(JSONException::class)
private fun JSONObject.toNavbarAction() = NavbarAction(
    id = getString("id"),
    label = optString("label"),
    icon = optJSONObject("icon")?.toBridgeIcon(),
    disabled = optBoolean("disabled")
)

@Throws(JSONException::class)
private fun JSONObject.toMenuSection() = MenuSection(
    id = getString("id"),
    title = optStringOrNull("title"),
    items = getJSONArray("items").map { it.toMenuEntry() }
)

@Throws(JSONException::class)
private fun JSONObject.toMenuEntry(): MenuEntry = MenuEntry(
    id = getString("id"),
    label = getString("label"),
    footer = optStringOrNull("footer"),
    icon = optJSONObject("icon")?.toBridgeIcon(),
    path = optStringOrNull("path"),
    active = optBoolean("active"),
    children = optJSONArray("children")?.map { it.toMenuEntry() }.orEmpty(),
    more = optJSONArray("more")?.map { it.toMenuEntry() }.orEmpty()
)
