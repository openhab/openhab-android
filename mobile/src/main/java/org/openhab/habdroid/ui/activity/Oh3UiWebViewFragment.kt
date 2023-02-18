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

import okhttp3.HttpUrl
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity

class Oh3UiWebViewFragment : AbstractWebViewFragment() {
    override val titleRes = R.string.mainmenu_openhab_oh3_ui
    override val multiServerTitleRes = R.string.mainmenu_openhab_oh3_ui_on_server
    override val errorMessageRes = R.string.oh3_ui_error
    override val urlToLoad = "/"
    override val urlForError = "/"
    override val lockDrawer = true
    override val shortcutIcon = R.mipmap.ic_shortcut_oh3_ui
    override val shortcutAction = MainActivity.ACTION_OH3_UI_SELECTED

    override fun modifyUrl(orig: HttpUrl): HttpUrl {
        if (orig.host.substringBefore('.') != "home") {
            return orig.newBuilder()
                .host("home." + orig.host)
                .build()
        }

        return orig
    }
}
