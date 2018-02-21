/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model;

import android.content.Context;

import org.openhab.habdroid.R;

import java.util.ArrayList;

public class OpenHABNFCActionList {
    private ArrayList<String> actionNames;
    private ArrayList<String> actionCommands;

    /**
     * Creates two list with all available nfc actions for an item, one with a user visble label
     * (name) and one with the command. It also adds "Navigate to this sitemap" at the end of the
     * list.
     * @param openHABWidget
     * @param ctx
     */
    public OpenHABNFCActionList(OpenHABWidget openHABWidget, Context ctx) {
        actionNames = new ArrayList<>();
        actionCommands = new ArrayList<>();
        if (openHABWidget.item() != null) {
            // If widget have mappings, we will populate names and commands with values
            // from this mappings
            if (openHABWidget.hasMappings()) {
                // Else we only can do it for Switch widget with On/Off/Toggle commands
                for (OpenHABWidgetMapping mapping : openHABWidget.mappings()) {
                    actionNames.add(mapping.label());
                    actionCommands.add(mapping.command());
                }
            } else if (openHABWidget.type() == OpenHABWidget.Type.Switch) {
                OpenHABItem item = openHABWidget.item();
                if (item.isOfTypeOrGroupType(OpenHABItem.Type.Switch)) {
                    actionNames.add(ctx.getString(R.string.nfc_action_on));
                    actionCommands.add("ON");
                    actionNames.add(ctx.getString(R.string.nfc_action_off));
                    actionCommands.add("OFF");
                    actionNames.add(ctx.getString(R.string.nfc_action_toggle));
                    actionCommands.add("TOGGLE");
                } else if (item.isOfTypeOrGroupType(OpenHABItem.Type.Rollershutter)) {
                    actionNames.add(ctx.getString(R.string.nfc_action_up));
                    actionCommands.add("UP");
                    actionNames.add(ctx.getString(R.string.nfc_action_down));
                    actionCommands.add("DOWN");
                    actionNames.add(ctx.getString(R.string.nfc_action_toggle));
                    actionCommands.add("TOGGLE");
                }
            } else if (openHABWidget.type() == OpenHABWidget.Type.Colorpicker) {
                actionNames.add(ctx.getString(R.string.nfc_action_on));
                actionCommands.add("ON");
                actionNames.add(ctx.getString(R.string.nfc_action_off));
                actionCommands.add("OFF");
                actionNames.add(ctx.getString(R.string.nfc_action_toggle));
                actionCommands.add("TOGGLE");
                if (openHABWidget.item() != null) {
                    actionNames.add(ctx.getString(R.string.nfc_action_current_color));
                    actionCommands.add(openHABWidget.item().state());
                }
            }
        }
        actionNames.add(ctx.getString(R.string.nfc_action_to_sitemap_page));
    }
    
    public String[] getNames() {
        return this.actionNames.toArray(new String[this.actionNames.size()]);
    }
    
    public String[] getCommands() {
        return this.actionCommands.toArray(new String[this.actionCommands.size()]);
    }

}
