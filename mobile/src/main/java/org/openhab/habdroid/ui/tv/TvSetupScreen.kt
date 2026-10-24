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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.tv.TvSetupViewModel.DiscoveryState

/**
 * First run setup: discover the local openHAB server via mDNS or enter it manually.
 */
@Composable
fun TvSetupScreen(
    onSaveServer: (url: String, userName: String?, password: String?) -> Unit,
    viewModel: TvSetupViewModel = viewModel()
) {
    var manualEntry by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 96.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.tv_setup_title), style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(32.dp))
        if (manualEntry) {
            ManualServerEntry(onSave = onSaveServer, onCancel = { manualEntry = false })
        } else {
            val state by viewModel.state.collectAsStateWithLifecycle()
            ServerDiscovery(
                state = state,
                onUseServer = { url -> onSaveServer(url, null, null) },
                onSearchAgain = viewModel::discover,
                onEnterManually = { manualEntry = true }
            )
        }
    }
}

@Composable
private fun ServerDiscovery(
    state: DiscoveryState,
    onUseServer: (String) -> Unit,
    onSearchAgain: () -> Unit,
    onEnterManually: () -> Unit
) {
    val primaryFocus = remember { FocusRequester() }

    val message = when (state) {
        DiscoveryState.Searching -> stringResource(R.string.resolving_openhab)
        is DiscoveryState.Found -> stringResource(R.string.tv_setup_server_found, state.url)
        DiscoveryState.NotFound -> stringResource(R.string.tv_setup_no_server_found)
    }
    Text(text = message, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(32.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state) {
            is DiscoveryState.Found -> {
                Button(onClick = { onUseServer(state.url) }, modifier = Modifier.focusRequester(primaryFocus)) {
                    Text(stringResource(R.string.tv_setup_use_server))
                }
            }

            DiscoveryState.NotFound -> {
                Button(onClick = onSearchAgain, modifier = Modifier.focusRequester(primaryFocus)) {
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
