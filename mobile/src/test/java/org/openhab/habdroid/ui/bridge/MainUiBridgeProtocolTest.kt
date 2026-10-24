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

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.openhab.habdroid.ui.bridge.MainUiBridgeProtocol.Incoming

class MainUiBridgeProtocolTest {
    @Test
    fun parseHello() {
        val hello = MainUiBridgeProtocol.parseIncoming(
            """{"v":1,"type":"ui.hello","payload":{"protocol":1,"impl":"shim","accepted":["routeRestore"],""" +
                """"features":["navbar","menu","routeRestore","layout"]}}"""
        )
        assertEquals(Incoming.Hello(PageImpl.SHIM, listOf("routeRestore")), hello)
    }

    @Test
    fun parseNavChanged() {
        val message = MainUiBridgeProtocol.parseIncoming(
            """{"v":1,"type":"nav.changed","payload":{"path":"/page/kitchen",""" +
                """"history":["/","/page/kitchen"],"props":[{},{"deep":true}],"modal":false}}"""
        )
        val state = (message as Incoming.NavChanged).state
        assertEquals("/page/kitchen", state.path)
        assertEquals(listOf("/", "/page/kitchen"), state.history)
        assertEquals(true, JSONObject(state.props!![1]).getBoolean("deep"))
        assertFalse(state.modal)
    }

    @Test
    fun navStateWithMismatchedPropsDropsProps() {
        val state = MainUiBridgeProtocol.parseNavState("""{"path":"/","history":["/"],"props":[{},{}]}""")!!
        assertNull(state.props)
    }

    @Test
    fun navStateRoundTrip() {
        val state = NavState("/page/a", listOf("/", "/page/a"), listOf("{}", """{"deep":true}"""), true)
        assertEquals(state, MainUiBridgeProtocol.parseNavState(MainUiBridgeProtocol.buildNavState(state)))
    }

    @Test
    fun parseReply() {
        assertEquals(
            Incoming.Reply("h3", false, "not_ready"),
            MainUiBridgeProtocol.parseIncoming(
                """{"v":1,"type":"reply","replyTo":"h3","payload":{"ok":false,"error":{"code":"not_ready"}}}"""
            )
        )
        assertEquals(
            Incoming.Reply("h4", true, null),
            MainUiBridgeProtocol.parseIncoming("""{"v":1,"type":"reply","replyTo":"h4","payload":{"ok":true}}""")
        )
    }

    @Test
    fun parseUnknownAndInvalid() {
        assertEquals(
            Incoming.Other("future.message"),
            MainUiBridgeProtocol.parseIncoming("""{"v":1,"type":"future.message","payload":{}}""")
        )
        assertNull(MainUiBridgeProtocol.parseIncoming("not json"))
        assertNull(MainUiBridgeProtocol.parseIncoming("""{"v":1,"type":"nav.changed","payload":{}}"""))
    }

    @Test
    fun credentialsReplyHasExplicitNull() {
        val reply = JSONObject(MainUiBridgeProtocol.buildReply("w1", null))
        assertEquals("w1", reply.getString("replyTo"))
        val payload = reply.getJSONObject("payload")
        assertEquals(true, payload.getBoolean("ok"))
        assertEquals(true, payload.has("result") && payload.isNull("result"))
    }

    @Test
    fun hostInfo() {
        val info = MainUiBridgeProtocol.buildHostInfo(
            appVersion = "3.22.4",
            features = listOf(MainUiBridgeProtocol.FEATURE_ROUTE_RESTORE),
            theme = "md",
            darkMode = true,
            restore = NavState("/page/a", listOf("/", "/page/a"), listOf("{}", """{"deep":true}"""), false)
        )
        assertEquals("android", info.getString("platform"))
        assertEquals("dark", info.getString("darkMode"))
        assertEquals("/page/a", info.getJSONArray("initialHistory").getString(1))
        assertEquals(true, info.getJSONArray("initialProps").getJSONObject(1).getBoolean("deep"))
        assertFalse(MainUiBridgeProtocol.buildHostInfo("1", emptyList(), "md", false, null).has("initialHistory"))
    }

