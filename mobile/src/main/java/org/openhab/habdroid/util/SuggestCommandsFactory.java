package org.openhab.habdroid.util;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.LabeledValue;
import org.openhab.habdroid.model.ParsedState;
import org.openhab.habdroid.model.Widget;

import java.util.List;

public class SuggestCommandsFactory {
    Context mContext;
    List<String> mCommands;
    List<String> mLabels;
    boolean mShowUndef;

    public SuggestCommandsFactory(Context context, List<String> commands, List<String> labels,
            boolean showUndef) {
        mContext = context;
        mCommands = commands;
        mLabels = labels;
        mShowUndef = showUndef;
    }
    
    public void fill(@Nullable Widget widget) {
        if (widget == null || widget.item() == null) {
            return;
        }

        if (widget.hasMappingsOrItemOptions()) {
            for (LabeledValue mapping : widget.getMappingsOrItemOptions()) {
                add(mapping.value(), mapping.label());
            }
        }

        if (widget.type() == Widget.Type.Setpoint || widget.type() == Widget.Type.Slider) {
            if (widget.state() != null && widget.state().asNumber() != null) {
                ParsedState.NumberState state = widget.state().asNumber();
                add(state.toString());
                add(ParsedState.NumberState.withValue(state, widget.minValue()).toString());
                add(ParsedState.NumberState.withValue(state, widget.maxValue()).toString());
                if (widget.switchSupport()) {
                    addOnOffCommands();
                }
            }
        }

        fill(widget.item());
    }

    public void fill(@Nullable Item item) {
        if (item == null) {
            return;
        }

        if (item.isOfTypeOrGroupType(Item.Type.Color)) {
            addOnOffCommands();
            addIncreaseDecreaseCommands();
            if (item.state() != null) {
                add(item.state().asString(), R.string.nfc_action_current_color);
            }
            addCommonPercentCommands();
        } else if (item.isOfTypeOrGroupType(Item.Type.Contact)) {
            // Contact items cannot receive commands
        } else if (item.isOfTypeOrGroupType(Item.Type.Dimmer)) {
            addOnOffCommands();
            addIncreaseDecreaseCommands();
            addCommonPercentCommands();
        } else if (item.isOfTypeOrGroupType(Item.Type.Number)) {
            // Don't suggest numbers that might be totally out of context if there's already
            // at least one command
            if (mCommands.size() == 0) {
                addCommonNumberCommands();
            }
        } else if (item.isOfTypeOrGroupType(Item.Type.NumberWithDimension)) {
            if (item.state() != null && item.state().asNumber() != null) {
                add(item.state().asNumber().toString());
            }
        } else if (item.isOfTypeOrGroupType(Item.Type.Player)) {
            add("PLAY", R.string.nfc_action_play);
            add("PAUSE", R.string.nfc_action_pause);
            add("NEXT", R.string.nfc_action_next);
            add("PREVIOUS", R.string.nfc_action_previous);
            add("REWIND", R.string.nfc_action_rewind);
            add("FASTFORWARD", R.string.nfc_action_fastforward);
        } else if (item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
            add("UP", R.string.nfc_action_up);
            add("DOWN", R.string.nfc_action_down);
            add("TOGGLE", R.string.nfc_action_toggle);
            add("MOVE", R.string.nfc_action_move);
            add("STOP", R.string.nfc_action_stop);
            addCommonPercentCommands();
        } else if (item.isOfTypeOrGroupType(Item.Type.StringItem)) {
            if (mShowUndef) {
                add("", R.string.nfc_action_empty_string);
                add("UNDEF", R.string.nfc_action_undefined);
            }
        } else if (item.isOfTypeOrGroupType(Item.Type.Switch)) {
            addOnOffCommands();
        } else {
            add("UNDEF", R.string.nfc_action_undefined);
        }
    }
    
    private void add(String commandAndlabel) {
        if (!mCommands.contains(commandAndlabel)) {
            mCommands.add(commandAndlabel);
            mLabels.add(commandAndlabel);
        }
    }

    private void add(String command, @StringRes int label) {
        add(command, mContext.getString(label));
    }

    private void add(String command, String label) {
        if (!mCommands.contains(command)) {
            mCommands.add(command);
            mLabels.add(label);
        }
    }

    private void addCommonNumberCommands() {
        for (String command : new String[]{"0", "33", "50", "66", "100"}) {
            add(command);
        }
    }

    private void addCommonPercentCommands() {
        for (String command : new String[]{"0", "33", "50", "66", "100"}) {
            add(command, String.format("%s %%", command));
        }
    }

    private void addOnOffCommands() {
        add("ON", R.string.nfc_action_on);
        add("OFF", R.string.nfc_action_off);
        add("TOGGLE", R.string.nfc_action_toggle);
    }

    private void addIncreaseDecreaseCommands() {
        add("INCREASE", R.string.nfc_action_increase);
        add("DECREASE", R.string.nfc_action_decrease);
    }
}
