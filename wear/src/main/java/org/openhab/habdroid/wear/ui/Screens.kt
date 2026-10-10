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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
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
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.wear.BuildConfig
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.config.WearConfigRepository
import org.openhab.habdroid.wear.connection.WearConnectionManager
import org.openhab.habdroid.wear.connection.WearConnectionManager.ConnectionError
import org.openhab.habdroid.wear.wearApplication

/**
 * Deep link handled by the phone app to open the settings of the primary server
 */
private const val PHONE_SETUP_URI = "openhab://wear-setup"

@Composable
fun LoadingScreen() {
    ScreenScaffold(timeText = { TimeText() }) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(title, textAlign = TextAlign.Center) }
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
        ScreenScaffold(scrollState = listState, edgeButton = { edgeButton() }) { padding -> content(padding) }
    } else {
        ScreenScaffold(scrollState = listState) { padding -> content(padding) }
    }
}

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showOpenOnPhone by remember { mutableStateOf(false) }
    val continueText = stringResource(R.string.continue_on_phone)
    val curvedTextStyle = OpenOnPhoneDialogDefaults.curvedTextStyle

    MessageScreen(
        title = stringResource(R.string.setup_title),
        message = stringResource(R.string.setup_message),
        edgeButton = {
            EdgeButton(onClick = {
                scope.launch {
                    showOpenOnPhone = context.openSetupOnPhone()
                }
            }) {
                Text(stringResource(R.string.setup_open_on_phone))
            }
        }
    ) {
        item {
            FilledTonalButton(
                onClick = { context.wearApplication.configRepository.setDemoMode(true) },
                label = { Text(stringResource(R.string.setup_demo)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    OpenOnPhoneDialog(
        visible = showOpenOnPhone,
        onDismissRequest = { showOpenOnPhone = false },
        curvedText = { openOnPhoneDialogCurvedText(text = continueText, style = curvedTextStyle) }
    )
}

/**
 * Opens the server settings of the phone app, or the app in the Play Store if it isn't installed.
 *
 * @return true if the phone has been asked to open the page
 */
private suspend fun Context.openSetupOnPhone(): Boolean {
    val helper = RemoteActivityHelper(this)
    val setupIntent = Intent(Intent.ACTION_VIEW, Uri.parse(PHONE_SETUP_URI)).addCategory(Intent.CATEGORY_BROWSABLE)
    val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    return try {
        helper.startRemoteActivity(setupIntent).await()
        true
    } catch (e: Exception) {
        Log.d("SetupScreen", "Unable to open setup on phone, try to open the Play Store", e)
        try {
            helper.startRemoteActivity(storeIntent).await()
            true
        } catch (e: Exception) {
            Log.w("SetupScreen", "Unable to open Play Store on phone", e)
            false
        }
    }
}

@Composable
fun ErrorScreen(state: SitemapViewModel.State.Error, onRetry: () -> Unit, navController: NavHostController?) {
    val message = when (state.error) {
        ConnectionError.ConnectionFailed -> stringResource(R.string.error_connection_failed, state.url.orEmpty())
        ConnectionError.CertificateNotTrusted -> stringResource(R.string.error_certificate_not_trusted)
        ConnectionError.AuthenticationFailed -> stringResource(R.string.error_authentication_failed)
        ConnectionError.HttpError -> stringResource(R.string.error_http, state.url.orEmpty())
    }
    MessageScreen(
        title = stringResource(R.string.error_title),
        message = message,
        edgeButton = {
            EdgeButton(onClick = onRetry) {
                Icon(painterResource(R.drawable.ic_refresh_24dp), contentDescription = stringResource(R.string.retry))
            }
        }
    ) {
        if (navController != null) {
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(Routes.SETTINGS) },
                    label = { Text(stringResource(R.string.settings)) },
                    icon = { Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null) },
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
                Icon(painterResource(R.drawable.ic_refresh_24dp), contentDescription = stringResource(R.string.retry))
            }
        }
    )
}

@Composable
fun SettingsScreen(viewModel: SitemapViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = context.wearApplication.configRepository
    val settings by repository.settings.collectAsStateWithLifecycle()
    val connectionState by context.wearApplication.connectionManager.state.collectAsStateWithLifecycle()
    var showOpenOnPhone by remember { mutableStateOf(false) }
    val continueText = stringResource(R.string.continue_on_phone)
    val curvedTextStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.settings)) }
            }
            item {
                val connection = (connectionState as? WearConnectionManager.State.Connected)
                    ?.connection
                val summary = when {
                    connection == null -> stringResource(R.string.settings_not_connected)

                    connection.connectionType == Connection.TYPE_LOCAL ->
                        stringResource(R.string.settings_connected_local)

                    else -> stringResource(R.string.settings_connected_remote)
                }
                Button(
                    onClick = { viewModel.retry() },
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
                    onCheckedChange = { checked ->
                        repository.setDemoMode(checked)
                        if (!checked) {
                            scope.launch { repository.syncFromDataLayer() }
                        }
                    },
                    label = { Text(stringResource(R.string.settings_demo_mode)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                FilledTonalButton(
                    onClick = { scope.launch { showOpenOnPhone = context.openSetupOnPhone() } },
                    label = { Text(stringResource(R.string.settings_edit_on_phone)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    OpenOnPhoneDialog(
        visible = showOpenOnPhone,
        onDismissRequest = { showOpenOnPhone = false },
        curvedText = { openOnPhoneDialogCurvedText(text = continueText, style = curvedTextStyle) }
    )
}
