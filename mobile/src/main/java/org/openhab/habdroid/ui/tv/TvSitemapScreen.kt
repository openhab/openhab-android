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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item

@Composable
fun TvSitemapScreen(viewModel: TvSitemapViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    BackHandler(enabled = (state as? TvSitemapViewModel.State.Page)?.canGoBack == true) {
        viewModel.goBack()
    }

    when (val s = state) {
        TvSitemapViewModel.State.Loading -> CenteredMessage(stringResource(R.string.tv_loading))
        is TvSitemapViewModel.State.Error -> CenteredMessage(s.message) {
            Button(onClick = { viewModel.retry() }, modifier = it) {
                Text(stringResource(R.string.retry))
            }
        }
        is TvSitemapViewModel.State.NoSitemaps -> CenteredMessage(
            stringResource(R.string.tv_no_sitemaps, s.serverUrl ?: stringResource(R.string.openhab))
        ) {
            Button(onClick = { viewModel.retry() }, modifier = it) {
                Text(stringResource(R.string.retry))
            }
        }
        is TvSitemapViewModel.State.Page -> SitemapPage(s, viewModel)
    }
}

@Composable
private fun SitemapPage(page: TvSitemapViewModel.State.Page, viewModel: TvSitemapViewModel) {
    val actions = remember(viewModel) {
        object : TvWidgetActions {
            override fun openPage(url: String) = viewModel.openPage(url)

            override fun sendCommand(item: Item?, command: String) = viewModel.sendCommand(item, command)
        }
    }

    // Scroll position and focused widget per page URL
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val focusedWidgetIds = remember { mutableMapOf<String, String>() }

    Column(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 27.dp)) {
        Text(text = page.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        val widgets = page.widgets
        if (widgets == null) {
            CenteredMessage(stringResource(R.string.tv_loading))
        } else {
            // Restore scroll position and focus when navigating back to a page
            key(page.url) {
                val listState = listStates.getOrPut(page.url) { LazyListState() }
                val initialFocusId = remember { focusedWidgetIds[page.url] }
                val listFocusRequester = remember { FocusRequester() }
                val widgetFocusRequester = remember { FocusRequester() }
                TvWidgetList(
                    widgets = widgets,
                    connection = page.connection,
                    actions = actions,
                    listState = listState,
                    initialFocusRequester = widgetFocusRequester,
                    initialFocusId = initialFocusId,
                    onWidgetFocused = { id -> focusedWidgetIds[page.url] = id },
                    modifier = Modifier.focusRequester(listFocusRequester).focusGroup()
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
private fun CenteredMessage(message: String, action: (@Composable (Modifier) -> Unit)? = null) {
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
