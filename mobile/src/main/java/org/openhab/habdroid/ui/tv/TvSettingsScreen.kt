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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.openhab.habdroid.R
import org.openhab.habdroid.model.DefaultSitemap
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.loadActiveServerConfig

/**
 * Settings that are relevant on TV: server and credentials, sitemap and about
 */
@Composable
fun TvSettingsScreen(onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    var showSitemapDialog by remember { mutableStateOf(false) }
    // Incremented after changes to reload the configuration
    var configVersion by remember { mutableIntStateOf(0) }
    val config = remember(configVersion) { context.loadActiveServerConfig() }
    val firstItemFocus = remember { FocusRequester() }

    var url by remember(config) { mutableStateOf(config?.localPath?.url.orEmpty()) }
    var userName by remember(config) { mutableStateOf(config?.localPath?.userName.orEmpty()) }
    var password by remember(config) { mutableStateOf(config?.localPath?.password.orEmpty()) }
    // Save once the user finished editing a field, so the connection isn't updated on every key press
    val saveServer: () -> Unit = {
        val trimmedUrl = url.trim()
        val isValidUrl = trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")
        if (config != null && isValidUrl) {
            val path = ServerPath(trimmedUrl, userName.ifEmpty { null }, password.ifEmpty { null })
            if (path != config.localPath) {
                ServerConfiguration.createFrom(config, localPath = path)
                    .saveToPrefs(context.getPrefs(), context.getSecretPrefs())
            }
        }
        // Reload the stored values, which also reverts an invalid URL
        configVersion++
    }

    Column(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 27.dp)) {
        Text(
            text = stringResource(R.string.mainmenu_openhab_preferences),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                TvTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.tv_settings_server),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    onEditingFinished = saveServer,
                    modifier = Modifier.padding(horizontal = 16.dp).focusRequester(firstItemFocus)
                )
            }
            item {
                TvTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = stringResource(R.string.settings_openhab_username),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onEditingFinished = saveServer,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                TvTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_openhab_password),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    visualTransformation = PasswordVisualTransformation(),
                    onEditingFinished = saveServer,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = { showSitemapDialog = true },
                    headlineContent = { Text(stringResource(R.string.tv_settings_sitemap)) },
                    supportingContent = {
                        Text(config?.defaultSitemap?.label ?: stringResource(R.string.tv_no_sitemap_selected))
                    }
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = onOpenAbout,
                    headlineContent = { Text(stringResource(R.string.about_title)) }
                )
            }
        }
    }

    if (showSitemapDialog && config != null) {
        val sitemaps by produceState<List<Sitemap>?>(initialValue = null) {
            val connection = context.getConnectionFactory().currentActive?.conn?.connection
            value = when (val result = connection?.let { ServerProperties.fetch(it) }) {
                is ServerProperties.Companion.PropsSuccess -> result.props.sitemaps
                else -> emptyList()
            }
        }
        sitemaps?.let { list ->
            TvSelectionDialog(
                title = stringResource(R.string.tv_settings_sitemap),
                options = list.map { sitemap -> sitemap.name to sitemap.label },
                selectedValue = config.defaultSitemap?.name,
                onSelected = { name ->
                    val sitemap = list.first { sitemap -> sitemap.name == name }
                    ServerConfiguration.saveDefaultSitemap(
                        context.getPrefs(),
                        config.id,
                        DefaultSitemap(sitemap.name, sitemap.label)
                    )
                    configVersion++
                    showSitemapDialog = false
                },
                onDismiss = { showSitemapDialog = false }
            )
        }
    }

    LaunchedEffect(Unit) {
        firstItemFocus.requestFocusWhenAttached()
    }
}
