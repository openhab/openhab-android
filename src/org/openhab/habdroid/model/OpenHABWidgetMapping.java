package org.openhab.habdroid.model;

public class OpenHABWidgetMapping {
	private String command;
	private String label;
	
	public OpenHABWidgetMapping(String command, String label) {
		this.command = command;
		this.label = label;
	}
	public String getCommand() {
		return command;
	}
	public void setCommand(String command) {
		this.command = command;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
}
