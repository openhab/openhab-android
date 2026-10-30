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

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.toColorTemperatureInKelvin
import org.openhab.habdroid.model.toItemCommand
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.util.asColorTemperatureToKelvin
import org.openhab.habdroid.util.beautify
import org.openhab.habdroid.util.getChartScalingFactor
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.shouldRequestHighResChart
import org.openhab.habdroid.wear.R

data class WidgetScope(
    val connection: Connection,
    val serverFlags: Int,
    val onOpenPage: (url: String) -> Unit,
    val onOpenSelection: (widget: Widget) -> Unit,
    val onOpenSetpoint: (widget: Widget) -> Unit,
    val onOpenButtons: (widget: Widget, buttons: List<ButtonInput>) -> Unit,
    val onOpenColor: (widget: Widget) -> Unit,
    val onSendCommand: (item: Item?, command: String) -> Unit
)

/**
 * A row of the page. Buttons of a button grid are part of the grid's row.
 */
data class WidgetRowData(val widget: Widget, val children: List<Widget> = emptyList())

/**
 * Text of a small button. The text is shrunk to avoid breaking words.
 */
@Composable
private fun ButtonText(text: String) {
    BasicText(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            color = LocalContentColor.current,
            textAlign = TextAlign.Center
        ),
        // A single line, as words might be broken otherwise
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 7.sp,
            maxFontSize = MaterialTheme.typography.labelMedium.fontSize
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private val UNSUPPORTED_TYPES = setOf(Widget.Type.Video, Widget.Type.Webview)

private const val SLIDER_COMMAND_DELAY_MILLIS = 300L
private const val MAX_SLIDER_SEGMENTS = 10
private const val MIN_COLOR_TEMPERATURE = 1000f

/**
 * Width of a typical phone screen, which charts are laid out for
 */
private const val CHART_REFERENCE_WIDTH_DP = 400

private val DEFAULT_COLOR_TEMPERATURE_RANGE = 2000f..6500f

fun buildWidgetRows(widgets: List<Widget>): List<WidgetRowData> {
    val visibleWidgets = widgets.filter { widget -> widget.visibility }
    val buttonGridIds = visibleWidgets
        .filter { widget -> widget.type == Widget.Type.Buttongrid }
        .map { widget -> widget.id }
        .toSet()
    val shownWidgets = visibleWidgets
        .filterNot { widget -> widget.type == Widget.Type.Button && widget.parentId in buttonGridIds }
        // Videos and web pages can't be shown, so they would be empty rows without a label
        .filterNot { widget -> widget.type in UNSUPPORTED_TYPES && widget.label.isEmpty() }
    val parentIds = shownWidgets.mapNotNull { widget -> widget.parentId }.toSet()
    return shownWidgets
        // Hide frames without widgets, e.g. if they only contain a video
        .filterNot { widget -> widget.type == Widget.Type.Frame && widget.id !in parentIds }
        .map { widget ->
            val children = if (widget.type == Widget.Type.Buttongrid) {
                visibleWidgets.filter { child -> child.parentId == widget.id && child.type == Widget.Type.Button }
            } else {
                emptyList()
            }
            WidgetRowData(widget, children)
        }
}

@Composable
fun WidgetScope.WidgetRow(row: WidgetRowData, modifier: Modifier, transformation: SurfaceTransformation) {
    val locale = LocalLocale.current
    val widget = row.widget
    val item = widget.item

    when (widget.type) {
        Widget.Type.Frame -> FrameHeader(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Switch -> when {
            widget.isPlayer() -> PlayerRow(
                widget = widget,
                modifier = modifier,
                transformation = transformation
            )

            widget.mappingsOrItemOptions.isNotEmpty() -> MappingsRow(
                widget = widget,
                mappings = widget.mappingsOrItemOptions,
                modifier = modifier,
                transformation = transformation
            )

            item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true -> RollershutterRow(
                widget = widget,
                modifier = modifier,
                transformation = transformation
            )

            else -> SwitchRow(
                widget = widget,
                modifier = modifier,
                transformation = transformation
            )
        }

        Widget.Type.Default if item?.isOfTypeOrGroupType(Item.Type.Player) == true -> PlayerRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Button -> ButtonRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Buttongrid -> ButtonGridRow(
            widget = widget,
            buttons = row.children,
            modifier = modifier
        )

        Widget.Type.Selection -> SelectionRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Setpoint -> ValueButton(
            widget = widget,
            modifier = modifier,
            transformation = transformation,
            onClick = { onOpenSetpoint(widget) }
        )

        Widget.Type.Slider -> SliderRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Colortemperaturepicker -> ColorTemperaturePickerRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Colorpicker -> ColorRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Image -> ImageRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Chart -> ChartRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )

        Widget.Type.Mapview -> TextRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation,
            state = widget.state?.asLocation?.let {
                String.format(locale.platformLocale, "%.5f, %.5f", it.latitude, it.longitude)
            }
        )

        // Group, Text, Default and widgets that can't be used on a watch, e.g. Webview or Mapview
        else -> TextRow(
            widget = widget,
            modifier = modifier,
            transformation = transformation
        )
    }
}

