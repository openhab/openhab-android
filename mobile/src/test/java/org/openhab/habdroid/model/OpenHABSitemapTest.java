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
        demoSitemapWithLabel = OpenHABSitemap.fromJson(jsonObject);

        jsonString = "{\"name\":\"home\",\"icon\":\"home\",\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home\",\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home/home\",\"leaf\":true,\"timeout\":false,\"widgets\":[]}}";
        jsonObject = new JSONObject(jsonString);
        homeSitemapWithoutLabel = OpenHABSitemap.fromJson(jsonObject);
    }

    @Test
    public void sitemapLabelNonNull() {
        assertEquals("Main Menu", demoSitemapWithLabel.label());
        assertEquals("Sitemap without explicit label should return name for getLabel","home", homeSitemapWithoutLabel.label());
    }

    @Test
    public void testGetLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo", demoSitemapWithLabel.link());
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home", homeSitemapWithoutLabel.link());
    }

    @Test
    public void testGetHomepageLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo/demo", demoSitemapWithLabel.homepageLink());
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home/home", homeSitemapWithoutLabel.homepageLink());
    }

    @Test
    public void testGetIcon() {
        assertNull(demoSitemapWithLabel.icon());
        assertEquals("home", homeSitemapWithoutLabel.icon());
    }

    @Test
    public void testGetName() {
        assertEquals("demo", demoSitemapWithLabel.name());
        assertEquals("home", homeSitemapWithoutLabel.name());
    }
}
