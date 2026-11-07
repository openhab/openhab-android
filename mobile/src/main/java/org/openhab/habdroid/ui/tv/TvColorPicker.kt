/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.openhab.habdroid.R
import org.openhab.habdroid.model.HsvState

/**
 * Dialog to change a color with three sliders for hue, saturation and brightness, which are changed with left and
 * right. [onColorChanged] is called with the new color as openHAB command once the user stops changing it.
 */
@Composable
fun TvColorDialog(title: String, initialColor: HsvState?, onColorChanged: (String) -> Unit, onDismiss: () -> Unit) {
    var hue by remember { mutableFloatStateOf(initialColor?.hue ?: 0f) }
    var saturation by remember { mutableFloatStateOf((initialColor?.saturation ?: 1f) * 100) }
    var brightness by remember { mutableFloatStateOf((initialColor?.value ?: 1f) * 100) }
    var changeCount by remember { mutableIntStateOf(0) }
    val initialFocus = remember { FocusRequester() }

    LaunchedEffect(changeCount) {
        if (changeCount == 0) {
            return@LaunchedEffect
        }
        delay(COLOR_SEND_DELAY)
        onColorChanged("${hue.roundToInt()},${saturation.roundToInt()},${brightness.roundToInt()}")
    }

    fun hsvColor(h: Float, s: Float, v: Float) = Color.hsv(h.coerceIn(0f, 360f), s / 100, v / 100)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(560.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(hsvColor(hue, saturation, brightness))
                            .border(2.dp, LocalContentColor.current.copy(alpha = 0.5f), CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                ColorSlider(
                    label = stringResource(R.string.tv_color_hue),
                    value = hue,
                    maxValue = 360f,
                    step = HUE_STEP,
                    displayValue = "${hue.roundToInt()}°",
                    gradient = (0..6).map { index -> hsvColor(index * 60f, 100f, 100f) },
                    modifier = Modifier.focusRequester(initialFocus),
                    onValueChange = { newValue ->
                        hue = newValue
                        changeCount++
                    }
                )
                ColorSlider(
                    label = stringResource(R.string.tv_color_saturation),
                    value = saturation,
                    maxValue = 100f,
                    step = PERCENT_STEP,
                    displayValue = "${saturation.roundToInt()} %",
                    gradient = listOf(hsvColor(hue, 0f, 100f), hsvColor(hue, 100f, 100f)),
                    onValueChange = { newValue ->
                        saturation = newValue
                        changeCount++
                    }
                )
                ColorSlider(
                    label = stringResource(R.string.tv_color_brightness),
                    value = brightness,
                    maxValue = 100f,
                    step = PERCENT_STEP,
                    displayValue = "${brightness.roundToInt()} %",
                    gradient = listOf(Color.Black, hsvColor(hue, saturation, 100f)),
                    onValueChange = { newValue ->
                        brightness = newValue
                        changeCount++
                    }
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        initialFocus.requestFocusWhenAttached()
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    maxValue: Float,
    step: Float,
    displayValue: String,
    gradient: List<Color>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        selected = false,
        onClick = {},
        headlineContent = {
            Column {
                Row {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ColorTrack(
                    fraction = value / maxValue,
                    gradient = gradient
                )
            }
        },
        modifier = modifier.onKeyEvent { event ->
            val delta = when (event.key) {
                Key.DirectionLeft -> -step
                Key.DirectionRight -> step
                else -> return@onKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown) {
                onValueChange((value + delta).coerceIn(0f, maxValue))
            }
            true
        }
    )
}

/**
 * Track with a color gradient and a marker at the current value
 */
@Composable
private fun ColorTrack(fraction: Float, gradient: List<Color>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(gradient))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (fraction > 0f) {
                Spacer(modifier = Modifier.weight(fraction))
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(12.dp)
                    .background(Color.White)
                    .border(1.dp, Color.Black)
            )
            if (fraction < 1f) {
                Spacer(modifier = Modifier.weight(1f - fraction))
            }
        }
    }
}

private val COLOR_SEND_DELAY = 500.milliseconds
private const val HUE_STEP = 10f
private const val PERCENT_STEP = 5f
