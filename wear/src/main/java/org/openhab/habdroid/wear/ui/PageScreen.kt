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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.wear.R

/**
 * Shows a page of the sitemap, optionally adding buttons for voice commands and settings.
 */
@Composable
fun PageScreen(
    pageUrl: String,
    state: SitemapViewModel.State.Ready,
    viewModel: SitemapViewModel,
    onOpenSubpage: (NavRoute) -> Unit,
    onOpenVoiceCommand: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val page = pages[pageUrl]

    LifecycleResumeEffect(pageUrl, state.connection) {
        viewModel.onPageVisible(pageUrl)
        onPauseOrDispose { }
    }

    val widgetScope = WidgetScope(
        connection = state.connection,
        serverFlags = state.serverFlags,
        onOpenPage = { url -> onOpenSubpage(NavRoute.Page(url)) },
        onOpenSelection = { widget -> onOpenSubpage(NavRoute.Selection(pageUrl, widget.id)) },
        onOpenSetpoint = { widget -> onOpenSubpage(NavRoute.Setpoint(pageUrl, widget.id)) },
        onOpenColor = { widget -> onOpenSubpage(NavRoute.Color(pageUrl, widget.id)) },
        onOpenButtons = { widget, buttons -> onOpenSubpage(NavRoute.Buttons(pageUrl, widget.id, buttons)) },
        onSendCommand = viewModel::sendCommand
    )

    PageScreen(
        page = page,
        sitemap = state.sitemap,
        widgetScope = widgetScope,
        onOpenSettings = onOpenSettings,
        onOpenVoiceCommand = onOpenVoiceCommand
    )
}

@Composable
fun PageScreen(
    page: SitemapViewModel.Page?,
    sitemap: Sitemap,
    widgetScope: WidgetScope,
    onOpenSettings: (() -> Unit)?,
    onOpenVoiceCommand: (() -> Unit)?
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val widgets = page?.widgets
    val content: @Composable (PaddingValues) -> Unit = { contentPadding ->
        if (widgets == null) {
            // Not part of the list, as the list would keep the progress indicator centered after loading
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding
            ) {
                item {
                    ListHeader(
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        PageHeader(
                            page = page,
                            sitemap = sitemap
                        )
                    }
                }

                val rows = buildWidgetRows(widgets)
                if (rows.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.page_empty),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                items(
                    items = rows,
                    key = { row -> row.widget.id }
                ) { row ->
                    with(widgetScope) {
                        WidgetRow(
                            row = row,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this@items, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        )
                    }
                }

                if (onOpenSettings != null) {
                    item {
                        FilledTonalButton(
                            onClick = onOpenSettings,
                            label = {
                                Text(stringResource(R.string.settings))
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        )
                    }
                }
            }
        }
    }

    if (onOpenVoiceCommand != null) {
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = onOpenVoiceCommand) {
                    Icon(
                        painterResource(R.drawable.ic_mic_24dp),
                        contentDescription = stringResource(R.string.voice_command)
                    )
                }
            },
            content = { padding -> content(padding) }
        )
    } else {
        ScreenScaffold(
            scrollState = listState,
            content = { padding -> content(padding) }
        )
    }
}

@Composable
private fun PageHeader(page: SitemapViewModel.Page, sitemap: Sitemap) {
    // Show the state part of the title ("Label [state]") below the label, like a Text widget
    val title = page.title
        ?.takeIf { it.isNotEmpty() }
        ?: sitemap.label
    val label = title.substringBefore("[").trim()
    val titleState = title.removePrefix(label).trim()
        .takeIf { it.startsWith("[") && it.endsWith("]") }
        ?.removeSurrounding("[", "]")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!titleState.isNullOrEmpty()) {
            Text(
                text = titleState,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
