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

package org.openhab.habdroid.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.core.graphics.drawable.IconCompat
import kotlin.collections.forEach
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.shouldRenderAsPlayer

class WidgetGridScreen(
    carContext: CarContext,
    val url: String,
    val id: String,
    val nestingDepth: Int,
    private val title: String,
    private val onPageSelected: (page: LinkedPage) -> Unit,
    private val onWidgetCommand: (widget: Widget, command: String) -> Unit
) : Screen(carContext) {
    private var widgets: MutableList<Widget>? = null
    private val widgetsById = mutableMapOf<String, Widget>()

    fun updateWidgets(widgets: List<Widget>) {
        this.widgets = widgets.toMutableList()
        widgetsById.clear()
        widgetsById.putAll(widgets.map { it.id to it })
        invalidate()
    }

    fun updateWidget(widget: Widget) {
        widgets?.let { widgets ->
            val pos = widgets.indexOfFirst { w -> w.id == widget.id }
            if (pos >= 0) {
                widgets[pos] = widget
                widgetsById[widget.id] = widget
                invalidate()
            }
        }
    }

    private fun shouldShowWidget(widget: Widget): Boolean {
        if (!widget.visibility || widget.type == Widget.Type.Frame) {
            return false
        }

        var checkedWidget: Widget? = widget
        do {
            val parent = checkedWidget?.parentId?.let { id -> widgetsById[id] }
            if (parent != null && !parent.visibility) {
                return false
            }
            checkedWidget = parent
        } while (checkedWidget != null)

        return true
    }

    private fun buildWidgetGridItem(widget: Widget): GridItem {
        val presentation = when (widget.type) {
            Widget.Type.Switch -> when {
                widget.shouldRenderAsPlayer() ->
                    buildPlayerPresentation(widget)

                widget.mappings.isNotEmpty() ->
                    buildSectionSwitchPresentation(widget, widget.mappings)

                widget.item?.isOfTypeOrGroupType(Item.Type.Switch) == true ->
                    buildToggleSwitchPresentation(widget)

                widget.item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true ->
                    buildRollerShutterPresentation(widget)

                widget.mappingsOrItemOptions.isNotEmpty() ->
                    buildSectionSwitchPresentation(widget, widget.mappingsOrItemOptions)

                else ->
                    buildToggleSwitchPresentation(widget)
            }

            Widget.Type.Selection -> buildSelectionPresentation(widget)

            else -> if (widget.linkedPage != null) {
                WidgetPresentation.PageLinkPresentation(widget, widget.linkedPage)
            } else {
                WidgetPresentation.TextPresentation(widget)
            }
        }

        val itemBuilder = GridItem.Builder()
            .setTitle(widget.label)
            .setImage(presentation.determineIcon(carContext), GridItem.IMAGE_TYPE_ICON)

        widget.stateFromLabel?.replace("\n", " ")?.let { itemBuilder.setText(it) }

        when (presentation) {
            is WidgetPresentation.TogglePresentation -> {
                itemBuilder.setOnClickListener { presentation.listener.onCheckedChange(!presentation.checked) }
            }

            is WidgetPresentation.SelectionPresentation -> {
                itemBuilder.setOnClickListener { openSelectionScreen(widget, presentation.options) }
            }

            is WidgetPresentation.ActionListPresentation -> {
                itemBuilder.setOnClickListener { openActionListScreen(widget, presentation.actions) }
            }

            is WidgetPresentation.PageLinkPresentation -> {
                // https://developers.google.com/cars/design/create-apps/apps-for-drivers/plan-task-flows#steps-refreshes
                // says that we must not use more than 5 navigation steps, and we need to keep room for a final
                // selection screen, so we limit page depth to 3 steps
                if (nestingDepth < 2) {
                    itemBuilder.setOnClickListener { onPageSelected(presentation.page) }
                }
            }

            is WidgetPresentation.TextPresentation -> {}
        }

        return itemBuilder.build()
    }

    private fun buildToggleSwitchPresentation(widget: Widget) =
        WidgetPresentation.TogglePresentation(widget, widget.item?.state?.asBoolean == true) { checked ->
            onWidgetCommand(widget, if (checked) "ON" else "OFF")
        }

    private fun buildSectionSwitchPresentation(widget: Widget, mappings: List<LabeledValue>): WidgetPresentation {
        val actions = mappings.map { MappingActionListItem(it) }
        return WidgetPresentation.ActionListPresentation(widget, actions)
    }

    private fun buildRollerShutterPresentation(widget: Widget): WidgetPresentation {
        val actions = listOf(
            InternalActionListItem(
                R.string.car_action_rollershutter_open,
                R.drawable.ic_keyboard_arrow_up_themed_24dp,
                "UP"
            ),
            InternalActionListItem(
                R.string.car_action_rollershutter_stop,
                R.drawable.ic_clear_themed_24dp,
                "STOP"
            ),
            InternalActionListItem(
                R.string.car_action_rollershutter_close,
                R.drawable.ic_keyboard_arrow_down_themed_24dp,
                "DOWN"
            )
        )
        return WidgetPresentation.ActionListPresentation(widget, actions)
    }

    private fun buildPlayerPresentation(widget: Widget): WidgetPresentation {
        val actions = listOf(
            InternalActionListItem(
                R.string.car_action_player_prev,
                R.drawable.ic_previous_track_themed_24dp,
                "PREVIOUS"
            ),
            InternalActionListItem(
                R.string.car_action_player_play,
                R.drawable.ic_play_themed_24dp,
                "PLAY"
            ),
            InternalActionListItem(
                R.string.car_action_player_pause,
                R.drawable.ic_pause_themed_24dp,
                "PAUSE"
            ),
            InternalActionListItem(
                R.string.car_action_player_next,
                R.drawable.ic_next_track_themed_24dp,
                "NEXT"
            )
        )
        return WidgetPresentation.ActionListPresentation(widget, actions)
    }

    private fun buildSelectionPresentation(widget: Widget): WidgetPresentation {
        val commands = widget.mappingsOrItemOptions.map { SelectionListItem(it.label, it.value) }
        return WidgetPresentation.SelectionPresentation(widget, commands)
    }

    private fun openActionListScreen(widget: Widget, actions: List<ActionListItem>) {
        val screen = ActionListScreen(carContext, widget.label, actions) { item ->
            onWidgetCommand(widget, item.command)
        }
        screenManager.push(screen)
    }

    private fun openSelectionScreen(widget: Widget, options: List<SelectionListItem>) {
        val screen = SelectionScreen(carContext, widget.label, options, widget.state?.asString) { item ->
            onWidgetCommand(widget, item.command)
        }
        screenManager.push(screen)
    }

    override fun onGetTemplate(): Template {
        val headerBuilder = Header.Builder()
            .setTitle(title)

        if (nestingDepth > 0) {
            headerBuilder.setStartHeaderAction(Action.BACK)
        }

        val templateBuilder = GridTemplate.Builder()
            .setHeader(headerBuilder.build())

        val widgetsToShow = widgets
            ?.filter { shouldShowWidget(it) }
        if (widgetsToShow == null) {
            templateBuilder.setLoading(true)
        } else {
            val listBuilder = ItemList.Builder()
            widgetsToShow.forEach { w ->
                listBuilder.addItem(buildWidgetGridItem(w))
            }
            templateBuilder.setSingleList(listBuilder.build())
        }

        return templateBuilder.build()
    }

    sealed class WidgetPresentation(val widget: Widget, private val hasActiveState: Boolean) {
        class TextPresentation(widget: Widget) : WidgetPresentation(widget, false)
        class PageLinkPresentation(widget: Widget, val page: LinkedPage) : WidgetPresentation(widget, false)
        class TogglePresentation(widget: Widget, val checked: Boolean, val listener: Toggle.OnCheckedChangeListener) :
            WidgetPresentation(widget, true)
        class SelectionPresentation(widget: Widget, val options: List<SelectionListItem>) :
            WidgetPresentation(widget, true)
        class ActionListPresentation(widget: Widget, val actions: List<ActionListItem>) :
            WidgetPresentation(widget, false)

        enum class WidgetType(val hasActiveState: Boolean) {
            Unknown(false),
            Light(true),
            PowerOutlet(true),
            Switch(true),
            Rollershutter(false),
            Window(false),
            Door(false),
            DoorLock(false),
            Gate(false),
            Fan(true),
            Alarm(true),
            Garage(false),
            Player(false),
            Thermostat(false)
        }

        fun guessType(): WidgetType {
            // First attempt: icon mapping
            val iconToTypeMapping = mapOf(
                "lightbulb" to WidgetType.Light,
                "light" to WidgetType.Light,
                "slider" to WidgetType.Light,
                "lock" to WidgetType.DoorLock,
                "fan" to WidgetType.Fan,
                "fan_box" to WidgetType.Fan,
                "fan_ceiling" to WidgetType.Fan,
                "blinds" to WidgetType.Rollershutter,
                "rollershutter" to WidgetType.Rollershutter,
                "window" to WidgetType.Window,
                "switch" to WidgetType.Switch,
                "wallswitch" to  WidgetType.Switch,
                "power" to WidgetType.PowerOutlet,
                "poweroutlet" to WidgetType.PowerOutlet,
                "poweroutlet_eu" to WidgetType.PowerOutlet,
                "door" to WidgetType.Door,
                "frontdoor" to WidgetType.Door,
                "alarm" to WidgetType.Alarm,
                "garage" to WidgetType.Garage,
                "garagedoor" to WidgetType.Garage,
                "garage_detached" to WidgetType.Gate,
                "garage_detached_selected" to WidgetType.Garage
            )

            iconToTypeMapping[widget.icon?.icon]?.let { type ->
                return type
            }

            val item = widget.item ?: return WidgetType.Unknown

            // Shortcut roller shutters
            if (item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
                return WidgetType.Rollershutter
            }

            // Second attempt: use category
            iconToTypeMapping[item.category?.lowercase()?.substringAfterLast(':')]?.let { type ->
                return type
            }

            // Third attempt: use tags
            val tagToTypeMapping = listOf(
                Item.Tag.Blinds to WidgetType.Rollershutter,
                Item.Tag.Car to WidgetType.Garage,
                Item.Tag.Carport to WidgetType.Garage,
                Item.Tag.Garage to WidgetType.Garage,
                Item.Tag.GarageDoor to WidgetType.Garage,
                Item.Tag.Light to WidgetType.Light,
                Item.Tag.LightStripe to WidgetType.Light,
                Item.Tag.Lightbulb to WidgetType.Light,
                Item.Tag.Alarm to WidgetType.Alarm,
                Item.Tag.AlarmSystem to WidgetType.Alarm,
                Item.Tag.Siren to WidgetType.Alarm,
                Item.Tag.CeilingFan to WidgetType.Fan,
                Item.Tag.Fan to WidgetType.Fan,
                // door tags - with least specific (Door) last
                Item.Tag.CellarDoor to WidgetType.Door,
                Item.Tag.FrontDoor to WidgetType.Door,
                Item.Tag.InnerDoor to WidgetType.Door,
                Item.Tag.SideDoor to WidgetType.Door,
                Item.Tag.Gate to WidgetType.Gate,
                Item.Tag.Door to WidgetType.Door,
                Item.Tag.HeatingCoolingMode to WidgetType.Thermostat,
                Item.Tag.TargetTemperature to WidgetType.Thermostat,
                Item.Tag.Temperature to WidgetType.Thermostat,
                Item.Tag.Lock to WidgetType.DoorLock,
                Item.Tag.Window to WidgetType.Window,
                Item.Tag.PowerOutlet to WidgetType.PowerOutlet,
                Item.Tag.Switch to WidgetType.Switch,
                Item.Tag.WallSwitch to WidgetType.Switch,
            )

            tagToTypeMapping.forEach { (tag, type) ->
                if (item.tags.contains(tag)) return type
            }

            // Last attempt: use item type
            return when {
                item.isOfTypeOrGroupType(Item.Type.Contact) -> WidgetType.Window
                item.isOfTypeOrGroupType(Item.Type.Player) -> WidgetType.Player
                item.isOfTypeOrGroupType(Item.Type.Switch) -> WidgetType.Switch
                item.isOfTypeOrGroupType(Item.Type.Dimmer) -> WidgetType.Light
                item.isOfTypeOrGroupType(Item.Type.Color) -> WidgetType.Light
                else -> WidgetType.Unknown
            }
        }

        fun determineIcon(carContext: CarContext): CarIcon {
            val widgetType = guessType()
            val isOn = widget.item?.state?.asBoolean == true

            val iconResourceId = when (widgetType) {
                WidgetType.PowerOutlet ->
                    if (isOn) R.drawable.car_icon_power_outlet_on else R.drawable.car_icon_power_outlet_off

                WidgetType.Alarm ->
                    if (isOn) R.drawable.car_icon_alarm_on else R.drawable.car_icon_alarm_off

                WidgetType.Fan -> R.drawable.car_icon_fan

                WidgetType.Rollershutter -> R.drawable.car_icon_roller_shutter

                WidgetType.Garage -> R.drawable.car_icon_garage

                WidgetType.Gate -> R.drawable.car_icon_gate

                WidgetType.Door -> R.drawable.car_icon_door

                WidgetType.Window -> R.drawable.car_icon_window

                WidgetType.Thermostat -> R.drawable.car_icon_thermostat

                WidgetType.Switch ->
                    if (isOn) R.drawable.car_icon_switch_on else R.drawable.car_icon_switch_off

                WidgetType.Light ->
                    if (isOn) R.drawable.car_icon_light_on else R.drawable.car_icon_light_off

                WidgetType.Player -> R.drawable.car_icon_player

                WidgetType.DoorLock -> R.drawable.car_icon_door_lock

                else -> R.drawable.ic_openhab_appicon_24dp
            }

            val icon = IconCompat.createWithResource(carContext, iconResourceId)
            val isActive = isOn && hasActiveState && widgetType.hasActiveState
            return CarIcon.Builder(icon)
                .setTint(if (isActive) CarColor.PRIMARY else CarColor.SECONDARY)
                .build()
        }
    }
}
