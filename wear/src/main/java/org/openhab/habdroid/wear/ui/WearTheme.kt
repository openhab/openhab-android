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

package org.openhab.habdroid.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * Colors derived from the openHAB orange, used if the watch doesn't provide dynamic colors
 */
private val OpenHabColorScheme = ColorScheme(
    primary = Color(0xFFFFB59D),
    primaryDim = Color(0xFFF5A68B),
    primaryContainer = Color(0xFF862200),
    onPrimary = Color(0xFF5F1500),
    onPrimaryContainer = Color(0xFFFFDBD0)
)

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    val colorScheme = dynamicColorScheme(LocalContext.current) ?: OpenHabColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
