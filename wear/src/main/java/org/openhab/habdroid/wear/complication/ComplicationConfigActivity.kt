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

package org.openhab.habdroid.wear.complication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import kotlinx.coroutines.launch
import org.json.JSONException
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.ui.WearTheme
import org.openhab.habdroid.wear.util.loadItemNames
import org.openhab.habdroid.wear.wearApplication

/**
 * Lets the user choose the item of a complication. If a group is set on the phone, only its members are offered.
 */
class ComplicationConfigActivity : ComponentActivity() {
    private sealed interface State {
        data object Loading : State

        data class Items(val items: List<Pair<String, String>>) : State

        data object Failed : State
    }

    private val state = mutableStateOf<State>(State.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val complicationId = intent.getIntExtra(ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID, -1)
        setResult(RESULT_CANCELED)

        setContent {
            WearTheme {
                AppScaffold {
                    Content(state.value) { itemName ->
                        ComplicationConfigStore(this@ComplicationConfigActivity).setItemName(complicationId, itemName)
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        }

        lifecycleScope.launch {
            val connected = wearApplication.connectionManager.awaitConnection()
            state.value = try {
                connected?.connection
                    ?.loadItemNames(connected.config.complicationGroup)
                    ?.let { items -> State.Items(items) }
                    ?: State.Failed
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Loading items failed", e)
                State.Failed
            } catch (e: JSONException) {
                Log.w(TAG, "Parsing items failed", e)
                State.Failed
            }
        }
    }

    @Composable
    private fun Content(current: State, onSelect: (String) -> Unit) {
        val listState = rememberTransformingLazyColumnState()
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                item {
                    ListHeader { Text(stringResource(R.string.complication_choose_item), textAlign = TextAlign.Center) }
                }
                when (current) {
                    is State.Loading -> item { CircularProgressIndicator() }

                    is State.Failed -> item {
                        Text(
                            text = stringResource(R.string.tile_not_connected),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    is State.Items -> items(current.items) { (name, label) ->
                        Button(
                            onClick = { onSelect(name) },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            secondaryLabel = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = ComplicationConfigActivity::class.java.simpleName
    }
}
