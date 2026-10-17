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

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.launch
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.wear.wearApplication

class WearMainActivity : ComponentActivity() {
    private val viewModel: SitemapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearTheme {
                WearApp(viewModel)
            }
        }
        lifecycleScope.launch {
            wearApplication.configRepository.syncFromDataLayer()
        }
    }

    override fun onStart() {
        super.onStart()
        // Pages are only kept up to date while the app is visible
        viewModel.start()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }
}

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    private const val PAGE = "page"
    private const val SELECTION = "selection"
    private const val SETPOINT = "setpoint"
    private const val COLOR = "color"
    private const val ARG_PAGE = "page"
    private const val ARG_WIDGET = "widget"

    const val PAGE_PATTERN = "$PAGE?$ARG_PAGE={$ARG_PAGE}"
    const val SELECTION_PATTERN = "$SELECTION?$ARG_PAGE={$ARG_PAGE}&$ARG_WIDGET={$ARG_WIDGET}"
    const val SETPOINT_PATTERN = "$SETPOINT?$ARG_PAGE={$ARG_PAGE}&$ARG_WIDGET={$ARG_WIDGET}"
    const val COLOR_PATTERN = "$COLOR?$ARG_PAGE={$ARG_PAGE}&$ARG_WIDGET={$ARG_WIDGET}"

    fun page(pageUrl: String) = "$PAGE?$ARG_PAGE=${Uri.encode(pageUrl)}"

    fun selection(pageUrl: String, widgetId: String) = widgetRoute(SELECTION, pageUrl, widgetId)

    fun setpoint(pageUrl: String, widgetId: String) = widgetRoute(SETPOINT, pageUrl, widgetId)

    fun color(pageUrl: String, widgetId: String) = widgetRoute(COLOR, pageUrl, widgetId)

    fun pageArg(args: Bundle?) = args?.getString(ARG_PAGE).orEmpty()

    fun widgetArg(args: Bundle?) = args?.getString(ARG_WIDGET).orEmpty()

    private fun widgetRoute(route: String, pageUrl: String, widgetId: String) =
        "$route?$ARG_PAGE=${Uri.encode(pageUrl)}&$ARG_WIDGET=${Uri.encode(widgetId)}"
}

@Composable
fun WearApp(viewModel: SitemapViewModel) {
    val navController = rememberSwipeDismissableNavController()
    AppScaffold {
        SwipeDismissableNavHost(navController = navController, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {
                MainScreen(viewModel, navController)
            }
            composable(Routes.PAGE_PATTERN) { entry ->
                val pageUrl = Routes.pageArg(entry.arguments)
                ReadyState(viewModel) { ready -> PageScreen(pageUrl, ready, viewModel, navController) }
            }
            composable(Routes.SELECTION_PATTERN) { entry ->
                WidgetState(viewModel, entry.arguments, navController) { widget, _ ->
                    SelectionScreen(widget, onSelect = { command ->
                        viewModel.sendCommand(widget.item, command)
                        navController.popBackStack()
                    })
                }
            }
            composable(Routes.SETPOINT_PATTERN) { entry ->
                WidgetState(viewModel, entry.arguments, navController) { widget, _ ->
                    SetpointScreen(widget, onCommand = { command -> viewModel.sendCommand(widget.item, command) })
                }
            }
            composable(Routes.COLOR_PATTERN) { entry ->
                WidgetState(viewModel, entry.arguments, navController) { widget, _ ->
                    ColorScreen(widget, onCommand = { command -> viewModel.sendCommand(widget.item, command) })
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
private fun MainScreen(viewModel: SitemapViewModel, navController: NavHostController) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is SitemapViewModel.State.Loading -> LoadingScreen()

        is SitemapViewModel.State.NotConfigured -> SetupScreen()

        is SitemapViewModel.State.Error -> ErrorScreen(
            current,
            onRetry = viewModel::retry,
            navController = navController
        )

        is SitemapViewModel.State.NoSitemaps -> NoSitemapsScreen(onRetry = viewModel::retry)

        is SitemapViewModel.State.Ready -> PageScreen(current.sitemap.homepageLink, current, viewModel, navController)
    }
}

/**
 * Shows the content only if the sitemap is loaded, e.g. after a process restart the connection is established again.
 */
@Composable
private fun ReadyState(viewModel: SitemapViewModel, content: @Composable (SitemapViewModel.State.Ready) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is SitemapViewModel.State.Ready -> content(current)
        is SitemapViewModel.State.Error -> ErrorScreen(current, onRetry = viewModel::retry, navController = null)
        else -> LoadingScreen()
    }
}

@Composable
private fun WidgetState(
    viewModel: SitemapViewModel,
    args: Bundle?,
    navController: NavHostController,
    content: @Composable (Widget, SitemapViewModel.State.Ready) -> Unit
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val widgetId = Routes.widgetArg(args)
    val widget = pages[Routes.pageArg(args)]?.widgets?.firstOrNull { widget -> widget.id == widgetId }
    ReadyState(viewModel) { ready ->
        if (widget == null) {
            // The page has been reloaded and the widget is gone
            LaunchedEffect(Unit) { navController.popBackStack() }
        } else {
            content(widget, ready)
        }
    }
}
