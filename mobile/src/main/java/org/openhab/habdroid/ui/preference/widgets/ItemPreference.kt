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

package org.openhab.habdroid.ui.preference.widgets

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.openhab.habdroid.R

/**
 * Preference that stores the name of an Item. The Item is selected via an item picker,
 * which has to be launched by the owning fragment from the click listener.
 * If set, android:summary is shown when no Item is selected.
 */
class ItemPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    var itemName: String? = null
        private set

    init {
        widgetLayoutResource = R.layout.pref_widget_clear
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? = a.getString(index)

    override fun onSetInitialValue(defaultValue: Any?) {
        itemName = getPersistedString(defaultValue as String?)
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.clear)?.apply {
            isVisible = !itemName.isNullOrEmpty()
            setOnClickListener { setItem(null) }
        }
    }

    override fun getSummary(): CharSequence? = when {
        itemName.isNullOrEmpty() -> super.getSummary() ?: context.getString(R.string.info_not_set)
        else -> itemName
    }

    fun setItem(name: String?) {
        val newValue = name?.takeIf { it.isNotEmpty() }
        if (!callChangeListener(newValue)) {
            return
        }
        itemName = newValue
        if (newValue == null) {
            if (shouldPersist()) {
                sharedPreferences?.edit()?.remove(key)?.apply()
            }
        } else {
            persistString(newValue)
        }
        notifyChanged()
    }
}
