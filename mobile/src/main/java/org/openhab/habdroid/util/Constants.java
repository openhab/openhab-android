/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

public class Constants {
    public static final String PREFERENCE_SCREENTIMEROFF    = "default_openhab_screentimeroff";
    public static final String PREFERENCE_USERNAME          = "default_openhab_username";
    public static final String PREFERENCE_PASSWORD          = "default_openhab_password";
    public static final String PREFERENCE_SITEMAP           = "default_openhab_sitemap";
    public static final String PREFERENCE_SSLCERT           = "default_openhab_sslcert";
    public static final String PREFERENCE_SSLHOST           = "default_openhab_sslhost";
    public static final String PREFERENCE_ALTURL            = "default_openhab_alturl";
    public static final String PREFERENCE_URL               = "default_openhab_url";
    public static final String PREFERENCE_THEME             = "default_openhab_theme";
    public static final String PREFERENCE_ANIMATION         = "default_openhab_animation";
    public static final String PREFERENCE_DEMOMODE          = "default_openhab_demomode";
    public static final String PREFERENCE_FULLSCREEN        = "default_openhab_fullscreen";
    public static final String PREFERENCE_TONE              = "default_openhab_alertringtone";
    public static final String PREFERENCE_CLEAR_CACHE       = "default_openhab_cleacache";
    public static final String PREFERENCE_SSLCLIENTCERT     = "default_openhab_sslclientcert";
    public static final String PREFERENCE_SSLCLIENTCERT_HOWTO = "default_openhab_sslclientcert_howto";
    public static final String PREFERENCE_DEBUG_MESSAGES      = "default_openhab_debug_messages";
    public static final String DEFAULT_GCM_SENDER_ID          = "737820980945";

    public interface MESSAGES {
        public static final int DIALOG = 1;
        public static final int SNACKBAR = 2;
        public static final int TOAST = 3;
        public interface LOGLEVEL {
            public static final int DEBUG = 0;
            public static final int REMOTE = 1;
            public static final int LOCAL = 2;
            public static final int NO_DEBUG = 4;
            public static final int ALWAYS = 5;
        }
    }
}