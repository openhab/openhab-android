package org.openhab.habdroid.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilTest {
    @Test
    public void overridePendingTransition() throws Exception {

    }

    @Test
    public void normalizeUrl() throws Exception {
        assertEquals("http://localhost/", Util.normalizeUrl("http://localhost/"));
        assertEquals("http://localhost/", Util.normalizeUrl("http://localhost"));
        assertEquals("http://127.0.0.1/", Util.normalizeUrl("http://127.0.0.1/"));
        assertEquals("http://127.0.0.1/", Util.normalizeUrl("http://127.0.0.1"));

        assertEquals("https://127.0.0.1/", Util.normalizeUrl("https://127.0.0.1/"));
        assertEquals("https://127.0.0.1/", Util.normalizeUrl("https://127.0.0.1"));

        assertEquals("https://127.0.0.1/abc/", Util.normalizeUrl("https://127.0.0.1/abc/"));
        assertEquals("https://127.0.0.1/abc/", Util.normalizeUrl("https://127.0.0.1/abc"));

        assertEquals("https://127.0.0.1:81/abc/", Util.normalizeUrl("https://127.0.0.1:81/abc"));
    }

    @Test
    public void initCrittercism() throws Exception {

    }

    @Test
    public void parseSitemapList() throws Exception {

    }

    @Test
    public void parseSitemapList1() throws Exception {

    }

    @Test
    public void sitemapExists() throws Exception {

    }

    @Test
    public void getSitemapByName() throws Exception {

    }

    @Test
    public void setActivityTheme() throws Exception {

    }

}