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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.CompactButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
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

interface WidgetActions {
    fun openPage(url: String)

    fun openSelection(widget: Widget)

    fun openSetpoint(widget: Widget)

    fun openColor(widget: Widget)

    fun sendCommand(item: Item?, command: String)
}

class WidgetScope(val connection: Connection, val serverFlags: Int, val actions: WidgetActions)

/**
 * A row of the page. Buttons of a button grid are part of the grid's row.
 */
data class WidgetRowData(val widget: Widget, val children: List<Widget> = emptyList())

/**
 * Maximum number of mappings shown as buttons. Switches with more mappings are shown like a selection.
 */
private const val MAX_INLINE_MAPPINGS = 3

/**
 * Maximum number of mappings with short labels, e.g. "<<" and ">>" of a player, shown as buttons
 */
private const val MAX_INLINE_SHORT_MAPPINGS = 5
private const val MAX_SHORT_MAPPING_LABEL_LENGTH = 3

private fun List<LabeledValue>.canBeShownInline() = size in 1..MAX_INLINE_MAPPINGS ||
    (size in 1..MAX_INLINE_SHORT_MAPPINGS && all { mapping -> mapping.label.length <= MAX_SHORT_MAPPING_LABEL_LENGTH })

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

private const val SLIDER_COMMAND_DELAY_MILLIS = 300L
private const val MAX_SLIDER_SEGMENTS = 10
private const val COLOR_TEMPERATURE_STEP = 100f
private const val MIN_COLOR_TEMPERATURE = 1000f
private val DEFAULT_COLOR_TEMPERATURE_RANGE = 2000f..6500f

