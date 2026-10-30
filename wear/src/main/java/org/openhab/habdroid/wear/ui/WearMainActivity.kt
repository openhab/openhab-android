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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.SwipeDismissableSceneStrategy
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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

@Serializable
sealed interface NavRoute : NavKey {
    @Serializable
    data object Main : NavRoute
    @Serializable
    data object Settings : NavRoute
    @Serializable
    data class Page(val pageUrl: String) : NavRoute

    @Serializable
    data class Selection(val pageUrl: String, val widgetId: String) : NavRoute
    @Serializable
    data class Setpoint(val pageUrl: String, val widgetId: String) : NavRoute
    @Serializable
    data class Color(val pageUrl: String, val widgetId: String) : NavRoute
    @Serializable
    data class Buttons(val pageUrl: String, val widgetId: String, val buttons: List<IconButtonSpec>) : NavRoute
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
    val backStack = rememberNavBackStack(NavRoute.Main)

    AppScaffold {
        NavDisplay(
            backStack = backStack,
            sceneStrategies = listOf(SwipeDismissableSceneStrategy()),
            entryProvider = entryProvider {
                entry<NavRoute.Main> {
                    MainScreen(
                        viewModel = viewModel,
                        onOpenRoute = { backStack += it },
                        onOpenVoiceCommand = onOpenVoiceCommand,
                        onOpenSetupOnPhone = {
                            scope.launch {
                                onOpenSetupOnPhone()
                            }
                        },
                        onEnableDemoMode = { onSetDemoMode(true) }
                    )
                }

                entry<NavRoute.Page> { key ->
                    ReadyState(viewModel) { ready ->
                        PageScreen(
                            pageUrl = key.pageUrl,
                            state = ready,
                            viewModel = viewModel,
                            onOpenSubpage = { route -> backStack += route },
                            onOpenVoiceCommand = null,
                            onOpenSettings = null
                        )
                    }
                }

                entry<NavRoute.Selection> { key ->
                    WidgetState(
                        viewModel = viewModel,
                        pageUrl = key.pageUrl,
                        widgetId = key.widgetId,
                        onGoBack = { backStack.removeLastOrNull() }
                    ) { widget, _ ->
                        SelectionScreen(
                            widget = widget,
                            onSelect = { command ->
                                viewModel.sendCommand(widget.item, command)
                                backStack.removeLastOrNull()
                            }
                        )
                    }
                }

                entry<NavRoute.Setpoint> { key ->
                    WidgetState(
                        viewModel = viewModel,
                        pageUrl = key.pageUrl,
                        widgetId = key.widgetId,
                        onGoBack = { backStack.removeLastOrNull() }
                    ) { widget, _ ->
                        SetpointScreen(
                            widget = widget,
                            onCommand = { command -> viewModel.sendCommand(widget.item, command) }
                        )
                    }
                }

                entry<NavRoute.Color> { key ->
                    WidgetState(
                        viewModel = viewModel,
                        pageUrl = key.pageUrl,
                        widgetId = key.widgetId,
                        onGoBack = { backStack.removeLastOrNull() }
                    ) { widget, _ ->
                        ColorScreen(
                            widget = widget,
                            onCommand = { command -> viewModel.sendCommand(widget.item, command) }
                        )
                    }
                }

                entry<NavRoute.Settings> {
                    // Go back to the sitemap, so the user sees the reconnection
                    SettingsScreen(
                        settingsFlow = settingsFlow,
                        stateFlow = viewModel.state,
                        onReconnect = {
                            viewModel.retry()
                            backStack.removeLastOrNull()
                        },
                        onSetDemoMode = onSetDemoMode,
                        onOpenSetupOnPhone = onOpenSetupOnPhone
                    )
                }
            }
        )
    }
}

@Composable
private fun MainScreen(
    viewModel: SitemapViewModel,
    onOpenRoute: (NavRoute) -> Unit,
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
            onGoToSettings = { onOpenRoute(NavRoute.Settings) }
        )

        is SitemapViewModel.State.NoSitemaps -> NoSitemapsScreen(onRetry = viewModel::retry)

        is SitemapViewModel.State.Ready -> PageScreen(
            pageUrl = current.sitemap.homepageLink,
            state = current,
            viewModel = viewModel,
            onOpenVoiceCommand = onOpenVoiceCommand,
            onOpenSubpage = onOpenRoute,
            onOpenSettings = { onOpenRoute(NavRoute.Settings) }
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
    pageUrl: String,
    widgetId: String,
    onGoBack: () -> Unit,
    content: @Composable (Widget, SitemapViewModel.State.Ready) -> Unit
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val widget = pages[pageUrl]
        ?.widgets
        ?.firstOrNull { widget -> widget.id == widgetId }

    ReadyState(viewModel) { ready ->
        if (widget == null) {
            // The page has been reloaded and the widget is gone
            LaunchedEffect(Unit) {
                onGoBack()
            }
        } else {
            content(widget, ready)
        }
    }
}
