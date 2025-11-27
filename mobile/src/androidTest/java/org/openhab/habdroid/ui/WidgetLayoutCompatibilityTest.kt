/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewbinding.ViewBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.WidgetlistColortemperatureitemBinding
import org.openhab.habdroid.databinding.WidgetlistColortemperatureitemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistDatetimeinputitemBinding
import org.openhab.habdroid.databinding.WidgetlistDatetimeinputitemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistFrameitemNestedBinding
import org.openhab.habdroid.databinding.WidgetlistFrameitemNestedCompactBinding
import org.openhab.habdroid.databinding.WidgetlistGenericitemBinding
import org.openhab.habdroid.databinding.WidgetlistGenericitemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistInputitemBinding
import org.openhab.habdroid.databinding.WidgetlistInputitemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistPlayeritemBinding
import org.openhab.habdroid.databinding.WidgetlistPlayeritemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistRollershutteritemBinding
import org.openhab.habdroid.databinding.WidgetlistRollershutteritemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistSectionswitchitemBinding
import org.openhab.habdroid.databinding.WidgetlistSectionswitchitemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistSelectionitemBinding
import org.openhab.habdroid.databinding.WidgetlistSelectionitemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistSetpointitemBinding
import org.openhab.habdroid.databinding.WidgetlistSetpointitemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistSlideritemBinding
import org.openhab.habdroid.databinding.WidgetlistSlideritemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistSwitchitemBinding
import org.openhab.habdroid.databinding.WidgetlistSwitchitemCompactBinding
import org.openhab.habdroid.databinding.WidgetlistTextitemBinding
import org.openhab.habdroid.databinding.WidgetlistTextitemCompactBinding

@RunWith(AndroidJUnit4::class)
class WidgetLayoutCompatibilityTest {
    @Test
    fun testCompatibilityBetweenNormalAndCompactLayout() {
        // The widget adapter inflates either compact or normal widget layout and binds them to the normal layout's
        // binding. This test ensures this approach will work by inflating the compact layouts and binding them to the
        // normal layout binding. This list therefore contains pairs of 'function that inflates compact layout' and
        // 'function that binds normal layout' and should have one entry for each widget layout that's different
        // between normal and compact modes.
        val layoutGeneratorsAndBinders: List<Pair<(LayoutInflater) -> ViewBinding, (View) -> ViewBinding>> = listOf(
            Pair(WidgetlistColortemperatureitemCompactBinding::inflate, WidgetlistColortemperatureitemBinding::bind),
            Pair(WidgetlistDatetimeinputitemCompactBinding::inflate, WidgetlistDatetimeinputitemBinding::bind),
            Pair(WidgetlistFrameitemNestedCompactBinding::inflate, WidgetlistFrameitemNestedBinding::bind),
            Pair(WidgetlistGenericitemCompactBinding::inflate, WidgetlistGenericitemBinding::bind),
            Pair(WidgetlistInputitemCompactBinding::inflate, WidgetlistInputitemBinding::bind),
            Pair(WidgetlistPlayeritemCompactBinding::inflate, WidgetlistPlayeritemBinding::bind),
            Pair(WidgetlistRollershutteritemCompactBinding::inflate, WidgetlistRollershutteritemBinding::bind),
            Pair(WidgetlistSectionswitchitemCompactBinding::inflate, WidgetlistSectionswitchitemBinding::bind),
            Pair(WidgetlistSelectionitemCompactBinding::inflate, WidgetlistSelectionitemBinding::bind),
            Pair(WidgetlistSetpointitemCompactBinding::inflate, WidgetlistSetpointitemBinding::bind),
            Pair(WidgetlistSlideritemCompactBinding::inflate, WidgetlistSlideritemBinding::bind),
            Pair(WidgetlistSwitchitemCompactBinding::inflate, WidgetlistSwitchitemBinding::bind),
            Pair(WidgetlistTextitemCompactBinding::inflate, WidgetlistTextitemBinding::bind)
        )

        val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.openHAB_DayNight_orange
        )
        val inflater = LayoutInflater.from(context)

        for ((compactGenerator, normalBinder) in layoutGeneratorsAndBinders) {
            val normalLayoutBinding = compactGenerator(inflater)
            normalBinder(normalLayoutBinding.root)
        }
    }
}