@Composable
private fun FrameHeader(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    if (widget.label.isNotEmpty()) {
        ListHeader(
            modifier = modifier,
            transformation = transformation
        ) {
            Text(
                text = widget.label,
                color = mapColor(widget.labelColor) ?: Color.Unspecified
            )
        }
    }
}

@Composable
private fun WidgetLabel(widget: Widget, maxLines: Int = 2) {
    Text(
        text = widget.label,
        color = mapColor(widget.labelColor) ?: Color.Unspecified,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun WidgetState(
    widget: Widget,
    modifier: Modifier = Modifier,
    state: String? = widget.stateFromLabel
) {
    state?.takeIf { it.isNotEmpty() }?.let {
        Text(
            text = it,
            color = mapColor(widget.valueColor) ?: Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

@Composable
private fun WidgetScope.TextRow(
    widget: Widget,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    state: String? = widget.stateFromLabel,
    onClick: (() -> Unit)? = widget.linkedPage?.let { page -> { onOpenPage(page.link) } }
) {
    val colors = if (onClick != null) {
        ButtonDefaults.filledTonalButtonColors()
    } else {
        ButtonDefaults.childButtonColors()
    }

    Button(
        onClick = onClick ?: {},
        modifier = modifier,
        colors = colors,
        transformation = transformation,
        icon = { WidgetIcon(widget.icon) },
        secondaryLabel = state
            ?.takeIf { it.isNotEmpty() }
            ?.let { { WidgetState(widget, state = state) } },
        label = { WidgetLabel(widget) }
    )
}

@Composable
private fun WidgetScope.SwitchRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    val isOn = widget.state?.asBoolean == true
    SwitchButton(
        checked = isOn,
        onCheckedChange = { checked -> onSendCommand(widget.item, if (checked) "ON" else "OFF") },
        modifier = modifier,
        transformation = transformation,
        icon = { WidgetIcon(widget.icon) },
        secondaryLabel = widget.stateFromLabel
            ?.takeIf { it.isNotEmpty() }
            ?.let { { WidgetState(widget) } },
        label = { WidgetLabel(widget) }
    )
}

@Composable
private fun WidgetScope.ButtonRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    val command = widget.command
        ?: return TextRow(widget, modifier, transformation)
    val isActive = widget.stateless == false && widget.item?.state?.asString == command
    Button(
        onClick = { onSendCommand(widget.item, command) },
        modifier = modifier,
        colors = if (isActive) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        transformation = transformation,
        icon = { WidgetIcon(widget.icon) },
        label = { WidgetLabel(widget) }
    )
}

/**
 * Label with a row of buttons below
 */
@Composable
private fun LabeledButtonGroup(widget: Widget, modifier: Modifier, buttons: @Composable () -> Unit) {
    Column(
        modifier = modifier
    ) {
        if (widget.label.isNotEmpty()) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                WidgetLabel(
                    widget = widget,
                    maxLines = 1
                )
            }
        }
        buttons()
    }
}

@Composable
private fun WidgetScope.MappingsRow(
    widget: Widget,
    mappings: List<LabeledValue>,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    val commands = mappings.map {
        ButtonInput.Button(it.value, it.label)
    }
    TextRow(
        widget = widget,
        modifier = modifier,
        transformation = transformation,
        onClick = { onOpenButtons(widget, commands) }
    )
}

@Composable
private fun WidgetScope.RollershutterRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    val commands = listOf(
        ButtonInput.IconButton("UP", R.drawable.ic_arrow_up_24dp, R.string.rollershutter_up),
        ButtonInput.IconButton("STOP", R.drawable.ic_stop_24dp, R.string.rollershutter_stop),
        ButtonInput.IconButton("DOWN", R.drawable.ic_arrow_down_24dp, R.string.rollershutter_down)
    )
    TextRow(
        widget = widget,
        modifier = modifier,
        transformation = transformation,
        onClick = { onOpenButtons(widget, commands) }
    )
}

