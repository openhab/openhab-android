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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.config.WearConfigRepository
import org.openhab.habdroid.wear.connection.WearConnectionManager.ConnectionError

@Composable
fun LoadingScreen() {
    ScreenScaffold(
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

/**
 * Simple scrollable screen with a title, a message and buttons.
 */
@Composable
private fun MessageScreen(
    title: String,
    message: String,
    edgeButton: (@Composable () -> Unit)? = null,
    buttons: TransformingLazyColumnScope.() -> Unit = {}
) {
    val listState = rememberTransformingLazyColumnState()
    val content: @Composable (PaddingValues) -> Unit = { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding
        ) {
            item {
                ListHeader {
                    Text(
                        text = title,
                        textAlign = TextAlign.Center
                    )
                }
            }
            item {
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            buttons()
        }
    }
    if (edgeButton != null) {
        ScreenScaffold(
            scrollState = listState,
            edgeButton = { edgeButton() },
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
fun SetupScreen(onOpenSetupOnPhone: () -> Unit, onEnableDemoMode: () -> Unit) {
    var openOnPhoneResult by remember { mutableStateOf<Boolean?>(null) }

    MessageScreen(
        title = stringResource(R.string.setup_title),
        message = stringResource(R.string.setup_message),
        edgeButton = {
            EdgeButton(onClick = onOpenSetupOnPhone) {
                Text(stringResource(R.string.setup_open_on_phone))
            }
        }
    ) {
        item {
            FilledTonalButton(
                onClick = onEnableDemoMode,
                label = { Text(stringResource(R.string.setup_demo)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    OpenOnPhoneResultDialog(
        result = openOnPhoneResult,
        onDismissRequest = { openOnPhoneResult = null }
    )
}

@Composable
private fun OpenOnPhoneResultDialog(result: Boolean?, onDismissRequest: () -> Unit) {
    val continueText = stringResource(R.string.continue_on_phone)
    val failedText = stringResource(R.string.phone_not_reachable)
    val openOnPhoneTextStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    val failureTextStyle = ConfirmationDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = result == true,
        onDismissRequest = onDismissRequest,
        curvedText = {
            openOnPhoneDialogCurvedText(
                text = continueText,
                style = openOnPhoneTextStyle
            )
        }
    )
    FailureConfirmationDialog(
        visible = result == false,
        onDismissRequest = onDismissRequest,
        curvedText = {
            confirmationDialogCurvedText(
                text = failedText,
                style = failureTextStyle
            )
        }
    )
}

@Composable
fun ErrorScreen(error: ConnectionError, url: String?, onRetry: () -> Unit, onGoToSettings: (() -> Unit)?) {
    val message = when (error) {
        ConnectionError.ConnectionFailed -> stringResource(R.string.error_connection_failed, url.orEmpty())
        ConnectionError.CertificateNotTrusted -> stringResource(R.string.error_certificate_not_trusted)
        ConnectionError.AuthenticationFailed -> stringResource(R.string.error_authentication_failed)
        ConnectionError.HttpError -> stringResource(R.string.error_http, url.orEmpty())
    }
    MessageScreen(
        title = stringResource(R.string.error_title),
        message = message,
        edgeButton = {
            EdgeButton(onClick = onRetry) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_24dp),
                    contentDescription = stringResource(R.string.retry)
                )
            }
        }
    ) {
        if (onGoToSettings != null) {
            item {
                FilledTonalButton(
                    onClick = onGoToSettings,
                    label = { Text(stringResource(R.string.settings)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_24dp),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun NoSitemapsScreen(onRetry: () -> Unit) {
    MessageScreen(
        title = stringResource(R.string.error_title),
        message = stringResource(R.string.error_no_sitemaps),
        edgeButton = {
            EdgeButton(onClick = onRetry) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_24dp),
                    contentDescription = stringResource(R.string.retry)
                )
            }
        }
    )
}

@Composable
fun SettingsScreen(
    settingsFlow: StateFlow<WearConfigRepository.Settings>,
    stateFlow: StateFlow<SitemapViewModel.State>,
    onReconnect: () -> Unit,
    onSetDemoMode: (Boolean) -> Unit,
    onOpenSetupOnPhone: suspend () -> Boolean
) {
    val scope = rememberCoroutineScope()
    val state by stateFlow.collectAsStateWithLifecycle()
    val settings by settingsFlow.collectAsStateWithLifecycle()
    var openOnPhoneResult by remember { mutableStateOf<Boolean?>(null) }
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.settings)) }
            }
            item {
                val connection = (state as? SitemapViewModel.State.Ready)?.connection
                val summary = when {
                    connection == null -> stringResource(R.string.settings_not_connected)

                    connection.connectionType == Connection.TYPE_LOCAL ->
                        stringResource(R.string.settings_connected_local)

                    else -> stringResource(R.string.settings_connected_remote)
                }
                Button(
                    onClick = onReconnect,
                    label = { Text(stringResource(R.string.settings_connection)) },
                    secondaryLabel = { Text(summary) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                val isDemo = (settings as? WearConfigRepository.Settings.Configured)
                    ?.isDemo == true
                SwitchButton(
                    checked = isDemo,
                    onCheckedChange = onSetDemoMode,
                    label = { Text(stringResource(R.string.settings_demo_mode)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            openOnPhoneResult = onOpenSetupOnPhone()
                        }
                    },
                    label = { Text(stringResource(R.string.settings_edit_on_phone)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    OpenOnPhoneResultDialog(
        result = openOnPhoneResult,
        onDismissRequest = { openOnPhoneResult = null }
    )
}
