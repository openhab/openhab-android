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

package org.openhab.habdroid

import androidx.core.content.edit
import androidx.test.InstrumentationRegistry

import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.getPrefs

abstract class TestWithIntro : ProgressbarAwareTest() {
    override fun setup() {
        InstrumentationRegistry.getTargetContext().getPrefs().edit {
            putString(Constants.PREFERENCE_SITEMAP_NAME, "")
            putBoolean(Constants.PREFERENCE_DEMOMODE, true)
            putBoolean(Constants.PREFERENCE_FIRST_START, true)
        }

        super.setup()
    }
}
