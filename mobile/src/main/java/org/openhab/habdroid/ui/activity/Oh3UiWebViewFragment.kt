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

package org.openhab.habdroid.ui.activity

import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import okhttp3.HttpUrl
import org.openhab.habdroid.R
import org.openhab.habdroid.model.WebViewUi
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getPrefs

class Oh3UiWebViewFragment : AbstractWebViewFragment() {
    override val titleRes = R.string.mainmenu_openhab_oh3_ui
    override val multiServerTitleRes = R.string.mainmenu_openhab_oh3_ui_on_server
    override val errorMessageRes = R.string.oh3_ui_error
    override val urlToLoad = "/"
    override val urlForError = "/"

    override val shortcutInfo: ShortcutInfoCompat
        get() {
            val context = requireContext()
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SERVER_ID, context.getPrefs().getActiveServerId())
                .setAction(WebViewUi.OH3_UI.shortcutAction)

            return ShortcutInfoCompat.Builder(context, WebViewUi.OH3_UI.shortcutAction)
                .setShortLabel(title!!)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_oh3_ui))
                .setIntent(intent)
                .build()
        }

    override fun modifyUrl(orig: HttpUrl): HttpUrl {
        if (orig.host == "myopenhab.org") {
            return orig.newBuilder()
                .host("home.myopenhab.org")
                .build()
        }
        return orig
    }
}
