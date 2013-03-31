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
