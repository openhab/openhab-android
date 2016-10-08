package org.openhab.habdroid.util;

import org.junit.Test;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
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
    public void parseSitemapList() throws Exception {
        List<OpenHABSitemap> sitemapList = Util.parseSitemapList(createSitemapDocument());
        assertFalse(sitemapList.isEmpty());

        // Should be sorted, null first
        assertEquals(null, sitemapList.get(0).getLabel());
        assertEquals("Garden", sitemapList.get(1).getLabel());
        assertEquals(8, sitemapList.size());
    }

    private Document createSitemapDocument() throws ParserConfigurationException, IOException, SAXException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<sitemaps><sitemap><name>default</name><label>i AM DEfault</label><link>http://myopenhab/rest/sitemaps/default</link><homepage><link>http://myopenhab/rest/sitemaps/default/default</link><leaf>false</leaf></homepage></sitemap>" +
                "<sitemap><name>heating</name><label>Heating</label><link>http://myopenhab/rest/sitemaps/heating</link><homepage><link>http://myopenhab/rest/sitemaps/heating/heating</link><leaf>false</leaf></homepage></sitemap>" +
                "<sitemap><name>lighting</name><label>Lighting</label><link>http://myopenhab/rest/sitemaps/lighting</link><homepage><link>http://myopenhab/rest/sitemaps/lighting/lighting</link><leaf>false</leaf></homepage></sitemap>" +
                "<sitemap><name>heatpump</name><label>Heatpump</label><link>http://myopenhab/rest/sitemaps/heatpump</link><homepage><link>http://myopenhab/rest/sitemaps/heatpump/heatpump</link><leaf>false</leaf></homepage></sitemap>" +
                "<sitemap><name>schedule</name><label>Schedule</label><link>http://myopenhab/rest/sitemaps/schedule</link><homepage><link>http://myopenhab/rest/sitemaps/schedule/schedule</link><leaf>false</leaf></homepage></sitemap>" +
                "<sitemap><name>outside</name><link>http://myopenhab/rest/sitemaps/outside</link><homepage><link>http://myopenhab/rest/sitemaps/outside/outside</link><leaf>false</leaf></homepage></sitemap>" +
                "<sitemap><name>garden</name><label>Garden</label><link>http://myopenhab/rest/sitemaps/garden</link><homepage><link>http://myopenhab/rest/sitemaps/garden/garden</link><leaf>false</leaf></homepage></sitemap>" +
                "<sitemap><name>scenes</name><label>Scenes</label><link>http://myopenhab/rest/sitemaps/scenes</link><homepage><link>http://myopenhab/rest/sitemaps/scenes/scenes</link><leaf>false</leaf></homepage></sitemap></sitemaps>";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }


    @Test
    public void sitemapExists() throws Exception {
        assertTrue(Util.sitemapExists(sitemapList(), "garden"));
        assertFalse(Util.sitemapExists(sitemapList(), "monkies"));
    }

    private List<OpenHABSitemap> sitemapList() throws IOException, SAXException, ParserConfigurationException {
        return Util.parseSitemapList(createSitemapDocument());
    }

    @Test
    public void getSitemapByName() throws Exception {
        assertEquals("i AM DEfault", Util.getSitemapByName(sitemapList(), "default").getLabel());
        assertEquals(null, Util.getSitemapByName(sitemapList(), "outside").getLabel());
    }
}