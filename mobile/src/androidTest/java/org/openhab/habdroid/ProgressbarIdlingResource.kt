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

package org.openhab.habdroid

import android.view.View
import androidx.core.view.isInvisible
import androidx.test.espresso.IdlingResource

class ProgressbarIdlingResource(private val name: String, private val progressBar: View) : IdlingResource {
    private var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String {
        return name
    }

    override fun isIdleNow(): Boolean {
        val idle = progressBar.isInvisible
        if (idle) {
            callback?.onTransitionToIdle()
        }

        return idle
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }
}
