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

package org.openhab.habdroid.util

import java.io.IOException
import java.io.StringReader
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openhab.habdroid.model.sortedWithDefaultName
import org.openhab.habdroid.model.toSitemapList
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXException

class UtilTest {
    private val sitemapOH1Document: Document
        @Throws(ParserConfigurationException::class, IOException::class, SAXException::class)
        get() {
            val xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sitemaps>
                    <sitemap>
                        <name>default</name>
                        <label>i AM DEfault</label>
                        <link>http://myopenhab/rest/sitemaps/default</link>
                        <homepage>
                            <link>http://myopenhab/rest/sitemaps/default/default</link>
                            <leaf>false</leaf>
                        </homepage>
                    </sitemap>
                    <sitemap>
                        <name>heating</name>
                        <label>Heating</label>
                        <link>http://myopenhab/rest/sitemaps/heating</link>
                        <homepage>
                            <link>http://myopenhab/rest/sitemaps/heating/heating</link>
                            <leaf>false</leaf>
                        </homepage>
                    </sitemap>
                    <sitemap>
                        <name>lighting</name>
                        <label>Lighting</label>
                        <link>http://myopenhab/rest/sitemaps/lighting</link>
                        <homepage>
                            <link>http://myopenhab/rest/sitemaps/lighting/lighting</link>
                            <leaf>false</leaf>
                        </homepage>
                    </sitemap>
                    <sitemap>
                        <name>heatpump</name>
                        <label>Heatpump</label>
                        <link>http://myopenhab/rest/sitemaps/heatpump</link>
                        <homepage>
                            <link>http://myopenhab/rest/sitemaps/heatpump/heatpump</link>
                            <leaf>false</leaf>
                        </homepage>
                    </sitemap>
                    <sitemap>
                        <name>schedule</name>
                        <label>Schedule</label>
                        <link>http://myopenhab/rest/sitemaps/schedule</link>
                        <homepage>
                            <link>http://myopenhab/rest/sitemaps/schedule/schedule</link>
                            <leaf>false</leaf>
                        </homepage>
                    </sitemap>
                    <sitemap>
                        <name>outside</name>
                        <link>http://myopenhab/rest/sitemaps/outside</link>
                        <homepage>
                            <link>http://myopenhab/rest/sitemaps/outside/outside</link>
                            <leaf>false</leaf>
                        </homepage>
                    </sitemap>
                    <sitemap>
                        <name>garden</name>
                        <label>Garden</label>
                        <link>http://myopenhab/rest/sitemaps/garden</link>
                        <homepage>
                            <link>http://myopenhab/rest/sitemaps/garden/garden</link>
                            <leaf>false</leaf>
                        </homepage>
                    </sitemap>
                    <sitemap>
                        <name>scenes</name>
                        <label>Scenes</label>
                        <link>http://myopenhab/rest/sitemaps/scenes</link>
                        <homepage>
                            <link>http://myopenhab/rest/sitemaps/scenes/scenes</link>
                            <leaf>false</leaf>
                        </homepage>
                    </sitemap>
                </sitemaps>
                """.trimIndent()

            val dbf = DocumentBuilderFactory.newInstance()
            val builder = dbf.newDocumentBuilder()
            return builder.parse(InputSource(StringReader(xml)))
        }

    @Test
    fun normalizeUrl() {
        assertEquals("http://localhost/", "http://localhost/".toNormalizedUrl())
        assertEquals("http://localhost/", "http://localhost".toNormalizedUrl())
        assertEquals("http://127.0.0.1/", "http://127.0.0.1/".toNormalizedUrl())
        assertEquals("http://127.0.0.1/", "http://127.0.0.1".toNormalizedUrl())

        assertEquals("https://127.0.0.1/", "https://127.0.0.1/".toNormalizedUrl())
        assertEquals("https://127.0.0.1/", "https://127.0.0.1".toNormalizedUrl())

        assertEquals("https://127.0.0.1/abc/", "https://127.0.0.1/abc/".toNormalizedUrl())
        assertEquals("https://127.0.0.1/abc/", "https://127.0.0.1/abc".toNormalizedUrl())

        assertEquals("https://127.0.0.1:81/abc/", "https://127.0.0.1:81/abc".toNormalizedUrl())

        assertEquals("http://localhost/", " http://localhost/".toNormalizedUrl())
        assertEquals("http://localhost/", "http://localhost/ ".toNormalizedUrl())
        assertEquals("http://localhost/", "http:// localhost/".toNormalizedUrl())
        assertEquals("http://localhost/", "http://local\nhost/".toNormalizedUrl())

        assertEquals("Empty string should return null", null, "".toNormalizedUrl())
        assertEquals("Null should return null", null, null.toNormalizedUrl())
        assertEquals("Url with one slash after the protocol should be fixed",
            "http://localhost/", "http:/localhost/".toNormalizedUrl())
        assertEquals("Url with invalid protocol should return null",
            null, "http//localhost/".toNormalizedUrl())
    }

    @Test
    fun parseOH1SitemapList() {
        val sitemapList = sitemapOH1Document.toSitemapList()
        assertFalse(sitemapList.isEmpty())

        assertEquals("i AM DEfault", sitemapList[0].label)
        assertEquals("Heating", sitemapList[1].label)
        assertEquals("Lighting", sitemapList[2].label)
        assertEquals("Heatpump", sitemapList[3].label)
        assertEquals("Schedule", sitemapList[4].label)
        assertEquals("outside", sitemapList[5].label)
        assertEquals("Garden", sitemapList[6].label)
        assertEquals("Scenes", sitemapList[7].label)
        assertEquals(8, sitemapList.size)
    }

    @Test
    fun parseOH2SitemapListWithId1() {
        val sitemapList = createJsonArray(1).toSitemapList()
        assertFalse(sitemapList.isEmpty())

        assertEquals("Main Menu", sitemapList[0].label)
        assertEquals(1, sitemapList.size)
    }

    @Test
    fun parseOH2SitemapListWithId2() {
        val sitemapList = createJsonArray(2).toSitemapList()
        assertFalse(sitemapList.isEmpty())

        assertEquals("Main Menu", sitemapList[0].label)
        assertEquals("HOME", sitemapList[1].label)
        assertEquals("test", sitemapList[2].label)
        assertEquals(3, sitemapList.size)
    }

    @Test
    fun parseOH2SitemapListWithId3() {
        val sitemapList = createJsonArray(3).toSitemapList()
        assertFalse(sitemapList.isEmpty())

        assertEquals("Home", sitemapList[0].label)
        assertEquals(1, sitemapList.size)
    }

    @Test
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun testSortSitemapList() {
        val sitemapList = sitemapOH1Document.toSitemapList()

        val sorted1 = sitemapList.sortedWithDefaultName("")
        // Should be sorted
        assertEquals("Garden", sorted1[0].label)
        assertEquals("Heating", sorted1[1].label)
        assertEquals("Heatpump", sorted1[2].label)
        assertEquals("i AM DEfault", sorted1[3].label)
        assertEquals("Lighting", sorted1[4].label)
        assertEquals("outside", sorted1[5].label)
        assertEquals("Scenes", sorted1[6].label)
        assertEquals("Schedule", sorted1[7].label)

        val sorted2 = sitemapList.sortedWithDefaultName("schedule")
        // Should be sorted, but "Schedule" should be the first one
        assertEquals("Schedule", sorted2[0].label)
        assertEquals("Garden", sorted2[1].label)
        assertEquals("Heating", sorted2[2].label)
        assertEquals("Heatpump", sorted2[3].label)
        assertEquals("i AM DEfault", sorted2[4].label)
        assertEquals("Lighting", sorted2[5].label)
        assertEquals("outside", sorted2[6].label)
        assertEquals("Scenes", sorted2[7].label)
    }

    @Test
    fun sitemapExists() {
        assertTrue("Sitemap \"demo\" is a \"normal\" one and exists",
                createJsonArray(1).toSitemapList().any { sitemap -> sitemap.name == "demo" })
        assertFalse("Sitemap \"_default\" exists on the server, but isn't the only one => don't display it in the app.",
                createJsonArray(1).toSitemapList().any { sitemap -> sitemap.name == "_default" })
        assertFalse("Sitemap \"_default\" exists on the server, but isn't the only one => don't display it in the app.",
                createJsonArray(2).toSitemapList().any { sitemap -> sitemap.name == "_default" })
        assertTrue("Sitemap \"_default\" exists on the server and is the only one => display it in the app.",
                createJsonArray(3).toSitemapList().any { sitemap -> sitemap.name == "_default" })
    }

    /**
     * @param id
     * 1: Two sitemaps, one "normal", one "_default"
     * 2: Three sitemaps, two "normal", one "_default"
     * 3: One "_default"
     * @return Sitemaps as jsonArray
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun createJsonArray(id: Int): JSONArray {
        val result = JSONArray()
        when (id) {
            1 -> {
                result.put(createTestJsonObject("demo", "Main Menu"))
                result.put(createTestJsonObject("_default", "Home"))
            }
            2 -> {
                result.put(createTestJsonObject("demo", "Main Menu"))
                result.put(createTestJsonObject("home", "HOME"))
                result.put(createTestJsonObject("test", null))
                result.put(createTestJsonObject("_default", "Home"))
            }
            3 -> result.put(createTestJsonObject("_default", "Home"))
            else -> throw IllegalArgumentException("Wrong id")
        }
        return result
    }

    @Throws(JSONException::class)
    private fun createTestJsonObject(name: String, label: String?): JSONObject {
        val result = JSONObject()
        result.put("name", name)
        if (label != null) {
            result.put("label", label)
        }
        result.put("link", "http://demo.openhab.org:8080/rest/sitemaps/$name")

        val homepage = JSONObject()
        homepage.put("link", "http://demo.openhab.org:8080/rest/sitemaps/$name/$name")
        homepage.put("leaf", false)
        homepage.put("timeout", false)
        homepage.put("widgets", JSONArray())

        result.put("homepage", homepage)

        return result
    }

    @Test
    fun testExceptionHasCause() {
        val cause = CertPathValidatorException()
        val e = SSLException(cause)

        assertTrue(
            "The exception is caused by CertPathValidatorException, so hasCause() should return true",
            e.hasCause(CertPathValidatorException::class.java))
        assertFalse(
            "The exception is not caused by ArrayIndexOutOfBoundsException, so hasCause() should return false",
            e.hasCause(ArrayIndexOutOfBoundsException::class.java))
    }

    @Test
    fun testObfuscateString() {
        assertEquals("abc***", "abcdef".obfuscate())
        assertEquals("abc", "abc".obfuscate())
        assertEquals(
            "The function should not throw an exception, when string length is shorter than clearTextCharCount",
            "a", "a".obfuscate(10))
        assertEquals("a**", "abc".obfuscate(1))
        assertEquals("***", "abc".obfuscate(0))
    }

    @Test
    fun testBeautifyFloat() {
        assertEquals("0", 0F.beautify())
        assertEquals("0", 0.0F.beautify())
        assertEquals("42", 42F.beautify())
        assertEquals("3.14159", 3.14159F.beautify())
    }
}
