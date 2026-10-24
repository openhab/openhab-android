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
 * Main UI's sidebar, to show in the app's drawer.
 */
class MainUiMenu(val sections: List<MenuSection>, val icons: MainUiIconLoader, val onSelected: (MenuEntry) -> Unit)

/**
 * A section of Main UI's sidebar, as sent in `menu.state`.
 */
data class MenuSection(val id: String, val title: String?, val items: List<MenuEntry>)

/**
 * @param path Selecting the entry navigates there. Entries without one do something in Main UI instead, which
 * `menu.activate` triggers.
 * @param children The entries the sidebar shows under this one.
 * @param more The rest, which the sidebar shows under "Show all".
 */
data class MenuEntry(
    val id: String,
    val label: String,
    val footer: String?,
    val icon: BridgeIcon?,
    val path: String?,
    val active: Boolean,
    val children: List<MenuEntry>,
    val more: List<MenuEntry>
)
