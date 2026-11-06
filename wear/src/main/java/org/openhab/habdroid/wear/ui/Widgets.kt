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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
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
import androidx.wear.compose.material3.SliderDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
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

data class WidgetRowScope(
    val widgetScope: WidgetScope,
    val row: WidgetRowData,
    val itemScope: TransformingLazyColumnItemScope,
    val transformationSpec: TransformationSpec
) {
    val widget get() = row.widget
    val item get() = row.widget.item

    val transformation = with(itemScope) {
        SurfaceTransformation(transformationSpec)
    }

    val modifier get() = Modifier
        .fillMaxWidth()
        .transformedHeight(itemScope, transformationSpec)
    val transformingModifier get() = modifier.graphicsLayer {
        with(itemScope) {
            with(transformationSpec) {
                applyContentTransformation(scrollProgress)
            }
        }
    }
}

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

fun buildWidgetRows(widgets: List<Widget>): List<WidgetRowData> {
    val widgetsById = widgets.associateBy { it.id }

    tailrec fun isWidgetOrParentVisible(widget: Widget): Boolean {
        if (!widget.visibility) {
            return false
        }
        val parent = widget.parentId?.let { widgetsById[it] } ?: return true
        return isWidgetOrParentVisible(parent)
    }

    val visibleWidgets = widgets.filter { isWidgetOrParentVisible(it) }
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
fun WidgetRowScope.WidgetRow() {
    val locale = LocalLocale.current

    when (widget.type) {
        Widget.Type.Frame -> FrameHeader()

        Widget.Type.Switch -> when {
            widget.isPlayer() -> PlayerRow()
            widget.mappings.isNotEmpty() -> MappingsRow(widget.mappings)
            item?.isOfTypeOrGroupType(Item.Type.Switch) == true -> SwitchRow()
            item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true -> RollershutterRow()
            widget.mappingsOrItemOptions.isNotEmpty() -> MappingsRow(widget.mappingsOrItemOptions)
            else -> SwitchRow()
        }

        Widget.Type.Default if item?.isOfTypeOrGroupType(Item.Type.Player) == true -> PlayerRow()

        Widget.Type.Button -> ButtonRow()

        Widget.Type.Buttongrid -> ButtonGridRow(row.children)

        Widget.Type.Selection -> ValueButton(
            onClick = { widgetScope.onOpenSelection(widget) }
        )

        Widget.Type.Setpoint -> ValueButton(
            onClick = { widgetScope.onOpenSetpoint(widget) }
        )

        Widget.Type.Slider -> SliderRow()

        Widget.Type.Colortemperaturepicker -> ColorTemperaturePickerRow()

        Widget.Type.Colorpicker -> ColorRow()

        Widget.Type.Image -> ImageRow()

        Widget.Type.Chart -> ChartRow()

        Widget.Type.Mapview -> TextRow(
            state = widget.state?.asLocation?.let {
                String.format(locale.platformLocale, "%.5f, %.5f", it.latitude, it.longitude)
            }
        )

        // Group, Text, Default and widgets that can't be used on a watch, e.g. Webview or Mapview
        else -> TextRow()
    }
}

@Composable
private fun WidgetRowScope.FrameHeader() {
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
private fun WidgetLabel(widget: Widget, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Text(
        text = widget.label,
        color = mapColor(widget.labelColor) ?: Color.Unspecified,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun WidgetState(widget: Widget, modifier: Modifier = Modifier, state: String? = widget.stateFromLabel) {
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
private fun WidgetRowScope.TextRow(
    state: String? = widget.stateFromLabel,
    onClick: (() -> Unit)? = widget.linkedPage?.let { page -> { widgetScope.onOpenPage(page.link) } }
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
private fun WidgetRowScope.SwitchRow() {
    val isOn = widget.state?.asBoolean == true
    SwitchButton(
        checked = isOn,
        onCheckedChange = { checked -> widgetScope.onSendCommand(widget.item, if (checked) "ON" else "OFF") },
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
private fun WidgetRowScope.ButtonRow() {
    val command = widget.command
        ?: return TextRow()
    val isActive = widget.stateless == false && widget.item?.state?.asString == command
    Button(
        onClick = { widgetScope.onSendCommand(widget.item, command) },
        modifier = modifier,
        colors = if (isActive) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        transformation = transformation,
        icon = { WidgetIcon(widget.icon) },
        label = { WidgetLabel(widget) }
    )
}

@Composable
private fun WidgetRowScope.MappingsRow(mappings: List<LabeledValue>) {
    val commands = mappings.map {
        ButtonInput.Button(it.value, it.label)
    }
    TextRow(
        onClick = { widgetScope.onOpenButtons(widget, commands) }
    )
}

@Composable
private fun WidgetRowScope.RollershutterRow() {
    val commands = listOf(
        ButtonInput.IconButton("UP", R.drawable.ic_arrow_up_24dp, R.string.rollershutter_up),
        ButtonInput.IconButton("STOP", R.drawable.ic_stop_24dp, R.string.rollershutter_stop),
        ButtonInput.IconButton("DOWN", R.drawable.ic_arrow_down_24dp, R.string.rollershutter_down)
    )
    TextRow(
        onClick = { widgetScope.onOpenButtons(widget, commands) }
    )
}

/**
 * Player items are sent as switches with the player commands as mappings
 */
private fun Widget.isPlayer() = item?.isOfTypeOrGroupType(Item.Type.Player) == true &&
    (mappings.isEmpty() || mappings.map { mapping -> mapping.value } == listOf("PREVIOUS", "PAUSE", "PLAY", "NEXT"))

@Composable
private fun WidgetRowScope.PlayerRow() {
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
        onClick = { widgetScope.onOpenButtons(widget, commands) }
    )
}

@Composable
private fun WidgetRowScope.ButtonGridRow(buttons: List<Widget>) {
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

    Column(
        modifier = transformingModifier
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
                                onClick = { widgetScope.onSendCommand(entry.item, entry.command) },
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

/**
 * Button with label and state, that opens a screen to change the value
 */
@Composable
private fun WidgetRowScope.ValueButton(onClick: () -> Unit) {
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
private fun WidgetRowScope.ColorTemperaturePickerRow() {
    val numberState = widget.state?.asNumber?.toColorTemperatureInKelvin()
    SliderRow(
        currentState = numberState?.value,
        range = widget.colorTemperatureRange(),
        itemStep = 100f,
        unit = numberState?.unit,
        commandConverter = { numberState?.withValue(it)?.toItemCommand(widget.item) }
    )
}

@Composable
private fun WidgetRowScope.SliderRow() {
    val numberState = widget.state?.asNumber
    SliderRow(
        currentState = numberState?.value ?: widget.state?.asBrightness?.toFloat(),
        range = widget.minValue..widget.maxValue,
        itemStep = widget.step.takeIf { it > 0 } ?: 1f,
        unit = numberState?.unit,
        commandConverter = { value ->
            if (widget.item?.isOfTypeOrGroupType(Item.Type.Color) == true) {
                value.beautify()
            } else {
                numberState.withValue(value).toItemCommand(widget.item)
            }
        }
    )
}

@Composable
private fun WidgetRowScope.SliderRow(
    currentState: Float?,
    range: ClosedFloatingPointRange<Float>,
    itemStep: Float,
    unit: String?,
    commandConverter: (Float) -> String?
) {
    // The slider can only be changed with buttons, so combine small steps to limit the number of taps
    val tickCount = (range.endInclusive - range.start) / itemStep
    val maxTicks = 10

    val step = if (tickCount <= maxTicks) {
        itemStep
    } else {
        itemStep * ceil(tickCount / maxTicks)
    }
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
            delay(300.milliseconds)
            val command = commandConverter(value)
            command?.let { widgetScope.onSendCommand(widget.item, it) }
        }
    }

    val state = if (value == serverValue && widget.stateFromLabel != null) {
        widget.stateFromLabel
    } else {
        widget.formatValue(value, step, unit)
    }

    val colors = SliderDefaults.sliderColors()

    Column(
        modifier = transformingModifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
        ) {
            WidgetLabel(
                widget = widget,
                maxLines = 1,
                modifier = Modifier.weight(1F)
            )
            WidgetState(
                widget = widget,
                state = state
            )
        }
        Slider(
            value = value,
            onValueChange = { newValue -> value = newValue },
            steps = steps,
            valueRange = range,
            colors = colors.copy(
                containerColor = Color.Transparent
            ),
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
    return if (min.isFinite() && max.isFinite() && min >= 1000F && max > min) {
        min..max
    } else {
        2000F..6500F
    }
}

/**
 * Formats a value with the precision of the step, as the format of the item might round it
 */
@Composable
fun Widget.formatValue(value: Float, step: Float, unit: String?): String {
    val decimals = step.toBigDecimal().stripTrailingZeros().scale().coerceAtLeast(0)
    val number = String.format(LocalLocale.current.platformLocale, "%.${decimals}f", value)
    val actualUnit = when {
        unit != null -> unit

        item?.isOfTypeOrGroupType(Item.Type.Dimmer) == true ||
            item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true -> "%"

        else -> null
    }
    return if (actualUnit.isNullOrEmpty()) number else "$number $actualUnit"
}

@Composable
private fun WidgetRowScope.ColorRow() {
    val color = widget.state?.asHsv?.let { hsv -> Color.hsv(hsv.hue, hsv.saturation, hsv.value) }
    FilledTonalButton(
        onClick = { widgetScope.onOpenColor(widget) },
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
private fun WidgetRowScope.ImageRow() {
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
                        connection = widgetScope.connection,
                        widthPixels = widthPixels,
                        refreshMillis = widget.refresh,
                        contentDescription = widget.label
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
private fun WidgetRowScope.ChartRow() {
    val configuration = LocalConfiguration.current
    val widthPixels = with(LocalDensity.current) {
        configuration.screenWidthDp.dp.roundToPx()
    }
    val theme = if (widgetScope.serverFlags and ServerProperties.SERVER_FLAG_TRANSPARENT_CHARTS != 0) {
        "black_transparent"
    } else {
        "black"
    }
    val url = widget.toChartUrl(
        scalingFactor = 2.5F,
        requestHighResCharts = true,
        width = widthPixels,
        chartTheme = theme,
        density = configuration.densityDpi,
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
                    connection = widgetScope.connection,
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
        Color(name.toColorInt())
    } catch (_: IllegalArgumentException) {
        null
    }

    else -> namedColors[name]
}
