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

import androidx.fragment.app.FragmentManager
import androidx.test.espresso.IdlingResource

import org.openhab.habdroid.ui.WidgetListFragment
import org.openhab.habdroid.ui.activity.ContentController

class FragmentStatusIdlingResource(private val name: String, private val fm: FragmentManager) : IdlingResource {
    private var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String {
        return name
    }

    override fun isIdleNow(): Boolean {
        val idle = !hasBusyFragments()
        if (idle) {
            callback?.onTransitionToIdle()
        }

        return idle
    }

    private fun hasBusyFragments(): Boolean {
        if (fm.isDestroyed) {
            return false
        }
        fm.executePendingTransactions()
        for (f in fm.fragments) {
            if (f is ContentController.ProgressFragment) {
                return true
            }
            if (f is WidgetListFragment) {
                if (f.recyclerView.hasPendingAdapterUpdates()) {
                    return true
                }
            }
        }
        return false
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }
}
