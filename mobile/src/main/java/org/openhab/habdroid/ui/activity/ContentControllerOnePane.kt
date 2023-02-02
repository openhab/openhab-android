/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import android.view.View
import android.view.ViewStub
import androidx.annotation.Keep
import androidx.fragment.app.commit
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity

// instantiated via reflection
@Suppress("UNUSED")
@Keep
class ContentControllerOnePane(activity: MainActivity) : ContentController(activity) {
    override val fragmentForTitle get() = if (pageStack.empty()) sitemapFragment else pageStack.peek().second
    override val fragmentForAppBarScroll get() = fragmentForTitle

    override fun executeStateUpdate(reason: FragmentUpdateReason) {
        val fragment = when {
            overridingFragment != null -> overridingFragment
            !pageStack.isEmpty() -> pageStack.peek().second
            else -> sitemapFragment
        }

        fm.commit(!fm.isStateSaved) {
            setCustomAnimations(determineEnterAnim(reason), determineExitAnim(reason))
            replace(R.id.content, fragment ?: defaultProgressFragment)
        }
    }

    override fun inflateContentView(stub: ViewStub): View {
        stub.layoutResource = R.layout.content_onepane
        return stub.inflate()
    }
}
