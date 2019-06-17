package org.openhab.habdroid.util

import android.content.Context
import android.text.InputType
import androidx.annotation.StringRes
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Widget
import java.util.*

class SuggestedCommandsFactory(private val context: Context, private val showUndef: Boolean) {
    fun fill(widget: Widget?): SuggestedCommands {
        val suggestedCommands = SuggestedCommands()
        if (widget?.item == null) {
            return suggestedCommands
        }

        for ((value, label) in widget.mappingsOrItemOptions) {
            add(suggestedCommands, value, label)
        }

        if (widget.type === Widget.Type.Setpoint || widget.type === Widget.Type.Slider) {
            val state = widget.state?.asNumber
            if (state != null) {
                add(suggestedCommands, state.toString())
                add(suggestedCommands, ParsedState.NumberState.withValue(state, widget.minValue).toString())
                add(suggestedCommands, ParsedState.NumberState.withValue(state, widget.maxValue).toString())
                if (widget.switchSupport) {
                    addOnOffCommands(suggestedCommands)
                }
            }
        }

        fill(widget.item, suggestedCommands)
        return suggestedCommands
    }

    fun fill(item: Item?): SuggestedCommands {
        val suggestedCommands = SuggestedCommands()
        if (item != null) {
            fill(item, suggestedCommands)
        }
        return suggestedCommands
    }

    private fun fill(item: Item, suggestedCommands: SuggestedCommands) = when {
        item.isOfTypeOrGroupType(Item.Type.Color) -> {
            addOnOffCommands(suggestedCommands)
            addIncreaseDecreaseCommands(suggestedCommands)
            if (item.state != null) {
                add(suggestedCommands, item.state.asString, R.string.nfc_action_current_color)
            }
            addCommonPercentCommands(suggestedCommands)
        }
        item.isOfTypeOrGroupType(Item.Type.Contact) -> {
            // Contact items cannot receive commands
            suggestedCommands.shouldShowCustom = false
        }
        item.isOfTypeOrGroupType(Item.Type.Dimmer) -> {
            addOnOffCommands(suggestedCommands)
            addIncreaseDecreaseCommands(suggestedCommands)
            addCommonPercentCommands(suggestedCommands)
            suggestedCommands.inputTypeFlags = INPUT_TYPE_SINGED_DECIMAL_NUMBER
        }
        item.isOfTypeOrGroupType(Item.Type.Number) -> {
            // Don't suggest numbers that might be totally out of context if there's already
            // at least one command
            if (suggestedCommands.commands.isEmpty()) {
                addCommonNumberCommands(suggestedCommands)
            }
            suggestedCommands.inputTypeFlags = INPUT_TYPE_SINGED_DECIMAL_NUMBER
        }
        item.isOfTypeOrGroupType(Item.Type.NumberWithDimension) -> {
            val numberState = item.state?.asNumber
            if (numberState != null) {
                add(suggestedCommands, numberState.toString())
            } else {}
        }
        item.isOfTypeOrGroupType(Item.Type.Player) -> {
            add(suggestedCommands, "PLAY", R.string.nfc_action_play)
            add(suggestedCommands, "PAUSE", R.string.nfc_action_pause)
            add(suggestedCommands, "NEXT", R.string.nfc_action_next)
            add(suggestedCommands, "PREVIOUS", R.string.nfc_action_previous)
            add(suggestedCommands, "REWIND", R.string.nfc_action_rewind)
            add(suggestedCommands, "FASTFORWARD", R.string.nfc_action_fastforward)
            suggestedCommands.shouldShowCustom = false
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
            } else {}
        }
        item.isOfTypeOrGroupType(Item.Type.Switch) -> {
            addOnOffCommands(suggestedCommands)
            suggestedCommands.shouldShowCustom = false
        }
        showUndef -> {
            add(suggestedCommands, "UNDEF", R.string.nfc_action_undefined)
        }
        else -> {}
    }

    private fun add(suggestedCommands: SuggestedCommands, commandAndLabel: String) {
        add(suggestedCommands, commandAndLabel, commandAndLabel)
    }

    private fun add(suggestedCommands: SuggestedCommands, command: String, @StringRes label: Int) {
        add(suggestedCommands, command, context.getString(label))
    }

    private fun add(suggestedCommands: SuggestedCommands, command: String, label: String) {
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
            add(suggestedCommands, command, "$command %")
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
        var shouldShowCustom = true
        var inputTypeFlags = InputType.TYPE_CLASS_TEXT
    }

    companion object {
        private const val INPUT_TYPE_DECIMAL_NUMBER =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        private const val INPUT_TYPE_SINGED_DECIMAL_NUMBER =
            INPUT_TYPE_DECIMAL_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    }
}
