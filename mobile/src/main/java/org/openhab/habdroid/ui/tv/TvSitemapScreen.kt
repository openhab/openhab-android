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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedIconButton
import androidx.tv.material3.Text
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.model.Sitemap

@Composable
fun TvSitemapScreen(onOpenSettings: () -> Unit, viewModel: TvSitemapViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    BackHandler(enabled = (state as? TvSitemapViewModel.State.Page)?.canGoBack == true) {
        viewModel.goBack()
    }

    when (val s = state) {
        TvSitemapViewModel.State.Loading ->
            CenteredMessage(
                message = stringResource(R.string.tv_loading),
                showProgress = true
            )

        is TvSitemapViewModel.State.Error ->
            CenteredMessage(
                message = s.message
            ) { modifier ->
                RetryAndSettingsButtons(
                    modifier = modifier,
                    onRetry = viewModel::retry,
                    onOpenSettings = onOpenSettings
                )
            }

        is TvSitemapViewModel.State.NoSitemaps ->
            CenteredMessage(
                message = stringResource(R.string.tv_no_sitemaps, s.serverUrl ?: stringResource(R.string.openhab))
            ) { modifier ->
                RetryAndSettingsButtons(
                    modifier = modifier,
                    onRetry = viewModel::retry,
                    onOpenSettings = onOpenSettings
                )
            }

        is TvSitemapViewModel.State.SelectSitemap ->
            SitemapSelection(
                sitemaps = s.sitemaps,
                onSelected = viewModel::selectSitemap
            )

        is TvSitemapViewModel.State.Page ->
            SitemapPage(
                page = s,
                onOpenPage = viewModel::openPage,
                onSendCommand = viewModel::sendCommand,
                onOpenSettings = onOpenSettings
            )
    }
}

@Composable
private fun SitemapPage(
    page: TvSitemapViewModel.State.Page,
    onOpenPage: (LinkedPage) -> Unit,
    onSendCommand: (item: Item?, command: String) -> Unit,
    onOpenSettings: () -> Unit
) {
    // Scroll position and focused widget per page URL
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val focusedWidgetIds = remember { mutableMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, top = 27.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedIconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_outline_grey_24dp),
                    contentDescription = stringResource(R.string.mainmenu_openhab_preferences),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (page.widgets == null) {
            CenteredMessage(
                message = stringResource(R.string.tv_loading),
                showProgress = true
            )
        } else {
            // Restore scroll position and focus when navigating back to a page
            key(page.url) {
                val listState = listStates.getOrPut(page.url) { LazyListState() }
                val initialFocusId = remember { focusedWidgetIds[page.url] }
                val listFocusRequester = remember { FocusRequester() }
                val widgetFocusRequester = remember { FocusRequester() }

                TvWidgetList(
                    widgets = page.widgets,
                    connection = page.connection,
                    serverFlags = page.serverFlags,
                    listState = listState,
                    initialFocusRequester = widgetFocusRequester,
                    initialFocusId = initialFocusId,
                    onOpenPage = onOpenPage,
                    onSendCommand = onSendCommand,
                    onWidgetFocused = { id -> focusedWidgetIds[page.url] = id },
                    modifier = Modifier
                        .focusRequester(listFocusRequester)
                        .focusGroup()
                )
                LaunchedEffect(Unit) {
                    // The previously focused widget might not exist anymore
                    if (initialFocusId == null || !widgetFocusRequester.requestFocusWhenAttached()) {
                        listFocusRequester.requestFocusWhenAttached()
                    }
                }
            }
        }
    }
}

@Composable
private fun SitemapSelection(sitemaps: List<Sitemap>, onSelected: (Sitemap) -> Unit) {
    val firstItemFocus = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, top = 27.dp)
    ) {
        Text(
            text = stringResource(R.string.tv_select_sitemap),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tv_select_sitemap_hint),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 720.dp)
        ) {
            itemsIndexed(
                items = sitemaps,
                key = { _, sitemap -> sitemap.name }
            ) { index, sitemap ->
                ListItem(
                    selected = false,
                    onClick = { onSelected(sitemap) },
                    headlineContent = { Text(sitemap.label) },
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                )
            }
        }
    }
    LaunchedEffect(sitemaps) {
        firstItemFocus.requestFocusWhenAttached()
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    showProgress: Boolean = false,
    action: (@Composable (Modifier) -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showProgress) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 720.dp)
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(32.dp))
            action(Modifier.focusRequester(focusRequester))
            LaunchedEffect(message) {
                focusRequester.requestFocusWhenAttached()
            }
        }
    }
}

@Composable
private fun RetryAndSettingsButtons(modifier: Modifier, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onRetry,
            modifier = modifier
        ) {
            Text(stringResource(R.string.retry))
        }
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.mainmenu_openhab_preferences))
        }
    }
}
