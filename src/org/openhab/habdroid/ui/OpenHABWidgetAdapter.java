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

package org.openhab.habdroid.ui;

import java.util.List;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABWidget;

import com.loopj.android.image.SmartImageView;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * This class provides openHAB widgets adapter for list view.
 * 
 * @author Victor Belov
 *
 */

public class OpenHABWidgetAdapter extends ArrayAdapter<OpenHABWidget> {
	public static final int TYPE_GENERICITEM = 0;
	public static final int TYPE_FRAME = 1;
	public static final int TYPE_GROUP = 2;
	public static final int TYPE_SWITCH = 3;
	public static final int TYPE_TEXT = 4;
	public static final int TYPES_COUNT = 5;
	private String openHABBaseUrl = "http://demo.openhab.org:8080/";

	public OpenHABWidgetAdapter(Context context, int resource,
			List<OpenHABWidget> objects) {
		super(context, resource, objects);
	}

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
    	RelativeLayout widgetView;
    	int widgetLayout = 0;
    	OpenHABWidget openHABWidget = getItem(position);
    	switch (this.getItemViewType(position)) {
    	case TYPE_FRAME:
    		widgetLayout = R.layout.openhabwidgetlist_frameitem;
    		break;
    	case TYPE_GROUP:
    		widgetLayout = R.layout.openhabwidgetlist_groupitem;
    		break;
    	case TYPE_SWITCH:
    		widgetLayout = R.layout.openhabwidgetlist_switchitem;
    		break;
    	case TYPE_TEXT:
    		widgetLayout = R.layout.openhabwidgetlist_textitem;
    		break;
    	default:
    		widgetLayout = R.layout.openhabwidgetlist_genericitem;
    		break;
    	}
    	if(convertView==null) {
    		widgetView = new RelativeLayout(getContext());
    		String inflater = Context.LAYOUT_INFLATER_SERVICE;
    		LayoutInflater vi;
    		vi = (LayoutInflater)getContext().getSystemService(inflater);
    		vi.inflate(widgetLayout, widgetView, true);
    	} else {
    		widgetView = (RelativeLayout) convertView;
    	}
		TextView labelTextView;
    	switch (getItemViewType(position)) {
    	case TYPE_FRAME:
    		labelTextView = (TextView)widgetView.findViewById(R.id.framelabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		widgetView.setClickable(false);
    		break;
    	case TYPE_GROUP:
    		labelTextView = (TextView)widgetView.findViewById(R.id.grouplabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		SmartImageView groupImage = (SmartImageView)widgetView.findViewById(R.id.groupimage);
    		groupImage.setImageUrl(openHABBaseUrl + "images/" +
    				openHABWidget.getIcon() + ".png");
    		break;
    	case TYPE_SWITCH:
    		labelTextView = (TextView)widgetView.findViewById(R.id.switchlabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		Switch switchSwitch = (Switch)widgetView.findViewById(R.id.switchswitch);
    		if (openHABWidget.hasItem()) {
    			if (openHABWidget.getItem().getState().equals("ON")) {
    				switchSwitch.setChecked(true);
    			} else {
    				switchSwitch.setChecked(false);    				
    			}
    		}
    		switchSwitch.setTag(openHABWidget.getItem());
    		switchSwitch.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Switch switchSwitch = (Switch)v;
					OpenHABItem linkedItem = (OpenHABItem)switchSwitch.getTag();
					if (switchSwitch.isChecked()) {
						linkedItem.sendCommand("ON");
					} else {
						linkedItem.sendCommand("OFF");						
					}
				}
    			
    		});
    		SmartImageView switchImage = (SmartImageView)widgetView.findViewById(R.id.switchimage);
    		switchImage.setImageUrl(openHABBaseUrl + "images/" +
    				openHABWidget.getIcon() + ".png");
    		break;
    	case TYPE_TEXT:
    		labelTextView = (TextView)widgetView.findViewById(R.id.textlabel);
    		String[] splitString = {};
    		splitString = openHABWidget.getLabel().split("\\[|\\]");
    		if (labelTextView != null)
    			labelTextView.setText(splitString[0]);
    		TextView valueTextView = (TextView)widgetView.findViewById(R.id.textvalue);
    		if (valueTextView != null) 
    			if (splitString.length > 1) {
    				valueTextView.setText(splitString[1]);
    			} else {
    				valueTextView.setText("");
    			}
    		SmartImageView textImage = (SmartImageView)widgetView.findViewById(R.id.textimage);
    		textImage.setImageUrl(openHABBaseUrl + "images/" +
    				openHABWidget.getIcon() + ".png");
    		break;
    	default:
    		labelTextView = (TextView)widgetView.findViewById(R.id.itemlabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		SmartImageView itemImage = (SmartImageView)widgetView.findViewById(R.id.itemimage);
    		itemImage.setImageUrl(openHABBaseUrl + "images/" +
    				openHABWidget.getIcon() + ".png");
    		break;
    	}
    	return widgetView;
    }
    
    @Override
    public int getViewTypeCount() {
        return TYPES_COUNT;
    }
    
    @Override
    public int getItemViewType(int position) {
    	OpenHABWidget openHABWidget = getItem(position);
    	if (openHABWidget.getType().equals("Frame")) {
    		return TYPE_FRAME;
    	} else if (openHABWidget.getType().equals("Group")) {
    		return TYPE_GROUP;
    	} else if (openHABWidget.getType().equals("Switch")) {
    		return TYPE_SWITCH;
    	} else if (openHABWidget.getType().equals("Text")) {
    		return TYPE_TEXT;
    	} else {
    		return TYPE_GENERICITEM;
    	}
    }
	
    public void setOpenHABBaseUrl(String baseUrl) {
    	openHABBaseUrl = baseUrl;
    }
    
}
