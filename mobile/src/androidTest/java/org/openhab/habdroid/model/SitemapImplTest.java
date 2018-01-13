package org.openhab.habdroid.model;

import android.os.Parcel;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SitemapImplTest {
    private Sitemap demoSitemapWithLabel;

    @Before
    public void initSitemaps() throws Exception {
        String jsonString = "{\"name\":\"demo\",\"label\":\"Main Menu\",\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo\",\"homepage\":{\"link\":\"http://demo.openhab.org:8080/rest/sitemaps/demo/demo\",\"leaf\":false,\"timeout\":false,\"widgets\":[]}}";
        ObjectMapper mapper = new ObjectMapper();
        demoSitemapWithLabel = mapper.readValue(jsonString, SitemapImpl.class);
    }

    @Test
    public void testWriteToParcel() throws Exception {
        Parcel parcel = Parcel.obtain();
        demoSitemapWithLabel.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);

        Sitemap createdFromParcel = SitemapImpl.CREATOR.createFromParcel(parcel);
        assertEquals(demoSitemapWithLabel, createdFromParcel);
    }
}
