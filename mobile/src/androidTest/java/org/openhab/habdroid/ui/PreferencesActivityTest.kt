/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available
 * at https://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals("https://myopenhab.WRONG_TLD", beautifyUrl("https://myopenhab.WRONG_TLD"))
        assertEquals("", beautifyUrl(null))
    }
}
