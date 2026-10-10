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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.voice.VoiceCommandActivity

/**
 * Shows a page of the sitemap. The root page additionally offers voice commands and settings.
 */
@Composable
fun PageScreen(
    pageUrl: String,
    state: SitemapViewModel.State.Ready,
    viewModel: SitemapViewModel,
    navController: NavHostController
) {
    val context = LocalContext.current
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val page = pages[pageUrl]
    val isRoot = pageUrl == state.sitemap.homepageLink
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LifecycleResumeEffect(pageUrl, state.connection) {
        viewModel.onPageVisible(pageUrl)
        onPauseOrDispose { }
    }

    val actions = object : WidgetActions {
        override fun openPage(url: String) = navController.navigate(Routes.page(url))

        override fun openSelection(widget: Widget) = navController.navigate(Routes.selection(pageUrl, widget.id))

        override fun openSetpoint(widget: Widget) = navController.navigate(Routes.setpoint(pageUrl, widget.id))

        override fun openColor(widget: Widget) = navController.navigate(Routes.color(pageUrl, widget.id))

        override fun sendCommand(item: Item?, command: String) = viewModel.sendCommand(item, command)
    }

    val widgets = page?.widgets
    val content: @Composable (PaddingValues) -> Unit = content@{ contentPadding ->
        if (widgets == null) {
            // Not part of the list, as the list would keep the progress indicator centered after loading
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@content
        }
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = page.title?.takeIf { it.isNotEmpty() } ?: state.sitemap.label,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val rows = buildWidgetRows(widgets)
            if (rows.isEmpty()) {
                item { Text(stringResource(R.string.page_empty), textAlign = TextAlign.Center) }
            }
            items(rows, key = { row -> row.widget.id }) { row ->
                WidgetRow(
                    row = row,
                    scope = WidgetScope(state.connection, state.serverFlags, actions),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            if (isRoot) {
                item {
                    FilledTonalButton(
                        onClick = { navController.navigate(Routes.SETTINGS) },
                        label = { Text(stringResource(R.string.settings)) },
                        icon = { Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
            }
        }
    }

    if (isRoot) {
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = { context.startActivity(Intent(context, VoiceCommandActivity::class.java)) }) {
                    Icon(
                        painterResource(R.drawable.ic_mic_24dp),
                        contentDescription = stringResource(R.string.voice_command)
                    )
                }
            }
        ) { padding -> content(padding) }
    } else {
        ScreenScaffold(scrollState = listState) { padding -> content(padding) }
    }
}
