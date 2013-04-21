/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.model;

import java.util.ArrayList;

public class OpenHABNFCActionList {
	private ArrayList<String> actionNames;
	private ArrayList<String> actionCommands;

	public OpenHABNFCActionList(OpenHABWidget openHABWidget) {
		actionNames = new ArrayList<String>();
		actionCommands = new ArrayList<String>();
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
				if (openHABWidget.getItem().getType().equals("SwitchItem")) {
					actionNames.add("On");
					actionCommands.add("ON");
					actionNames.add("Off");
					actionCommands.add("OFF");
					actionNames.add("Toggle");
					actionCommands.add("TOGGLE");
				} else if (openHABWidget.getItem().getType().equals("RollershutterItem")) {
					actionNames.add("Up");
					actionCommands.add("UP");
					actionNames.add("Down");
					actionCommands.add("DOWN");					
					actionNames.add("Toggle");
					actionCommands.add("TOGGLE");					
				}
			} else if (openHABWidget.getType().equals("Colorpicker")) {
				actionNames.add("On");
				actionCommands.add("ON");
				actionNames.add("Off");
				actionCommands.add("OFF");
				actionNames.add("Toggle");
				actionCommands.add("TOGGLE");
				if (openHABWidget.getItem() != null) {
					actionNames.add("Current Color");
					actionCommands.add(openHABWidget.getItem().getState());
				}
			}
		}
	}
	
	public String[] getNames() {
		return this.actionNames.toArray(new String[this.actionNames.size()]);
	}
	
	public String[] getCommands() {
		return this.actionCommands.toArray(new String[this.actionCommands.size()]);
	}

}
