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
 * A router command in Main UI's old `handleCommand` string form, as used by the UI command item and by `ui:`
 * notification actions.
 */
sealed class MainUiCommand {
    data class Navigate(val path: String) : MainUiCommand()

    data class OpenModal(val kind: String, val target: String) : MainUiCommand()

    data object CloseModals : MainUiCommand()

    data object Back : MainUiCommand()

    data object Reload : MainUiCommand()

    companion object {
        fun parse(command: String): MainUiCommand? {
            val prefix = command.substringBefore(":")
            val content = command.substringAfter(":", "")
            return when (prefix) {
                "navigate" -> content.takeIf { it.isNotEmpty() }?.let { Navigate("/" + it.removePrefix("/")) }
                "popup", "popover", "sheet" -> content.takeIf { it.isNotEmpty() }?.let { OpenModal(prefix, it) }
                "close" -> CloseModals
                "back" -> Back
                "reload" -> Reload
                else -> null
            }
        }
    }
}
