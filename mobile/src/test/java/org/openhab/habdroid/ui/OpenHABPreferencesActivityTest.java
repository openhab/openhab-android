/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available
 * at https://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.AbstractSettingsFragment.isWeakPassword;

public class OpenHABPreferencesActivityTest {
    @Test
    public void testIsWeakPassword() {
        assertTrue(isWeakPassword(""));
        assertTrue(isWeakPassword("abc"));
        assertTrue(isWeakPassword("abcd1234"));
        assertTrue(isWeakPassword("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertTrue(!isWeakPassword("AbcD1234"));
        assertTrue(!isWeakPassword("4BCd+-efgh"));
        assertTrue(!isWeakPassword("Mb2.r5oHf-0t"));
        assertTrue(!isWeakPassword("abcdefg1+"));
    }
}
