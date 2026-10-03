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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.ui.toItemCommand
import org.openhab.habdroid.util.beautify

interface TvWidgetActions {
    fun openPage(url: String)

    fun sendCommand(item: Item?, command: String)
}

/**
 * Renders the widgets of a sitemap page. Every row is focusable, so the whole page can be scrolled with a D-pad.
 */
@Composable
fun TvWidgetList(
    widgets: List<Widget>,
    connection: Connection,
    serverFlags: Int,
    actions: TvWidgetActions,
    listState: LazyListState,
    initialFocusRequester: FocusRequester,
    initialFocusId: String?,
    onWidgetFocused: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorMapper = rememberTvColorMapper()
    val visibleWidgets = widgets.filter { widget -> widget.visibility }
    val buttonGridIds = visibleWidgets
        .filter { widget -> widget.type == Widget.Type.Buttongrid }
        .map { widget -> widget.id }
        .toSet()
    // Buttons of a button grid are rendered as part of the grid
    val listWidgets = visibleWidgets.filterNot { widget ->
        widget.type == Widget.Type.Button && widget.parentId in buttonGridIds
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(listWidgets, key = { widget -> widget.id }) { widget ->
            val row = TvWidgetRowScope(widget, connection, serverFlags, actions, colorMapper)
            val focusModifier = if (widget.id == initialFocusId) {
                Modifier.focusRequester(initialFocusRequester).focusGroup()
            } else {
                Modifier
            }
            Box(
                modifier = focusModifier.onFocusChanged { state ->
                    if (state.hasFocus) {
                        onWidgetFocused(widget.id)
                    }
                }
            ) {
                row.Content(visibleWidgets)
            }
        }
    }
}

@Composable
private fun TvWidgetRowScope.Content(visibleWidgets: List<Widget>) {
    when (widget.type) {
        Widget.Type.Frame -> FrameHeader()

        Widget.Type.Switch -> SwitchRow()

        Widget.Type.Button -> MappingsRow(listOfNotNull(widget.toButtonMapping()))

        Widget.Type.Buttongrid -> ButtonGridRow(
            visibleWidgets.filter { child -> child.parentId == widget.id && child.type == Widget.Type.Button }
        )

        Widget.Type.Selection -> SelectionRow()

        Widget.Type.Setpoint -> SetpointRow()

        Widget.Type.Slider, Widget.Type.Colortemperaturepicker -> SliderRow()

        Widget.Type.Group, Widget.Type.Text, Widget.Type.Default -> TextRow()

        Widget.Type.Image, Widget.Type.Chart, Widget.Type.Video -> TvMediaRow(widget, connection, serverFlags)

        // Not usable with a D-pad (yet), so only show label and state
        else -> TextRow()
    }
}

private class TvWidgetRowScope(
    val widget: Widget,
    val connection: Connection,
    val serverFlags: Int,
    val actions: TvWidgetActions,
    val colorMapper: (String?) -> Color?
) {
    val labelColor get() = colorMapper(widget.labelColor) ?: Color.Unspecified
    val valueColor get() = colorMapper(widget.valueColor) ?: Color.Unspecified

    fun send(command: String?) {
        if (command != null) {
            actions.sendCommand(widget.item, command)
        }
    }

    fun numberCommand(value: Float): String? = if (widget.item?.isOfTypeOrGroupType(Item.Type.Color) == true) {
        value.beautify()
    } else {
        widget.state?.asNumber.withValue(value).toItemCommand(widget.item)
    }
}