    @Test
    fun origin() {
        assertEquals("https://myopenhab.org", MainUiBridgeProtocol.originOf("https://myopenhab.org/".toHttpUrl()))
        assertEquals(
            "http://192.168.1.2:8080",
            MainUiBridgeProtocol.originOf("http://192.168.1.2:8080/page/x".toHttpUrl())
        )
        assertEquals("http://[fe80::1]:8080", MainUiBridgeProtocol.originOf("http://[fe80::1]:8080/".toHttpUrl()))
    }

    @Test
    fun parseNavbar() {
        val message = MainUiBridgeProtocol.parseIncoming(
            """{"v":1,"type":"navbar.state","payload":{"title":"Kitchen","titleInContent":false,"hidden":false,""" +
                """"back":{"label":"Overview"},"leading":[],"trailing":[{"id":"7","label":"",""" +
                """"icon":{"name":"f7:pencil","md":"material:add_location"}},{"id":"8","label":"Save"}]}}"""
        )
        val state = (message as Incoming.NavbarChanged).state
        assertEquals("Kitchen", state.title)
        assertEquals("Overview", state.back)
        assertEquals(BridgeIcon("f7:pencil", "material:add_location", null), state.trailing[0].icon)
        assertEquals("Add location", state.trailing[0].displayLabel)
        assertEquals("Save", state.trailing[1].displayLabel)
        assertNull(state.trailing[1].icon)

        val noBack = MainUiBridgeProtocol.parseIncoming(
            """{"v":1,"type":"navbar.state","payload":{"title":"","hidden":true,"back":null}}"""
        )
        assertNull((noBack as Incoming.NavbarChanged).state.back)
    }

    @Test
    fun parseMenu() {
        val message = MainUiBridgeProtocol.parseIncoming(
            """{"v":1,"type":"menu.state","payload":{"sections":[{"id":"settings","title":"Administration",""" +
                """"items":[{"id":"/settings/","label":"Settings","path":"/settings/","active":true,""" +
                """"children":[{"id":"/settings/things/","label":"Things","path":"/settings/things/"}],""" +
                """"more":[{"id":"/settings/transformations/","label":"Transformations"}]}]},""" +
                """{"id":"account","items":[{"id":"unlock","label":"Unlock Administration"}]}]}}"""
        )
        val sections = (message as Incoming.MenuChanged).sections
        assertEquals(listOf("settings", "account"), sections.map { it.id })
        assertEquals("Administration", sections[0].title)
        assertNull(sections[1].title)
        val settings = sections[0].items[0]
        assertEquals("/settings/", settings.path)
        assertEquals(true, settings.active)
        assertEquals("Things", settings.children[0].label)
        assertEquals("Transformations", settings.more[0].label)
        assertNull(sections[1].items[0].path)
    }

    @Test
    fun hostInfoLayout() {
        val withNavbar = MainUiBridgeProtocol.buildHostInfo(
            "1",
            listOf(MainUiBridgeProtocol.FEATURE_NAVBAR),
            "md",
            false,
            null
        )
        assertEquals(0, withNavbar.getJSONObject("layout").getInt("navbarHeight"))
        val withoutNavbar = MainUiBridgeProtocol.buildHostInfo("1", emptyList(), "md", false, null)
        assertFalse(withoutNavbar.getJSONObject("layout").has("navbarHeight"))
    }

    @Test
    fun parseCommands() {
        assertEquals(MainUiCommand.Navigate("/page/cameras"), MainUiCommand.parse("navigate:/page/cameras"))
        assertEquals(MainUiCommand.Navigate("/page/cameras"), MainUiCommand.parse("navigate:page/cameras"))
        assertEquals(
            MainUiCommand.OpenModal("popup", "widget:garage_door"),
            MainUiCommand.parse("popup:widget:garage_door")
        )
        assertEquals(MainUiCommand.CloseModals, MainUiCommand.parse("close"))
        assertNull(MainUiCommand.parse("popup:"))
        assertNull(MainUiCommand.parse("notification:foo"))
    }
}