fun buildWidgetRows(widgets: List<Widget>): List<WidgetRowData> {
    val visibleWidgets = widgets.filter { widget -> widget.visibility }
    val buttonGridIds = visibleWidgets
        .filter { widget -> widget.type == Widget.Type.Buttongrid }
        .map { widget -> widget.id }
        .toSet()
    return visibleWidgets
        .filterNot { widget -> widget.type == Widget.Type.Button && widget.parentId in buttonGridIds }
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
fun WidgetRow(row: WidgetRowData, scope: WidgetScope, modifier: Modifier, transformation: SurfaceTransformation) {
    val widget = row.widget
    val item = widget.item
    when (widget.type) {
        Widget.Type.Frame -> FrameHeader(widget, modifier, transformation)

        Widget.Type.Switch -> when {
            widget.mappings.canBeShownInline() -> MappingsRow(widget, scope, widget.mappings, modifier)
            widget.mappings.isNotEmpty() -> SelectionRow(widget, scope, modifier, transformation)
            item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true -> RollershutterRow(widget, scope, modifier)
            item?.isOfTypeOrGroupType(Item.Type.Player) == true -> PlayerRow(widget, scope, modifier)
            else -> SwitchRow(widget, scope, modifier, transformation)
        }

        Widget.Type.Default if item?.isOfTypeOrGroupType(Item.Type.Player) == true -> PlayerRow(widget, scope, modifier)

        Widget.Type.Button -> ButtonRow(widget, scope, modifier, transformation)

        Widget.Type.Buttongrid -> ButtonGridRow(widget, row.children, scope, modifier)

        Widget.Type.Selection -> SelectionRow(widget, scope, modifier, transformation)

        Widget.Type.Setpoint -> ValueButton(widget, scope, modifier, transformation) {
            scope.actions.openSetpoint(widget)
        }

        Widget.Type.Slider, Widget.Type.Colortemperaturepicker -> SliderRow(widget, scope, modifier)

        Widget.Type.Colorpicker -> ColorRow(widget, scope, modifier, transformation)

        Widget.Type.Image -> ImageRow(widget, scope, modifier)

        Widget.Type.Chart -> ChartRow(widget, scope, modifier)

        // Group, Text, Default and widgets that can't be used on a watch, e.g. Webview or Mapview
        else -> TextRow(widget, scope, modifier, transformation)
    }
}

@Composable
private fun FrameHeader(widget: Widget, modifier: Modifier, transformation: SurfaceTransformation) {
    if (widget.label.isNotEmpty()) {
        ListHeader(modifier = modifier, transformation = transformation) {
            Text(widget.label, color = mapColor(widget.labelColor) ?: Color.Unspecified)
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
private fun WidgetState(widget: Widget, state: String? = widget.stateFromLabel) {
    state?.takeIf { it.isNotEmpty() }?.let {
        Text(
            text = it,
            color = mapColor(widget.valueColor) ?: Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TextRow(widget: Widget, scope: WidgetScope, modifier: Modifier, transformation: SurfaceTransformation) {
    val linkedPage = widget.linkedPage
    Button(
        onClick = { linkedPage?.let { page -> scope.actions.openPage(page.link) } },
        enabled = true,
        modifier = modifier,
        colors = if (linkedPage !=
            null
        ) {
            ButtonDefaults.filledTonalButtonColors()
        } else {
            ButtonDefaults.childButtonColors()
        },
        transformation = transformation,
        icon = { WidgetIcon(widget.icon, scope.connection, scope.serverFlags) },
        secondaryLabel = widget.stateFromLabel?.takeIf { it.isNotEmpty() }?.let { { WidgetState(widget) } },
        label = { WidgetLabel(widget) }
    )
}

@Composable
private fun SwitchRow(widget: Widget, scope: WidgetScope, modifier: Modifier, transformation: SurfaceTransformation) {
    val isOn = widget.state?.asBoolean == true
    SwitchButton(
        checked = isOn,
        onCheckedChange = { checked -> scope.actions.sendCommand(widget.item, if (checked) "ON" else "OFF") },
        modifier = modifier,
        transformation = transformation,
        icon = { WidgetIcon(widget.icon, scope.connection, scope.serverFlags) },
        secondaryLabel = widget.stateFromLabel?.takeIf { it.isNotEmpty() }?.let { { WidgetState(widget) } },
        label = { WidgetLabel(widget) }
    )
}

@Composable
private fun ButtonRow(widget: Widget, scope: WidgetScope, modifier: Modifier, transformation: SurfaceTransformation) {
    val command = widget.command ?: return TextRow(widget, scope, modifier, transformation)
    val isActive = widget.stateless == false && widget.item?.state?.asString == command
    Button(
        onClick = { scope.actions.sendCommand(widget.item, command) },
        modifier = modifier,
        colors = if (isActive) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        transformation = transformation,
        icon = { WidgetIcon(widget.icon, scope.connection, scope.serverFlags) },
        label = { WidgetLabel(widget) }
    )
}

/**
 * Label with a row of buttons below
 */
@Composable
private fun LabeledButtonGroup(widget: Widget, modifier: Modifier, buttons: @Composable () -> Unit) {
    Column(modifier = modifier) {
        if (widget.label.isNotEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                WidgetLabel(widget, maxLines = 1)
            }
        }
        buttons()
    }
}

@Composable
private fun MappingsRow(widget: Widget, scope: WidgetScope, mappings: List<LabeledValue>, modifier: Modifier) {
    LabeledButtonGroup(widget, modifier) {
        ButtonGroup(modifier = Modifier.fillMaxWidth()) {
            mappings.forEach { mapping ->
                val selected = widget.state?.asString == mapping.value
                Button(
                    onClick = { scope.actions.sendCommand(widget.item, mapping.value) },
                    modifier = Modifier.weight(1f),
                    colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = CompactButtonDefaults.ContentPadding
                ) {
                    ButtonText(mapping.label)
                }
            }
        }
    }
}

@Composable
private fun RollershutterRow(widget: Widget, scope: WidgetScope, modifier: Modifier) {
    val commands = listOf(
        "UP" to (R.drawable.ic_arrow_up_24dp to R.string.rollershutter_up),
        "STOP" to (R.drawable.ic_stop_24dp to R.string.rollershutter_stop),
        "DOWN" to (R.drawable.ic_arrow_down_24dp to R.string.rollershutter_down)
    )
    IconButtonRow(widget, scope, modifier, commands)
}

/**
 * Label with a row of icon buttons that send commands
 *
 * @param commands Command with icon and content description
 */
@Composable
private fun IconButtonRow(
    widget: Widget,
    scope: WidgetScope,
    modifier: Modifier,
    commands: List<Pair<String, Pair<Int, Int>>>
) {
    LabeledButtonGroup(widget, modifier) {
        ButtonGroup(modifier = Modifier.fillMaxWidth()) {
            commands.forEach { (command, resources) ->
                FilledTonalButton(
                    onClick = { scope.actions.sendCommand(widget.item, command) },
                    modifier = Modifier.weight(1f),
                    contentPadding = CompactButtonDefaults.ContentPadding
                ) {
                    Icon(
                        painter = painterResource(resources.first),
                        contentDescription = stringResource(resources.second),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(widget: Widget, scope: WidgetScope, modifier: Modifier) {
    val isPlaying = widget.item?.state?.asString == "PLAY"
    val commands = listOf(
        "PREVIOUS" to (R.drawable.ic_skip_previous_24dp to R.string.player_previous),
        if (isPlaying) {
            "PAUSE" to (R.drawable.ic_pause_24dp to R.string.player_pause)
        } else {
            "PLAY" to (R.drawable.ic_play_24dp to R.string.player_play)
        },
        "NEXT" to (R.drawable.ic_skip_next_24dp to R.string.player_next)
    )
    IconButtonRow(widget, scope, modifier, commands)
}

@Composable
private fun ButtonGridRow(widget: Widget, buttons: List<Widget>, scope: WidgetScope, modifier: Modifier) {
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

    LabeledButtonGroup(widget, modifier) {
        Column {
            entries.groupBy { entry -> entry.row }.toSortedMap().values.forEach { rowEntries ->
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    // Keep the buttons in their columns, e.g. for arrow keys of a remote control
                    columnRange.forEach { column ->
                        val entry = rowEntries.firstOrNull { entry -> entry.column == column }
                        if (entry == null) {
                            Box(modifier = Modifier.weight(1f))
                            return@forEach
                        }
                        FilledTonalButton(
                            onClick = { scope.actions.sendCommand(entry.item, entry.command) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            if (entry.icon != null) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    WidgetIcon(entry.icon, scope.connection, scope.serverFlags)
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

@Composable
private fun SelectionRow(
    widget: Widget,
    scope: WidgetScope,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    ValueButton(widget, scope, modifier, transformation) { scope.actions.openSelection(widget) }
}

/**
 * Button with label and state, that opens a screen to change the value
 */
@Composable
private fun ValueButton(
    widget: Widget,
    scope: WidgetScope,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit
) {
    val state = widget.stateFromLabel ?: widget.state?.asNumber?.toString()
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        transformation = transformation,
        icon = { WidgetIcon(widget.icon, scope.connection, scope.serverFlags) },
        secondaryLabel = state?.takeIf { it.isNotEmpty() }?.let { { WidgetState(widget, state) } },
        label = { WidgetLabel(widget) }
    )
}

@Composable
private fun SliderRow(widget: Widget, scope: WidgetScope, modifier: Modifier) {
    val isColorTemperature = widget.type == Widget.Type.Colortemperaturepicker
    val numberState = widget.state?.asNumber.let { state ->
        if (isColorTemperature) state?.toColorTemperatureInKelvin() else state
    }
    val range = if (isColorTemperature) widget.colorTemperatureRange() else widget.minValue..widget.maxValue
    val itemStep = if (isColorTemperature) COLOR_TEMPERATURE_STEP else widget.step.takeIf { it > 0 } ?: 1f
    // The slider can only be changed with buttons, so combine small steps to limit the number of taps
    val units = (range.endInclusive - range.start) / itemStep
    val step = if (units > MAX_SLIDER_SEGMENTS) itemStep * ceil(units / MAX_SLIDER_SEGMENTS) else itemStep
    val steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
    val serverValue = (numberState?.value ?: widget.state?.asBrightness?.toFloat() ?: range.start)
        .coerceIn(range.start, range.endInclusive)
    var value by remember(widget.id, serverValue) { mutableFloatStateOf(serverValue) }

    // Wait for further changes to avoid sending a command for every step
    LaunchedEffect(value) {
        if (value != serverValue) {
            delay(SLIDER_COMMAND_DELAY_MILLIS)
            val command = if (isColorTemperature) {
                numberState?.withValue(value)?.toItemCommand(widget.item)
            } else {
                widget.numberCommand(value)
            }
            command?.let { scope.actions.sendCommand(widget.item, it) }
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WidgetLabel(widget, maxLines = 1)
            }
            val state = if (value == serverValue && widget.stateFromLabel != null) {
                widget.stateFromLabel
            } else {
                widget.formatValue(value, itemStep, numberState?.unit)
            }
            WidgetState(widget, state)
        }
        Slider(
            value = value,
            onValueChange = { newValue -> value = newValue },
            steps = steps,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
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
private fun ColorRow(widget: Widget, scope: WidgetScope, modifier: Modifier, transformation: SurfaceTransformation) {
    val color = widget.state?.asHsv?.let { hsv -> Color(hsv.toColor(includeValue = false)) }
    FilledTonalButton(
        onClick = { scope.actions.openColor(widget) },
        modifier = modifier,
        transformation = transformation,
        icon = {
            if (color != null) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(color))
            } else {
                Icon(painterResource(R.drawable.ic_palette_24dp), contentDescription = null)
            }
        },
        label = { WidgetLabel(widget) }
    )
}

@Composable
private fun ImageRow(widget: Widget, scope: WidgetScope, modifier: Modifier) {
    val url = widget.url ?: widget.item?.link ?: return
    val widthPixels = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    RemoteImage(
        url = url,
        connection = scope.connection,
        widthPixels = widthPixels,
        refreshMillis = widget.refresh,
        contentDescription = widget.label,
        modifier = modifier.clip(MaterialTheme.shapes.medium)
    )
}

@Composable
private fun ChartRow(widget: Widget, scope: WidgetScope, modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPixels = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val theme = if (scope.serverFlags and ServerProperties.SERVER_FLAG_TRANSPARENT_CHARTS != 0) {
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
        density = context.resources.displayMetrics.densityDpi,
        forcedLegend = false
    ) ?: return
    Column(modifier = modifier) {
        if (widget.label.isNotEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                WidgetLabel(widget, maxLines = 1)
            }
        }
        RemoteImage(
            url = url,
            connection = scope.connection,
            widthPixels = widthPixels,
            refreshMillis = widget.refresh,
            contentDescription = widget.label,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
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
