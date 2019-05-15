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
import org.junit.Assert.assertTrue

class PreferencesActivityTest {
    @Test
    fun testIsWeakPassword() {
        assertTrue(PreferencesActivity.AbstractSettingsFragment.isWeakPassword(""))
        assertTrue(PreferencesActivity.AbstractSettingsFragment.isWeakPassword("abc"))
        assertTrue(PreferencesActivity.AbstractSettingsFragment.isWeakPassword("abcd1234"))
        assertTrue(PreferencesActivity.AbstractSettingsFragment.isWeakPassword("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertTrue(!PreferencesActivity.AbstractSettingsFragment.isWeakPassword("AbcD1234"))
        assertTrue(!PreferencesActivity.AbstractSettingsFragment.isWeakPassword("4BCd+-efgh"))
        assertTrue(!PreferencesActivity.AbstractSettingsFragment.isWeakPassword("Mb2.r5oHf-0t"))
        assertTrue(!PreferencesActivity.AbstractSettingsFragment.isWeakPassword("abcdefg1+"))
    }

    @Test
    fun testBeautifyHostName() {
        assertEquals("abc", PreferencesActivity.MainSettingsFragment.beautifyUrl("abc"))
        assertEquals("", PreferencesActivity.MainSettingsFragment.beautifyUrl(""))
        assertEquals("myopenhab.org", PreferencesActivity.MainSettingsFragment.beautifyUrl("myopenhab.org"))
        assertEquals("myopenHAB", PreferencesActivity.MainSettingsFragment.beautifyUrl("https://myopenhab.org"))
        assertEquals("myopenHAB", PreferencesActivity.MainSettingsFragment.beautifyUrl("https://home.myopenhab.org"))
        assertEquals("https://myopenhab.WRONG_TLD", PreferencesActivity.MainSettingsFragment.beautifyUrl("https://myopenhab.WRONG_TLD"))
        assertEquals("", PreferencesActivity.MainSettingsFragment.beautifyUrl(null))
    }
}
