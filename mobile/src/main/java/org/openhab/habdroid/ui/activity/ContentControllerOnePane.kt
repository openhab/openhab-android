/*
 * Copyright (c) 2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.activity

import android.view.ViewStub
import androidx.fragment.app.transaction
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.WidgetListFragment

// instantiated via reflection
@Suppress("UNUSED")
class ContentControllerOnePane(activity: MainActivity) : ContentController(activity) {
    override val fragmentForTitle: WidgetListFragment?
        get() = if (pageStack.empty()) sitemapFragment else pageStack.peek().second

    override fun executeStateUpdate(reason: FragmentUpdateReason, allowStateLoss: Boolean) {
        val fragment = when {
            overridingFragment != null -> overridingFragment
            !pageStack.isEmpty() -> pageStack.peek().second
            else -> sitemapFragment
        }

        fm.transaction(allowStateLoss = allowStateLoss) {
            setCustomAnimations(determineEnterAnim(reason), determineExitAnim(reason))
            replace(R.id.content, fragment ?: defaultProgressFragment)
        }
    }

    override fun inflateViews(stub: ViewStub) {
        stub.layoutResource = R.layout.content_onepane
        stub.inflate()
    }
}
