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

package org.openhab.habdroid.ui.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference

class TileItemAndStatePreference constructor(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    var item: String? = null
    var label: String? = null
    var state: String? = null
    var mappedState: String? = null
}
