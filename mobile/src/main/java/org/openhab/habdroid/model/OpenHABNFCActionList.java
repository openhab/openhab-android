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
        if (openHABWidget.getItem() != null) {
            // If widget have mappings, we will populate names and commands with values
            // from this mappings
            if (openHABWidget.hasMappings()) {
                // Else we only can do it for Switch widget with On/Off/Toggle commands
                for (int i = 0; i < openHABWidget.getMappings().size(); i++) {
                    actionNames.add(openHABWidget.getMappings().get(i).getLabel());
                    actionCommands.add(openHABWidget.getMappings().get(i).getCommand());
                }
            } else if (openHABWidget.getType().equals("Switch")) {
                //SwitchItem changed to Switch in later builds of OH2
                if (openHABWidget.getItem().getType().equals("SwitchItem")
                        || "Switch".equals(openHABWidget.getItem().getType())
                        || "Switch".equals(openHABWidget.getItem().getGroupType())) {
                    actionNames.add(ctx.getString(R.string.nfc_action_on));
                    actionCommands.add("ON");
                    actionNames.add(ctx.getString(R.string.nfc_action_off));
                    actionCommands.add("OFF");
                    actionNames.add(ctx.getString(R.string.nfc_action_toggle));
                    actionCommands.add("TOGGLE");
                    //RollerShutterItem changed to RollerShutter in later builds of OH2
                } else if ("RollershutterItem".equals(openHABWidget.getItem().getType())
                        || "Rollershutter".equals(openHABWidget.getItem().getType())
                        || "Rollershutter".equals(openHABWidget.getItem().getGroupType())) {
                    actionNames.add(ctx.getString(R.string.nfc_action_up));
                    actionCommands.add("UP");
                    actionNames.add(ctx.getString(R.string.nfc_action_down));
                    actionCommands.add("DOWN");
                    actionNames.add(ctx.getString(R.string.nfc_action_toggle));
                    actionCommands.add("TOGGLE");
                }
            } else if (openHABWidget.getType().equals("Colorpicker")) {
                actionNames.add(ctx.getString(R.string.nfc_action_on));
                actionCommands.add("ON");
                actionNames.add(ctx.getString(R.string.nfc_action_off));
                actionCommands.add("OFF");
                actionNames.add(ctx.getString(R.string.nfc_action_toggle));
                actionCommands.add("TOGGLE");
                if (openHABWidget.getItem() != null) {
                    actionNames.add(ctx.getString(R.string.nfc_action_current_color));
                    actionCommands.add(openHABWidget.getItem().getState());
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
