/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available
 * at https://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid;

import org.junit.Test;
import org.openhab.habdroid.util.Util;

import static org.junit.Assert.assertEquals;

public class UtilTest {

    @Test
    public void testGetHostFromUrl() {
        assertEquals("1.2.3.4", Util.getHostFromUrl("http://1.2.3.4/"));
        assertEquals("1.2.3.4", Util.getHostFromUrl("http://1.2.3.4:4345"));
        assertEquals("1.2.3.4", Util.getHostFromUrl("http://1.2.3.4:342/path/"));
        assertEquals("1.2.3.4", Util.getHostFromUrl("http://1.2.3.4/path"));
        assertEquals("1.2.3.4", Util.getHostFromUrl("https://1.2.3.4/"));
        assertEquals("openhab.org", Util.getHostFromUrl("https://openhab.org/"));
        assertEquals("demo.openhab.org", Util.getHostFromUrl("https://demo.openhab.org/"));
    }
}
