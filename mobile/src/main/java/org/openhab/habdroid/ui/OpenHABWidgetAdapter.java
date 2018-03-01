/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.support.annotation.ColorInt;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.VideoView;

import com.loopj.android.image.SmartImage;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetMapping;
import org.openhab.habdroid.ui.widget.ColorPickerDialog;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.ui.widget.OnColorChangedListener;
import org.openhab.habdroid.ui.widget.SegmentedControlButton;
import org.openhab.habdroid.util.MjpegStreamer;
import org.openhab.habdroid.util.MySmartImageView;
import org.openhab.habdroid.util.Util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * This class provides openHAB widgets adapter for list view.
 */

public class OpenHABWidgetAdapter extends RecyclerView.Adapter<OpenHABWidgetAdapter.ViewHolder>
        implements View.OnClickListener, View.OnLongClickListener {
    private static final String TAG = "OpenHABWidgetAdapter";

    public interface ItemClickListener {
        boolean onItemClicked(OpenHABWidget item); // true -> select position
        void onItemLongClicked(OpenHABWidget item);
    }

    private static final int TYPE_GENERICITEM = 0;
    private static final int TYPE_FRAME = 1;
    private static final int TYPE_GROUP = 2;
    private static final int TYPE_SWITCH = 3;
    private static final int TYPE_TEXT = 4;
    private static final int TYPE_SLIDER = 5;
    private static final int TYPE_IMAGE = 6;
    private static final int TYPE_SELECTION = 7;
    private static final int TYPE_SECTIONSWITCH = 8;
    private static final int TYPE_ROLLERSHUTTER = 9;
    private static final int TYPE_SETPOINT = 10;
    private static final int TYPE_CHART = 11;
    private static final int TYPE_VIDEO = 12;
    private static final int TYPE_WEB = 13;
    private static final int TYPE_COLOR = 14;
    private static final int TYPE_VIDEO_MJPEG = 15;

    private final ArrayList<OpenHABWidget> mItems = new ArrayList<>();
    private final LayoutInflater mInflater;
    private ItemClickListener mItemClickListener;
    private @ColorInt int mPrimaryForegroundColor;
    private CharSequence mChartTheme;
    private int mSelectedPosition = -1;
    private final boolean mSelectionEnabled;
    private Connection mConnection;

    public OpenHABWidgetAdapter(Context context, ItemClickListener itemClickListener, boolean
            selectionEnabled, Connection conn) {
        super();

        mInflater = LayoutInflater.from(context);
        mItemClickListener = itemClickListener;
        mSelectionEnabled = selectionEnabled;

        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorControlNormal, tv, false);
        mPrimaryForegroundColor = ContextCompat.getColor(context, tv.data);
        context.getTheme().resolveAttribute(R.attr.chartTheme, tv, true);
        mChartTheme = tv.string;

        mConnection = conn;
    }

    public void update(List<OpenHABWidget> widgets) {
        mItems.clear();
        mItems.addAll(widgets);
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final ViewHolder holder;
        switch (viewType) {
            case TYPE_GENERICITEM:
                holder = new GenericViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_FRAME:
                holder = new FrameViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_GROUP:
                holder = new GroupViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_SWITCH:
                holder = new SwitchViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_TEXT:
                holder = new TextViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_SLIDER:
                holder = new SliderViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_IMAGE:
                holder = new ImageViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_SELECTION:
                holder = new SelectionViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_SECTIONSWITCH:
                holder = new SectionSwitchViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_ROLLERSHUTTER:
                holder = new RollerShutterViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_SETPOINT:
                holder = new SetpointViewHolder(mInflater, parent, mPrimaryForegroundColor, mConnection);
                break;
            case TYPE_CHART:
                holder = new ChartViewHolder(mInflater, parent, mChartTheme, mConnection);
                break;
            case TYPE_VIDEO:
                holder = new VideoViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_WEB:
                holder = new WebViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_COLOR:
                holder = new ColorViewHolder(mInflater, parent, mConnection);
                break;
            case TYPE_VIDEO_MJPEG:
                holder = new MjpegVideoViewHolder(mInflater, parent, mConnection);
                break;
            default:
                throw new IllegalArgumentException("View type " + viewType + " is not known");
        }

        holder.itemView.setTag(holder);

        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.stop();
        holder.bind(mItems.get(position));
        holder.itemView.setActivated(mSelectedPosition == position && mSelectionEnabled);
        holder.itemView.setOnClickListener(mItemClickListener != null ? this : null);
        holder.itemView.setOnLongClickListener(mItemClickListener != null ? this : null);
        holder.itemView.setClickable(mItemClickListener != null);
    }

    @Override
    public void onViewAttachedToWindow(ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        holder.start();
    }

    @Override
    public void onViewDetachedFromWindow(ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.stop();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        OpenHABWidget openHABWidget = mItems.get(position);
        switch (openHABWidget.getType()) {
            case "Frame":
                return TYPE_FRAME;
            case "Group":
                return TYPE_GROUP;
            case "Switch":
                if (openHABWidget.hasMappings()) {
                    return TYPE_SECTIONSWITCH;
                } else {
                    OpenHABItem item = openHABWidget.getItem();
                    if (item != null) {
                        //RollerShutterItem changed to RollerShutter in later builds of OH2
                        if ("RollershutterItem".equals(item.getType()) ||
                                "Rollershutter".equals(item.getType()) ||
                                "Rollershutter".equals(item.getGroupType())) {
                            return TYPE_ROLLERSHUTTER;
                        }
                    }
                    return TYPE_SWITCH;
                }
            case "Text":
                return TYPE_TEXT;
            case "Slider":
                return TYPE_SLIDER;
            case "Image":
                return TYPE_IMAGE;
            case "Selection":
                return TYPE_SELECTION;
            case "Setpoint":
                return TYPE_SETPOINT;
            case "Chart":
                return TYPE_CHART;
            case "Video":
                if ("mjpeg".equalsIgnoreCase(openHABWidget.getEncoding())) {
                    return TYPE_VIDEO_MJPEG;
                }
                return TYPE_VIDEO;
            case "Webview":
                return TYPE_WEB;
            case "Colorpicker":
                return TYPE_COLOR;
            default:
                return TYPE_GENERICITEM;
        }
    }

    public void setSelectedPosition(int position) {
        if (mSelectedPosition >= 0) {
            notifyItemChanged(mSelectedPosition);
        }
        mSelectedPosition = position;
        notifyItemChanged(position);
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    @Override
    public void onClick(View view) {
        ViewHolder holder = (ViewHolder) view.getTag();
        int position = holder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            int oldSelectedPosition = mSelectedPosition;
            setSelectedPosition(position);
            if (!mItemClickListener.onItemClicked(mItems.get(position))) {
                setSelectedPosition(oldSelectedPosition);
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        ViewHolder holder = (ViewHolder) view.getTag();
        int position = holder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            mItemClickListener.onItemLongClicked(mItems.get(position));
        }
        return false;
    }

    public abstract static class ViewHolder extends RecyclerView.ViewHolder {
        protected final Connection mConnection;

        ViewHolder(LayoutInflater inflater, ViewGroup parent, @LayoutRes int layoutResId,
                   Connection conn) {
            super(inflater.inflate(layoutResId, parent, false));
            mConnection = conn;
        }

        public abstract void bind(OpenHABWidget widget);
        public void start() {}
        public void stop() {}

        protected static void updateTextViewColor(TextView view, Integer color) {
            ColorStateList origColor = (ColorStateList) view.getTag(R.id.originalColor);
            if (color != null) {
                if (origColor == null) {
                    view.setTag(R.id.originalColor, view.getTextColors());
                }
                view.setTextColor(color);
            } else if (origColor != null) {
                view.setTextColor(origColor);
                view.setTag(R.id.originalColor, null);
            }
        }

        protected void updateIcon(MySmartImageView iconView, OpenHABWidget widget) {
            // This is needed to escape possible spaces and everything according to rfc2396
            String iconUrl = mConnection.getOpenHABUrl() + Uri.encode(widget.getIconPath(), "/?=&");
            iconView.setImageUrl(iconUrl, mConnection.getUsername(), mConnection.getPassword(),
                    R.drawable.blank_icon);
            Integer iconColor = widget.getIconColor();
            if (iconColor != null) {
                iconView.setColorFilter(iconColor);
            } else {
                iconView.clearColorFilter();
            }
        }
    }

    public static class GenericViewHolder extends ViewHolder {
        private final TextView mLabelView;
        private final MySmartImageView mIconView;

        GenericViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_genericitem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mIconView = itemView.findViewById(R.id.widgetimage);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mLabelView.setText(widget.getLabel());
            updateTextViewColor(mLabelView, widget.getLabelColor());
            updateIcon(mIconView, widget);
        }
    }

    public static class FrameViewHolder extends ViewHolder {
        private final TextView mLabelView;

        FrameViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_frameitem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            itemView.setClickable(false);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mLabelView.setText(widget.getLabel());
            updateTextViewColor(mLabelView, widget.getValueColor());
            // hide empty frames
            itemView.setVisibility(widget.getLabel().isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    public static class GroupViewHolder extends ViewHolder {
        private final TextView mLabelView;
        private final TextView mValueView;
        private final MySmartImageView mIconView;

        GroupViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_groupitem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mValueView = itemView.findViewById(R.id.widgetvalue);
            mIconView = itemView.findViewById(R.id.widgetimage);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            String[] splitString = widget.getLabel().split("\\[|\\]");
            mLabelView.setText(splitString.length > 0 ? splitString[0] : null);
            updateTextViewColor(mLabelView, widget.getLabelColor());
            mValueView.setText(splitString.length > 1 ? splitString[1] : null);
            updateTextViewColor(mValueView, widget.getValueColor());
            updateIcon(mIconView, widget);
        }
    }

    public static class SwitchViewHolder extends ViewHolder implements View.OnTouchListener {
        private final TextView mLabelView;
        private final MySmartImageView mIconView;
        private final SwitchCompat mSwitch;
        private OpenHABItem mBoundItem;

        SwitchViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_switchitem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mIconView = itemView.findViewById(R.id.widgetimage);
            mSwitch = itemView.findViewById(R.id.switchswitch);
            mSwitch.setOnTouchListener(this);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mLabelView.setText(widget.getLabel());
            updateTextViewColor(mLabelView, widget.getLabelColor());
            updateIcon(mIconView, widget);
            mSwitch.setChecked(widget.hasItem() && widget.getItem().getStateAsBoolean());
            mBoundItem = widget.getItem();
        }

        @Override
        public boolean onTouch(View v, MotionEvent motionEvent) {
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
                Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem,
                        mSwitch.isChecked() ? "OFF" : "ON");
            }
            return false;
        }
    }

    public static class TextViewHolder extends ViewHolder {
        private final TextView mLabelView;
        private final TextView mValueView;
        private final MySmartImageView mIconView;

        TextViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_textitem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mValueView = itemView.findViewById(R.id.widgetvalue);
            mIconView = itemView.findViewById(R.id.widgetimage);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            String[] splitString = widget.getLabel().split("\\[|\\]");
            mLabelView.setText(splitString.length > 0 ? splitString[0] : widget.getLabel());
            updateTextViewColor(mLabelView, widget.getLabelColor());
            mValueView.setText(splitString.length > 1 ? splitString[1] : null);
            mValueView.setVisibility(splitString.length > 1 ? View.VISIBLE : View.GONE);
            updateTextViewColor(mValueView, widget.getValueColor());
            updateIcon(mIconView, widget);
        }
    }

    public static class SliderViewHolder extends ViewHolder implements SeekBar.OnSeekBarChangeListener {
        private final TextView mLabelView;
        private final MySmartImageView mIconView;
        private final SeekBar mSeekBar;
        private OpenHABItem mBoundItem;

        SliderViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_slideritem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mIconView = itemView.findViewById(R.id.widgetimage);
            mSeekBar = itemView.findViewById(R.id.sliderseekbar);
            mSeekBar.setOnSeekBarChangeListener(this);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            String[] splitString = widget.getLabel().split("\\[|\\]");
            mLabelView.setText(splitString.length > 0 ? splitString[0] : null);
            updateTextViewColor(mLabelView, widget.getLabelColor());
            updateIcon(mIconView, widget);
            if (widget.hasItem()) {
                int progress;
                if (widget.getItem().getType().equals("Color") || "Color".equals(widget.getItem().getGroupType())) {
                    try {
                        progress = widget.getItem().getStateAsBrightness();
                    } catch (IllegalStateException e) {
                        progress = 0;
                    }
                } else {
                    progress = widget.getItem().getStateAsFloat().intValue();
                }
                mSeekBar.setProgress(progress);
            } else {
                mSeekBar.setProgress(0);
            }
            mBoundItem = widget.getItem();
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "onStartTrackingTouch position = " + seekBar.getProgress());
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "onStopTrackingTouch position = " + seekBar.getProgress());
            Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem,
                    String.valueOf(seekBar.getProgress()));
        }
    }

    public static class ImageViewHolder extends ViewHolder {
        private final MySmartImageView mImageView;
        private final View mParentView;
        private int mRefreshRate;

        ImageViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_imageitem, conn);
            mImageView = itemView.findViewById(R.id.imageimage);
            mParentView = parent;
        }

        @Override
        public void bind(OpenHABWidget widget) {
            // We scale the image at max 90% of the available height
            mImageView.setMaxSize(mParentView.getWidth(), mParentView.getHeight() * 90 / 100);

            OpenHABItem item = widget.getItem();
            final String state = item != null ? item.getState() : null;

            if (state != null && state.matches("data:image/.*;base64,.*")) {
                mImageView.setImageWithData(new SmartImage() {
                    @Override
                    public Bitmap getBitmap(Context context) {
                        byte[] data = Base64.decode(state.substring(state.indexOf(",") + 1), Base64.DEFAULT);
                        return BitmapFactory.decodeByteArray(data, 0, data.length);
                    }
                });
                mRefreshRate = 0;
            } else {
                // Widget URL may be relative, add base URL if that's the case
                Uri uri = Uri.parse(widget.getUrl());
                if (uri.getScheme() == null) {
                    uri = Uri.parse(mConnection.getOpenHABUrl() + widget.getUrl());
                }
                mImageView.setImageUrl(uri.toString(), mConnection.getUsername(),
                        mConnection.getPassword(), false);
                mRefreshRate = widget.getRefresh();
            }
        }

        @Override
        public void start() {
            if (mRefreshRate > 0) {
                mImageView.setRefreshRate(mRefreshRate);
            } else {
                mImageView.cancelRefresh();
            }
        }

        @Override
        public void stop() {
            mImageView.cancelRefresh();
        }
    }

    public static class SelectionViewHolder extends ViewHolder implements AdapterView.OnItemClickListener {
        private final TextView mLabelView;
        private final MySmartImageView mIconView;
        private final Spinner mSpinner;

        SelectionViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_selectionitem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mSpinner = itemView.findViewById(R.id.selectionspinner);
            mIconView = itemView.findViewById(R.id.widgetimage);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            int spinnerSelectedIndex = -1;
            String[] splitString = widget.getLabel().split("\\[|\\]");

            mLabelView.setText(splitString.length > 0 ? splitString[0] : widget.getLabel());
            updateTextViewColor(mLabelView, widget.getLabelColor());

            updateIcon(mIconView, widget);

            ArrayList<String> spinnerArray = new ArrayList<String>();
            String state = widget.getItem() != null ? widget.getItem().getState() : null;

            for (OpenHABWidgetMapping mapping : widget.getMappings()) {
                String command = mapping.getCommand();
                spinnerArray.add(mapping.getLabel());
                if (command != null && command.equals(state)) {
                    spinnerSelectedIndex = spinnerArray.size() - 1;
                }
            }
            if (spinnerSelectedIndex == -1) {
                spinnerArray.add("          ");
                spinnerSelectedIndex = spinnerArray.size() - 1;
            }

            ArrayAdapter<String> spinnerAdapter = new SpinnerClickAdapter<String>(itemView.getContext(),
                    android.R.layout.simple_spinner_item, spinnerArray, widget, this);
            spinnerAdapter.setDropDownViewResource(android.R.layout.select_dialog_singlechoice);

            mSpinner.setPrompt(mLabelView.getText());
            mSpinner.setAdapter(spinnerAdapter);
            if (spinnerSelectedIndex >= 0) {
                Log.d(TAG, "Setting spinner selected index to " + String.valueOf(spinnerSelectedIndex));
                mSpinner.setSelection(spinnerSelectedIndex);
            } else {
                Log.d(TAG, "Not setting spinner selected index");
            }
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
            Log.d(TAG, "Spinner item click on index " + index);
            String selectedLabel = (String) parent.getAdapter().getItem(index);
            Log.d(TAG, "Spinner onItemSelected selected label = " + selectedLabel);

            OpenHABWidget widget = (OpenHABWidget) parent.getTag();
            if (index < widget.getMappings().size()) {
                Log.d(TAG, "Label selected = " + widget.getMapping(index).getLabel());
                for (OpenHABWidgetMapping mapping : widget.getMappings()) {
                    if (mapping.getLabel().equals(selectedLabel)) {
                        Log.d(TAG, "Spinner onItemSelected found match with " + mapping.getCommand());
                        Util.sendItemCommand(mConnection.getAsyncHttpClient(), widget.getItem(),
                                mapping.getCommand());
                    }
                }
            }
            // TODO: there's probably a better solution...
            try {
                // Close the spinner programmatically
                Method method = Spinner.class.getDeclaredMethod("onDetachedFromWindow");
                method.setAccessible(true);
                method.invoke(mSpinner);
            } catch (Exception ignored) {
            }
        }
    }

    public static class SectionSwitchViewHolder extends ViewHolder implements View.OnClickListener {
        private final LayoutInflater mInflater;
        private final TextView mLabelView;
        private final TextView mValueView;
        private final MySmartImageView mIconView;
        private final RadioGroup mRadioGroup;
        private OpenHABItem mBoundItem;

        SectionSwitchViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_sectionswitchitem, conn);
            mInflater = inflater;
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mValueView = itemView.findViewById(R.id.widgetvalue);
            mIconView = itemView.findViewById(R.id.widgetimage);
            mRadioGroup = itemView.findViewById(R.id.sectionswitchradiogroup);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            String[] splitString = widget.getLabel().split("\\[|\\]");
            mLabelView.setText(splitString.length > 0 ? splitString[0] : widget.getLabel());
            updateTextViewColor(mLabelView, widget.getLabelColor());
            mValueView.setText(splitString.length > 1 ? splitString[1] : null);
            updateTextViewColor(mValueView, widget.getValueColor());
            updateIcon(mIconView, widget);

            mBoundItem = widget.getItem();

            List<OpenHABWidgetMapping> mappings = widget.getMappings();
            // inflate missing views
            for (int i = mRadioGroup.getChildCount(); i < mappings.size(); i++) {
                View view = mInflater.inflate(R.layout.openhabwidgetlist_sectionswitchitem_button,
                        mRadioGroup, false);
                view.setOnClickListener(this);
                mRadioGroup.addView(view);
            }
            // bind views
            for (int i = 0; i < mappings.size(); i++) {
                SegmentedControlButton button = (SegmentedControlButton) mRadioGroup.getChildAt(i);
                String command = mappings.get(i).getCommand();
                button.setText(mappings.get(i).getLabel());
                button.setTag(command);
                button.setChecked(mBoundItem != null && command != null
                        && command.equals(mBoundItem.getState()));
                button.setVisibility(View.VISIBLE);
            }
            // hide spare views
            for (int i = mappings.size(); i < mRadioGroup.getChildCount(); i++) {
                mRadioGroup.getChildAt(i).setVisibility(View.GONE);
            }
        }

        @Override
        public void onClick(View view) {
            Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem, (String) view.getTag());
        }
    }

    public static class RollerShutterViewHolder extends ViewHolder implements View.OnTouchListener {
        private final TextView mLabelView;
        private final MySmartImageView mIconView;
        private OpenHABItem mBoundItem;

        RollerShutterViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_rollershutteritem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mIconView = itemView.findViewById(R.id.widgetimage);
            initButton(R.id.rollershutterbutton_up, "UP");
            initButton(R.id.rollershutterbutton_down, "DOWN");
            initButton(R.id.rollershutterbutton_stop, "STOP");
        }

        private void initButton(@IdRes int resId, String command) {
            ImageButton button = itemView.findViewById(resId);
            button.setOnTouchListener(this);
            button.setTag(command);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mLabelView.setText(widget.getLabel());
            updateTextViewColor(mLabelView, widget.getLabelColor());
            updateIcon(mIconView, widget);
            mBoundItem = widget.getItem();
        }

        @Override
        public boolean onTouch(View v, MotionEvent motionEvent) {
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
                Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem, (String) v.getTag());
            }
            return false;
        }
    }

    public static class SetpointViewHolder extends ViewHolder implements View.OnClickListener {
        private final TextView mLabelView;
        private final TextView mValueView;
        private final MySmartImageView mIconView;
        private final LayoutInflater mInflater;
        private OpenHABWidget mBoundWidget;

        SetpointViewHolder(LayoutInflater inflater, ViewGroup parent,
                @ColorInt int primaryForegroundColor, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_setpointitem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mValueView = itemView.findViewById(R.id.widgetvalue);
            mValueView.setOnClickListener(this);
            mIconView = itemView.findViewById(R.id.widgetimage);
            mInflater = inflater;

            ImageView dropdownArrow = itemView.findViewById(R.id.imageViewDownArrow);
            dropdownArrow.setOnClickListener(this);
            dropdownArrow.setColorFilter(primaryForegroundColor, PorterDuff.Mode.SRC_IN);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            String[] splitString = widget.getLabel().split("\\[|\\]");
            mLabelView.setText(splitString.length > 0 ? splitString[0] : widget.getLabel());
            updateTextViewColor(mLabelView, widget.getLabelColor());
            mValueView.setText(splitString.length > 1 ? splitString[1] : null);
            mValueView.setVisibility(splitString.length > 1 ? View.VISIBLE : View.GONE);
            updateTextViewColor(mValueView, widget.getValueColor());
            updateIcon(mIconView, widget);
            mBoundWidget = widget;
        }

        @Override
        public void onClick(final View view) {
            float minValue = mBoundWidget.getMinValue();
            //This prevents an exception below. But could lead to user confusion if this case is ever encountered.
            float maxValue = Math.max(minValue, mBoundWidget.getMaxValue());
            float stepSize = minValue == maxValue ? 1 : mBoundWidget.getStep();

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

            final View dialogView = mInflater.inflate(R.layout.openhab_dialog_numberpicker, null);
            final NumberPicker numberPicker = dialogView.findViewById(R.id.numberpicker);

            numberPicker.setMinValue(0);
            numberPicker.setMaxValue(stepValues.length - 1);
            numberPicker.setDisplayedValues(stepValues);

            // Find the closest value in the calculated step value
            String stateString = Float.toString(mBoundWidget.getItem().getStateAsFloat());
            int stepIndex = Arrays.binarySearch(stepValues, stateString, new Comparator<CharSequence>() {
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

            new AlertDialog.Builder(view.getContext())
                    .setTitle(mLabelView.getText())
                    .setView(dialogView)
                    .setPositiveButton(R.string.set, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundWidget.getItem(),
                                    stepValues[numberPicker.getValue()]);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    public static class ChartViewHolder extends ViewHolder {
        private final MySmartImageView mImageView;
        private final View mParentView;
        private final CharSequence mChartTheme;
        private final Random mRandom = new Random();
        private int mRefreshRate = 0;
        private int mDensity;

        ChartViewHolder(LayoutInflater inflater, ViewGroup parent, CharSequence theme, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_chartitem, conn);
            mImageView = itemView.findViewById(R.id.chartimage);
            mParentView = parent;

            WindowManager wm = (WindowManager) itemView.getContext().getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);

            mDensity = metrics.densityDpi;
            mChartTheme = theme;
        }

        @Override
        public void bind(OpenHABWidget widget) {
            OpenHABItem item = widget.getItem();

            if (item != null) {
                StringBuilder chartUrl = new StringBuilder(mConnection.getOpenHABUrl());

                if ("GroupItem".equals(item.getType()) || "Group".equals(item.getType())) {
                    chartUrl.append("chart?groups=").append(item.getName());
                } else {
                    chartUrl.append("chart?items=").append(item.getName());
                }
                chartUrl.append("&period=").append(widget.getPeriod())
                        .append("&random=").append(mRandom.nextInt())
                        .append("&dpi=").append(mDensity);
                if (!TextUtils.isEmpty(widget.getService())) {
                    chartUrl.append("&service=").append(widget.getService());
                }
                if (mChartTheme != null) {
                    chartUrl.append("&theme=").append(mChartTheme);
                }
                if (widget.getLegend() != null) {
                    chartUrl.append("&legend=").append(widget.getLegend());
                }

                int parentWidth = mParentView.getWidth();
                if (parentWidth > 0) {
                    chartUrl.append("&w=").append(parentWidth);
                    chartUrl.append("&h=").append(parentWidth / 2);
                }

                Log.d(TAG, "Chart url = " + chartUrl);

                mImageView.setImageUrl(chartUrl.toString(), mConnection.getUsername(),
                        mConnection.getPassword(), false);
                mRefreshRate = widget.getRefresh();
            } else {
                Log.e(TAG, "Chart item is null");
                mImageView.setImageDrawable(null);
                mRefreshRate = 0;
            }
        }

        @Override
        public void start() {
            if (mRefreshRate > 0) {
                mImageView.setRefreshRate(mRefreshRate);
            } else {
                mImageView.cancelRefresh();
            }
        }

        @Override
        public void stop() {
            mImageView.cancelRefresh();
        }
    }

    public static class VideoViewHolder extends ViewHolder {
        private final VideoView mVideoView;

        VideoViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_videoitem, conn);
            mVideoView = itemView.findViewById(R.id.videovideo);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            // FIXME: check for URL changes here
            if (!mVideoView.isPlaying()) {
                final String videoUrl;
                OpenHABItem videoItem = widget.getItem();
                if ("hls".equalsIgnoreCase(widget.getEncoding())
                        && videoItem != null && videoItem.getType().equals("String")
                        && !"UNDEF".equals(videoItem.getState())) {
                    videoUrl = videoItem.getState();
                } else {
                    videoUrl = widget.getUrl();
                }
                Log.d(TAG, "Opening video at " + videoUrl);
                mVideoView.setVideoURI(Uri.parse(videoUrl));
            }
        }

        @Override
        public void start() {
            mVideoView.start();
        }

        @Override
        public void stop() {
            mVideoView.stopPlayback();
        }
    }

    public static class WebViewHolder extends ViewHolder {
        private final WebView mWebView;
        private final int mRowHeightPixels;

        WebViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_webitem, conn);
            mWebView = itemView.findViewById(R.id.webweb);

            final Resources res = itemView.getContext().getResources();
            mRowHeightPixels = res.getDimensionPixelSize(R.dimen.webview_row_height);
        }

        @SuppressLint("SetJavaScriptEnabled")
        @Override
        public void bind(OpenHABWidget widget) {
            ViewGroup.LayoutParams lp = mWebView.getLayoutParams();
            int desiredHeightPixels = widget.getHeight() > 0
                    ? widget.getHeight() * mRowHeightPixels : ViewGroup.LayoutParams.WRAP_CONTENT;
            if (lp.height != desiredHeightPixels) {
                lp.height = desiredHeightPixels;
                mWebView.setLayoutParams(lp);
            }

            mWebView.setWebViewClient(new AnchorWebViewClient(widget.getUrl(),
                    mConnection.getUsername(), mConnection.getPassword()));
            mWebView.getSettings().setDomStorageEnabled(true);
            mWebView.getSettings().setJavaScriptEnabled(true);
            mWebView.loadUrl(widget.getUrl());
        }
    }

    public static class ColorViewHolder extends ViewHolder implements View.OnTouchListener {
        private final TextView mLabelView;
        private final MySmartImageView mIconView;
        private OpenHABItem mBoundItem;

        ColorViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_coloritem, conn);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mIconView = itemView.findViewById(R.id.widgetimage);
            initButton(R.id.colorbutton_up, "ON");
            initButton(R.id.colorbutton_down, "OFF");
            itemView.findViewById(R.id.colorbutton_color).setOnTouchListener(this);
        }

        private void initButton(@IdRes int resId, String command) {
            ImageButton button = itemView.findViewById(resId);
            button.setOnTouchListener(this);
            button.setTag(command);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mLabelView.setText(widget.getLabel());
            updateTextViewColor(mLabelView, widget.getLabelColor());
            updateIcon(mIconView, widget);
            mBoundItem = widget.getItem();
        }

        @Override
        public boolean onTouch(View v, MotionEvent motionEvent) {
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
                if (v.getTag() instanceof String) {
                    Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem, (String) v.getTag());
                } else {
                    ColorPickerDialog colorDialog = new ColorPickerDialog(v.getContext(), new OnColorChangedListener() {
                        @Override
                        public void colorChanged(float[] hsv, View v) {
                            Log.d(TAG, "New color HSV = " + hsv[0] + ", " + hsv[1] + ", " + hsv[2]);
                            String newColor = String.valueOf(hsv[0]) + "," + String.valueOf(hsv[1] * 100) + "," + String.valueOf(hsv[2] * 100);
                            Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem, newColor);
                        }
                    }, mBoundItem.getStateAsHSV());
                    colorDialog.show();
                }
            }
            return false;
        }
    }

    public static class MjpegVideoViewHolder extends ViewHolder {
        private final ImageView mImageView;
        private MjpegStreamer mStreamer;

        MjpegVideoViewHolder(LayoutInflater inflater, ViewGroup parent, Connection conn) {
            super(inflater, parent, R.layout.openhabwidgetlist_videomjpegitem, conn);
            mImageView = itemView.findViewById(R.id.mjpegimage);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mStreamer = new MjpegStreamer(widget.getUrl(),
                    mConnection.getUsername(), mConnection.getPassword(), mImageView.getContext());
            mStreamer.setTargetImageView(mImageView);
        }

        @Override
        public void start() {
            if (mStreamer != null) {
                mStreamer.start();
            }
        }

        @Override
        public void stop() {
            if (mStreamer != null) {
                mStreamer.stop();
            }
        }
    }

    public static class WidgetItemDecoration extends DividerItemDecoration {
        public WidgetItemDecoration(Context context) {
            super(context);
        }

        @Override
        protected boolean suppressDividerForChild(View child, RecyclerView parent) {
            if (super.suppressDividerForChild(child, parent)) {
                return true;
            }

            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) {
                return false;
            }

            // hide dividers before and after frame widgets
            if (parent.getAdapter().getItemViewType(position) == TYPE_FRAME) {
                return true;
            }
            if (position < parent.getAdapter().getItemCount() - 1) {
                if (parent.getAdapter().getItemViewType(position + 1) == TYPE_FRAME) {
                    return true;
                }
            }

            return false;
        }
    }
}
