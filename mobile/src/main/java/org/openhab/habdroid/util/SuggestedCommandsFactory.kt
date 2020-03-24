/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

package org.openhab.habdroid.util

import android.content.Context
import android.text.InputType
import androidx.annotation.StringRes
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import java.util.ArrayList

class SuggestedCommandsFactory(private val context: Context, private val showUndef: Boolean) {
    fun fill(widget: Widget?, forItemUpdate: Boolean = false): SuggestedCommands {
        val suggestedCommands = SuggestedCommands()
        if (widget?.item == null || widget.type == Widget.Type.Chart) {
            return suggestedCommands
        }

        for ((value, label) in widget.mappingsOrItemOptions) {
            add(suggestedCommands, value, label)
        }

        if (widget.type === Widget.Type.Setpoint || widget.type === Widget.Type.Slider) {
            val state = widget.state?.asNumber
            if (state != null) {
                add(suggestedCommands, state.toString())
                add(suggestedCommands, state.withValue(widget.minValue).toString())
                add(suggestedCommands, state.withValue(widget.maxValue).toString())
                if (widget.switchSupport) {
                    addOnOffCommands(suggestedCommands)
                }
            }
        }

        fill(widget.item, suggestedCommands, forItemUpdate)
        return suggestedCommands
    }

    fun fill(item: Item?, forItemUpdate: Boolean = false): SuggestedCommands {
        val suggestedCommands = SuggestedCommands()
        if (item != null) {
            fill(item, suggestedCommands, forItemUpdate)
        }
        return suggestedCommands
    }

    private fun fill(item: Item, suggestedCommands: SuggestedCommands, forItemUpdate: Boolean) = when {
        item.isOfTypeOrGroupType(Item.Type.Color) -> {
            addOnOffCommands(suggestedCommands)
            addIncreaseDecreaseCommands(suggestedCommands)
            if (item.state != null) {
                add(suggestedCommands, item.state.asString, R.string.nfc_action_current_color)
            }
            addCommonPercentCommands(suggestedCommands)
            suggestedCommands.shouldShowCustom = true
        }
        item.isOfTypeOrGroupType(Item.Type.Contact) -> {
            @Suppress("ControlFlowWithEmptyBody")
            if (forItemUpdate) {
                add(suggestedCommands, "OPEN", R.string.nfc_action_open)
                add(suggestedCommands, "CLOSED", R.string.nfc_action_closed)
                add(suggestedCommands, "TOGGLE", R.string.nfc_action_toggled)
            } else {
                // Contact Items cannot receive commands
            }
        }
        item.isOfTypeOrGroupType(Item.Type.Dimmer) -> {
            addOnOffCommands(suggestedCommands)
            addIncreaseDecreaseCommands(suggestedCommands)
            addCommonPercentCommands(suggestedCommands)
            suggestedCommands.inputTypeFlags = INPUT_TYPE_SINGED_DECIMAL_NUMBER
            suggestedCommands.shouldShowCustom = true
        }
        item.isOfTypeOrGroupType(Item.Type.Number) -> {
            // Don't suggest numbers that might be totally out of context if there's already
            // at least one command
            if (suggestedCommands.commands.isEmpty()) {
                addCommonNumberCommands(suggestedCommands)
            }
            item.state?.asString?.let { value -> add(suggestedCommands, value) }
            suggestedCommands.inputTypeFlags = INPUT_TYPE_SINGED_DECIMAL_NUMBER
            suggestedCommands.shouldShowCustom = true
        }
        item.isOfTypeOrGroupType(Item.Type.NumberWithDimension) -> {
            val numberState = item.state?.asNumber
            if (numberState != null) {
                add(suggestedCommands, numberState.toString())
            }
            suggestedCommands.shouldShowCustom = true
        }
        item.isOfTypeOrGroupType(Item.Type.Player) -> {
            add(suggestedCommands, "PLAY", R.string.nfc_action_play)
            add(suggestedCommands, "PAUSE", R.string.nfc_action_pause)
            add(suggestedCommands, "TOGGLE", R.string.nfc_action_toggle)
            add(suggestedCommands, "NEXT", R.string.nfc_action_next)
            add(suggestedCommands, "PREVIOUS", R.string.nfc_action_previous)
            add(suggestedCommands, "REWIND", R.string.nfc_action_rewind)
            add(suggestedCommands, "FASTFORWARD", R.string.nfc_action_fastforward)
        }
        item.isOfTypeOrGroupType(Item.Type.Rollershutter) -> {
            add(suggestedCommands, "UP", R.string.nfc_action_up)
            add(suggestedCommands, "DOWN", R.string.nfc_action_down)
            add(suggestedCommands, "TOGGLE", R.string.nfc_action_toggle)
            add(suggestedCommands, "MOVE", R.string.nfc_action_move)
            add(suggestedCommands, "STOP", R.string.nfc_action_stop)
            addCommonPercentCommands(suggestedCommands)
            suggestedCommands.inputTypeFlags = INPUT_TYPE_DECIMAL_NUMBER
        }
        item.isOfTypeOrGroupType(Item.Type.StringItem) -> {
            if (showUndef) {
                add(suggestedCommands, "", R.string.nfc_action_empty_string)
                add(suggestedCommands, "UNDEF", R.string.nfc_action_undefined)
            }
            item.state?.asString?.let { value -> add(suggestedCommands, value) }
            suggestedCommands.shouldShowCustom = true
        }
        item.isOfTypeOrGroupType(Item.Type.Switch) -> {
            addOnOffCommands(suggestedCommands)
        }
        showUndef -> {
            add(suggestedCommands, "UNDEF", R.string.nfc_action_undefined)
            suggestedCommands.shouldShowCustom = true
        }
        else -> {}
    }

    private fun add(suggestedCommands: SuggestedCommands, command: String, @StringRes label: Int) {
        add(suggestedCommands, command, context.getString(label))
    }

    private fun add(suggestedCommands: SuggestedCommands, command: String, label: String = command) {
        if (command !in suggestedCommands.commands) {
            suggestedCommands.commands.add(command)
            suggestedCommands.labels.add(label)
        }
    }

    private fun addCommonNumberCommands(suggestedCommands: SuggestedCommands) {
        for (command in arrayOf("0", "33", "50", "66", "100")) {
            add(suggestedCommands, command)
        }
    }

    private fun addCommonPercentCommands(suggestedCommands: SuggestedCommands) {
        for (command in arrayOf("0", "33", "50", "66", "100")) {
            add(suggestedCommands, command, "$command\u00A0%")
        }
    }

    private fun addOnOffCommands(suggestedCommands: SuggestedCommands) {
        add(suggestedCommands, "ON", R.string.nfc_action_on)
        add(suggestedCommands, "OFF", R.string.nfc_action_off)
        add(suggestedCommands, "TOGGLE", R.string.nfc_action_toggle)
    }

    private fun addIncreaseDecreaseCommands(suggestedCommands: SuggestedCommands) {
        add(suggestedCommands, "INCREASE", R.string.nfc_action_increase)
        add(suggestedCommands, "DECREASE", R.string.nfc_action_decrease)
    }

    inner class SuggestedCommands {
        var commands: MutableList<String> = ArrayList()
        var labels: MutableList<String> = ArrayList()
        var shouldShowCustom = false
        var inputTypeFlags = InputType.TYPE_CLASS_TEXT
    }

    companion object {
        private const val INPUT_TYPE_DECIMAL_NUMBER =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        private const val INPUT_TYPE_SINGED_DECIMAL_NUMBER =
            INPUT_TYPE_DECIMAL_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    }
}
