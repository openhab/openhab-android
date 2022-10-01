/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity

class HabpanelWebViewFragment : AbstractWebViewFragment() {
    override val titleRes = R.string.mainmenu_openhab_habpanel
    override val multiServerTitleRes = R.string.mainmenu_openhab_habpanel_on_server
    override val errorMessageRes = R.string.habpanel_error
    override val urlToLoad = "/habpanel/index.html"
    override val urlForError = "/rest/events"
    override val lockDrawer = true
    override val shortcutIcon = R.mipmap.ic_shortcut_habpanel
    override val shortcutAction = MainActivity.ACTION_HABPANEL_SELECTED
}
