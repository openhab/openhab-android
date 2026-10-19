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

            "nav.changed" -> Incoming.NavChanged(NavState.fromJson(payload))

            "connection.state" -> Incoming.ConnectionState(payload.getBoolean("sseConnected"))

            "navbar.state" -> Incoming.NavbarChanged(NavbarState.fromJson(payload))

            "menu.state" -> Incoming.MenuChanged(
                payload.getJSONArray("sections").map { MenuSection.fromJson(it) }
            )

            "auth.getCredentials" -> Incoming.CredentialsRequest(message.getString("id"))

            else -> Incoming.Other(type)
        }
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

/**
 * The page on screen and the pages behind it, as sent in `nav.changed`.
 *
 * @param props What each page in [history] was opened with, as JSON objects. The app never looks inside, it
 * only hands them back in `initialProps`.
 */
data class NavState(val path: String, val history: List<String>, val props: List<String>?, val modal: Boolean) {
    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("path", path)
            .put("history", JSONArray(history))
            .put("modal", modal)
        props?.let { json.put("props", JSONArray(it.map { p -> JSONObject(p) })) }
        return json
    }

    companion object {
        @Throws(JSONException::class)
        fun fromJson(json: JSONObject): NavState {
            val history = json.getJSONArray("history").mapString { it }
            val props = json.optJSONArray("props")
                ?.map { it.toString() }
                ?.takeIf { it.size == history.size }
            return NavState(json.getString("path"), history, props, json.optBoolean("modal"))
        }
    }
}

/**
 * A router command in Main UI's old `handleCommand` string form, as used by the UI command item and by `ui:`
 * notification actions.
 */
sealed class MainUiCommand {
    data class Navigate(val path: String) : MainUiCommand()

    data class OpenModal(val kind: String, val target: String) : MainUiCommand()

    data object CloseModals : MainUiCommand()

    data object Back : MainUiCommand()

    data object Reload : MainUiCommand()

    companion object {
        fun parse(command: String): MainUiCommand? {
            val prefix = command.substringBefore(":")
            val content = command.substringAfter(":", "")
            return when (prefix) {
                "navigate" -> content.takeIf { it.isNotEmpty() }?.let { Navigate("/" + it.removePrefix("/")) }
                "popup", "popover", "sheet" -> content.takeIf { it.isNotEmpty() }?.let { OpenModal(prefix, it) }
                "close" -> CloseModals
                "back" -> Back
                "reload" -> Reload
                else -> null
            }
        }
    }
}

/**
 * An icon in Main UI's format, e.g. `f7:gear_alt_fill`, `material:settings`, `oh:classic:light` or
 * `iconify:mdi:lightbulb`.
 *
 * @param name The icon Main UI shows on the iOS and Aurora themes.
 * @param md The icon Main UI shows on the Material theme, when it differs.
 * @param svg SVG markup, only when there is no name.
 */
data class BridgeIcon(val name: String?, val md: String?, val svg: String?) {
    /**
     * The icon to show on Android, which matches Main UI's Material theme.
     */
    val androidName get() = md ?: name

    companion object {
        fun fromJson(json: JSONObject?): BridgeIcon? {
            json ?: return null
            fun opt(key: String) = json.optStringOrNull(key)?.takeIf { it.isNotEmpty() }
            val icon = BridgeIcon(opt("name"), opt("md"), opt("svg"))
            return icon.takeIf { it.androidName != null || it.svg != null }
        }
    }
}

/**
 * Main UI's top bar, as sent in `navbar.state`.
 *
 * @param back Null when there is nothing to go back to, otherwise the label of the back button, which may be empty.
 */
data class NavbarState(
    val title: String,
    val hidden: Boolean,
    val back: String?,
    val leading: List<NavbarAction>,
    val trailing: List<NavbarAction>
) {
    companion object {
        @Throws(JSONException::class)
        fun fromJson(json: JSONObject) = NavbarState(
            title = json.optString("title"),
            hidden = json.optBoolean("hidden"),
            back = json.optJSONObject("back")?.optString("label"),
            leading = json.optJSONArray("leading")?.map { NavbarAction.fromJson(it) }.orEmpty(),
            trailing = json.optJSONArray("trailing")?.map { NavbarAction.fromJson(it) }.orEmpty()
        )
    }
}

data class NavbarAction(val id: String, val label: String, val icon: BridgeIcon?, val disabled: Boolean) {
    /**
     * The label, or for buttons that only show an icon, a label made from the icon's name, e.g. "Add location" for
     * `material:add_location`. The app needs one for the overflow menu and for accessibility.
     */
    val displayLabel: String get() {
        if (label.isNotEmpty()) {
            return label
        }
        val iconName = icon?.androidName?.substringAfterLast(':') ?: return ""
        return iconName.replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }
    }

    companion object {
        @Throws(JSONException::class)
        fun fromJson(json: JSONObject) = NavbarAction(
            id = json.getString("id"),
            label = json.optString("label"),
            icon = BridgeIcon.fromJson(json.optJSONObject("icon")),
            disabled = json.optBoolean("disabled")
        )
    }
}

/**
 * A section of Main UI's sidebar, as sent in `menu.state`.
 */
data class MenuSection(val id: String, val title: String?, val items: List<MenuEntry>) {
    companion object {
        @Throws(JSONException::class)
        fun fromJson(json: JSONObject) = MenuSection(
            id = json.getString("id"),
            title = json.optStringOrNull("title"),
            items = json.getJSONArray("items").map { MenuEntry.fromJson(it) }
        )
    }
}

/**
 * @param path Selecting the entry navigates there. Entries without one do something in Main UI instead, which
 * `menu.activate` triggers.
 * @param children The entries the sidebar shows under this one.
 * @param more The rest, which the sidebar shows under "Show all".
 */
data class MenuEntry(
    val id: String,
    val label: String,
    val footer: String?,
    val icon: BridgeIcon?,
    val path: String?,
    val active: Boolean,
    val children: List<MenuEntry>,
    val more: List<MenuEntry>
) {
    companion object {
        @Throws(JSONException::class)
        fun fromJson(json: JSONObject): MenuEntry = MenuEntry(
            id = json.getString("id"),
            label = json.getString("label"),
            footer = json.optStringOrNull("footer"),
            icon = BridgeIcon.fromJson(json.optJSONObject("icon")),
            path = json.optStringOrNull("path"),
            active = json.optBoolean("active"),
            children = json.optJSONArray("children")?.map { fromJson(it) }.orEmpty(),
            more = json.optJSONArray("more")?.map { fromJson(it) }.orEmpty()
        )
    }
}