@Composable
private fun TvWidgetRowScope.FrameHeader() {
    if (widget.label.isEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        Text(
            text = widget.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun TvWidgetRowScope.WidgetListItem(
    onClick: () -> Unit = {},
    trailingContent: @Composable () -> Unit = { StateText() }
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text(text = widget.label, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { TvWidgetIcon(widget.icon, connection) },
        trailingContent = trailingContent
    )
}

@Composable
private fun TvWidgetRowScope.StateText() {
    widget.stateFromLabel?.let { state ->
        Text(text = state, color = valueColor, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

@Composable
private fun TvWidgetRowScope.TextRow() {
    val linkedPage = widget.linkedPage
    WidgetListItem(
        onClick = { linkedPage?.let { page -> actions.openPage(page.link) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateText()
                if (linkedPage != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_arrow_right_themed_24dp),
                        contentDescription = null
                    )
                }
            }
        }
    )
}

@Composable
private fun TvWidgetRowScope.SwitchRow() {
    val item = widget.item
    when {
        widget.mappings.isNotEmpty() -> MappingsRow(widget.mappings)

        item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true -> OptionsRow(
            listOf(
                TvOption(iconRes = R.drawable.ic_keyboard_arrow_up_themed_24dp, onClick = { send("UP") }),
                TvOption(iconRes = R.drawable.ic_stop_24dp, onClick = { send("STOP") }),
                TvOption(iconRes = R.drawable.ic_keyboard_arrow_down_themed_24dp, onClick = { send("DOWN") })
            )
        )

        else -> {
            val isOn = widget.state?.asBoolean == true
            WidgetListItem(
                onClick = { send(if (isOn) "OFF" else "ON") },
                trailingContent = { Switch(checked = isOn, onCheckedChange = null) }
            )
        }
    }
}

@Composable
private fun TvWidgetRowScope.MappingsRow(mappings: List<LabeledValue>) {
    OptionsRow(
        mappings.map { mapping ->
            TvOption(
                label = mapping.label,
                selected = widget.state?.asString == mapping.value,
                onClick = { send(mapping.value) }
            )
        }
    )
}

@Composable
private fun TvWidgetRowScope.ButtonGridRow(buttons: List<Widget>) {
    // Button grids either have Button child widgets (openHAB 4.2+) or mappings with row and column
    val gridEntries = buttons.mapNotNull { button ->
        val mapping = button.toButtonMapping() ?: return@mapNotNull null
        // Like on the phone, only stateful buttons show the state
        val stateful = button.stateless == false
        Triple(button.row ?: 0, button.column ?: 0, TvGridButton(mapping, button.item ?: widget.item, stateful))
    } + widget.mappings.map { mapping ->
        Triple(mapping.row, mapping.column, TvGridButton(mapping, widget.item, true))
    }

    // Each row of the grid is a single focusable row
    Column {
        gridEntries.groupBy { (row, _, _) -> row }.toSortedMap().values.forEachIndexed { index, rowEntries ->
            OptionsRow(
                options = rowEntries.sortedBy { (_, column, _) -> column }.map { (_, _, button) ->
                    TvOption(
                        label = button.mapping.label,
                        selected = button.stateful && button.item?.state?.asString == button.mapping.value,
                        onClick = { button.item?.let { item -> actions.sendCommand(item, button.mapping.value) } }
                    )
                },
                showLabel = index == 0
            )
        }
    }
}

private data class TvGridButton(val mapping: LabeledValue, val item: Item?, val stateful: Boolean)

private fun Widget.toButtonMapping(): LabeledValue? {
    val command = command ?: return null
    return LabeledValue(command, releaseCommand, label, icon, row ?: 0, column ?: 0)
}

private class TvOption(
    val label: String? = null,
    val iconRes: Int? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Row with multiple options, e.g. mappings. The row is a single focus stop, so it can't be skipped when moving
 * the focus up and down. Left and right move the highlight between the options, click activates the highlighted
 * option. The option matching the current state is filled.
 */
@Composable
private fun TvWidgetRowScope.OptionsRow(options: List<TvOption>, showLabel: Boolean = true) {
    var highlightedIndex by remember(options.size) {
        mutableIntStateOf(options.indexOfFirst { option -> option.selected }.coerceAtLeast(0))
    }
    var isFocused by remember { mutableStateOf(false) }

    ListItem(
        selected = false,
        onClick = { options.getOrNull(highlightedIndex)?.onClick?.invoke() },
        headlineContent = {
            if (showLabel) {
                Text(text = widget.label, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            if (showLabel) {
                TvWidgetIcon(widget.icon, connection)
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { index, option ->
                    OptionChip(option, isHighlighted = isFocused && index == highlightedIndex)
                }
            }
        },
        modifier = Modifier
            .onFocusChanged { state -> isFocused = state.isFocused }
            .onKeyEvent { event ->
                val delta = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    highlightedIndex = (highlightedIndex + delta).coerceIn(0, options.lastIndex)
                }
                true
            }
    )
}

@Composable
private fun OptionChip(option: TvOption, isHighlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (option.selected) colors.onPrimary else LocalContentColor.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (option.selected) colors.primary else Color.Transparent)
            .border(
                width = if (isHighlighted) 3.dp else 1.dp,
                color = if (isHighlighted) LocalContentColor.current else contentColor.copy(alpha = 0.5f),
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (option.iconRes != null) {
            Icon(
                painter = painterResource(option.iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(text = option.label.orEmpty(), color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TvWidgetRowScope.SelectionRow() {
    var showDialog by remember { mutableStateOf(false) }
    val options = widget.mappingsOrItemOptions
    val selectedLabel = options.firstOrNull { option -> option.value == widget.state?.asString }?.label
        ?: widget.stateFromLabel
    WidgetListItem(
        onClick = { showDialog = true },
        trailingContent = {
            selectedLabel?.let { Text(text = it, color = valueColor, style = MaterialTheme.typography.bodyLarge) }
        }
    )
    if (showDialog) {
        TvSelectionDialog(
            title = widget.label,
            options = options.map { option -> option.value to option.label },
            selectedValue = widget.state?.asString,
            onSelected = { value ->
                showDialog = false
                send(value)
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Dialog to select one of multiple options, given as pairs of value and label
 */
@Composable
fun TvSelectionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocus = remember { FocusRequester() }
    // Focus the selected option, or the first one if none is selected
    val initialFocusIndex = options.indexOfFirst { (value, _) -> value == selectedValue }.coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(24.dp).width(480.dp)) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(options) { index, (value, label) ->
                        ListItem(
                            selected = value == selectedValue,
                            onClick = { onSelected(value) },
                            headlineContent = { Text(label) },
                            modifier = if (index == initialFocusIndex) {
                                Modifier.focusRequester(initialFocus)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        initialFocus.requestFocusWhenAttached()
    }
}

@Composable
private fun TvWidgetRowScope.SetpointRow() = ValueRow(showTrack = false)

@Composable
private fun TvWidgetRowScope.SliderRow() = ValueRow(showTrack = true)

/**
 * Row for numeric values that are changed with left and right. The command is sent once the user stops changing
 * the value.
 */
@Composable
private fun TvWidgetRowScope.ValueRow(showTrack: Boolean) {
    val stateValue = widget.state?.asNumber?.value ?: widget.minValue
    var pendingValue by remember(widget.state) { mutableStateOf<Float?>(null) }
    val currentValue = pendingValue ?: stateValue
    val range = (widget.maxValue - widget.minValue).takeIf { it > 0 } ?: 1f
    val fraction = ((currentValue - widget.minValue) / range).coerceIn(0f, 1f)
    // Sliders usually have small steps, which would require lots of key presses.
    // Move by roughly 5 % of the range in that case, but by a multiple of the step.
    val dpadStep = if (showTrack) {
        widget.step * max(1f, (range / SLIDER_DPAD_STEP_COUNT / widget.step).roundToInt().toFloat())
    } else {
        widget.step
    }
    val displayValue = pendingValue
        ?.let { pending -> widget.state?.asNumber.withValue(pending).toString() }
        ?: widget.stateFromLabel

    LaunchedEffect(pendingValue) {
        val pending = pendingValue ?: return@LaunchedEffect
        delay(VALUE_SEND_DELAY_MS)
        send(numberCommand(pending))
    }

    ListItem(
        selected = false,
        onClick = {
            // Like on the phone, clicking a dimmer toggles it
            if (showTrack && widget.switchSupport) {
                send(if (stateValue > widget.minValue) "OFF" else "ON")
            }
        },
        headlineContent = {
            Column {
                Text(text = widget.label, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showTrack) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SliderTrack(fraction)
                }
            }
        },
        leadingContent = { TvWidgetIcon(widget.icon, connection) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!showTrack) {
                    Text(text = "\u2212  ", style = MaterialTheme.typography.bodyLarge)
                }
                displayValue?.let { Text(text = it, color = valueColor, style = MaterialTheme.typography.bodyLarge) }
                if (!showTrack) {
                    Text(text = "  +", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        modifier = Modifier.onKeyEvent { event ->
            val delta = when (event.key) {
                Key.DirectionLeft -> -dpadStep
                Key.DirectionRight -> dpadStep
                else -> return@onKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown) {
                pendingValue = (currentValue + delta).coerceIn(widget.minValue, widget.maxValue)
            }
            true
        }
    )
}

@Composable
private fun SliderTrack(fraction: Float) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(LocalContentColor.current.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(colors.primary)
        )
    }
}

/**
 * Maps the color names used in sitemaps to colors suitable for a dark background
 */
@Composable
private fun rememberTvColorMapper(): (String?) -> Color? {
    val context = LocalContext.current
    return remember(context) {
        val names = context.resources.getStringArray(R.array.valueColorNames)
        val values = context.resources.obtainTypedArray(R.array.valueColorsDarkBackground)
        val colorMap = names.mapIndexed { index, name -> name to Color(values.getColor(index, 0)) }.toMap()
        values.recycle()
        val mapper: (String?) -> Color? = { name ->
            when {
                name == null -> null
                name.startsWith("#") -> runCatching { Color(android.graphics.Color.parseColor(name)) }.getOrNull()
                name == "primary" -> null
                else -> colorMap[name]
            }
        }
        mapper
    }
}

private const val VALUE_SEND_DELAY_MS = 500L
private const val SLIDER_DPAD_STEP_COUNT = 20
