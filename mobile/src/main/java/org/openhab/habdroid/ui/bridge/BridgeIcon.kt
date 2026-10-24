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
 * An icon in Main UI's format, e.g. `f7:gear_alt_fill`, `material:settings`, `oh:classic:light` or
 * `iconify:mdi:lightbulb`.
 *
 * @param name The icon Main UI shows on the iOS and Aurora themes.
 * @param md The icon Main UI shows on the Material theme, when it differs.
 * @param svg SVG markup, only when there is no name.
 */
data class BridgeIcon(val name: String?, val md: String?, val svg: String?) {
    /**
     * The icon to show on Android, which matches Main UI's Material theme.
     */
    val androidName get() = md ?: name
}
