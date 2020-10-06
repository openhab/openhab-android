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

package org.openhab.habdroid.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity

data class WebViewUi(
    @StringRes val titleRes: Int,
    @StringRes val multiServerTitleRes: Int,
    @StringRes val errorRes: Int,
    val urlToLoad: String,
    val urlForError: String,
    val serverFlag: Int,
    val shortcutAction: String,
    @DrawableRes val shortcutIconRes: Int
) {
    companion object {
        val HABPANEL = WebViewUi(
            R.string.mainmenu_openhab_habpanel,
            R.string.mainmenu_openhab_habpanel_on_server,
            R.string.habpanel_error,
            "/habpanel/index.html",
            "/rest/events",
            ServerProperties.SERVER_FLAG_HABPANEL_INSTALLED,
            MainActivity.ACTION_HABPANEL_SELECTED,
            R.mipmap.ic_shortcut_habpanel
        )

        val OH3_UI = WebViewUi(
            R.string.mainmenu_openhab_oh3_ui,
            R.string.mainmenu_openhab_oh3_ui_on_server,
            R.string.oh3_ui_error,
            "/",
            "/",
            ServerProperties.SERVER_FLAG_OH3_UI,
            MainActivity.ACTION_OH3_UI_SELECTED,
            R.mipmap.ic_shortcut_oh3_ui
        )
    }
}
