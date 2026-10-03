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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import org.openhab.habdroid.R
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.util.AsyncServiceResolver
import org.openhab.habdroid.util.getNextAvailableServerId
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs

private sealed class DiscoveryState {
    data object Searching : DiscoveryState()
    data class Found(val url: String) : DiscoveryState()
    data object NotFound : DiscoveryState()
}

/**
 * First run setup: discover the local openHAB server via mDNS or enter it manually.
 */
@Composable
fun TvSetupScreen(onServerConfigured: () -> Unit) {
    val context = LocalContext.current
    var manualEntry by rememberSaveable { mutableStateOf(false) }

    val saveServer = { url: String, userName: String?, password: String? ->
        val prefs = context.getPrefs()
        val config = ServerConfiguration(
            prefs.getNextAvailableServerId(),
            context.getString(R.string.openhab),
            ServerPath(url, userName, password),
            null,
            null,
            null,
            null,
            false,
            null,
            null
        )
        config.saveToPrefs(prefs, context.getSecretPrefs())
        onServerConfigured()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 96.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.tv_setup_title), style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(32.dp))
        if (manualEntry) {
            ManualServerEntry(onSave = saveServer, onCancel = { manualEntry = false })
        } else {
            ServerDiscovery(
                onUseServer = { url -> saveServer(url, null, null) },
                onEnterManually = { manualEntry = true }
            )
        }
    }
}

@Composable
private fun ServerDiscovery(onUseServer: (String) -> Unit, onEnterManually: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<DiscoveryState>(DiscoveryState.Searching) }
    var attempt by remember { mutableIntStateOf(0) }
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        state = DiscoveryState.Searching
        val info = AsyncServiceResolver(context, AsyncServiceResolver.OPENHAB_SERVICE_TYPE, this).resolve()
        state = if (info != null) {
            DiscoveryState.Found("https://${info.hostAddresses[0]}:${info.port}")
        } else {
            DiscoveryState.NotFound
        }
    }

    val message = when (val s = state) {
        DiscoveryState.Searching -> stringResource(R.string.resolving_openhab)
        is DiscoveryState.Found -> stringResource(R.string.tv_setup_server_found, s.url)
        DiscoveryState.NotFound -> stringResource(R.string.tv_setup_no_server_found)
    }
    Text(text = message, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(32.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        when (val s = state) {
            is DiscoveryState.Found -> {
                Button(onClick = { onUseServer(s.url) }, modifier = Modifier.focusRequester(primaryFocus)) {
                    Text(stringResource(R.string.tv_setup_use_server))
                }
            }

            DiscoveryState.NotFound -> {
                Button(onClick = { attempt++ }, modifier = Modifier.focusRequester(primaryFocus)) {
                    Text(stringResource(R.string.tv_setup_search_again))
                }
            }

            DiscoveryState.Searching -> {}
        }
        OutlinedButton(
            onClick = onEnterManually,
            modifier = if (state == DiscoveryState.Searching) Modifier.focusRequester(primaryFocus) else Modifier
        ) {
            Text(stringResource(R.string.tv_setup_enter_manually))
        }
    }

    LaunchedEffect(state) {
        primaryFocus.requestFocusWhenAttached()
    }
}

@Composable
private fun ManualServerEntry(onSave: (String, String?, String?) -> Unit, onCancel: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("https://") }
    var userName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val urlFocus = remember { FocusRequester() }

    Column(modifier = Modifier.width(640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.settings_openhab_url),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.focusRequester(urlFocus)
        )
        TvTextField(
            value = userName,
            onValueChange = { userName = it },
            label = stringResource(R.string.settings_openhab_username),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        TvTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.settings_openhab_password),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onSave(url.trim(), userName.ifEmpty { null }, password.ifEmpty { null }) },
                enabled = url.trim().let { it.startsWith("http://") || it.startsWith("https://") } &&
                    url.trim().length > "https://".length
            ) {
                Text(stringResource(R.string.save))
            }
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }

    LaunchedEffect(Unit) {
        urlFocus.requestFocusWhenAttached()
    }
}
