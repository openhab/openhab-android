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
    private OpenHABSitemap mDemoSitemapWithLabel;
    private OpenHABSitemap mHomeSitemapWithoutLabel;

    @Before
    public void initSitemaps() throws JSONException {
        String jsonString = "{\"name\":\"demo\",\"label\":\"Main Menu\",\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo\",\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo/demo\",\"leaf\":false,\"timeout\":false,\"widgets\":[]}}";
        JSONObject jsonObject = new JSONObject(jsonString);
        mDemoSitemapWithLabel = OpenHABSitemap.fromJson(jsonObject);

        jsonString = "{\"name\":\"home\",\"icon\":\"home\",\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home\",\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/home/home\",\"leaf\":true,\"timeout\":false,\"widgets\":[]}}";
        jsonObject = new JSONObject(jsonString);
        mHomeSitemapWithoutLabel = OpenHABSitemap.fromJson(jsonObject);
    }

    @Test
    public void sitemapLabelNonNull() {
        assertEquals("Main Menu", mDemoSitemapWithLabel.label());
        assertEquals("Sitemap without explicit label should return name for getLabel","home", mHomeSitemapWithoutLabel.label());
    }

    @Test
    public void testGetLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo", mDemoSitemapWithLabel.link());
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home", mHomeSitemapWithoutLabel.link());
    }

    @Test
    public void testGetHomepageLink() {
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/demo/demo", mDemoSitemapWithLabel.homepageLink());
        assertEquals("http://demo.openhab.org:8080/rest/sitemaps/home/home", mHomeSitemapWithoutLabel.homepageLink());
    }

    @Test
    public void testGetIcon() {
        assertNull(mDemoSitemapWithLabel.icon());
        assertEquals("home", mHomeSitemapWithoutLabel.icon());
    }

    @Test
    public void testGetName() {
        assertEquals("demo", mDemoSitemapWithLabel.name());
        assertEquals("home", mHomeSitemapWithoutLabel.name());
    }
}
