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
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.VisibleForTesting;
import android.support.v7.app.AlertDialog;
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
import android.widget.TextView;
import android.widget.VideoView;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABLabeledValue;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.ui.widget.ExtendedSpinner;
import org.openhab.habdroid.ui.widget.SegmentedControlButton;
import org.openhab.habdroid.ui.widget.WidgetImageView;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MjpegStreamer;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import okhttp3.HttpUrl;

/**
 * This class provides openHAB widgets adapter for list view.
 */

public class OpenHABWidgetAdapter extends RecyclerView.Adapter<OpenHABWidgetAdapter.ViewHolder>
        implements View.OnClickListener, View.OnLongClickListener {
    private static final String TAG = "OpenHABWidgetAdapter";

    public interface ItemClickListener {
        void onItemClicked(OpenHABWidget item);
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
    private static final int TYPE_LOCATION = 16;

    private final ArrayList<OpenHABWidget> mItems = new ArrayList<>();
    private final LayoutInflater mInflater;
    private ItemClickListener mItemClickListener;
    private CharSequence mChartTheme;
    private int mSelectedPosition = -1;
    private Connection mConnection;
    private ColorMapper mColorMapper;

    public OpenHABWidgetAdapter(Context context, Connection connection,
            ItemClickListener itemClickListener) {
        super();

        mInflater = LayoutInflater.from(context);
        mItemClickListener = itemClickListener;
        mConnection = connection;
        mColorMapper = new ColorMapper(context);

        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.chartTheme, tv, true);
        mChartTheme = tv.string;
    }

    public void update(List<OpenHABWidget> widgets) {
        boolean compatibleUpdate = true;

        if (widgets.size() != mItems.size()) {
            compatibleUpdate = false;
        } else {
            for (int i = 0; i < widgets.size(); i++) {
                if (getItemViewType(mItems.get(i)) != getItemViewType(widgets.get(i))) {
                    compatibleUpdate = false;
                    break;
                }
            }
        }

        if (compatibleUpdate) {
            for (int i = 0; i < widgets.size(); i++) {
                if (!mItems.get(i).equals(widgets.get(i))) {
                    mItems.set(i, widgets.get(i));
                    notifyItemChanged(i);
                }
            }
        } else {
            mItems.clear();
            mItems.addAll(widgets);
            notifyDataSetChanged();
        }
    }

    public void updateWidget(OpenHABWidget widget) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).id().equals(widget.id())) {
                mItems.set(i, widget);
                notifyItemChanged(i);
                break;
            }
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final ViewHolder holder;
        switch (viewType) {
            case TYPE_GENERICITEM:
                holder = new GenericViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_FRAME:
                holder = new FrameViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_GROUP:
                holder = new GroupViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_SWITCH:
                holder = new SwitchViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_TEXT:
                holder = new TextViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_SLIDER:
                holder = new SliderViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_IMAGE:
                holder = new ImageViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_SELECTION:
                holder = new SelectionViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_SECTIONSWITCH:
                holder = new SectionSwitchViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_ROLLERSHUTTER:
                holder = new RollerShutterViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_SETPOINT:
                holder = new SetpointViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_CHART:
                holder = new ChartViewHolder(mInflater, parent, mChartTheme, mConnection, mColorMapper);
                break;
            case TYPE_VIDEO:
                holder = new VideoViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_WEB:
                holder = new WebViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_COLOR:
                holder = new ColorViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_VIDEO_MJPEG:
                holder = new MjpegVideoViewHolder(mInflater, parent, mConnection, mColorMapper);
                break;
            case TYPE_LOCATION:
                holder = MapViewHelper.createViewHolder(mInflater, parent, mConnection, mColorMapper);
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
        if (holder instanceof FrameViewHolder) {
            ((FrameViewHolder) holder).setShownAsFirst(position == 0);
        }
        holder.itemView.setActivated(mSelectedPosition == position);
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

    public OpenHABWidget getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        return getItemViewType(mItems.get(position));
    }

    private int getItemViewType(OpenHABWidget widget) {
        switch (widget.type()) {
            case Frame:
                return TYPE_FRAME;
            case Group:
                return TYPE_GROUP;
            case Switch:
                if (widget.hasMappings()) {
                    return TYPE_SECTIONSWITCH;
                } else {
                    OpenHABItem item = widget.item();
                    if (item != null && item.isOfTypeOrGroupType(OpenHABItem.Type.Rollershutter)) {
                        return TYPE_ROLLERSHUTTER;
                    }
                    return TYPE_SWITCH;
                }
            case Text:
                return TYPE_TEXT;
            case Slider:
                return TYPE_SLIDER;
            case Image:
                return TYPE_IMAGE;
            case Selection:
                return TYPE_SELECTION;
            case Setpoint:
                return TYPE_SETPOINT;
            case Chart:
                return TYPE_CHART;
            case Video:
                if ("mjpeg".equalsIgnoreCase(widget.encoding())) {
                    return TYPE_VIDEO_MJPEG;
                }
                return TYPE_VIDEO;
            case Webview:
                return TYPE_WEB;
            case Colorpicker:
                return TYPE_COLOR;
            case Mapview:
                return TYPE_LOCATION;
            default:
                return TYPE_GENERICITEM;
        }
    }

    public void setSelectedPosition(int position) {
        if (mSelectedPosition == position) {
            return;
        }
        if (mSelectedPosition >= 0) {
            notifyItemChanged(mSelectedPosition);
        }
        mSelectedPosition = position;
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    @Override
    public void onClick(View view) {
        ViewHolder holder = (ViewHolder) view.getTag();
        int position = holder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            mItemClickListener.onItemClicked(mItems.get(position));
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
        private final ColorMapper mColorMapper;

        ViewHolder(LayoutInflater inflater, ViewGroup parent, @LayoutRes int layoutResId,
                Connection conn, ColorMapper colorMapper) {
            super(inflater.inflate(layoutResId, parent, false));
            mConnection = conn;
            mColorMapper = colorMapper;
        }

        public abstract void bind(OpenHABWidget widget);
        public void start() {}
        public void stop() {}

        protected void updateTextViewColor(TextView view, String colorName) {
            ColorStateList origColor = (ColorStateList) view.getTag(R.id.originalColor);
            Integer color = mColorMapper.mapColor(colorName);
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

        protected void updateIcon(WidgetImageView iconView, OpenHABWidget widget) {
            if (widget.icon() == null) {
                iconView.setImageDrawable(null);
                return;
            }
            // This is needed to escape possible spaces and everything according to rfc2396
            String iconUrl = Uri.encode(widget.iconPath(), "/?=&");
            iconView.setImageUrl(mConnection, iconUrl);
            Integer iconColor = mColorMapper.mapColor(widget.iconColor());
            if (iconColor != null) {
                iconView.setColorFilter(iconColor);
            } else {
                iconView.clearColorFilter();
            }
        }
    }

    public static abstract class LabeledItemBaseViewHolder extends ViewHolder {
        protected final TextView mLabelView;
        protected final TextView mValueView;
        protected final WidgetImageView mIconView;

        LabeledItemBaseViewHolder(LayoutInflater inflater, ViewGroup parent,
                @LayoutRes int layoutResId, Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, layoutResId, conn, colorMapper);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mValueView = itemView.findViewById(R.id.widgetvalue);
            mIconView = itemView.findViewById(R.id.widgeticon);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            String[] splitString = widget.label().split("\\[|\\]");
            mLabelView.setText(splitString.length > 0 ? splitString[0] : null);
            updateTextViewColor(mLabelView, widget.labelColor());
            if (mValueView != null) {
                mValueView.setText(splitString.length > 1 ? splitString[1] : null);
                mValueView.setVisibility(splitString.length > 1 ? View.VISIBLE : View.GONE);
                updateTextViewColor(mValueView, widget.valueColor());
            }
            updateIcon(mIconView, widget);
        }
    }

    public static class GenericViewHolder extends ViewHolder {
        private final TextView mLabelView;
        private final WidgetImageView mIconView;

        GenericViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_genericitem, conn, colorMapper);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mIconView = itemView.findViewById(R.id.widgeticon);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mLabelView.setText(widget.label());
            updateTextViewColor(mLabelView, widget.labelColor());
            updateIcon(mIconView, widget);
        }
    }

    public static class FrameViewHolder extends ViewHolder {
        private final View mDivider;
        private final View mSpacer;
        private final TextView mLabelView;

        FrameViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_frameitem, conn, colorMapper);
            mLabelView = itemView.findViewById(R.id.widgetlabel);
            mDivider = itemView.findViewById(R.id.divider);
            mSpacer = itemView.findViewById(R.id.spacer);
            itemView.setClickable(false);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mLabelView.setText(widget.label());
            updateTextViewColor(mLabelView, widget.valueColor());
            // hide empty frames
            itemView.setVisibility(widget.label().isEmpty() ? View.GONE : View.VISIBLE);
        }

        public void setShownAsFirst(boolean shownAsFirst) {
            mDivider.setVisibility(shownAsFirst ? View.GONE : View.VISIBLE);
            mSpacer.setVisibility(shownAsFirst ? View.VISIBLE : View.GONE);
        }
    }

    public static class GroupViewHolder extends LabeledItemBaseViewHolder {
        private final ImageView mRightArrow;

        GroupViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_groupitem, conn, colorMapper);
            mRightArrow = itemView.findViewById(R.id.right_arrow);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);
            mRightArrow.setVisibility(widget.linkedPage() != null ? View.VISIBLE : View.GONE);
        }
    }

    public static class SwitchViewHolder extends LabeledItemBaseViewHolder
            implements View.OnTouchListener {
        private final SwitchCompat mSwitch;
        private OpenHABItem mBoundItem;

        SwitchViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_switchitem, conn, colorMapper);
            mSwitch = itemView.findViewById(R.id.toggle);
            mSwitch.setOnTouchListener(this);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);
            mBoundItem = widget.item();
            mSwitch.setChecked(mBoundItem != null && mBoundItem.stateAsBoolean());
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

    public static class TextViewHolder extends LabeledItemBaseViewHolder {
        private final ImageView mRightArrow;

        TextViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_textitem, conn, colorMapper);
            mRightArrow = itemView.findViewById(R.id.right_arrow);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);
            mRightArrow.setVisibility(widget.linkedPage() != null ? View.VISIBLE : View.GONE);
        }
    }

    public static class SliderViewHolder extends LabeledItemBaseViewHolder
            implements SeekBar.OnSeekBarChangeListener {
        private final SeekBar mSeekBar;
        private OpenHABItem mBoundItem;

        SliderViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_slideritem, conn, colorMapper);
            mSeekBar = itemView.findViewById(R.id.seekbar);
            mSeekBar.setOnSeekBarChangeListener(this);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);
            mBoundItem = widget.item();
            if (mBoundItem != null) {
                int progress;
                if (mBoundItem.isOfTypeOrGroupType(OpenHABItem.Type.Color)) {
                    Integer brightness = mBoundItem.stateAsBrightness();
                    progress = brightness != null ? brightness : 0;
                } else {
                    progress = (int) mBoundItem.stateAsFloat();
                }
                mSeekBar.setProgress(progress);
            } else {
                mSeekBar.setProgress(0);
            }
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
        private final WidgetImageView mImageView;
        private final View mParentView;
        private int mRefreshRate;

        ImageViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_imageitem, conn, colorMapper);
            mImageView = itemView.findViewById(R.id.image);
            mParentView = parent;
        }

        @Override
        public void bind(OpenHABWidget widget) {
            OpenHABItem item = widget.item();
            final String state = item != null ? item.state() : null;

            // Make sure images fit into the content frame by scaling
            // them at max 90% of the available height
            int maxHeight = mParentView.getHeight() > 0
                    ? Math.round(0.9f * mParentView.getHeight()) : Integer.MAX_VALUE;
            mImageView.setMaxHeight(maxHeight);

            if (state != null && state.matches("data:image/.*;base64,.*")) {
                byte[] data = Base64.decode(state.substring(state.indexOf(",") + 1), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                mImageView.setImageBitmap(bitmap);
                mRefreshRate = 0;
            } else {
                mImageView.setImageUrl(mConnection, widget.url());
                mRefreshRate = widget.refresh();
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

    public static class SelectionViewHolder extends LabeledItemBaseViewHolder
            implements ExtendedSpinner.OnSelectionUpdatedListener {
        private final ExtendedSpinner mSpinner;
        private OpenHABItem mBoundItem;
        private List<OpenHABLabeledValue> mBoundMappings;

        SelectionViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_selectionitem, conn, colorMapper);
            mSpinner = itemView.findViewById(R.id.spinner);
            mSpinner.setOnSelectionUpdatedListener(this);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);

            mBoundItem = widget.item();
            mBoundMappings = widget.getMappingsOrItemOptions();

            int spinnerSelectedIndex = -1;
            ArrayList<String> spinnerArray = new ArrayList<>();
            String state = mBoundItem != null ? mBoundItem.state() : null;

            for (OpenHABLabeledValue mapping : mBoundMappings) {
                String command = mapping.value();
                spinnerArray.add(mapping.label());
                if (command != null && command.equals(state)) {
                    spinnerSelectedIndex = spinnerArray.size() - 1;
                }
            }
            if (spinnerSelectedIndex == -1) {
                spinnerArray.add("          ");
                spinnerSelectedIndex = spinnerArray.size() - 1;
            }

            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(itemView.getContext(),
                    android.R.layout.simple_spinner_item, spinnerArray);
            spinnerAdapter.setDropDownViewResource(android.R.layout.select_dialog_singlechoice);

            mSpinner.setPrompt(mLabelView.getText());
            mSpinner.setAdapter(spinnerAdapter);
            mSpinner.setSelectionWithoutUpdateCallback(spinnerSelectedIndex);
        }

        @Override
        public void onSelectionUpdated(int position) {
            Log.d(TAG, "Spinner item click on index " + position);
            if (position >= mBoundMappings.size()) {
                return;
            }
            OpenHABLabeledValue item = mBoundMappings.get(position);
            Log.d(TAG, "Spinner onItemSelected found match with " + item.value());
            Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem, item.value());
        }
    }

    public static class SectionSwitchViewHolder extends LabeledItemBaseViewHolder
            implements View.OnClickListener {
        private final LayoutInflater mInflater;
        private final RadioGroup mRadioGroup;
        private OpenHABItem mBoundItem;

        SectionSwitchViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_sectionswitchitem, conn, colorMapper);
            mInflater = inflater;
            mRadioGroup = itemView.findViewById(R.id.switchgroup);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);
            mBoundItem = widget.item();

            List<OpenHABLabeledValue> mappings = widget.mappings();
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
                String command = mappings.get(i).value();
                button.setText(mappings.get(i).label());
                button.setTag(command);
                button.setChecked(mBoundItem != null && command != null
                        && command.equals(mBoundItem.state()));
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

    public static class RollerShutterViewHolder extends LabeledItemBaseViewHolder
            implements View.OnTouchListener {
        private OpenHABItem mBoundItem;

        RollerShutterViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_rollershutteritem, conn, colorMapper);
            initButton(R.id.up_button, "UP");
            initButton(R.id.down_button, "DOWN");
            initButton(R.id.stop_button, "STOP");
        }

        private void initButton(@IdRes int resId, String command) {
            ImageButton button = itemView.findViewById(resId);
            button.setOnTouchListener(this);
            button.setTag(command);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);
            mBoundItem = widget.item();
        }

        @Override
        public boolean onTouch(View v, MotionEvent motionEvent) {
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
                Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem, (String) v.getTag());
            }
            return false;
        }
    }

    public static class SetpointViewHolder extends LabeledItemBaseViewHolder
            implements View.OnClickListener {
        private final LayoutInflater mInflater;
        private OpenHABWidget mBoundWidget;

        SetpointViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_setpointitem, conn, colorMapper);
            mValueView.setOnClickListener(this);
            mInflater = inflater;

            ImageView dropdownArrow = itemView.findViewById(R.id.down_arrow);
            dropdownArrow.setOnClickListener(this);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);
            mBoundWidget = widget;
        }

        @Override
        public void onClick(final View view) {
            float minValue = mBoundWidget.minValue();
            float maxValue = mBoundWidget.maxValue();
            //This prevents an exception below. But could lead to user confusion if this case is ever encountered.
            float stepSize = minValue == maxValue ? 1 : mBoundWidget.step();

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
            String stateString = Float.toString(mBoundWidget.item().stateAsFloat());
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
                            Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundWidget.item(),
                                    stepValues[numberPicker.getValue()]);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    public static class ChartViewHolder extends ViewHolder {
        private final WidgetImageView mImageView;
        private final View mParentView;
        private final CharSequence mChartTheme;
        private final Random mRandom = new Random();
        private final SharedPreferences mPrefs;
        private int mRefreshRate = 0;
        private int mDensity;

        ChartViewHolder(LayoutInflater inflater, ViewGroup parent, CharSequence theme,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_chartitem, conn, colorMapper);
            mImageView = itemView.findViewById(R.id.chart);
            mParentView = parent;

            WindowManager wm = (WindowManager) itemView.getContext().getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);

            mDensity = metrics.densityDpi;
            mChartTheme = theme;
            mPrefs = PreferenceManager.getDefaultSharedPreferences(itemView.getContext());
        }

        @Override
        public void bind(OpenHABWidget widget) {
            OpenHABItem item = widget.item();

            if (item != null) {
                float scalingFactor = mPrefs.getFloat(Constants.PREFERENCE_CHART_SCALING, 1.0f);
                float actualDensity = (float) mDensity / scalingFactor;

                StringBuilder chartUrl = new StringBuilder("chart?")
                        .append(item.type() == OpenHABItem.Type.Group ? "groups=" : "items=")
                        .append(item.name())
                        .append("&period=").append(widget.period())
                        .append("&random=").append(mRandom.nextInt())
                        .append("&dpi=").append((int) actualDensity);
                if (!TextUtils.isEmpty(widget.service())) {
                    chartUrl.append("&service=").append(widget.service());
                }
                if (mChartTheme != null) {
                    chartUrl.append("&theme=").append(mChartTheme);
                }
                if (widget.legend() != null) {
                    chartUrl.append("&legend=").append(widget.legend());
                }

                int parentWidth = mParentView.getWidth();
                if (parentWidth > 0) {
                    chartUrl.append("&w=").append(parentWidth);
                    chartUrl.append("&h=").append(parentWidth / 2);
                }

                Log.d(TAG, "Chart url = " + chartUrl);

                mImageView.setImageUrl(mConnection, chartUrl.toString(), true);
                mRefreshRate = widget.refresh();
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

        VideoViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_videoitem, conn, colorMapper);
            mVideoView = itemView.findViewById(R.id.video);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            // FIXME: check for URL changes here
            if (!mVideoView.isPlaying()) {
                final String videoUrl;
                OpenHABItem videoItem = widget.item();
                if ("hls".equalsIgnoreCase(widget.encoding())
                        && videoItem != null
                        && videoItem.type() == OpenHABItem.Type.StringItem
                        && videoItem.state() != null) {
                    videoUrl = videoItem.state();
                } else {
                    videoUrl = widget.url();
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

        WebViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_webitem, conn, colorMapper);
            mWebView = itemView.findViewById(R.id.webview);

            final Resources res = itemView.getContext().getResources();
            mRowHeightPixels = res.getDimensionPixelSize(R.dimen.row_height);
        }

        @SuppressLint("SetJavaScriptEnabled")
        @Override
        public void bind(OpenHABWidget widget) {
            ViewGroup.LayoutParams lp = mWebView.getLayoutParams();
            int desiredHeightPixels = widget.height() > 0
                    ? widget.height() * mRowHeightPixels : ViewGroup.LayoutParams.WRAP_CONTENT;
            if (lp.height != desiredHeightPixels) {
                lp.height = desiredHeightPixels;
                mWebView.setLayoutParams(lp);
            }

            HttpUrl url = mConnection.getAsyncHttpClient().buildUrl(widget.url());

            mWebView.setWebViewClient(new AnchorWebViewClient(url.toString(),
                    mConnection.getUsername(), mConnection.getPassword()));
            mWebView.getSettings().setDomStorageEnabled(true);
            mWebView.getSettings().setJavaScriptEnabled(true);
            mWebView.loadUrl(url.toString());
        }
    }

    public static class ColorViewHolder extends LabeledItemBaseViewHolder implements
            View.OnTouchListener, Handler.Callback, ColorPicker.OnColorChangedListener {
        private OpenHABItem mBoundItem;
        private final LayoutInflater mInflater;
        private final Handler mHandler = new Handler(this);

        ColorViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_coloritem, conn, colorMapper);
            mInflater = inflater;
            initButton(R.id.up_button, "ON");
            initButton(R.id.down_button, "OFF");
            itemView.findViewById(R.id.select_color_button).setOnTouchListener(this);
        }

        private void initButton(@IdRes int resId, String command) {
            ImageButton button = itemView.findViewById(resId);
            button.setOnTouchListener(this);
            button.setTag(command);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            super.bind(widget);
            mBoundItem = widget.item();
        }

        @Override
        public boolean onTouch(View v, MotionEvent motionEvent) {
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
                if (v.getTag() instanceof String) {
                    Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem, (String) v.getTag());
                } else {
                    showColorPickerDialog();
                }
            }
            return false;
        }

        @Override
        public boolean handleMessage(Message msg) {
            final float[] hsv = new float[3];
            Color.RGBToHSV(Color.red(msg.arg1), Color.green(msg.arg1), Color.blue(msg.arg1), hsv);
            Log.d(TAG, "New color HSV = " + hsv[0] + ", " + hsv[1] + ", " + hsv[2]);
            final String newColorValue = String.format(Locale.US, "%f,%f,%f",
                    hsv[0], hsv[1] * 100, hsv[2] * 100);
            Util.sendItemCommand(mConnection.getAsyncHttpClient(), mBoundItem, newColorValue);
            return true;
        }

        @Override
        public void onColorChanged(int color) {
            mHandler.removeMessages(0);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(0, color, 0), 100);
        }

        private void showColorPickerDialog() {
            View contentView = mInflater.inflate(R.layout.color_picker_dialog, null);
            ColorPicker colorPicker = contentView.findViewById(R.id.picker);
            SaturationBar saturationBar = contentView.findViewById(R.id.saturation_bar);
            ValueBar valueBar = contentView.findViewById(R.id.value_bar);

            colorPicker.addSaturationBar(saturationBar);
            colorPicker.addValueBar(valueBar);
            colorPicker.setOnColorChangedListener(this);
            colorPicker.setShowOldCenterColor(false);

            float[] initialColor = mBoundItem.stateAsHSV();
            if (initialColor != null) {
                colorPicker.setColor(Color.HSVToColor(initialColor));
            }

            new AlertDialog.Builder(contentView.getContext())
                    .setView(contentView)
                    .setNegativeButton(R.string.close, null)
                    .show();
        }
    }

    public static class MjpegVideoViewHolder extends ViewHolder {
        private final ImageView mImageView;
        private MjpegStreamer mStreamer;

        MjpegVideoViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection conn, ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_videomjpegitem, conn, colorMapper);
            mImageView = itemView.findViewById(R.id.mjpegimage);
        }

        @Override
        public void bind(OpenHABWidget widget) {
            mStreamer = new MjpegStreamer(mImageView, mConnection, widget.url());
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

    @VisibleForTesting
    public static class ColorMapper {
        private final Map<String, Integer> mColorMap = new HashMap<>();

        ColorMapper(Context context) {
            String[] colorNames = context.getResources().getStringArray(R.array.valueColorNames);

            TypedValue tv = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.valueColors, tv, false);
            TypedArray ta = context.getResources().obtainTypedArray(tv.data);

            for (int i = 0; i < ta.length() && i < colorNames.length; i++) {
                mColorMap.put(colorNames[i], ta.getColor(i, 0));
            }

            ta.recycle();
        }

        public Integer mapColor(String colorName) {
            if (colorName == null) {
                return null;
            }
            if (colorName.startsWith("#")) {
                try {
                    return Color.parseColor(colorName);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            } else {
                return mColorMap.get(colorName);
            }
        }
    }
}
