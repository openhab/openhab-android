/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openhab.habdroid.ui.PreferencesActivity.AbstractSettingsFragment.Companion.isWeakPassword
import org.openhab.habdroid.ui.PreferencesActivity.ServerEditorFragment.Companion.beautifyUrl

class PreferencesUtilTest {
    @Test
    fun testIsWeakPassword() {
        assertTrue(isWeakPassword(""))
        assertTrue(isWeakPassword("abc"))
        assertTrue(isWeakPassword("abcd1234"))
        assertTrue(isWeakPassword("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertFalse(isWeakPassword("AbcD1234"))
        assertFalse(isWeakPassword("4BCd+-efgh"))
        assertFalse(isWeakPassword("Mb2.r5oHf-0t"))
        assertFalse(isWeakPassword("abcdefg1+"))
    }

    @Test
    fun testBeautifyHostName() {
        assertEquals("For invalid urls it should return the input value", "abc", beautifyUrl("abc"))
        assertEquals("For an empty string it should return an empty string", "", beautifyUrl(""))
        assertEquals("URLs without scheme should treated like one with", "myopenHAB", beautifyUrl("myopenhab.org"))
        assertEquals("For myopenhab.org it should return myopenHAB", "myopenHAB", beautifyUrl("https://myopenhab.org"))
        assertEquals("For home.myopenhab.org it should return myopenHAB", "myopenHAB",
            beautifyUrl("https://home.myopenhab.org"))
        assertEquals("not.myopenhab.org", beautifyUrl("https://not.myopenhab.org"))
        assertEquals("notmyopenhab.org", beautifyUrl("https://notmyopenhab.org"))
        assertEquals("myopenhab.wrong_tld", beautifyUrl("https://myopenhab.WRONG_TLD"))
    }
}
