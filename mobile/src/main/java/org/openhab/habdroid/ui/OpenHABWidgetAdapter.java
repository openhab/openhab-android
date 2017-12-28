/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.support.annotation.ColorInt;
import android.support.v7.widget.SwitchCompat;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.VideoView;

import com.loopj.android.image.SmartImage;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionAvailbilityAwareAcivity;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.Connections;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.message.MessageHandler;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetMapping;
import org.openhab.habdroid.ui.widget.ColorPickerDialog;
import org.openhab.habdroid.ui.widget.OnColorChangedListener;
import org.openhab.habdroid.ui.widget.SegmentedControlButton;
import org.openhab.habdroid.util.MjpegStreamer;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.MySmartImageView;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import okhttp3.Call;
import okhttp3.Headers;

/**
 * This class provides openHAB widgets adapter for list view.
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
    public static final int TYPE_VIDEO_MJPEG = 15;
    public static final int TYPES_COUNT = 16;
    private static final String TAG = "OpenHABWidgetAdapter";
    private ArrayList<VideoView> videoWidgetList;
    private ArrayList<MySmartImageView> refreshImageList;
    private ArrayList<MjpegStreamer> mjpegWidgetList;
    private @ColorInt int mPrimaryForegroundColor;

    public OpenHABWidgetAdapter(Context context, int resource,
                                List<OpenHABWidget> objects) {
        super(context, resource, objects);
        // Initialize video view array
        videoWidgetList = new ArrayList<VideoView>();
        refreshImageList = new ArrayList<MySmartImageView>();
        mjpegWidgetList = new ArrayList<MjpegStreamer>();

        TypedArray a = context.obtainStyledAttributes(new int[] {
            R.attr.colorControlNormal
        });
        mPrimaryForegroundColor = a.getColor(0, 0);
        a.recycle();
    }

    @SuppressWarnings("deprecation")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        /* TODO: This definitely needs some huge refactoring */
        final RelativeLayout widgetView;
        final TextView labelTextView;
        final TextView valueTextView;
        final Connection conn;
        if (getContext() instanceof ConnectionAvailbilityAwareAcivity) {
            conn = ((ConnectionAvailbilityAwareAcivity) getContext())
                    .getConnection(Connections.ANY);
        } else {
            conn = ConnectionFactory.getConnection(Connections.ANY, getContext());
        }
        int widgetLayout;
        String[] splitString;
        final OpenHABWidget openHABWidget = getItem(position);
        int screenWidth = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getWidth();
        int screenHeight = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getHeight();
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
            case TYPE_VIDEO_MJPEG:
                widgetLayout = R.layout.openhabwidgetlist_videomjpegitem;
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
        if (convertView == null) {
            widgetView = new RelativeLayout(getContext());
            String inflater = Context.LAYOUT_INFLATER_SERVICE;
            LayoutInflater vi;
            vi = (LayoutInflater) getContext().getSystemService(inflater);
            vi.inflate(widgetLayout, widgetView, true);
        } else {
            widgetView = (RelativeLayout) convertView;
        }

        // Process the colour attributes
        Integer iconColor = openHABWidget.getIconColor();
        Integer labelColor = openHABWidget.getLabelColor();
        Integer valueColor = openHABWidget.getValueColor();

        // Process widgets icon image
        MySmartImageView widgetImage = (MySmartImageView) widgetView.findViewById(R.id.widgetimage);
        // Some of widgets, for example frames, doesn't have an icon, so...
        if (widgetImage != null) {
            // This is needed to escape possible spaces and everything according to rfc2396
            String iconUrl = conn.getOpenHABUrl() +
                    Uri.encode(openHABWidget.getIconPath(), "/?=&");
//                Log.d(TAG, "Will try to load icon from " + iconUrl);
            // Now set image URL
            widgetImage.setImageUrl(iconUrl, R.drawable.blank_icon,
                    conn.getUsername(),
                    conn.getPassword());
            if (iconColor != null) {
                widgetImage.setColorFilter(iconColor);
            } else {
                widgetImage.clearColorFilter();
            }
        }
        TextView defaultTextView = new TextView(widgetView.getContext());
        // Get TextView for widget label and set it's color
        labelTextView = (TextView) widgetView.findViewById(R.id.widgetlabel);
        // Change label color only for non-frame widgets
        if (labelColor != null && labelTextView != null && this.getItemViewType(position) != TYPE_FRAME) {
            Log.d(TAG, String.format("Setting label color to %d", labelColor));
            labelTextView.setTextColor(labelColor);
        } else if (labelTextView != null && this.getItemViewType(position) != TYPE_FRAME)
            labelTextView.setTextColor(defaultTextView.getTextColors().getDefaultColor());
        // Get TextView for widget value and set it's color
        valueTextView = (TextView) widgetView.findViewById(R.id.widgetvalue);
        if (valueColor != null && valueTextView != null) {
            Log.d(TAG, String.format("Setting value color to %d", valueColor));
            valueTextView.setTextColor(valueColor);
        } else if (valueTextView != null)
            valueTextView.setTextColor(defaultTextView.getTextColors().getDefaultColor());
        defaultTextView = null;
        switch (getItemViewType(position)) {
            case TYPE_FRAME:
                if (labelTextView != null) {
                    labelTextView.setText(openHABWidget.getLabel());
                    if (valueColor != null)
                        labelTextView.setTextColor(valueColor);
                }
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
                if (labelTextView != null && valueTextView != null) {
                    splitString = openHABWidget.getLabel().split("\\[|\\]");
                    if (splitString.length > 0) {
                        labelTextView.setText(splitString[0]);
                    }
                    if (splitString.length > 1) { // We have some value
                        valueTextView.setText(splitString[1]);
                    } else {
                        // This is needed to clean up cached TextViews
                        valueTextView.setText("");
                    }
                }
                break;
            case TYPE_SECTIONSWITCH:
                splitString = openHABWidget.getLabel().split("\\[|\\]");
                if (labelTextView != null && splitString.length > 0)
                    labelTextView.setText(splitString[0]);
                if (splitString.length > 1 && valueTextView != null) { // We have some value
                    valueTextView.setText(splitString[1]);
                } else {
                    // This is needed to clean up cached TextViews
                    valueTextView.setText("");
                }
                RadioGroup sectionSwitchRadioGroup = (RadioGroup) widgetView.findViewById(R.id.sectionswitchradiogroup);
                // As we create buttons in this radio in runtime, we need to remove all
                // exiting buttons first
                sectionSwitchRadioGroup.removeAllViews();
                sectionSwitchRadioGroup.setTag(openHABWidget);
                Iterator<OpenHABWidgetMapping> sectionMappingIterator = openHABWidget.getMappings().iterator();
                while (sectionMappingIterator.hasNext()) {
                    OpenHABWidgetMapping widgetMapping = sectionMappingIterator.next();
                    SegmentedControlButton segmentedControlButton =
                            (SegmentedControlButton) LayoutInflater.from(sectionSwitchRadioGroup.getContext()).inflate(
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
                    segmentedControlButton.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Log.i(TAG, "Button clicked");
                            RadioGroup group = (RadioGroup) view.getParent();
                            if (group.getTag() != null) {
                                OpenHABWidget radioWidget = (OpenHABWidget) group.getTag();
                                SegmentedControlButton selectedButton = (SegmentedControlButton) view;
                                if (selectedButton.getTag() != null) {
                                    sendItemCommand(radioWidget.getItem(), (String) selectedButton.getTag());
                                }
                            }
                        }
                    });
                    sectionSwitchRadioGroup.addView(segmentedControlButton);
                }


                sectionSwitchRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        OpenHABWidget radioWidget = (OpenHABWidget) group.getTag();
                        SegmentedControlButton selectedButton = (SegmentedControlButton) group.findViewById(checkedId);
                        if (selectedButton != null) {
                            Log.d(TAG, "Selected " + selectedButton.getText());
                            Log.d(TAG, "Command = " + (String) selectedButton.getTag());
//						radioWidget.getItem().sendCommand((String)selectedButton.getTag());
                            sendItemCommand(radioWidget.getItem(), (String) selectedButton.getTag());
                        }
                    }
                });
                break;
            case TYPE_SWITCH:
                if (labelTextView != null)
                    labelTextView.setText(openHABWidget.getLabel());
                SwitchCompat switchSwitch = (SwitchCompat) widgetView.findViewById(R.id.switchswitch);
                if (openHABWidget.hasItem()) {
                    if (openHABWidget.getItem().getStateAsBoolean()) {
                        switchSwitch.setChecked(true);
                    } else {
                        switchSwitch.setChecked(false);
                    }
                }
                switchSwitch.setTag(openHABWidget.getItem());
                switchSwitch.setOnTouchListener(new OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent motionEvent) {
                        SwitchCompat switchSwitch = (SwitchCompat) v;
                        OpenHABItem linkedItem = (OpenHABItem) switchSwitch.getTag();
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
                if (labelTextView != null)
                    labelTextView.setText(openHABWidget.getLabel());
                ImageButton colorUpButton = (ImageButton) widgetView.findViewById(R.id.colorbutton_up);
                ImageButton colorDownButton = (ImageButton) widgetView.findViewById(R.id.colorbutton_down);
                ImageButton colorColorButton = (ImageButton) widgetView.findViewById(R.id.colorbutton_color);
                colorUpButton.setTag(openHABWidget.getItem());
                colorDownButton.setTag(openHABWidget.getItem());
                colorColorButton.setTag(openHABWidget.getItem());
                colorUpButton.setOnTouchListener(new OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent motionEvent) {
                        ImageButton colorButton = (ImageButton) v;
                        OpenHABItem colorItem = (OpenHABItem) colorButton.getTag();
                        if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
                            sendItemCommand(colorItem, "ON");
                        return false;
                    }
                });
                colorDownButton.setOnTouchListener(new OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent motionEvent) {
                        ImageButton colorButton = (ImageButton) v;
                        OpenHABItem colorItem = (OpenHABItem) colorButton.getTag();
                        if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
                            sendItemCommand(colorItem, "OFF");
                        return false;
                    }
                });
                colorColorButton.setOnTouchListener(new OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent motionEvent) {
                        ImageButton colorButton = (ImageButton) v;
                        OpenHABItem colorItem = (OpenHABItem) colorButton.getTag();
                        if (colorItem != null) {
                            if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
                                Log.d(TAG, "Time to launch color picker!");
                                ColorPickerDialog colorDialog = new ColorPickerDialog(widgetView.getContext(), new OnColorChangedListener() {
                                    public void colorChanged(float[] hsv, View v) {
                                        Log.d(TAG, "New color HSV = " + hsv[0] + ", " + hsv[1] + ", " +
                                                hsv[2]);
                                        String newColor = String.valueOf(hsv[0]) + "," + String.valueOf(hsv[1] * 100) + "," + String.valueOf(hsv[2] * 100);
                                        OpenHABItem colorItem = (OpenHABItem) v.getTag();
                                        sendItemCommand(colorItem, newColor);
                                    }
                                }, colorItem.getStateAsHSV());
                                colorDialog.setTag(colorItem);
                                colorDialog.show();
                            }
                        }
                        return false;
                    }
                });
                break;
            case TYPE_ROLLERSHUTTER:
                if (labelTextView != null)
                    labelTextView.setText(openHABWidget.getLabel());
                ImageButton rollershutterUpButton = (ImageButton) widgetView.findViewById(R.id.rollershutterbutton_up);
                ImageButton rollershutterStopButton = (ImageButton) widgetView.findViewById(R.id.rollershutterbutton_stop);
                ImageButton rollershutterDownButton = (ImageButton) widgetView.findViewById(R.id.rollershutterbutton_down);
                rollershutterUpButton.setTag(openHABWidget.getItem());
                rollershutterStopButton.setTag(openHABWidget.getItem());
                rollershutterDownButton.setTag(openHABWidget.getItem());
                rollershutterUpButton.setOnTouchListener(new OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent motionEvent) {
                        ImageButton rollershutterButton = (ImageButton) v;
                        OpenHABItem rollershutterItem = (OpenHABItem) rollershutterButton.getTag();
                        if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
                            sendItemCommand(rollershutterItem, "UP");
                        return false;
                    }
                });
                rollershutterStopButton.setOnTouchListener(new OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent motionEvent) {
                        ImageButton rollershutterButton = (ImageButton) v;
                        OpenHABItem rollershutterItem = (OpenHABItem) rollershutterButton.getTag();
                        if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
                            sendItemCommand(rollershutterItem, "STOP");
                        return false;
                    }
                });
                rollershutterDownButton.setOnTouchListener(new OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent motionEvent) {
                        ImageButton rollershutterButton = (ImageButton) v;
                        OpenHABItem rollershutterItem = (OpenHABItem) rollershutterButton.getTag();
                        if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
                            sendItemCommand(rollershutterItem, "DOWN");
                        return false;
                    }
                });
                break;
            case TYPE_TEXT:
                splitString = openHABWidget.getLabel().split("\\[|\\]");
                if (labelTextView != null)
                    if (splitString.length > 0) {
                        labelTextView.setText(splitString[0]);
                    } else {
                        labelTextView.setText(openHABWidget.getLabel());
                    }
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
                splitString = openHABWidget.getLabel().split("\\[|\\]");
                if (labelTextView != null && splitString.length > 0)
                    labelTextView.setText(splitString[0]);
                SeekBar sliderSeekBar = (SeekBar) widgetView.findViewById(R.id.sliderseekbar);
                if (openHABWidget.hasItem()) {
                    sliderSeekBar.setTag(openHABWidget.getItem());
                    int progress;
                    if(openHABWidget.getItem().getType().equals("Color") ||
                            (openHABWidget.getItem().getGroupType() != null && openHABWidget.getItem().getGroupType().equals("Color"))) {
                        Log.d(TAG, "Color slider");
                        try {
                            progress = openHABWidget.getItem().getStateAsBrightness();
                        } catch (IllegalStateException e) {
                            progress = 0;
                        }
                    } else {
                        progress = openHABWidget.getItem().getStateAsFloat().intValue();
                    }
                    sliderSeekBar.setProgress(progress);
                    sliderSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                        public void onProgressChanged(SeekBar seekBar,
                                                      int progress, boolean fromUser) {
                        }

                        public void onStartTrackingTouch(SeekBar seekBar) {
                            Log.d(TAG, "onStartTrackingTouch position = " + seekBar.getProgress());
                        }

                        public void onStopTrackingTouch(SeekBar seekBar) {
                            Log.d(TAG, "onStopTrackingTouch position = " + seekBar.getProgress());
                            OpenHABItem sliderItem = (OpenHABItem) seekBar.getTag();
//							sliderItem.sendCommand(String.valueOf(seekBar.getProgress()));
                            if (sliderItem != null && seekBar != null) {
                                sendItemCommand(sliderItem, String.valueOf(seekBar.getProgress()));
                            }
                        }
                    });
                }
                break;
            case TYPE_IMAGE:
                MySmartImageView imageImage = (MySmartImageView) widgetView.findViewById(R.id.imageimage);
                // We scale the image at max 90% of the available height
                imageImage.setMaxSize(parent.getWidth() > 0 ? parent.getWidth() : screenWidth,
                        (parent.getHeight() > 0 ? parent.getHeight() : screenHeight) * 90 / 100);
                OpenHABItem item = openHABWidget.getItem();
                if (item != null && item.getType().equals("Image") && item.getState() != null
                        && item.getState().startsWith("data:")) {
                   imageImage.setImageWithData(new MyImageFromItem(item.getState()));
                } else {
                    imageImage.setImageUrl(ensureAbsoluteURL(
                            conn.getOpenHABUrl(), openHABWidget.getUrl()), false,
                            conn.getUsername(), conn.getPassword());
                    if (openHABWidget.getRefresh() > 0) {
                        imageImage.setRefreshRate(openHABWidget.getRefresh());
                        refreshImageList.add(imageImage);
                    }
                }
                break;
            case TYPE_CHART:
                MySmartImageView chartImage = (MySmartImageView) widgetView.findViewById(R.id.chartimage);
                // Always clear the drawable, so no images from recycled views appear
                chartImage.setImageDrawable(null);
                OpenHABItem chartItem = openHABWidget.getItem();
                Random random = new Random();
                String chartUrl = "";
                if (chartItem != null) {
                    int fragmentWidth = parent.getWidth() > 0 ? parent.getWidth() : screenWidth;
                    Log.d(TAG, "Chart width = " + fragmentWidth + " - screen width " + screenWidth);

                    if (chartItem.getType().equals("GroupItem") || chartItem.getType().equals("Group")) {
                        chartUrl = conn.getOpenHABUrl() + "chart?groups=" + chartItem.getName() +
                                "&period=" + openHABWidget.getPeriod() + "&random=" +
                                String.valueOf(random.nextInt());
                    } else {
                        chartUrl = conn.getOpenHABUrl() + "chart?items=" + chartItem.getName() +
                                "&period=" + openHABWidget.getPeriod() + "&random=" +
                                String.valueOf(random.nextInt());
                    }
                    if (openHABWidget.getService() != null && openHABWidget.getService().length() > 0) {
                        chartUrl += "&service=" + openHABWidget.getService();
                    }
                    // add theme attribute
                    TypedValue chartTheme = new TypedValue();
                    if (getContext().getTheme().resolveAttribute(R.attr.chartTheme, chartTheme, true)) {
                        chartUrl += "&theme=" + chartTheme.string;
                    }

                    // add dpi attribute
                    WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
                    DisplayMetrics metrics = new DisplayMetrics();
                    wm.getDefaultDisplay().getMetrics(metrics);
                    int dpi = metrics.densityDpi;
                    chartUrl += "&dpi=" + dpi;

                    // add legend
                    if (openHABWidget.getLegend() != null) {
                        chartUrl += "&legend=" + openHABWidget.getLegend();
                    }
                    Log.d(TAG, "Chart url = " + chartUrl);
                    ViewGroup.LayoutParams chartLayoutParams = chartImage.getLayoutParams();
                    chartLayoutParams.height = (int) (fragmentWidth / 2);
                    chartImage.setLayoutParams(chartLayoutParams);
                    chartUrl += "&w=" + String.valueOf(fragmentWidth);
                    chartUrl += "&h=" + String.valueOf(fragmentWidth / 2);
                    chartImage.setImageUrl(chartUrl, false, conn.getUsername(), conn.getPassword());
                    // TODO: This is quite dirty fix to make charts look full screen width on all displays
                    if (openHABWidget.getRefresh() > 0) {
                        chartImage.setRefreshRate(openHABWidget.getRefresh());
                        refreshImageList.add(chartImage);
                    }
                    Log.d(TAG, "chart size = " + chartLayoutParams.width + " " + chartLayoutParams.height);
                } else {
                    Log.e(TAG, "Chart item is null");
                }
                break;
            case TYPE_VIDEO:
                VideoView videoVideo = (VideoView) widgetView.findViewById(R.id.videovideo);
                // TODO: This is quite dirty fix to make video look maximum available size on all screens
                WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
                ViewGroup.LayoutParams videoLayoutParams = videoVideo.getLayoutParams();
                videoLayoutParams.height = (int) (wm.getDefaultDisplay().getWidth() / 1.77);
                videoVideo.setLayoutParams(videoLayoutParams);
                // We don't have any event handler to know if the VideoView is on the screen
                // so we manage an array of all videos to stop them when user leaves the page
                if (!videoWidgetList.contains(videoVideo))
                    videoWidgetList.add(videoVideo);
                // Start video
                if (!videoVideo.isPlaying()) {
                    String videoUrl;
                    OpenHABItem videoItem = openHABWidget.getItem();
                    if (openHABWidget.getEncoding() != null && openHABWidget.getEncoding().toLowerCase().equals("hls")
                            && videoItem != null && videoItem.getType().equals("String")
                            && videoItem.getState() != null && !videoItem.getState().equals("UNDEF")) {
                        videoUrl = videoItem.getState();
                    } else {
                        videoUrl = openHABWidget.getUrl();
                    }
                    Log.d(TAG, "Opening video at " + videoUrl);
                    videoVideo.setVideoURI(Uri.parse(videoUrl));
                    videoVideo.start();
                }
                Log.d(TAG, "Video height is " + videoVideo.getHeight());
                break;
            case TYPE_VIDEO_MJPEG:
                Log.d(TAG, "Video is mjpeg");
                ImageView mjpegImage = (ImageView) widgetView.findViewById(R.id.mjpegimage);
                MjpegStreamer mjpegStreamer = new MjpegStreamer(openHABWidget.getUrl(), conn.getUsername(),
                        conn.getPassword(), this.getContext());
                mjpegStreamer.setTargetImageView(mjpegImage);
                mjpegStreamer.start();
                if (!mjpegWidgetList.contains(mjpegStreamer))
                    mjpegWidgetList.add(mjpegStreamer);
                break;
            case TYPE_WEB:
                WebView webWeb = (WebView) widgetView.findViewById(R.id.webweb);
                if (openHABWidget.getHeight() > 0) {
                    ViewGroup.LayoutParams webLayoutParams = webWeb.getLayoutParams();
                    webLayoutParams.height = openHABWidget.getHeight() * 80;
                    webWeb.setLayoutParams(webLayoutParams);
                }
                webWeb.setWebViewClient(new AnchorWebViewClient(openHABWidget.getUrl(), conn.getUsername(),
                        conn.getPassword()));
                webWeb.getSettings().setDomStorageEnabled(true);
                webWeb.getSettings().setJavaScriptEnabled(true);
                webWeb.loadUrl(openHABWidget.getUrl());
                break;
            case TYPE_SELECTION:
                int spinnerSelectedIndex = -1;
                splitString = openHABWidget.getLabel().split("\\[|\\]");
                if (labelTextView != null) {
                    if (splitString.length > 0) {
                        labelTextView.setText(splitString[0]);
                    } else {
                        labelTextView.setText(openHABWidget.getLabel());
                    }
                }
                final Spinner selectionSpinner = (Spinner) widgetView.findViewById(R.id.selectionspinner);
                ArrayList<String> spinnerArray = new ArrayList<String>();
                Iterator<OpenHABWidgetMapping> mappingIterator = openHABWidget.getMappings().iterator();
                while (mappingIterator.hasNext()) {
                    OpenHABWidgetMapping openHABWidgetMapping = mappingIterator.next();
                    spinnerArray.add(openHABWidgetMapping.getLabel());
                    if (openHABWidgetMapping.getCommand() != null && openHABWidget.getItem() != null)
                        if (openHABWidgetMapping.getCommand().equals(openHABWidget.getItem().getState())) {
                            spinnerSelectedIndex = spinnerArray.size() - 1;
                        }
                }
                if (spinnerSelectedIndex == -1) {
                    spinnerArray.add("          ");
                    spinnerSelectedIndex = spinnerArray.size() - 1;
                }
                ArrayAdapter<String> spinnerAdapter = new SpinnerClickAdapter<String>(this.getContext(),
                        android.R.layout.simple_spinner_item, spinnerArray, openHABWidget, new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
                        Log.d(TAG, "Spinner item click on index " + index);
                        String selectedLabel = (String) parent.getAdapter().getItem(index);
                        Log.d(TAG, "Spinner onItemSelected selected label = " + selectedLabel);
                        OpenHABWidget openHABWidget = (OpenHABWidget) parent.getTag();
                        if (openHABWidget != null && index < openHABWidget.getMappings().size()) {
                            Log.d(TAG, "Label selected = " + openHABWidget.getMapping(index).getLabel());
                            for (OpenHABWidgetMapping openHABWidgetMapping : openHABWidget.getMappings()) {
                                if (openHABWidgetMapping.getLabel().equals(selectedLabel)) {
                                    Log.d(TAG, "Spinner onItemSelected found match with " + openHABWidgetMapping.getCommand());
                                    if (openHABWidget.getItem() != null && openHABWidget.getItem().getState() != null) {
                                        sendItemCommand(openHABWidget.getItem(), openHABWidgetMapping.getCommand());
                                    } else if (openHABWidget.getItem() != null && openHABWidget.getItem().getState() == null) {
                                        Log.d(TAG, "Spinner onItemSelected selected label command and state == null");
                                        sendItemCommand(openHABWidget.getItem(), openHABWidgetMapping.getCommand());
                                    }
                                }
                            }
                        }
                        // TODO: there's probably a better solution...
                        try {
                            // Close the spinner programmatically
                            Method method = Spinner.class.getDeclaredMethod("onDetachedFromWindow");
                            method.setAccessible(true);
                            method.invoke(selectionSpinner);
                        } catch (Exception ignored) {
                        }
                    }
                });
                spinnerAdapter.setDropDownViewResource(android.R.layout.select_dialog_singlechoice);
                selectionSpinner.setPrompt(splitString.length > 0 ? splitString[0] : openHABWidget.getLabel());
                selectionSpinner.setAdapter(spinnerAdapter);
                if (spinnerSelectedIndex >= 0) {
                    Log.d(TAG, "Setting spinner selected index to " + String.valueOf(spinnerSelectedIndex));
                    selectionSpinner.setSelection(spinnerSelectedIndex);
                } else {
                    Log.d(TAG, "Not setting spinner selected index");
                }
                break;
            case TYPE_SETPOINT:
                splitString = openHABWidget.getLabel().split("\\[|\\]");
                if (labelTextView != null && splitString.length > 0)
                    labelTextView.setText(splitString[0]);
                if (valueTextView != null) {
                    if (splitString.length > 1) {
                        // If value is not empty, show TextView
                        valueTextView.setVisibility(View.VISIBLE);
                        valueTextView.setText(splitString[1]);
                    }
                    final Context context = getContext();

                     View.OnClickListener clickListener = new OnClickListener() {
                         @Override
                         public void onClick(final View view) {

                             float minValue = openHABWidget.getMinValue();
                             float maxValue = openHABWidget.getMaxValue();

                             //This prevents an exception below. But could lead to user confusion if this case is ever encountered.
                             if (minValue > maxValue) {
                                 maxValue = minValue;
                             }
                             final float stepSize;
                             if (minValue == maxValue) {
                                 stepSize = 1;
                             } else {
                                 //Ensure min step size is 1
                                 stepSize = openHABWidget.getStep();
                             }


                             final String[] stepValues = new String[((int) (Math.abs(maxValue - minValue) / stepSize)) + 1];
                             for (int i = 0; i < stepValues.length; i++) {
                                 //Check if step size is a whole integer.
                                 if (stepSize == Math.ceil(stepSize)) {
                                     //Cast to int to prevent .0 being added to all values in picker
                                     stepValues[i] = String.valueOf((int) (minValue + (i * stepSize)));
                                 } else {

                                     stepValues[i] = String.valueOf(minValue + (i * stepSize));
                                 }

                             }

                             AlertDialog.Builder builder = new AlertDialog.Builder(context);

                             if (labelTextView != null) {
                                 builder.setTitle(labelTextView.getText());
                             }
                             final LayoutInflater inflater = LayoutInflater.from(context);
                             final View dialogView = inflater.inflate(R.layout.openhab_dialog_numberpicker, null);
                             builder.setView(dialogView);

                             // OK button
                             builder.setPositiveButton(R.string.set, new DialogInterface.OnClickListener() {
                                 public void onClick(DialogInterface dialog, int id) {
                                     final NumberPicker numberPicker = (NumberPicker) dialogView.findViewById(R.id.numberpicker);
                                     sendItemCommand(openHABWidget.getItem(), stepValues[numberPicker.getValue()]);
                                 }
                             });
                             // Cancel button
                             builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                 public void onClick(DialogInterface dialog, int which) {
                                     // do nothing, just close the dialog
                                 }
                             });

                             AlertDialog dialog = builder.create();

                             final NumberPicker numberPicker = (NumberPicker) dialogView.findViewById(R.id.numberpicker);

                             numberPicker.setMinValue(0);
                             numberPicker.setMaxValue(stepValues.length - 1);
                             numberPicker.setDisplayedValues(stepValues);

                             // Find the closest value in the calculated step value
                             int stepIndex = Arrays.binarySearch(stepValues, Float.toString(openHABWidget.getItem().getStateAsFloat()), new Comparator<CharSequence>() {
                                 @Override
                                 public int compare(CharSequence t1, CharSequence t2) {
                                     return Float.valueOf(t1.toString()).compareTo(Float.valueOf(t2.toString()));
                                 }
                             });
                             if (stepIndex < 0) {
                                 stepIndex = (-(stepIndex + 1)); // Use the returned insertion point if value is not found and select the closest value.
                                 stepIndex = Math.min(stepIndex, stepValues.length - 1);  //handle case where insertion would be larger than the array
                             }
                             numberPicker.setValue(stepIndex);

                             dialog.show();
                         }
                     };
                    valueTextView.setOnClickListener(clickListener);
                    ImageView dropdownArrow = widgetView.findViewById(R.id.imageViewDownArrow);
                    dropdownArrow.setOnClickListener(clickListener);
                    dropdownArrow.setColorFilter(mPrimaryForegroundColor, PorterDuff.Mode.SRC_IN);
                }
                break;
            default:
                if (labelTextView != null)
                    labelTextView.setText(openHABWidget.getLabel());
                break;
        }
        LinearLayout dividerLayout = (LinearLayout) widgetView.findViewById(R.id.listdivider);
        if (dividerLayout != null) {
            if (position < this.getCount() - 1) {
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
                if (openHABWidget.getItem().getType() != null) {
                    //RollerShutterItem changed to RollerShutter in later builds of OH2
                    if ("RollershutterItem".equals(openHABWidget.getItem().getType()) ||
                            "Rollershutter".equals(openHABWidget.getItem().getType()) ||
                            "Rollershutter".equals(openHABWidget.getItem().getGroupType()))
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
            if (openHABWidget.getEncoding() != null) {
                if (openHABWidget.getEncoding().toLowerCase().equals("mjpeg")) {
                    return TYPE_VIDEO_MJPEG;
                } else {
                    return TYPE_VIDEO;
                }
            } else {
                return TYPE_VIDEO;
            }
        } else if (openHABWidget.getType().equals("Webview")) {
            return TYPE_WEB;
        } else if (openHABWidget.getType().equals("Colorpicker")) {
            return TYPE_COLOR;
        } else {
            return TYPE_GENERICITEM;
        }
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
        if (item == null && command == null) {
            return;
        }
        Connection conn;
        try {
            conn = ConnectionFactory.getConnection(Connections.ANY, getContext());
        } catch(ConnectionException e) {
            MessageHandler.showMessageToUser((Activity) getContext(),
                    e.getMessage(), MessageHandler.TYPE_DIALOG, MessageHandler.LOGLEVEL_ALWAYS);
            return;
        }
        conn.getAsyncHttpClient()
                .post(item.getLink(), command, "text/plain", new MyHttpClient.TextResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                Log.e(TAG, "Got command error " + error.getMessage());
                if (responseString != null)
                    Log.e(TAG, "Error response = " + responseString);
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                Log.d(TAG, "Command was sent successfully");
            }
        });
    }

    public void stopVideoWidgets() {
        Log.d(TAG, "Stopping video for " + videoWidgetList.size() + " widgets");
        for (int i = 0; i < videoWidgetList.size(); i++) {
            if (videoWidgetList.get(i) != null)
                videoWidgetList.get(i).stopPlayback();
        }
        videoWidgetList.clear();
        for (int i = 0; i < mjpegWidgetList.size(); i++) {
            if (mjpegWidgetList.get(i) != null)
                mjpegWidgetList.get(i).stop();
        }
        mjpegWidgetList.clear();
    }


    public void stopImageRefresh() {
        Log.d(TAG, "Stopping image refresh for " + refreshImageList.size() + " widgets");
        for (int i = 0; i < refreshImageList.size(); i++) {
            if (refreshImageList.get(i) != null)
                refreshImageList.get(i).cancelRefresh();
        }
        refreshImageList.clear();
    }

    public boolean areAllItemsEnabled() {
        return false;
    }

    public boolean isEnabled(int position) {
        OpenHABWidget openHABWidget = getItem(position);
        if (openHABWidget.getType().equals("Frame"))
            return false;
        return true;
    }

    class MyImageFromItem implements SmartImage {
        private String itemState;

        public MyImageFromItem(String itemState) {
            this.itemState = itemState;
        }

        public Bitmap getBitmap(Context context) {
            byte[] data = Base64.decode(itemState.substring(itemState.indexOf(",") + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        }
    };
}
