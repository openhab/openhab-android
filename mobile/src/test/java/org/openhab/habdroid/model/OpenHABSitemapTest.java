package org.openhab.habdroid.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class OpenHABSitemapTest {
    OpenHABSitemap sitemap1;
    OpenHABSitemap sitemap2;

    @Before
    public void initSitemaps() throws JSONException {
        String jsonString = "{\"name\":\"demo\",\"label\":\"Main Menu\",\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo\",\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo/demo\",\"leaf\":false,\"timeout\":false,\"widgets\":[]}}";
        JSONObject jsonObject = new JSONObject(jsonString);
        sitemap1 = new OpenHAB2Sitemap(jsonObject);

        jsonString = "{\"name\":\"home\",\"icon\":\"home\",\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home\",\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home/home\",\"leaf\":true,\"timeout\":false,\"widgets\":[]}}";
        jsonObject = new JSONObject(jsonString);
        sitemap2 = new OpenHAB2Sitemap(jsonObject);
    }

    @Test
    public void sitemapInstanceOf() {
        assertTrue(sitemap1 instanceof OpenHAB2Sitemap);
        assertTrue(sitemap2 instanceof OpenHAB2Sitemap);
    }

    @Test
    public void sitemapLabelNonNull() {
        assertEquals("Main Menu", sitemap1.getLabel());
        assertEquals("home", sitemap2.getLabel());
    }

    @Test
    public void testGetLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo", sitemap1.getLink());
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home", sitemap2.getLink());
    }

    @Test
    public void testGetHomepageLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo/demo", sitemap1.getHomepageLink());
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home/home", sitemap2.getHomepageLink());
    }

    @Test
    public void testGetIcon() {
        assertNull(sitemap1.getIcon());
        assertEquals("home", sitemap2.getIcon());
    }

    @Test
    public void testGetIsLeaf() {
        assertFalse(sitemap1.isLeaf());
        assertFalse("Leaf isn't set in OpenHAB2Sitemap", sitemap2.isLeaf());
    }

    @Test
    public void testGetName() {
        assertEquals("demo", sitemap1.getName());
        assertEquals("home", sitemap2.getName());
    }
}
