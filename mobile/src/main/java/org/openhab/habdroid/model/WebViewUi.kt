/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.activity.HabpanelWebViewFragment
import org.openhab.habdroid.ui.activity.Oh3UiWebViewFragment
import org.openhab.habdroid.ui.activity.AbstractWebViewFragment

data class WebViewUi(
    val serverFlag: Int,
    val shortcutAction: String,
    val fragment: Class<out AbstractWebViewFragment>
) {
    companion object {
        val HABPANEL = WebViewUi(
            ServerProperties.SERVER_FLAG_HABPANEL_INSTALLED,
            MainActivity.ACTION_HABPANEL_SELECTED,
            HabpanelWebViewFragment::class.java
        )

        val OH3_UI = WebViewUi(
            ServerProperties.SERVER_FLAG_OH3_UI,
            MainActivity.ACTION_OH3_UI_SELECTED,
            Oh3UiWebViewFragment::class.java
        )
    }
}
