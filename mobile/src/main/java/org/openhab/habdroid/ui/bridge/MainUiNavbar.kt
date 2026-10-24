/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.bridge

/**
 * Main UI's top bar, as sent in `navbar.state`.
 *
 * @param back Null when there is nothing to go back to, otherwise the label of the back button, which may be empty.
 */
data class NavbarState(
    val title: String,
    val hidden: Boolean,
    val back: String?,
    val leading: List<NavbarAction>,
    val trailing: List<NavbarAction>
)

data class NavbarAction(val id: String, val label: String, val icon: BridgeIcon?, val disabled: Boolean) {
    /**
     * The label, or for buttons that only show an icon, a label made from the icon's name, e.g. "Add location" for
     * `material:add_location`. The app needs one for the overflow menu and for accessibility.
     */
    val displayLabel: String get() {
        if (label.isNotEmpty()) {
            return label
        }
        val iconName = icon?.androidName?.substringAfterLast(':') ?: return ""
        return iconName.replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }
    }
}
