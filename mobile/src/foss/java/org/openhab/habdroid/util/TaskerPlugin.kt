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

package org.openhab.habdroid.util

import android.content.Context
import android.os.Bundle

object TaskerPlugin {
    fun hostSupportsRelevantVariables(bundle: Bundle?) : Boolean {
        return false
    }

    fun getRelevantVariableList(bundle: Bundle?) : Array<String>? {
        return null
    }

    object Setting {
        fun hostSupportsOnFireVariableReplacement(context: Context) : Boolean {
            return false
        }

        fun setVariableReplaceKeys(bundle: Bundle?, keys: Array<String>) {
            // no-op
        }
    }
}
