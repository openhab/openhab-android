package org.openhab.habdroid.model;

import android.os.Parcel;
import android.os.Parcelable;

public class OpenHABPage implements Parcelable {
	private String pageUrl;
	private int widgetListPosition;
	
	public OpenHABPage (String pageUrl, int widgetListPosition) {
		this.pageUrl = pageUrl;
		this.widgetListPosition = widgetListPosition;
	}
	
	public String getPageUrl() {
		return pageUrl;
	}
	public void setPageUrl(String pageUrl) {
		this.pageUrl = pageUrl;
	}
	public int getWidgetListPosition() {
		return widgetListPosition;
	}
	public void setWidgetListPosition(int widgetListPosition) {
		this.widgetListPosition = widgetListPosition;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(this.pageUrl);
		out.writeInt(this.widgetListPosition);
	}
}
