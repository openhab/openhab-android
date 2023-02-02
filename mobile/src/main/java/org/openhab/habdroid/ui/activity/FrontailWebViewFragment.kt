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

class FrontailWebViewFragment : AbstractWebViewFragment() {
    override val titleRes = R.string.mainmenu_openhab_frontail
    override val multiServerTitleRes = R.string.mainmenu_openhab_frontail_on_server
    override val errorMessageRes = R.string.frontail_error
    override val urlToLoad = "/"
    override val urlForError = "/"
    override val avoidAuthentication = true
    override val lockDrawer = false
    override val shortcutIcon = R.mipmap.ic_shortcut_frontail
    override val shortcutAction = MainActivity.ACTION_FRONTAIL_SELECTED

    override fun modifyUrl(orig: HttpUrl): HttpUrl {
        return orig.newBuilder()
            .scheme("http")
            .port(9001)
            .build()
    }
}
