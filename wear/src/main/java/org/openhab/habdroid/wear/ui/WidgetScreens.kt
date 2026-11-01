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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SliderDefaults
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.toItemCommand
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.wear.R

/**
 * Lets the user choose one of the mappings or command options of a selection or switch widget
 */
@Composable
fun SelectionScreen(widget: Widget, onSelect: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val currentState = widget.state?.asString
    ScreenScaffold(
        scrollState = listState
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding
        ) {
            item {
                WidgetScreenHeader(
                    widget = widget,
                    transformationSpec = transformationSpec,
                    showState = false
                )
            }
            items(widget.mappingsOrItemOptions) { mapping ->
                RadioButton(
                    selected = mapping.value == currentState,
                    onSelect = { onSelect(mapping.value) },
                    label = { Text(mapping.label) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
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
    val numberState = widget.state?.asNumber
    val serverValue = numberState?.value ?: min
    var value by remember(widget.id) {
        mutableFloatStateOf(serverValue.coerceIn(min, max))
    }

    // Wait for further changes to avoid sending a command for every step
    LaunchedEffect(value) {
        if (value != serverValue) {
            delay(500.milliseconds)
            numberState.withValue(value).toItemCommand(widget.item)?.let(onCommand)
        }
    }

    ScreenScaffold {
        Stepper(
            value = value,
            onValueChange = { newValue -> value = newValue },
            steps = steps,
            valueRange = min..max,
            decreaseIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_remove_24dp),
                    contentDescription = stringResource(R.string.decrease)
                )
            },
            increaseIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.increase)
                )
            }
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = widget.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (value == serverValue && widget.stateFromLabel != null) {
                            widget.stateFromLabel.orEmpty()
                        } else {
                            widget.formatValue(value, step, widget.state?.asNumber?.unit)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private val PresetColors = listOf(
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
    val transformationSpec = rememberTransformationSpec()

    val serverBrightness = widget.state?.asBrightness ?: 0
    var brightness by remember(widget.id) {
        mutableFloatStateOf(serverBrightness.toFloat())
    }

    LaunchedEffect(brightness) {
        if (brightness.roundToInt() != serverBrightness) {
            delay(500.milliseconds)
            onCommand(brightness.roundToInt().toString())
        }
    }

    ScreenScaffold(
        scrollState = listState
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding
        ) {
            item {
                WidgetScreenHeader(
                    widget = widget,
                    transformationSpec = transformationSpec,
                    showState = false
                )
            }
            item {
                SwitchButton(
                    checked = serverBrightness > 0,
                    onCheckedChange = { checked -> onCommand(if (checked) "ON" else "OFF") },
                    label = { Text(stringResource(R.string.color_power)) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
            item {
                val colors = SliderDefaults.sliderColors()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.containerColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.color_brightness),
                            modifier = Modifier.weight(1f)
                        )
                        Text("${brightness.roundToInt()} %")
                    }
                    Slider(
                        value = brightness,
                        onValueChange = { newValue -> brightness = newValue },
                        steps = 9,
                        valueRange = 0f..100f,
                        colors = colors.copy(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            items(PresetColors) { (labelRes, hue) ->
                val color = Color.hsv(hue, 1f, 1f)
                Button(
                    onClick = {
                        // Keep the brightness, but make sure the light is visible
                        val value = brightness.roundToInt().takeIf { it > 0 } ?: 100
                        onCommand("${hue.roundToInt()},100,$value")
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    },
                    label = { Text(stringResource(labelRes)) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
            item {
                Button(
                    onClick = {
                        val brightness = brightness.roundToInt().takeIf { it > 0 } ?: 100
                        onCommand("0,0,$brightness")
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    },
                    label = { Text(stringResource(R.string.color_white)) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
        }
    }
}

@Serializable
sealed interface ButtonInput {
    val command: String
    val iconResId: Int?

    @Composable
    fun label(): String

    @Serializable
    data class IconButton(override val command: String, override val iconResId: Int, val descriptionResId: Int) :
        ButtonInput {
        @Composable
        override fun label() = stringResource(descriptionResId)
    }

    @Serializable
    data class Button(override val command: String, val label: String) : ButtonInput {
        override val iconResId get() = null

        @Composable
        override fun label() = label
    }
}

@Composable
fun IconButtonListScreen(widget: Widget, buttons: List<ButtonInput>, onCommand: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding
        ) {
            item {
                WidgetScreenHeader(
                    widget = widget,
                    transformationSpec = transformationSpec
                )
            }
            items(buttons) { button ->
                ButtonRow(
                    label = button.label(),
                    iconResId = button.iconResId,
                    onClick = { onCommand(button.command) },
                    transformationSpec = transformationSpec
                )
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.WidgetScreenHeader(
    widget: Widget,
    showState: Boolean = true,
    transformationSpec: TransformationSpec
) {
    ListHeader(
        modifier = Modifier.transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = widget.label,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            widget.stateFromLabel
                ?.takeIf { it.isNotEmpty() && showState }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.ButtonRow(
    label: String,
    iconResId: Int?,
    onClick: () -> Unit,
    transformationSpec: TransformationSpec
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
        icon = iconResId?.let {
            {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = label
                )
            }
        },
        label = {
            Text(label)
        },
        colors = ButtonDefaults.filledTonalButtonColors(),
        contentPadding = CompactButtonDefaults.ContentPadding
    )
}
