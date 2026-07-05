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

package org.openhab.habdroid.ui.activity

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.util.loadActiveServerConfig

class FrontailWebViewFragment : AbstractWebViewFragment() {
    override val titleRes = R.string.mainmenu_openhab_frontail
    override val errorMessageRes = R.string.frontail_error
    override val urlToLoad = "/"
    override val pathForError = "/"
    override val lockDrawer = false
    override val shortcutIcon = R.mipmap.ic_shortcut_frontail
    override val shortcutAction = MainActivity.ACTION_FRONTAIL_SELECTED

    override fun buildUrl(connection: Connection, url: String): HttpUrl {
        val connectionUrl = connection.httpClient.buildUrl(url)
        val frontailUrl = context?.loadActiveServerConfig()?.frontailUrl?.toHttpUrlOrNull()

        return connectionUrl.newBuilder()
            .scheme(frontailUrl?.scheme ?: "http")
            .port(frontailUrl?.port ?: 9001)
            .host(frontailUrl?.host ?: connectionUrl.host)
            .build()
    }

    companion object {
        private val TAG = FrontailWebViewFragment::class.java.simpleName
    }
}
