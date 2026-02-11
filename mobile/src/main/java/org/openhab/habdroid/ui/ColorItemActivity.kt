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
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.databinding.ActivityColorPickerBinding
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.ColorPickerHelper
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelable

class ColorItemActivity : AbstractBaseActivity() {
    private lateinit var binding: ActivityColorPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val boundItem = intent.extras?.parcelable<Item>(EXTRA_ITEM)

        supportActionBar?.title = boundItem?.label.orDefaultIfEmpty(getString(R.string.widget_type_color))

        val pickerHelper = ColorPickerHelper(binding.picker, binding.brightnessSlider)
        pickerHelper.attach(boundItem, this, ConnectionFactory.primaryUsableConnection?.connection)
    }

    override fun inflateBinding(): CommonBinding {
        binding = ActivityColorPickerBinding.inflate(layoutInflater)
        return CommonBinding(binding.root, binding.appBar, binding.coordinator, binding.activityContent)
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
