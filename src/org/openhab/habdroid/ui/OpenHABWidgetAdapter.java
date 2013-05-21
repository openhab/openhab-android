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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.http.entity.StringEntity;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetMapping;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MySmartImageView;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.VideoView;
import at.bookworm.widget.segcontrol.SegmentedControlButton;

import com.crittercism.app.Crittercism;
import com.loopj.android.http.AsyncHttpResponseHandler;

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
	public static final int TYPE_SLIDER = 5;
	public static final int TYPE_IMAGE = 6;
	public static final int TYPE_SELECTION = 7;
	public static final int TYPE_SECTIONSWITCH = 8;
	public static final int TYPE_ROLLERSHUTTER = 9;
	public static final int TYPE_SETPOINT = 10;
	public static final int TYPE_CHART = 11;
	public static final int TYPE_VIDEO = 12;
	public static final int TYPE_WEB = 13;
	public static final int TYPE_COLOR = 14;
	public static final int TYPES_COUNT = 15;
	private static final String TAG = "OpenHABWidgetAdapter";
	private String openHABBaseUrl = "http://demo.openhab.org:8080/";
	private String openHABUsername = "";
	private String openHABPassword = "";
	private ArrayList<VideoView> videoWidgetList;
	private ArrayList<MySmartImageView> refreshImageList;

	public OpenHABWidgetAdapter(Context context, int resource,
			List<OpenHABWidget> objects) {
		super(context, resource, objects);
		// Initialize video view array
		videoWidgetList = new ArrayList<VideoView>();
		refreshImageList = new ArrayList<MySmartImageView>();
	}

    @SuppressWarnings("deprecation")
	@Override
    public View getView(int position, View convertView, ViewGroup parent) {
    	/* TODO: This definitely needs some huge refactoring */
    	final RelativeLayout widgetView;
		TextView labelTextView;
		TextView valueTextView;
    	int widgetLayout = 0;
    	String[] splitString = {};
    	OpenHABWidget openHABWidget = getItem(position);
    	int screenWidth = ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getWidth();
    	switch (this.getItemViewType(position)) {
    	case TYPE_FRAME:
    		widgetLayout = R.layout.openhabwidgetlist_frameitem;
    		break;
    	case TYPE_GROUP:
    		widgetLayout = R.layout.openhabwidgetlist_groupitem;
    		break;
    	case TYPE_SECTIONSWITCH:
    		widgetLayout = R.layout.openhabwidgetlist_sectionswitchitem;
    		break;
    	case TYPE_SWITCH:
    		widgetLayout = R.layout.openhabwidgetlist_switchitem;
    		break;
    	case TYPE_ROLLERSHUTTER:
    		widgetLayout = R.layout.openhabwidgetlist_rollershutteritem;
    		break;
    	case TYPE_TEXT:
    		widgetLayout = R.layout.openhabwidgetlist_textitem;
    		break;
    	case TYPE_SLIDER:
    		widgetLayout = R.layout.openhabwidgetlist_slideritem;
    		break;
    	case TYPE_IMAGE:
    		widgetLayout = R.layout.openhabwidgetlist_imageitem;
    		break;
    	case TYPE_SELECTION:
    		widgetLayout = R.layout.openhabwidgetlist_selectionitem;
    		break;
    	case TYPE_SETPOINT:
    		widgetLayout = R.layout.openhabwidgetlist_setpointitem;
    		break;
    	case TYPE_CHART:
    		widgetLayout = R.layout.openhabwidgetlist_chartitem;
    		break;
    	case TYPE_VIDEO:
    		widgetLayout = R.layout.openhabwidgetlist_videoitem;
    		break;
    	case TYPE_WEB:
    		widgetLayout = R.layout.openhabwidgetlist_webitem;
    		break;
    	case TYPE_COLOR:
    		widgetLayout = R.layout.openhabwidgetlist_coloritem;
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
        // Process widgets icon image
        MySmartImageView widgetImage = (MySmartImageView)widgetView.findViewById(R.id.widgetimage);
        // Some of widgets, for example Frame doesnt' have an icon, so...
        if (widgetImage != null) {
            /* TODO: This need a fix inside smart image not to mess non existing icons
               instead of blinking them all blank before loading... */
            if (openHABWidget.getIcon() != null) {
            //    widgetImage.setImageResource(R.drawable.blank_icon);
                widgetImage.setImageUrl(openHABBaseUrl + "images/" +
                        openHABWidget.getIcon() + ".png", R.drawable.blank_icon, openHABUsername, openHABPassword);
            }
        }
    	switch (getItemViewType(position)) {
    	case TYPE_FRAME:
    		labelTextView = (TextView)widgetView.findViewById(R.id.framelabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		widgetView.setClickable(false);
    		if (openHABWidget.getLabel().length() > 0) { // hide empty frames
    			widgetView.setVisibility(View.VISIBLE);
    			labelTextView.setVisibility(View.VISIBLE);
    		} else {
    			widgetView.setVisibility(View.GONE);
    			labelTextView.setVisibility(View.GONE);
    		}
    		break;
    	case TYPE_GROUP:
    		labelTextView = (TextView)widgetView.findViewById(R.id.grouplabel);
    		valueTextView = (TextView)widgetView.findViewById(R.id.groupvalue);
    		if (labelTextView != null && valueTextView != null) {
    			splitString = openHABWidget.getLabel().split("\\[|\\]");
    			labelTextView.setText(splitString[0]);
    			if (splitString.length > 1) { // We have some value
    				valueTextView.setText(splitString[1]);
    			} else {
    				// This is needed to clean up cached TextViews
    				valueTextView.setText("");
    			}
    		}
    		break;
    	case TYPE_SECTIONSWITCH:
    		labelTextView = (TextView)widgetView.findViewById(R.id.sectionswitchlabel);
    		valueTextView = (TextView)widgetView.findViewById(R.id.sectionswitchvalue);
			splitString = openHABWidget.getLabel().split("\\[|\\]");
			if (labelTextView != null)
				labelTextView.setText(splitString[0]);
			if (splitString.length > 1 && valueTextView != null) { // We have some value
				valueTextView.setText(splitString[1]);
			} else {
				// This is needed to clean up cached TextViews
				valueTextView.setText("");
			}
    		RadioGroup sectionSwitchRadioGroup = (RadioGroup)widgetView.findViewById(R.id.sectionswitchradiogroup);
    		// As we create buttons in this radio in runtime, we need to remove all
    		// exiting buttons first
    		sectionSwitchRadioGroup.removeAllViews();
    		sectionSwitchRadioGroup.setTag(openHABWidget);
    		Iterator<OpenHABWidgetMapping> sectionMappingIterator = openHABWidget.getMappings().iterator();
    		while (sectionMappingIterator.hasNext()) {
    			/* TODO: There is some problem here, because multiply buttons inside RadioGroup
    			 * can be checked at the same time. I suspect there is some problem in parent
    			 * child relationship or in inflater
    			 */ 
    			OpenHABWidgetMapping widgetMapping = sectionMappingIterator.next();
    			SegmentedControlButton segmentedControlButton = 
    					(SegmentedControlButton)LayoutInflater.from(sectionSwitchRadioGroup.getContext()).inflate(
    							R.layout.openhabwidgetlist_sectionswitchitem_button, sectionSwitchRadioGroup, false);
    			segmentedControlButton.setText(widgetMapping.getLabel());
    			segmentedControlButton.setTag(widgetMapping.getCommand());
    			if (openHABWidget.getItem() != null && widgetMapping.getCommand() != null) {
	    			if (widgetMapping.getCommand().equals(openHABWidget.getItem().getState())) {
	    				segmentedControlButton.setChecked(true);
	    			} else {
	    				segmentedControlButton.setChecked(false);
	    			}
    			} else {
    				segmentedControlButton.setChecked(false);
    			}
    			sectionSwitchRadioGroup.addView(segmentedControlButton);
    		}
    		sectionSwitchRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				public void onCheckedChanged(RadioGroup group, int checkedId) {
					OpenHABWidget radioWidget = (OpenHABWidget)group.getTag();
					SegmentedControlButton selectedButton = (SegmentedControlButton)group.findViewById(checkedId);
					if (selectedButton != null) {
						Log.d(TAG, "Selected " + selectedButton.getText());
						Log.d(TAG, "Command = " + (String)selectedButton.getTag());
//						radioWidget.getItem().sendCommand((String)selectedButton.getTag());
						sendItemCommand(radioWidget.getItem(), (String)selectedButton.getTag());
					}
				}
    		});
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
    		switchSwitch.setOnTouchListener(new OnTouchListener() {
				public boolean onTouch(View v, MotionEvent motionEvent) {
					Switch switchSwitch = (Switch)v;
					OpenHABItem linkedItem = (OpenHABItem)switchSwitch.getTag();
					if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
						if (!switchSwitch.isChecked()) {
							sendItemCommand(linkedItem, "ON");
						} else {
							sendItemCommand(linkedItem, "OFF");
						}
					return false;
				}
                		});
    		break;
    	case TYPE_COLOR:
    		labelTextView = (TextView)widgetView.findViewById(R.id.colorlabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		ImageButton colorUpButton = (ImageButton)widgetView.findViewById(R.id.colorbutton_up);
    		ImageButton colorDownButton = (ImageButton)widgetView.findViewById(R.id.colorbutton_down);
    		ImageButton colorColorButton = (ImageButton)widgetView.findViewById(R.id.colorbutton_color);
    		colorUpButton.setTag(openHABWidget.getItem());
    		colorDownButton.setTag(openHABWidget.getItem());
    		colorColorButton.setTag(openHABWidget.getItem());
    		colorUpButton.setOnTouchListener(new OnTouchListener() {
				public boolean onTouch(View v, MotionEvent motionEvent) {
					ImageButton colorButton = (ImageButton)v;
					OpenHABItem colorItem = (OpenHABItem)colorButton.getTag();
					if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
						sendItemCommand(colorItem, "ON");
					return false;
				}
            });
    		colorDownButton.setOnTouchListener(new OnTouchListener() {
				public boolean onTouch(View v, MotionEvent motionEvent) {
					ImageButton colorButton = (ImageButton)v;
					OpenHABItem colorItem = (OpenHABItem)colorButton.getTag();
					if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
						sendItemCommand(colorItem, "OFF");
					return false;
				}
                		});
    		colorColorButton.setOnTouchListener(new OnTouchListener() {
				public boolean onTouch(View v, MotionEvent motionEvent) {
					ImageButton colorButton = (ImageButton)v;
					OpenHABItem colorItem = (OpenHABItem)colorButton.getTag();
					if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
						Log.d(TAG, "Time to launch color picker!");
						ColorPickerDialog colorDialog = new ColorPickerDialog(widgetView.getContext(), new OnColorChangedListener() {
							public void colorChanged(float[] hsv, View v) {
								Log.d(TAG, "New color HSV = " + hsv[0] + ", " + hsv[1] + ", " +
										hsv[2]);
								String newColor = String.valueOf(hsv[0]) + "," + String.valueOf(hsv[1]*100) + "," + String.valueOf(hsv[2]*100);
								OpenHABItem colorItem = (OpenHABItem) v.getTag();
								sendItemCommand(colorItem, newColor);
							}
						}, colorItem.getStateAsHSV());
						colorDialog.setTag(colorItem);
						colorDialog.show();
					}
					return false;
				}
                		});
    		break;
    	case TYPE_ROLLERSHUTTER:
    		labelTextView = (TextView)widgetView.findViewById(R.id.rollershutterlabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		ImageButton rollershutterUpButton = (ImageButton)widgetView.findViewById(R.id.rollershutterbutton_up);
    		ImageButton rollershutterStopButton = (ImageButton)widgetView.findViewById(R.id.rollershutterbutton_stop);
    		ImageButton rollershutterDownButton = (ImageButton)widgetView.findViewById(R.id.rollershutterbutton_down);
    		rollershutterUpButton.setTag(openHABWidget.getItem());
    		rollershutterStopButton.setTag(openHABWidget.getItem());
    		rollershutterDownButton.setTag(openHABWidget.getItem());
    		rollershutterUpButton.setOnTouchListener(new OnTouchListener() {
				public boolean onTouch(View v, MotionEvent motionEvent) {
					ImageButton rollershutterButton = (ImageButton)v;
					OpenHABItem rollershutterItem = (OpenHABItem)rollershutterButton.getTag();
					if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
						sendItemCommand(rollershutterItem, "UP");
					return false;
				}
                		});
    		rollershutterStopButton.setOnTouchListener(new OnTouchListener() {
				public boolean onTouch(View v, MotionEvent motionEvent) {
					ImageButton rollershutterButton = (ImageButton)v;
					OpenHABItem rollershutterItem = (OpenHABItem)rollershutterButton.getTag();
					if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
						sendItemCommand(rollershutterItem, "STOP");
					return false;
				}
                		});
    		rollershutterDownButton.setOnTouchListener(new OnTouchListener() {
				public boolean onTouch(View v, MotionEvent motionEvent) {
					ImageButton rollershutterButton = (ImageButton)v;
					OpenHABItem rollershutterItem = (OpenHABItem)rollershutterButton.getTag();
					if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
						sendItemCommand(rollershutterItem, "DOWN");
					return false;
				}
                		});
    		break;
    	case TYPE_TEXT:
    		labelTextView = (TextView)widgetView.findViewById(R.id.textlabel);
    		splitString = openHABWidget.getLabel().split("\\[|\\]");
    		if (labelTextView != null)
    			if (splitString.length > 0) {
    				labelTextView.setText(splitString[0]);
    			} else {
    				labelTextView.setText(openHABWidget.getLabel());
    			}
    		valueTextView = (TextView)widgetView.findViewById(R.id.textvalue);
    		if (valueTextView != null) 
    			if (splitString.length > 1) {
    				// If value is not empty, show TextView
    				valueTextView.setVisibility(View.VISIBLE);
    				valueTextView.setText(splitString[1]);
    			} else {
    				// If value is empty, hide TextView to fix vertical alignment of label
    				valueTextView.setVisibility(View.GONE);
    				valueTextView.setText("");
    			}
    		break;
    	case TYPE_SLIDER:
    		labelTextView = (TextView)widgetView.findViewById(R.id.sliderlabel);
    		splitString = openHABWidget.getLabel().split("\\[|\\]");
    		if (labelTextView != null)
    			labelTextView.setText(splitString[0]);
    		SeekBar sliderSeekBar = (SeekBar)widgetView.findViewById(R.id.sliderseekbar);
    		if (openHABWidget.hasItem()) {
    			sliderSeekBar.setTag(openHABWidget.getItem());
    			int sliderState = 0;
    			try {
    				sliderState = (int)Float.parseFloat(openHABWidget.getItem().getState());
    			} catch (NumberFormatException e) {
    				if (e != null) {
    					Crittercism.logHandledException(e);
    					Log.e(TAG, e.getMessage());
    				}
    				if (openHABWidget.getItem().getState().equals("OFF")) {
    					sliderState = 0;
    				} else if (openHABWidget.getItem().getState().equals("ON")) {
    					sliderState = 100;
    				}
    			}
    			sliderSeekBar.setProgress(sliderState);
    			sliderSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
						public void onProgressChanged(SeekBar seekBar,
								int progress, boolean fromUser) {
						}
						public void onStartTrackingTouch(SeekBar seekBar) {
							Log.d(TAG, "onStartTrackingTouch position = " + seekBar.getProgress());
						}
						public void onStopTrackingTouch(SeekBar seekBar) {
							Log.d(TAG, "onStopTrackingTouch position = " + seekBar.getProgress());
							OpenHABItem sliderItem = (OpenHABItem)seekBar.getTag();
//							sliderItem.sendCommand(String.valueOf(seekBar.getProgress()));
							if (sliderItem != null && seekBar != null)
								sendItemCommand(sliderItem, String.valueOf(seekBar.getProgress()));
						}
    			});
    		}
    		break;
    	case TYPE_IMAGE:
    		MySmartImageView imageImage = (MySmartImageView)widgetView.findViewById(R.id.imageimage);
    		imageImage.setImageUrl(ensureAbsoluteURL(openHABBaseUrl, openHABWidget.getUrl()), false, 
    				openHABUsername, openHABPassword);
//    		ViewGroup.LayoutParams imageLayoutParams = imageImage.getLayoutParams();
//    		float imageRatio = imageImage.getDrawable().getIntrinsicWidth()/imageImage.getDrawable().getIntrinsicHeight();
//    		imageLayoutParams.height = (int) (screenWidth/imageRatio);
//    		imageImage.setLayoutParams(imageLayoutParams);
    		if (openHABWidget.getRefresh() > 0) {
    			imageImage.setRefreshRate(openHABWidget.getRefresh());
    			refreshImageList.add(imageImage);
    		}
   		break;
    	case TYPE_CHART:
    		MySmartImageView chartImage = (MySmartImageView)widgetView.findViewById(R.id.chartimage);
    		OpenHABItem chartItem = openHABWidget.getItem();
    		Random random = new Random();
    		String chartUrl = "";
    		if (chartItem != null) {
	    		if (chartItem.getType().equals("GroupItem")) {
	    			chartUrl = openHABBaseUrl + "rrdchart.png?groups=" + chartItem.getName() + 
	    					"&period=" + openHABWidget.getPeriod() + "&random=" +
	    					String.valueOf(random.nextInt());
	    		}
    		}
    		Log.d(TAG, "Chart url = " + chartUrl);
    		if (chartImage == null)
    			Log.e(TAG, "chartImage == null !!!");
//    		if (openHABUsername != null && openHABPassword != null)
   			chartImage.setImageUrl(chartUrl, false, openHABUsername, openHABPassword);
//    		else
//    			chartImage.setImageUrl(chartUrl, false);
    		// TODO: This is quite dirty fix to make charts look full screen width on all displays
    		ViewGroup.LayoutParams chartLayoutParams = chartImage.getLayoutParams();
    		chartLayoutParams.height = (int) (screenWidth/1.88);
    		chartImage.setLayoutParams(chartLayoutParams);
    		if (openHABWidget.getRefresh() > 0) {
    			chartImage.setRefreshRate(openHABWidget.getRefresh());
    			refreshImageList.add(chartImage);
    		}
    		Log.d(TAG, "chart size = " + chartLayoutParams.width + " " + chartLayoutParams.height);
    	break;
    	case TYPE_VIDEO:
    		VideoView videoVideo = (VideoView)widgetView.findViewById(R.id.videovideo);
    		Log.d(TAG, "Opening video at " + openHABWidget.getUrl());
    		// TODO: This is quite dirty fix to make video look maximum available size on all screens
    		WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
    		ViewGroup.LayoutParams videoLayoutParams = videoVideo.getLayoutParams();
    		videoLayoutParams.height = (int)(wm.getDefaultDisplay().getWidth()/1.77);
    		videoVideo.setLayoutParams(videoLayoutParams);
    		// We don't have any event handler to know if the VideoView is on the screen
    		// so we manage an array of all videos to stop them when user leaves the page
    		if (!videoWidgetList.contains(videoVideo))
    			videoWidgetList.add(videoVideo);
    		// Start video
    		if (!videoVideo.isPlaying()) {
        		videoVideo.setVideoURI(Uri.parse(openHABWidget.getUrl()));
    			videoVideo.start();
    		}
    		Log.d(TAG, "Video height is " + videoVideo.getHeight());
    	break;
    	case TYPE_WEB:
    		WebView webWeb = (WebView)widgetView.findViewById(R.id.webweb);
    		if (openHABWidget.getHeight() > 0) {
    			ViewGroup.LayoutParams webLayoutParams = webWeb.getLayoutParams();
    			webLayoutParams.height = openHABWidget.getHeight() * 80;
    			webWeb.setLayoutParams(webLayoutParams);
    		}
    		webWeb.setWebViewClient(new WebViewClient());
    		webWeb.loadUrl(openHABWidget.getUrl());
    	break;
    	case TYPE_SELECTION:
    		int spinnerSelectedIndex = -1;
    		labelTextView = (TextView)widgetView.findViewById(R.id.selectionlabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		Spinner selectionSpinner = (Spinner)widgetView.findViewById(R.id.selectionspinner);
    		ArrayList<String> spinnerArray = new ArrayList<String>();
    		Iterator<OpenHABWidgetMapping> mappingIterator = openHABWidget.getMappings().iterator();
    		while (mappingIterator.hasNext()) {
    			OpenHABWidgetMapping openHABWidgetMapping = mappingIterator.next();
    			spinnerArray.add(openHABWidgetMapping.getLabel());
    			if (openHABWidgetMapping.getCommand().equals(openHABWidget.getItem().getState())) {
    				spinnerSelectedIndex = spinnerArray.size() - 1;
    			}
    		}
    		ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this.getContext() ,
    				android.R.layout.simple_spinner_item, spinnerArray);
    		spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    		selectionSpinner.setAdapter(spinnerAdapter);
    		selectionSpinner.setTag(openHABWidget);
    		if (spinnerSelectedIndex >= 0)
    			selectionSpinner.setSelection(spinnerSelectedIndex);
    		
    		selectionSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
				public void onItemSelected(AdapterView<?> parent, View view,
						int index, long id) {
					Log.d(TAG, "Spinner item click on index " + index);
					Spinner spinner = (Spinner)parent;
					String selectedLabel = (String)spinner.getAdapter().getItem(index);
					Log.d(TAG, "Spinner onItemSelected selected label = " + selectedLabel);
					OpenHABWidget openHABWidget = (OpenHABWidget)parent.getTag();
					if (openHABWidget != null) {
						Log.d(TAG, "Label selected = " + openHABWidget.getMapping(index).getLabel());
						Iterator<OpenHABWidgetMapping> mappingIterator = openHABWidget.getMappings().iterator();
						while (mappingIterator.hasNext()) {
							OpenHABWidgetMapping openHABWidgetMapping = mappingIterator.next();
							if (openHABWidgetMapping.getLabel().equals(selectedLabel)) {
								Log.d(TAG, "Spinner onItemSelected found match with " + openHABWidgetMapping.getCommand());
								if (!openHABWidget.getItem().getState().equals(openHABWidgetMapping.getCommand())) {
									Log.d(TAG, "Spinner onItemSelected selected label command != current item state");
									sendItemCommand(openHABWidget.getItem(), openHABWidgetMapping.getCommand());
								}
							}
						}
					}
//					if (!openHABWidget.getItem().getState().equals(openHABWidget.getMapping(index).getCommand()))
//						sendItemCommand(openHABWidget.getItem(),
//								openHABWidget.getMapping(index).getCommand());
				}

				public void onNothingSelected(AdapterView<?> arg0) {
				}    			
    		});
    		break;
    	case TYPE_SETPOINT:
    		labelTextView = (TextView)widgetView.findViewById(R.id.setpointlabel);
    		splitString = openHABWidget.getLabel().split("\\[|\\]");
    		if (labelTextView != null)
    			labelTextView.setText(splitString[0]);
    		TextView setPointValueTextView = (TextView)widgetView.findViewById(R.id.setpointvaluelabel);
    		if (setPointValueTextView != null) 
    			if (splitString.length > 1) {
    				// If value is not empty, show TextView
    				setPointValueTextView.setVisibility(View.VISIBLE);
    				setPointValueTextView.setText(splitString[1]);
    			}
    		Button setPointMinusButton = (Button)widgetView.findViewById(R.id.setpointbutton_minus);
    		Button setPointPlusButton = (Button)widgetView.findViewById(R.id.setpointbutton_plus);
    		setPointMinusButton.setTag(openHABWidget);
    		setPointPlusButton.setTag(openHABWidget);
    		setPointMinusButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Log.d(TAG, "Minus");
					OpenHABWidget setPointWidget = (OpenHABWidget)v.getTag();
					float currentValue = Float.valueOf(setPointWidget.getItem().getState()).floatValue();
					currentValue = currentValue - setPointWidget.getStep();
					if (currentValue < setPointWidget.getMinValue())
						currentValue = setPointWidget.getMinValue();
                    if (currentValue > setPointWidget.getMaxValue())
                        currentValue = setPointWidget.getMaxValue();
					sendItemCommand(setPointWidget.getItem(), String.valueOf(currentValue));

				}
                		});
    		setPointPlusButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Log.d(TAG,"Plus");
					OpenHABWidget setPointWidget = (OpenHABWidget)v.getTag();
					float currentValue = Float.valueOf(setPointWidget.getItem().getState()).floatValue();
					currentValue = currentValue + setPointWidget.getStep();
                    if (currentValue < setPointWidget.getMinValue())
                        currentValue = setPointWidget.getMinValue();
					if (currentValue > setPointWidget.getMaxValue())
						currentValue = setPointWidget.getMaxValue();
					sendItemCommand(setPointWidget.getItem(), String.valueOf(currentValue));
				}
                		});
    		break;
    	default:
    		labelTextView = (TextView)widgetView.findViewById(R.id.itemlabel);
    		if (labelTextView != null)
    			labelTextView.setText(openHABWidget.getLabel());
    		break;
    	}
    	LinearLayout dividerLayout = (LinearLayout)widgetView.findViewById(R.id.listdivider);
    	if (dividerLayout != null) {
    		if (position < this.getCount()-1) {
    			if (this.getItemViewType(position + 1) == TYPE_FRAME) {
        			dividerLayout.setVisibility(View.GONE); // hide dividers before frame widgets
    			} else {
    				dividerLayout.setVisibility(View.VISIBLE); // show dividers for all others
    			}
    		} else { // last widget in the list, hide divider
    			dividerLayout.setVisibility(View.GONE);
    		}
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
    		if (openHABWidget.hasMappings()) {
    			return TYPE_SECTIONSWITCH;
    		} else if (openHABWidget.getItem() != null) {
    			if (openHABWidget.getItem().getType()!= null) {
	    			if (openHABWidget.getItem().getType().equals("RollershutterItem"))
	    				return TYPE_ROLLERSHUTTER;
	    			else
	    				return TYPE_SWITCH;
    			} else
    				return TYPE_SWITCH;
    		} else {
    			return TYPE_SWITCH;
    		}
    	} else if (openHABWidget.getType().equals("Text")) {
    		return TYPE_TEXT;
    	} else if (openHABWidget.getType().equals("Slider")) {
    		return TYPE_SLIDER;
    	} else if (openHABWidget.getType().equals("Image")) {
    		return TYPE_IMAGE;
    	} else if (openHABWidget.getType().equals("Selection")) {
    		return TYPE_SELECTION;
    	} else if (openHABWidget.getType().equals("Setpoint")) {
    		return TYPE_SETPOINT;
    	} else if (openHABWidget.getType().equals("Chart")) {
    		return TYPE_CHART;
    	} else if (openHABWidget.getType().equals("Video")) {
    		return TYPE_VIDEO;
    	} else if (openHABWidget.getType().equals("Webview")) {
    		return TYPE_WEB;
    	} else if (openHABWidget.getType().equals("Colorpicker")) {
    		return TYPE_COLOR;
    	} else {
    		return TYPE_GENERICITEM;
    	}
    }
	
    public void setOpenHABBaseUrl(String baseUrl) {
    	openHABBaseUrl = baseUrl;
    }
    
    private String ensureAbsoluteURL(String base, String maybeRelative) {
        if (maybeRelative.startsWith("http")) {
            return maybeRelative;
        } else {
            try {
               return new URL(new URL(base), maybeRelative).toExternalForm();
            } catch (MalformedURLException e) {
               return "";
            }
        }
    }
    
    public void sendItemCommand(OpenHABItem item, String command) {
		MyAsyncHttpClient asyncHttpClient = new MyAsyncHttpClient();
		asyncHttpClient.setBasicAuthCredientidals(openHABUsername, openHABPassword);
		try {
			StringEntity se = new StringEntity(command);
			asyncHttpClient.post(null, item.getLink(), se, "text/plain", new AsyncHttpResponseHandler());
		} catch (UnsupportedEncodingException e) {
		}
    }

	public String getOpenHABUsername() {
		return openHABUsername;
	}

	public void setOpenHABUsername(String openHABUsername) {
		this.openHABUsername = openHABUsername;
	}

	public String getOpenHABPassword() {
		return openHABPassword;
	}

	public void setOpenHABPassword(String openHABPassword) {
		this.openHABPassword = openHABPassword;
	}
	
	public void stopVideoWidgets() {
		Log.d(TAG, "Stopping video for " + videoWidgetList.size() + " widgets");
		for (int i=0; i<videoWidgetList.size(); i++) {
			if (videoWidgetList.get(i) != null)
				videoWidgetList.get(i).stopPlayback();
		}
		videoWidgetList.clear();
	}
	
	public void stopImageRefresh() {
		Log.d(TAG, "Stopping image refresh for " + refreshImageList.size() + " widgets");
		for (int i=0; i<refreshImageList.size(); i++) {
			if (refreshImageList.get(i) != null)
				refreshImageList.get(i).cancelRefresh();
		}
		refreshImageList.clear();
	}
}
