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

package org.openhab.habdroid.model

import org.openhab.habdroid.ui.activity.AbstractWebViewFragment
import org.openhab.habdroid.ui.activity.FrontailWebViewFragment
import org.openhab.habdroid.ui.activity.HabpanelWebViewFragment
import org.openhab.habdroid.ui.activity.MainUiWebViewFragment

data class WebViewUi(
    val serverFlag: Int,
    val fragment: Class<out AbstractWebViewFragment>
) {
    companion object {
        val HABPANEL = WebViewUi(
            ServerProperties.SERVER_FLAG_HABPANEL_INSTALLED,
            HabpanelWebViewFragment::class.java
        )

        val MAIN_UI = WebViewUi(
            ServerProperties.SERVER_FLAG_MAIN_UI,
            MainUiWebViewFragment::class.java
        )

        val FRONTAIL = WebViewUi(
            0,
            FrontailWebViewFragment::class.java
        )
    }
}
