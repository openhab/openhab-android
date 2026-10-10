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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.wear.R

private const val STEPPER_COMMAND_DELAY_MILLIS = 500L

/**
 * Lets the user choose one of the mappings or command options of a selection or switch widget
 */
@Composable
fun SelectionScreen(widget: Widget, onSelect: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val currentState = widget.state?.asString
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(widget.label, textAlign = TextAlign.Center) }
            }
            items(widget.mappingsOrItemOptions) { mapping ->
                RadioButton(
                    selected = mapping.value == currentState,
                    onSelect = { onSelect(mapping.value) },
                    label = { Text(mapping.label) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Changes the value of a setpoint widget. The value can also be changed with the rotary input.
 */
@Composable
fun SetpointScreen(widget: Widget, onCommand: (String) -> Unit) {
    val min = widget.minValue
    val max = widget.maxValue
    val step = widget.step.takeIf { it > 0 } ?: 1f
    val steps = (((max - min) / step).roundToInt() - 1).coerceAtLeast(0)
    val serverValue = widget.state?.asNumber?.value ?: min
    var value by remember(widget.id) { mutableFloatStateOf(serverValue.coerceIn(min, max)) }

    // Wait for further changes to avoid sending a command for every step
    LaunchedEffect(value) {
        if (value != serverValue) {
            delay(STEPPER_COMMAND_DELAY_MILLIS)
            widget.numberCommand(value)?.let(onCommand)
        }
    }

    ScreenScaffold {
        Stepper(
            value = value,
            onValueChange = { newValue -> value = newValue },
            steps = steps,
            valueRange = min..max,
            decreaseIcon = {
                Icon(painterResource(R.drawable.ic_remove_24dp), contentDescription = stringResource(R.string.decrease))
            },
            increaseIcon = {
                Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.increase))
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(widget.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    Text(
                        text = widget.state?.asNumber.withValue(value).toString(),
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private val presetColors = listOf(
    R.string.color_red to 0f,
    R.string.color_orange to 30f,
    R.string.color_yellow to 60f,
    R.string.color_green to 120f,
    R.string.color_cyan to 180f,
    R.string.color_blue to 240f,
    R.string.color_purple to 280f,
    R.string.color_pink to 320f
)

/**
 * Simplified color picker: Switch, brightness and some preset colors
 */
@Composable
fun ColorScreen(widget: Widget, onCommand: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val serverBrightness = widget.state?.asBrightness ?: 0
    var brightness by remember(widget.id) { mutableFloatStateOf(serverBrightness.toFloat()) }

    LaunchedEffect(brightness) {
        if (brightness.roundToInt() != serverBrightness) {
            delay(STEPPER_COMMAND_DELAY_MILLIS)
            onCommand(brightness.roundToInt().toString())
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(widget.label, textAlign = TextAlign.Center) }
            }
            item {
                SwitchButton(
                    checked = serverBrightness > 0,
                    onCheckedChange = { checked -> onCommand(if (checked) "ON" else "OFF") },
                    label = { Text(stringResource(R.string.color_power)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Slider(
                    value = brightness,
                    onValueChange = { newValue -> brightness = newValue },
                    steps = 9,
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(presetColors) { (labelRes, hue) ->
                val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                Button(
                    onClick = {
                        // Keep the brightness, but make sure the light is visible
                        val value = brightness.roundToInt().takeIf { it > 0 } ?: 100
                        onCommand("${hue.roundToInt()},100,$value")
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(color)) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = { onCommand("0,0,${brightness.roundToInt().takeIf { it > 0 } ?: 100}") },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White)) },
                    label = { Text(stringResource(R.string.color_white)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
