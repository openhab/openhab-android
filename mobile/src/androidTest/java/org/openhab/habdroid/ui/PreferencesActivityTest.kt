/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

package org.openhab.habdroid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openhab.habdroid.ui.PreferencesActivity.AbstractSettingsFragment.Companion.isWeakPassword
import org.openhab.habdroid.ui.PreferencesActivity.MainSettingsFragment.Companion.beautifyUrl

class PreferencesActivityTest {
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
        assertEquals("abc", beautifyUrl("abc"))
        assertEquals("", beautifyUrl(""))
        assertEquals("myopenhab.org", beautifyUrl("myopenhab.org"))
        assertEquals("myopenHAB", beautifyUrl("https://myopenhab.org"))
        assertEquals("myopenHAB", beautifyUrl("https://home.myopenhab.org"))
        assertEquals("myopenhab.WRONG_TLD", beautifyUrl("https://myopenhab.WRONG_TLD"))
        assertEquals("", beautifyUrl(null))
    }
}
