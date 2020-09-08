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

package org.openhab.habdroid.ui.activity

import android.view.ViewStub
import androidx.fragment.app.commit
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity

// instantiated via reflection
@Suppress("UNUSED")
class ContentControllerOnePane(activity: MainActivity) : ContentController(activity) {
    override val fragmentForTitle get() = if (pageStack.empty()) sitemapFragment else pageStack.peek().second

    override fun executeStateUpdate(reason: FragmentUpdateReason, allowStateLoss: Boolean) {
        val fragment = when {
            overridingFragment != null -> overridingFragment
            !pageStack.isEmpty() -> pageStack.peek().second
            else -> sitemapFragment
        }

        fm.commit(allowStateLoss) {
            setCustomAnimations(determineEnterAnim(reason), determineExitAnim(reason))
            replace(R.id.content, fragment ?: defaultProgressFragment)
        }
    }

    override fun inflateViews(stub: ViewStub) {
        stub.layoutResource = R.layout.content_onepane
        stub.inflate()
    }
}
