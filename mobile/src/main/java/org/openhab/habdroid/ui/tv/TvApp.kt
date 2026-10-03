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
package org.openhab.habdroid.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrefs

object TvRoutes {
    const val SETUP = "setup"
    const val SITEMAP = "sitemap"
    const val SETTINGS = "settings"
}

@Composable
fun TvApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val startDestination = if (context.getPrefs().getConfiguredServerIds().isEmpty()) {
        TvRoutes.SETUP
    } else {
        TvRoutes.SITEMAP
    }

    // Surface provides the content color for all screens
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(TvRoutes.SETUP) {
                TvSetupScreen(
                    onServerConfigured = {
                        navController.navigate(TvRoutes.SITEMAP) {
                            popUpTo(TvRoutes.SETUP) { inclusive = true }
                        }
                    }
                )
            }
            composable(TvRoutes.SITEMAP) { PlaceholderScreen("Sitemap") }
            composable(TvRoutes.SETTINGS) { PlaceholderScreen("Settings") }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.displaySmall)
    }
}
