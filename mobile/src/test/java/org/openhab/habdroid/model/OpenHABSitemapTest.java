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
    OpenHABSitemap demoSitemapWithLabel;
    OpenHABSitemap homeSitemapWithoutLabel;

    @Before
    public void initSitemaps() throws JSONException {
        String jsonString = "{\"name\":\"demo\",\"label\":\"Main Menu\",\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo\",\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo/demo\",\"leaf\":false,\"timeout\":false,\"widgets\":[]}}";
        JSONObject jsonObject = new JSONObject(jsonString);
        demoSitemapWithLabel = new OpenHAB2Sitemap(jsonObject);

        jsonString = "{\"name\":\"home\",\"icon\":\"home\",\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home\",\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home/home\",\"leaf\":true,\"timeout\":false,\"widgets\":[]}}";
        jsonObject = new JSONObject(jsonString);
        homeSitemapWithoutLabel = new OpenHAB2Sitemap(jsonObject);
    }

    @Test
    public void sitemapLabelNonNull() {
        assertEquals("Main Menu", demoSitemapWithLabel.getLabel());
        assertEquals("Sitemap without explicit label should return name for getLabel","home", homeSitemapWithoutLabel.getLabel());
    }

    @Test
    public void testGetLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo", demoSitemapWithLabel.getLink());
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home", homeSitemapWithoutLabel.getLink());
    }

    @Test
    public void testGetHomepageLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo/demo", demoSitemapWithLabel.getHomepageLink());
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home/home", homeSitemapWithoutLabel.getHomepageLink());
    }

    @Test
    public void testGetIcon() {
        assertNull(demoSitemapWithLabel.getIcon());
        assertEquals("home", homeSitemapWithoutLabel.getIcon());
    }

    @Test
    public void testGetIsLeaf() {
        assertFalse("isLeaf is always false for openHAB 2 Sitemaps",demoSitemapWithLabel.isLeaf());
        assertFalse("isLeaf is always false for openHAB 2 Sitemaps", homeSitemapWithoutLabel.isLeaf());
    }

    @Test
    public void testGetName() {
        assertEquals("demo", demoSitemapWithLabel.getName());
        assertEquals("home", homeSitemapWithoutLabel.getName());
    }
}
