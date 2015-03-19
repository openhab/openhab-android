package org.openhab.habdroid.util;

/**
 * Created by tamon on 18.03.15.
 */
public class SharedConstants {

    public enum DataMapKey {
        SITEMAP_NAME, SITEMAP_LINK, SITEMAP_XML;
    }

    public enum DataMapUrl {
        SITEMAP_BASE, SITEMAP_DETAILS;

        public String value() {
            return "/" + name();
        }
    }

    public enum MessagePath {
        LOAD_SITEMAP;

        public String value() {
            return "/" + name();
        }
    }
}
