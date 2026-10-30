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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.wear.BuildConfig
import org.openhab.habdroid.wear.config.WearConfigRepository
import org.openhab.habdroid.wear.voice.VoiceCommandActivity
import org.openhab.habdroid.wear.wearApplication

class WearMainActivity : ComponentActivity() {
    private val viewModel: SitemapViewModel by viewModels()
    private val configRepository get() = wearApplication.configRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearTheme {
                WearApp(
                    viewModel = viewModel,
                    settingsFlow = configRepository.settings,
                    onOpenVoiceCommand = {
                        startActivity(Intent(this, VoiceCommandActivity::class.java))
                    },
                    onOpenSetupOnPhone = this::openSetupOnPhone,
                    onSetDemoMode = { enable ->
                        configRepository.setDemoMode(enable)
                        if (!enable) {
                            lifecycleScope.launch {
                                configRepository.syncFromDataLayer()
                            }
                        }
                    }
                )
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

    private suspend fun openSetupOnPhone(): Boolean {
        val helper = RemoteActivityHelper(this)
        val setupIntent = Intent(Intent.ACTION_VIEW, "openhab://wear-setup".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val storeIntent = Intent(Intent.ACTION_VIEW, "market://details?id=${BuildConfig.APPLICATION_ID}".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            helper.startRemoteActivity(setupIntent).await()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Unable to open setup on phone, try to open the Play Store", e)
            try {
                helper.startRemoteActivity(storeIntent).await()
                true
            } catch (e: Exception) {
                Log.w(TAG, "Unable to open Play Store on phone", e)
                false
            }
        }
    }

    companion object {
        private const val TAG = "WearMainActivity"
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
fun WearApp(
    viewModel: SitemapViewModel,
    settingsFlow: StateFlow<WearConfigRepository.Settings>,
    onOpenVoiceCommand: () -> Unit,
    onOpenSetupOnPhone: suspend () -> Boolean,
    onSetDemoMode: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val navController = rememberSwipeDismissableNavController()
    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Routes.MAIN
        ) {
            composable(Routes.MAIN) {
                MainScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onOpenVoiceCommand = onOpenVoiceCommand,
                    onOpenSetupOnPhone = {
                        scope.launch {
                            onOpenSetupOnPhone()
                        }
                    },
                    onEnableDemoMode = { onSetDemoMode(true) }
                )
            }

            composable(Routes.PAGE_PATTERN) { entry ->
                val pageUrl = Routes.pageArg(entry.arguments)
                ReadyState(viewModel) { ready ->
                    PageScreen(
                        pageUrl = pageUrl,
                        state = ready,
                        viewModel = viewModel,
                        navController = navController,
                        onOpenVoiceCommand = null,
                        onOpenSettings = null
                    )
                }
            }

            composable(Routes.SELECTION_PATTERN) { entry ->
                WidgetState(viewModel, entry.arguments, navController = navController) { widget, _ ->
                    SelectionScreen(
                        widget = widget,
                        onSelect = { command ->
                            viewModel.sendCommand(widget.item, command)
                            navController.popBackStack()
                        }
                    )
                }
            }

            composable(Routes.SETPOINT_PATTERN) { entry ->
                WidgetState(viewModel, entry.arguments, navController) { widget, _ ->
                    SetpointScreen(
                        widget = widget,
                        onCommand = { command -> viewModel.sendCommand(widget.item, command) }
                    )
                }
            }

            composable(Routes.COLOR_PATTERN) { entry ->
                WidgetState(viewModel, entry.arguments, navController) { widget, _ ->
                    ColorScreen(
                        widget = widget,
                        onCommand = { command -> viewModel.sendCommand(widget.item, command) }
                    )
                }
            }

            composable(Routes.SETTINGS) {
                // Go back to the sitemap, so the user sees the reconnection
                SettingsScreen(
                    settingsFlow = settingsFlow,
                    stateFlow = viewModel.state,
                    onReconnect = {
                        viewModel.retry()
                        navController.popBackStack()
                    },
                    onSetDemoMode = onSetDemoMode,
                    onOpenSetupOnPhone = onOpenSetupOnPhone
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    viewModel: SitemapViewModel,
    navController: NavHostController,
    onOpenVoiceCommand: () -> Unit,
    onOpenSetupOnPhone: () -> Unit,
    onEnableDemoMode: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is SitemapViewModel.State.Loading -> LoadingScreen()

        is SitemapViewModel.State.NotConfigured -> SetupScreen(
            onOpenSetupOnPhone = onOpenSetupOnPhone,
            onEnableDemoMode = onEnableDemoMode
        )

        is SitemapViewModel.State.Error -> ErrorScreen(
            error = current.error,
            url = current.url,
            onRetry = viewModel::retry,
            onGoToSettings = { navController.navigate(Routes.SETTINGS) }
        )

        is SitemapViewModel.State.NoSitemaps -> NoSitemapsScreen(onRetry = viewModel::retry)

        is SitemapViewModel.State.Ready -> PageScreen(
            pageUrl = current.sitemap.homepageLink,
            state = current,
            viewModel = viewModel,
            navController = navController,
            onOpenVoiceCommand = onOpenVoiceCommand,
            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
        )
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

        is SitemapViewModel.State.Error -> ErrorScreen(
            error = current.error,
            url = current.url,
            onRetry = viewModel::retry,
            onGoToSettings = null
        )

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
    val widget = pages[Routes.pageArg(args)]
        ?.widgets
        ?.firstOrNull { widget -> widget.id == widgetId }

    ReadyState(viewModel) { ready ->
        if (widget == null) {
            // The page has been reloaded and the widget is gone
            LaunchedEffect(Unit) {
                navController.popBackStack()
            }
        } else {
            content(widget, ready)
        }
    }
}
