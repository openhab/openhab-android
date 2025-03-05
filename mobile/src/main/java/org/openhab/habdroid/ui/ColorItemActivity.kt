/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

import android.os.Bundle
import android.view.MenuItem
import com.chimbori.colorpicker.ColorPickerView
import com.google.android.material.slider.Slider
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.ColorPickerHelper
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelable

class ColorItemActivity : AbstractBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_color_picker)

        val boundItem = intent.extras?.parcelable<Item>(EXTRA_ITEM)

        supportActionBar?.title = boundItem?.label.orDefaultIfEmpty(getString(R.string.widget_type_color))

        val colorPicker = findViewById<ColorPickerView>(R.id.picker)
        val slider = findViewById<Slider>(R.id.brightness_slider)
        val pickerHelper = ColorPickerHelper(colorPicker, slider)
        pickerHelper.attach(boundItem, this, ConnectionFactory.primaryUsableConnection?.connection)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_ITEM = "item"
    }
}
