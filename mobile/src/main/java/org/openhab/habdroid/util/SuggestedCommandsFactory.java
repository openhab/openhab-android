package org.openhab.habdroid.util;

import android.content.Context;
import android.text.InputType;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.LabeledValue;
import org.openhab.habdroid.model.ParsedState;
import org.openhab.habdroid.model.Widget;

import java.util.ArrayList;
import java.util.List;

public class SuggestedCommandsFactory {
    private final static int INPUT_TYPE_DECIMAL_NUMBER = InputType.TYPE_CLASS_NUMBER
            | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    private final static int INPUT_TYPE_SINGED_DECIMAL_NUMBER = INPUT_TYPE_DECIMAL_NUMBER
            | InputType.TYPE_NUMBER_FLAG_SIGNED;
    private Context mContext;
    private boolean mShowUndef;

    public SuggestedCommandsFactory(Context context, boolean showUndef) {
        mContext = context;
        mShowUndef = showUndef;
    }
    
    public SuggestedCommands fill(@Nullable Widget widget) {
        SuggestedCommands suggestedCommands = new SuggestedCommands();
        if (widget == null || widget.item() == null) {
            return suggestedCommands;
        }

        if (widget.hasMappingsOrItemOptions()) {
            for (LabeledValue mapping : widget.getMappingsOrItemOptions()) {
                add(suggestedCommands, mapping.value(), mapping.label());
            }
        }

        if (widget.type() == Widget.Type.Setpoint || widget.type() == Widget.Type.Slider) {
            if (widget.state() != null && widget.state().asNumber() != null) {
                ParsedState.NumberState state = widget.state().asNumber();
                add(suggestedCommands, state.toString());
                add(suggestedCommands, ParsedState.NumberState.withValue(state, widget.minValue()).toString());
                add(suggestedCommands, ParsedState.NumberState.withValue(state, widget.maxValue()).toString());
                if (widget.switchSupport()) {
                    addOnOffCommands(suggestedCommands);
                }
            }
        }

        return fill(widget.item(), suggestedCommands);
    }

    public SuggestedCommands fill(@Nullable Item item) {
        return fill(item, null);
    }

    private SuggestedCommands fill(@Nullable Item item, SuggestedCommands suggestedCommands) {
        if (suggestedCommands == null) {
            suggestedCommands = new SuggestedCommands();
        }
        if (item == null) {
            return suggestedCommands;
        }

        if (item.isOfTypeOrGroupType(Item.Type.Color)) {
            addOnOffCommands(suggestedCommands);
            addIncreaseDecreaseCommands(suggestedCommands);
            if (item.state() != null) {
                add(suggestedCommands, item.state().asString(), R.string.nfc_action_current_color);
            }
            addCommonPercentCommands(suggestedCommands);
        } else if (item.isOfTypeOrGroupType(Item.Type.Contact)) {
            // Contact items cannot receive commands
            suggestedCommands.shouldShowCustom = false;
        } else if (item.isOfTypeOrGroupType(Item.Type.Dimmer)) {
            addOnOffCommands(suggestedCommands);
            addIncreaseDecreaseCommands(suggestedCommands);
            addCommonPercentCommands(suggestedCommands);
            suggestedCommands.inputTypeFlags = INPUT_TYPE_SINGED_DECIMAL_NUMBER;
        } else if (item.isOfTypeOrGroupType(Item.Type.Number)) {
            // Don't suggest numbers that might be totally out of context if there's already
            // at least one command
            if (suggestedCommands.commands.isEmpty()) {
                addCommonNumberCommands(suggestedCommands);
            }
            suggestedCommands.inputTypeFlags = INPUT_TYPE_SINGED_DECIMAL_NUMBER;
        } else if (item.isOfTypeOrGroupType(Item.Type.NumberWithDimension)) {
            if (item.state() != null && item.state().asNumber() != null) {
                add(suggestedCommands, item.state().asNumber().toString());
            }
        } else if (item.isOfTypeOrGroupType(Item.Type.Player)) {
            add(suggestedCommands, "PLAY", R.string.nfc_action_play);
            add(suggestedCommands, "PAUSE", R.string.nfc_action_pause);
            add(suggestedCommands, "NEXT", R.string.nfc_action_next);
            add(suggestedCommands, "PREVIOUS", R.string.nfc_action_previous);
            add(suggestedCommands, "REWIND", R.string.nfc_action_rewind);
            add(suggestedCommands, "FASTFORWARD", R.string.nfc_action_fastforward);
            suggestedCommands.shouldShowCustom = false;
        } else if (item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
            add(suggestedCommands, "UP", R.string.nfc_action_up);
            add(suggestedCommands, "DOWN", R.string.nfc_action_down);
            add(suggestedCommands, "TOGGLE", R.string.nfc_action_toggle);
            add(suggestedCommands, "MOVE", R.string.nfc_action_move);
            add(suggestedCommands, "STOP", R.string.nfc_action_stop);
            addCommonPercentCommands(suggestedCommands);
            suggestedCommands.inputTypeFlags = INPUT_TYPE_DECIMAL_NUMBER;
        } else if (item.isOfTypeOrGroupType(Item.Type.StringItem)) {
            if (mShowUndef) {
                add(suggestedCommands, "", R.string.nfc_action_empty_string);
                add(suggestedCommands, "UNDEF", R.string.nfc_action_undefined);
            }
        } else if (item.isOfTypeOrGroupType(Item.Type.Switch)) {
            addOnOffCommands(suggestedCommands);
            suggestedCommands.shouldShowCustom = false;
        } else {
            if (mShowUndef) {
                add(suggestedCommands, "UNDEF", R.string.nfc_action_undefined);
            }
        }

        return suggestedCommands;
    }
    
    private void add(SuggestedCommands suggestedCommands, String commandAndLabel) {
        add(suggestedCommands, commandAndLabel, commandAndLabel);
    }

    private void add(SuggestedCommands suggestedCommands, String command, @StringRes int label) {
        add(suggestedCommands, command, mContext.getString(label));
    }

    private void add(SuggestedCommands suggestedCommands, String command, String label) {
        if (!suggestedCommands.commands.contains(command)) {
            suggestedCommands.commands.add(command);
            suggestedCommands.labels.add(label);
        }
    }

    private void addCommonNumberCommands(SuggestedCommands suggestedCommands) {
        for (String command : new String[]{"0", "33", "50", "66", "100"}) {
            add(suggestedCommands, command);
        }
    }

    private void addCommonPercentCommands(SuggestedCommands suggestedCommands) {
        for (String command : new String[]{"0", "33", "50", "66", "100"}) {
            add(suggestedCommands, command, String.format("%s %%", command));
        }
    }

    private void addOnOffCommands(SuggestedCommands suggestedCommands) {
        add(suggestedCommands, "ON", R.string.nfc_action_on);
        add(suggestedCommands, "OFF", R.string.nfc_action_off);
        add(suggestedCommands, "TOGGLE", R.string.nfc_action_toggle);
    }

    private void addIncreaseDecreaseCommands(SuggestedCommands suggestedCommands) {
        add(suggestedCommands, "INCREASE", R.string.nfc_action_increase);
        add(suggestedCommands, "DECREASE", R.string.nfc_action_decrease);
    }

    public class SuggestedCommands {
        public List<String> commands = new ArrayList<>();
        public List<String> labels = new ArrayList<>();
        public boolean shouldShowCustom = true;
        public int inputTypeFlags = InputType.TYPE_CLASS_TEXT;
    }
}