/**
 * Player items are sent as switches with the player commands as mappings
 */
private fun Widget.isPlayer() = item?.isOfTypeOrGroupType(Item.Type.Player) == true &&
    (mappings.isEmpty() || mappings.map { mapping -> mapping.value } == listOf("PREVIOUS", "PAUSE", "PLAY", "NEXT"))

@Composable
private fun WidgetScope.PlayerRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    val isPlaying = widget.item?.state?.asString == "PLAY"
    val commands = listOf(
        ButtonInput.IconButton("PREVIOUS", R.drawable.ic_skip_previous_24dp, R.string.player_previous),
        if (isPlaying) {
            ButtonInput.IconButton("PAUSE", R.drawable.ic_pause_24dp, R.string.player_pause)
        } else {
            ButtonInput.IconButton("PLAY", R.drawable.ic_play_24dp, R.string.player_play)
        },
        ButtonInput.IconButton("NEXT", R.drawable.ic_skip_next_24dp, R.string.player_next)
    )
    TextRow(
        widget = widget,
        modifier = modifier,
        transformation = transformation,
        onClick = { onOpenButtons(widget, commands) }
    )
}

@Composable
private fun WidgetScope.ButtonGridRow(widget: Widget, buttons: List<Widget>, modifier: Modifier) {
    // Button grids either have Button child widgets (openHAB 4.2+) or mappings with row and column
    data class GridButton(
        val row: Int,
        val column: Int,
        val label: String,
        val icon: IconResource?,
        val command: String,
        val item: Item?
    )

    val entries = buttons.mapNotNull { button ->
        val command = button.command ?: return@mapNotNull null
        GridButton(button.row ?: 0, button.column ?: 0, button.label, button.icon, command, button.item ?: widget.item)
    } + widget.mappings.map { mapping ->
        GridButton(mapping.row, mapping.column, mapping.label, mapping.icon, mapping.value, widget.item)
    }
    val columns = entries.map { entry -> entry.column }
    val columnRange = (columns.minOrNull() ?: 0)..(columns.maxOrNull() ?: 0)

    LabeledButtonGroup(
        widget = widget,
        modifier = modifier
    ) {
        Column {
            val rows = entries
                .groupBy { entry -> entry.row }
                .toSortedMap()
                .values
            rows.forEach { rowEntries ->
                ButtonGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    // Keep the buttons in their columns, e.g. for arrow keys of a remote control
                    columnRange.forEach { column ->
                        val entry = rowEntries.firstOrNull { entry -> entry.column == column }
                        if (entry == null) {
                            Box(modifier = Modifier.weight(1f))
                        } else {
                            FilledTonalButton(
                                onClick = { onSendCommand(entry.item, entry.command) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                if (entry.icon != null) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        WidgetIcon(entry.icon)
                                    }
                                } else {
                                    ButtonText(entry.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetScope.SelectionRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    ValueButton(
        widget = widget,
        modifier = modifier,
        transformation = transformation,
        onClick = { onOpenSelection(widget) }
    )
}

/**
 * Button with label and state, that opens a screen to change the value
 */
@Composable
private fun WidgetScope.ValueButton(
    widget: Widget,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit
) {
    val state = widget.stateFromLabel ?: widget.state?.asNumber?.toString()
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        transformation = transformation,
        icon = { WidgetIcon(widget.icon) },
        secondaryLabel = state
            ?.takeIf { it.isNotEmpty() }
            ?.let { { WidgetState(widget, state = state) } },
        label = { WidgetLabel(widget) }
    )
}

@Composable
private fun WidgetScope.ColorTemperaturePickerRow(
    widget: Widget,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    val numberState = widget.state?.asNumber?.toColorTemperatureInKelvin()
    SliderRow(
        widget = widget,
        currentState = numberState?.value,
        range = widget.colorTemperatureRange(),
        itemStep = 100f,
        unit = numberState?.unit,
        commandConverter = { numberState?.withValue(it)?.toItemCommand(widget.item) },
        modifier = modifier,
        transformation = transformation
    )
}

@Composable
private fun WidgetScope.SliderRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    val numberState = widget.state?.asNumber
    SliderRow(
        widget = widget,
        currentState = numberState?.value ?: widget.state?.asBrightness?.toFloat(),
        range = widget.minValue..widget.maxValue,
        itemStep = widget.step.takeIf { it > 0 } ?: 1f,
        unit = numberState?.unit,
        commandConverter = { widget.numberCommand(it) },
        modifier = modifier,
        transformation = transformation
    )
}

@Composable
private fun WidgetScope.SliderRow(
    widget: Widget,
    currentState: Float?,
    range: ClosedFloatingPointRange<Float>,
    itemStep: Float,
    unit: String?,
    commandConverter: (Float) -> String?,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    // The slider can only be changed with buttons, so combine small steps to limit the number of taps
    val units = (range.endInclusive - range.start) / itemStep
    val step = if (units > MAX_SLIDER_SEGMENTS) itemStep * ceil(units / MAX_SLIDER_SEGMENTS) else itemStep
    val steps = (((range.endInclusive - range.start) / step).roundToInt() - 1)
        .coerceAtLeast(0)
    val serverValue = (currentState ?: range.start)
        .coerceIn(range.start, range.endInclusive)
    var value by remember(widget.id, serverValue) {
        mutableFloatStateOf(serverValue)
    }

    // Wait for further changes to avoid sending a command for every step
    LaunchedEffect(value) {
        if (value != serverValue) {
            delay(SLIDER_COMMAND_DELAY_MILLIS)
            val command = commandConverter(value)
            command?.let { onSendCommand(widget.item, it) }
        }
    }

    val state = if (value == serverValue && widget.stateFromLabel != null) {
        widget.stateFromLabel
    } else {
        widget.formatValue(value, step, unit)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier,
        transformation = transformation
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleSmall) {
            WidgetLabel(widget, maxLines = 1)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        ) {
            Slider(
                value = value,
                onValueChange = { newValue -> value = newValue },
                steps = steps,
                valueRange = range,
                modifier = Modifier.fillMaxWidth()
            )
            WidgetState(
                widget = widget,
                state = state,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * @return The range of the color temperature in Kelvin. Falls back to the range of common lamps.
 */
private fun Widget.colorTemperatureRange(): ClosedFloatingPointRange<Float> {
    val first = minValue.asColorTemperatureToKelvin()
    val second = maxValue.asColorTemperatureToKelvin()
    val min = minOf(first, second)
    val max = maxOf(first, second)
    return if (min.isFinite() && max.isFinite() && min >= MIN_COLOR_TEMPERATURE && max > min) {
        min..max
    } else {
        DEFAULT_COLOR_TEMPERATURE_RANGE
    }
}

/**
 * Formats a value with the precision of the step, as the format of the item might round it
 */
fun Widget.formatValue(value: Float, step: Float, unit: String?): String {
    val decimals = step.toBigDecimal().stripTrailingZeros().scale().coerceAtLeast(0)
    val number = String.format(Locale.getDefault(), "%.${decimals}f", value)
    val actualUnit = unit ?: if (item?.isOfTypeOrGroupType(Item.Type.Dimmer) == true ||
        item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true
    ) {
        "%"
    } else {
        null
    }
    return if (actualUnit.isNullOrEmpty()) number else "$number $actualUnit"
}

fun Widget.numberCommand(value: Float): String? = if (item?.isOfTypeOrGroupType(Item.Type.Color) == true) {
    value.beautify()
} else {
    state?.asNumber.withValue(value).toItemCommand(item)
}

@Composable
private fun WidgetScope.ColorRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    val color = widget.state?.asHsv?.let { hsv -> Color.hsv(hsv.hue, hsv.saturation, hsv.value) }
    FilledTonalButton(
        onClick = { onOpenColor(widget) },
        modifier = modifier,
        transformation = transformation,
        icon = {
            if (color != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_palette_24dp),
                    contentDescription = null
                )
            }
        },
        label = {
            WidgetLabel(widget)
        }
    )
}

@Composable
private fun WidgetScope.ImageRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    // Image items contain the image as data URL in their state
    val dataUrl = widget.item?.state?.asString?.takeIf { state -> state.matches(DATA_URL_REGEX) }
    val dataBitmap = remember(dataUrl) {
        dataUrl?.decodeDataUrl()
    }
    val url = widget.url ?: widget.item?.link

    when {
        dataBitmap != null ->
            Card(
                modifier = modifier,
                transformation = transformation,
                content = {
                    Image(
                        bitmap = dataBitmap,
                        contentDescription = widget.label,
                        contentScale = ContentScale.FillWidth
                    )
                }
            )

        url != null -> {
            val widthPixels = with(LocalDensity.current) {
                LocalConfiguration.current.screenWidthDp.dp.roundToPx()
            }
            Card(
                modifier = modifier,
                transformation = transformation,
                content = {
                    RemoteImage(
                        url = url,
                        connection = connection,
                        widthPixels = widthPixels,
                        refreshMillis = widget.refresh,
                        contentDescription = widget.label,
                    )
                }
            )
        }
    }
}

private val DATA_URL_REGEX = "data:image/.*;base64,.*".toRegex()

private fun String.decodeDataUrl(): ImageBitmap? = try {
    val bytes = Base64.decode(substringAfter(","), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: IllegalArgumentException) {
    null
}

@Composable
private fun WidgetScope.ChartRow(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val widthPixels = with(density) { screenWidthDp.dp.roundToPx() }
    val theme = if (serverFlags and ServerProperties.SERVER_FLAG_TRANSPARENT_CHARTS != 0) {
        "black_transparent"
    } else {
        "black"
    }
    val prefs = context.getPrefs()
    val url = widget.toChartUrl(
        scalingFactor = prefs.getChartScalingFactor(),
        requestHighResCharts = prefs.shouldRequestHighResChart(),
        width = widthPixels,
        chartTheme = theme,
        // Lower the density, so that the chart is laid out like on a phone screen instead of using large fonts
        density = context.resources.displayMetrics.densityDpi * screenWidthDp / CHART_REFERENCE_WIDTH_DP,
        forcedLegend = false
    )

    if (url != null) {
        TitleCard(
            title = {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleSmall) {
                    WidgetLabel(
                        widget = widget,
                        maxLines = 1
                    )
                }
            },
            content = {
                RemoteImage(
                    url = url,
                    connection = connection,
                    widthPixels = widthPixels,
                    refreshMillis = widget.refresh,
                    contentDescription = widget.label
                )
            },
            modifier = modifier,
            colors = CardDefaults.cardColors(),
            transformation = transformation
        )
    }
}

private val namedColors = mapOf(
    "maroon" to Color(0xFFB23939),
    "red" to Color(0xFFFF0000),
    "orange" to Color(0xFFFFA500),
    "olive" to Color(0xFF808000),
    "yellow" to Color(0xFFFFFF00),
    "purple" to Color(0xFFCE93D8),
    "fuchsia" to Color(0xFFFF00FF),
    "pink" to Color(0xFFFFC0CB),
    "white" to Color(0xFFFFFFFF),
    "lime" to Color(0xFF00FF00),
    "green" to Color(0xFF4CAF50),
    "navy" to Color(0xFF3F51B5),
    "blue" to Color(0xFF2196F3),
    "teal" to Color(0xFF008080),
    "aqua" to Color(0xFF00FFFF),
    // Use white on the black background
    "black" to Color(0xFFFFFFFF),
    "silver" to Color(0xFFC0C0C0),
    "grey" to Color(0xFF808080),
    "gold" to Color(0xFFDAA520)
)

@Composable
private fun mapColor(name: String?): Color? = when {
    name == null -> null

    name == "primary" -> MaterialTheme.colorScheme.primary

    name == "secondary" -> MaterialTheme.colorScheme.secondary

    name.startsWith("#") -> try {
        Color(android.graphics.Color.parseColor(name))
    } catch (_: IllegalArgumentException) {
        null
    }

    else -> namedColors[name]
}
