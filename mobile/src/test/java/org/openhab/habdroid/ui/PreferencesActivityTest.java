/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available
 * at https://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openhab.habdroid.ui.PreferencesActivity.AbstractSettingsFragment.isWeakPassword;
import static org.openhab.habdroid.ui.PreferencesActivity.MainSettingsFragment.beautifyUrl;

public class PreferencesActivityTest {
    @Test
    public void testIsWeakPassword() {
        assertTrue(Companion.isWeakPassword(""));
        assertTrue(Companion.isWeakPassword("abc"));
        assertTrue(Companion.isWeakPassword("abcd1234"));
        assertTrue(Companion.isWeakPassword("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertTrue(!Companion.isWeakPassword("AbcD1234"));
        assertTrue(!Companion.isWeakPassword("4BCd+-efgh"));
        assertTrue(!Companion.isWeakPassword("Mb2.r5oHf-0t"));
        assertTrue(!Companion.isWeakPassword("abcdefg1+"));
    }

    @Test
    public void testBeautifyUrl() {
        assertEquals("abc", Companion.beautifyUrl("abc"));
        assertEquals("", Companion.beautifyUrl(""));
        assertEquals("myopenHAB", Companion.beautifyUrl("myopenhab.org"));
        assertEquals("myopenHAB", Companion.beautifyUrl("home.myopenhab.org"));
        assertEquals("myopenhab.WRONG_TLD", Companion.beautifyUrl("myopenhab.WRONG_TLD"));
        assertEquals(null, Companion.beautifyUrl(null));
    }
}
