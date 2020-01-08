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

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.util.IconFormat

class SitemapTest {
    private lateinit var demoSitemapWithLabel: Sitemap
    private lateinit var homeSitemapWithoutLabel: Sitemap

    @Before
    @Throws(JSONException::class)
    fun initSitemaps() {
        demoSitemapWithLabel = JSONObject("""
            { 'name': 'demo','label': 'Main Menu', 'link': 'http://demo.openhab.org:8080/rest/sitemaps/demo',
              'homepage': { 'link': 'http://demo.openhab.org:8080/rest/sitemaps/demo/demo', 'leaf': false,
              'timeout': false,'widgets': [] }}"
             """.trimIndent()).toSitemap()!!

        homeSitemapWithoutLabel = JSONObject("""
            { 'name': 'home', 'icon': 'home', 'link': 'http://demo.openhab.org:8080/rest/sitemaps/home',
              'homepage': { 'link': 'http://demo.openhab.org:8080/rest/sitemaps/home/home', 'leaf': true,
              'timeout': false, 'widgets': [] }}"
              """.trimIndent()).toSitemap()!!
    }

    @Test
    fun sitemapLabelNonNull() {
        assertEquals("Main Menu", demoSitemapWithLabel.label)
        assertEquals("Sitemap without explicit label should return name for getLabel",
                "home", homeSitemapWithoutLabel.label)
    }

    @Test
    fun testGetHomepageLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo/demo",
                demoSitemapWithLabel.homepageLink)
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home/home",
                homeSitemapWithoutLabel.homepageLink)
    }

    @Test
    fun testGetIcon() {
        assertNull(demoSitemapWithLabel.icon)
        assertEquals("icon/home?format=SVG&anyFormat=true", homeSitemapWithoutLabel.icon?.toUrl(IconFormat.Svg))
    }

    @Test
    fun testGetName() {
        assertEquals("demo", demoSitemapWithLabel.name)
        assertEquals("home", homeSitemapWithoutLabel.name)
    }
}
