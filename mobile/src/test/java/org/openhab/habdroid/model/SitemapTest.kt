package org.openhab.habdroid.model

import org.json.JSONException
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

@RunWith(MockitoJUnitRunner::class)
class SitemapTest {
    private lateinit var demoSitemapWithLabel: Sitemap
    private lateinit var homeSitemapWithoutLabel: Sitemap

    @Before
    @Throws(JSONException::class)
    fun initSitemaps() {
        var jsonString = ("{\"name\":\"demo\",\"label\":\"Main Menu\","
                + "\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo\","
                + "\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo/demo\","
                + "\"leaf\":false,\"timeout\":false,\"widgets\":[]}}")
        demoSitemapWithLabel = JSONObject(jsonString).toSitemap()!!

        jsonString = ("{\"name\":\"home\",\"icon\":\"home\","
                + "\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home\","
                + "\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home/home\","
                + "\"leaf\":true,\"timeout\":false,\"widgets\":[]}}")
        homeSitemapWithoutLabel = JSONObject(jsonString).toSitemap()!!
    }

    @Test
    fun sitemapLabelNonNull() {
        assertEquals("Main Menu", demoSitemapWithLabel.label)
        assertEquals("Sitemap without explicit label should return name for getLabel",
                "home", homeSitemapWithoutLabel.label)
    }

    @Test
    fun testGetLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo",
                demoSitemapWithLabel.link)
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home",
                homeSitemapWithoutLabel.link)
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
        assertEquals("home", homeSitemapWithoutLabel.icon)
    }

    @Test
    fun testGetName() {
        assertEquals("demo", demoSitemapWithLabel.name)
        assertEquals("home", homeSitemapWithoutLabel.name)
    }
}
