/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

import com.larswerkman.holocolorpicker.ColorPicker
import com.larswerkman.holocolorpicker.SaturationBar
import com.larswerkman.holocolorpicker.ValueBar
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.widget.DividerItemDecoration
import org.openhab.habdroid.ui.widget.ExtendedSpinner
import org.openhab.habdroid.ui.widget.SegmentedControlButton
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.MjpegStreamer
import org.openhab.habdroid.util.Util

import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import java.util.Random

/**
 * This class provides openHAB widgets adapter for list view.
 */

class WidgetAdapter(context: Context, private val connection: Connection,
                    private val itemClickListener: ItemClickListener) :
        RecyclerView.Adapter<WidgetAdapter.ViewHolder>(), View.OnClickListener, View.OnLongClickListener {

    private val items = ArrayList<Widget>()
    private val inflater: LayoutInflater
    private val chartTheme: CharSequence
    private var selectedPosition = -1
    private val colorMapper: ColorMapper

    interface ItemClickListener {
        fun onItemClicked(item: Widget): Boolean  // returns whether click was handled
        fun onItemLongClicked(item: Widget)
    }

    init {
        inflater = LayoutInflater.from(context)
        colorMapper = ColorMapper(context)

        val tv = TypedValue()
        context.theme.resolveAttribute(R.attr.chartTheme, tv, true)
        chartTheme = tv.string
    }

    fun update(widgets: List<Widget>, forceFullUpdate: Boolean) {
        var compatibleUpdate = true

        if (widgets.size != items.size || forceFullUpdate) {
            compatibleUpdate = false
        } else {
            for (i in widgets.indices) {
                if (getItemViewType(items[i]) != getItemViewType(widgets[i])) {
                    compatibleUpdate = false
                    break
                }
            }
        }

        if (compatibleUpdate) {
            for (i in widgets.indices) {
                if (items[i] != widgets[i]) {
                    items[i] = widgets[i]
                    notifyItemChanged(i)
                }
            }
        } else {
            items.clear()
            items.addAll(widgets)
            notifyDataSetChanged()
        }
    }

    fun updateWidget(widget: Widget) {
        for (i in items.indices) {
            if (items[i].id == widget.id) {
                items[i] = widget
                notifyItemChanged(i)
                break
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder: ViewHolder
        when (viewType) {
            TYPE_GENERICITEM -> holder = GenericViewHolder(inflater, parent, connection, colorMapper)
            TYPE_FRAME -> holder = FrameViewHolder(inflater, parent, connection, colorMapper)
            TYPE_GROUP -> holder = GroupViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SWITCH -> holder = SwitchViewHolder(inflater, parent, connection, colorMapper)
            TYPE_TEXT -> holder = TextViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SLIDER -> holder = SliderViewHolder(inflater, parent, connection, colorMapper)
            TYPE_IMAGE -> holder = ImageViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SELECTION -> holder = SelectionViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SECTIONSWITCH -> holder = SectionSwitchViewHolder(inflater, parent, connection, colorMapper)
            TYPE_ROLLERSHUTTER -> holder = RollerShutterViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SETPOINT -> holder = SetpointViewHolder(inflater, parent, connection, colorMapper)
            TYPE_CHART -> holder = ChartViewHolder(inflater, parent, chartTheme, connection, colorMapper)
            TYPE_VIDEO -> holder = VideoViewHolder(inflater, parent, connection, colorMapper)
            TYPE_WEB -> holder = WebViewHolder(inflater, parent, connection, colorMapper)
            TYPE_COLOR -> holder = ColorViewHolder(inflater, parent, connection, colorMapper)
            TYPE_VIDEO_MJPEG -> holder = MjpegVideoViewHolder(inflater, parent, connection, colorMapper)
            TYPE_LOCATION -> holder = MapViewHelper.createViewHolder(inflater, parent, connection, colorMapper)
            else -> throw IllegalArgumentException("View type $viewType is not known")
        }

        holder.itemView.tag = holder

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.stop()
        holder.bind(items[position])
        if (holder is FrameViewHolder) {
            holder.setShownAsFirst(position == 0)
        }
        holder.itemView.isActivated = selectedPosition == position
        holder.itemView.setOnClickListener(if (itemClickListener != null) this else null)
        holder.itemView.setOnLongClickListener(if (itemClickListener != null) this else null)
        holder.itemView.isClickable = itemClickListener != null
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.start()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.stop()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun getItem(position: Int): Widget {
        return items[position]
    }

    override fun getItemViewType(position: Int): Int {
        return getItemViewType(items[position])
    }

    private fun getItemViewType(widget: Widget): Int {
        when (widget.type) {
            Widget.Type.Frame -> return TYPE_FRAME
            Widget.Type.Group -> return TYPE_GROUP
            Widget.Type.Switch -> if (widget.hasMappings()) {
                return TYPE_SECTIONSWITCH
            } else {
                val item = widget.item
                return if (item != null && item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
                    TYPE_ROLLERSHUTTER
                } else TYPE_SWITCH
            }
            Widget.Type.Text -> return TYPE_TEXT
            Widget.Type.Slider -> return TYPE_SLIDER
            Widget.Type.Image -> return TYPE_IMAGE
            Widget.Type.Selection -> return TYPE_SELECTION
            Widget.Type.Setpoint -> return TYPE_SETPOINT
            Widget.Type.Chart -> return TYPE_CHART
            Widget.Type.Video -> {
                return if ("mjpeg".equals(widget.encoding!!, ignoreCase = true)) {
                    TYPE_VIDEO_MJPEG
                } else TYPE_VIDEO
            }
            Widget.Type.Webview -> return TYPE_WEB
            Widget.Type.Colorpicker -> return TYPE_COLOR
            Widget.Type.Mapview -> return TYPE_LOCATION
            else -> return TYPE_GENERICITEM
        }
    }

    fun setSelectedPosition(position: Int): Boolean {
        if (selectedPosition == position) {
            return false
        }
        if (selectedPosition >= 0) {
            notifyItemChanged(selectedPosition)
        }
        selectedPosition = position
        if (position >= 0) {
            notifyItemChanged(position)
        }
        return true
    }

    override fun onClick(view: View) {
        val holder = view.tag as ViewHolder
        val position = holder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            if (!itemClickListener.onItemClicked(items[position])) {
                holder.handleRowClick()
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        val holder = view.tag as ViewHolder
        val position = holder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            itemClickListener.onItemLongClicked(items[position])
        }
        return false
    }

    abstract class ViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup, @LayoutRes layoutResId: Int,
                                                   protected val connection: Connection, private val colorMapper: ColorMapper) :
            RecyclerView.ViewHolder(inflater.inflate(layoutResId, parent, false)) {

        abstract fun bind(widget: Widget)
        open fun start() {}
        open fun stop() {}

        protected fun updateTextViewColor(view: TextView, colorName: String?) {
            val origColor = view.getTag(R.id.originalColor) as ColorStateList
            val color = colorMapper.mapColor(colorName)
            if (color != null) {
                if (origColor == null) {
                    view.setTag(R.id.originalColor, view.textColors)
                }
                view.setTextColor(color)
            } else if (origColor != null) {
                view.setTextColor(origColor)
                view.setTag(R.id.originalColor, null)
            }
        }

        protected fun updateIcon(iconView: WidgetImageView, widget: Widget) {
            if (widget.icon == null) {
                iconView.setImageDrawable(null)
                return
            }
            // This is needed to escape possible spaces and everything according to rfc2396
            val iconUrl = Uri.encode(widget.iconPath, "/?=&")
            iconView.setImageUrl(connection, iconUrl, iconView.resources
                    .getDimensionPixelSize(R.dimen.notificationlist_icon_size))
            val iconColor = colorMapper.mapColor(widget.iconColor)
            if (iconColor != null) {
                iconView.setColorFilter(iconColor)
            } else {
                iconView.clearColorFilter()
            }
        }

        open fun handleRowClick() {}
    }

    abstract class LabeledItemBaseViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                                                  @LayoutRes layoutResId: Int, conn: Connection,
                                                                  colorMapper: ColorMapper) :
            ViewHolder(inflater, parent, layoutResId, conn, colorMapper) {
        protected val labelView: TextView
        protected val valueView: TextView?
        protected val iconView: WidgetImageView

        init {
            labelView = itemView.findViewById(R.id.widgetlabel)
            valueView = itemView.findViewById(R.id.widgetvalue)
            iconView = itemView.findViewById(R.id.widgeticon)
        }

        override fun bind(widget: Widget) {
            val splitString = widget.label.split("[", "]")
            labelView.text = if (splitString.size > 0) splitString[0] else null
            updateTextViewColor(labelView, widget.labelColor)
            if (valueView != null) {
                valueView.text = if (splitString.size > 1) splitString[1] else null
                valueView.visibility = if (splitString.size > 1) View.VISIBLE else View.GONE
                updateTextViewColor(valueView, widget.valueColor)
            }
            updateIcon(iconView, widget)
        }
    }

    class GenericViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                                 conn: Connection, colorMapper: ColorMapper) :
            ViewHolder(inflater, parent, R.layout.widgetlist_genericitem, conn, colorMapper) {
        private val labelView: TextView
        private val iconView: WidgetImageView

        init {
            labelView = itemView.findViewById(R.id.widgetlabel)
            iconView = itemView.findViewById(R.id.widgeticon)
        }

        override fun bind(widget: Widget) {
            labelView.text = widget.label
            updateTextViewColor(labelView, widget.labelColor)
            updateIcon(iconView, widget)
        }
    }

    class FrameViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                               conn: Connection, colorMapper: ColorMapper) :
            ViewHolder(inflater, parent, R.layout.widgetlist_frameitem, conn, colorMapper) {
        private val divider: View
        private val spacer: View
        private val labelView: TextView

        init {
            labelView = itemView.findViewById(R.id.widgetlabel)
            divider = itemView.findViewById(R.id.divider)
            spacer = itemView.findViewById(R.id.spacer)
            itemView.isClickable = false
        }

        override fun bind(widget: Widget) {
            labelView.text = widget.label
            updateTextViewColor(labelView, widget.valueColor)
            // hide empty frames
            itemView.visibility = if (widget.label.isEmpty()) View.GONE else View.VISIBLE
        }

        fun setShownAsFirst(shownAsFirst: Boolean) {
            divider.visibility = if (shownAsFirst) View.GONE else View.VISIBLE
            spacer.visibility = if (shownAsFirst) View.VISIBLE else View.GONE
        }
    }

    class GroupViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                               conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_groupitem, conn, colorMapper) {
        private val rightArrow: ImageView

        init {
            rightArrow = itemView.findViewById(R.id.right_arrow)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            rightArrow.visibility = if (widget.linkedPage != null) View.VISIBLE else View.GONE
        }
    }

    class SwitchViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                                conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_switchitem, conn, colorMapper), View.OnTouchListener {
        private val switch: SwitchCompat
        private var boundItem: Item? = null

        init {
            switch = itemView.findViewById(R.id.toggle)
            switch.setOnTouchListener(this)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundItem = widget.item
            switch.isChecked = boundItem?.state?.asBoolean ?: false
        }

        override fun handleRowClick() {
            toggleSwitch()
        }

        override fun onTouch(v: View, motionEvent: MotionEvent): Boolean {
            if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                toggleSwitch()
            }
            return false
        }

        private fun toggleSwitch() {
            Util.sendItemCommand(connection.asyncHttpClient, boundItem,
                    if (switch.isChecked) "OFF" else "ON")
        }
    }

    class TextViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                              conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_textitem, conn, colorMapper) {
        private val rightArrow: ImageView

        init {
            rightArrow = itemView.findViewById(R.id.right_arrow)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            rightArrow.visibility = if (widget.linkedPage != null) View.VISIBLE else View.GONE
        }
    }

    class SliderViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                                conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_slideritem, conn, colorMapper), SeekBar.OnSeekBarChangeListener {
        private val seekBar: SeekBar
        private var boundWidget: Widget? = null

        init {
            seekBar = itemView.findViewById(R.id.seekbar)
            seekBar.setOnSeekBarChangeListener(this)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundWidget = widget

            val stepCount = (widget.maxValue - widget.minValue) / widget.step
            seekBar.max = Math.ceil(stepCount.toDouble()).toInt()
            seekBar.progress = 0

            val item = widget.item
            val state = item?.state
            if (state == null) {
                return
            }

            if (item.isOfTypeOrGroupType(Item.Type.Color)) {
                val brightness = state.asBrightness
                if (brightness != null) {
                    seekBar.max = 100
                    seekBar.progress = brightness
                }
            } else {
                val number = state.asNumber
                if (number != null) {
                    val progress = (number.value.toFloat() - widget.minValue) / widget.step
                    seekBar.progress = Math.round(progress)
                }
            }
        }

        override fun handleRowClick() {
            if (boundWidget?.switchSupport ?: false) {
                Util.sendItemCommand(connection.asyncHttpClient,
                        boundWidget?.item, if (seekBar.progress == 0) "ON" else "OFF")
            }
        }

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            // no-op
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            Log.d(TAG, "onStartTrackingTouch position = " + seekBar.progress)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            val progress = seekBar.progress
            Log.d(TAG, "onStopTrackingTouch position = $progress")
            val widget = boundWidget
            val item = widget?.item ?: return
            val newValue = widget.minValue + widget.step * progress
            Util.sendItemCommand(connection.asyncHttpClient, item,
                    ParsedState.NumberState.withValue(item?.state?.asNumber, newValue))
        }
    }

    class ImageViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                               conn: Connection, colorMapper: ColorMapper) :
            ViewHolder(inflater, parent, R.layout.widgetlist_imageitem, conn, colorMapper) {
        private val imageView: WidgetImageView
        private val parentView: View
        private var refreshRate: Int = 0

        init {
            imageView = itemView.findViewById(R.id.image)
            parentView = parent
        }

        override fun bind(widget: Widget) {
            val value = widget.state?.asString

            // Make sure images fit into the content frame by scaling
            // them at max 90% of the available height
            if (parentView.height > 0) {
                imageView.maxHeight = Math.round(0.9f * parentView.height)
            } else {
                imageView.maxHeight = Integer.MAX_VALUE
            }

            if (value != null && value.matches("data:image/.*;base64,.*".toRegex())) {
                val dataString = value.substring(value.indexOf(",") + 1)
                val data = Base64.decode(dataString, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                imageView.setImageBitmap(bitmap)
                refreshRate = 0
            } else if (widget.url != null) {
                imageView.setImageUrl(connection, widget.url, parentView.width)
                refreshRate = widget.refresh
            } else {
                imageView.setImageDrawable(null)
                refreshRate = 0
            }
        }

        override fun start() {
            if (refreshRate > 0) {
                imageView.setRefreshRate(refreshRate)
            } else {
                imageView.cancelRefresh()
            }
        }

        override fun stop() {
            imageView.cancelRefresh()
        }
    }

    class SelectionViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                                   conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_selectionitem, conn, colorMapper), ExtendedSpinner.OnSelectionUpdatedListener {
        private val spinner: ExtendedSpinner
        private var boundItem: Item? = null
        private var boundMappings: List<LabeledValue>? = null

        init {
            spinner = itemView.findViewById(R.id.spinner)
            spinner.onSelectionUpdatedListener = this
        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            boundItem = widget.item
            boundMappings = widget.mappingsOrItemOptions

            var spinnerSelectedIndex = -1
            val spinnerArray = ArrayList<String>()
            val stateString = boundItem?.state?.asString

            for ((command, label) in boundMappings!!) {
                spinnerArray.add(label)
                if (command != null && command == stateString) {
                    spinnerSelectedIndex = spinnerArray.size - 1
                }
            }
            if (spinnerSelectedIndex == -1) {
                spinnerArray.add("          ")
                spinnerSelectedIndex = spinnerArray.size - 1
            }

            val spinnerAdapter = ArrayAdapter(itemView.context,
                    android.R.layout.simple_spinner_item, spinnerArray)
            spinnerAdapter.setDropDownViewResource(R.layout.select_dialog_singlechoice)

            spinner.prompt = labelView.text
            spinner.adapter = spinnerAdapter
            spinner.setSelectionWithoutUpdateCallback(spinnerSelectedIndex)
        }

        override fun handleRowClick() {
            spinner.performClick()
        }

        override fun onSelectionUpdated(position: Int) {
            val boundMappings = boundMappings ?: return
            Log.d(TAG, "Spinner item click on index $position")
            if (position >= boundMappings.size) {
                return
            }
            val (value) = boundMappings[position]
            Log.d(TAG, "Spinner onItemSelected found match with $value")
            Util.sendItemCommand(connection.asyncHttpClient, boundItem, value)
        }
    }

    class SectionSwitchViewHolder internal constructor(private val mInflater: LayoutInflater, parent: ViewGroup,
                                                       conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(mInflater, parent, R.layout.widgetlist_sectionswitchitem, conn, colorMapper), View.OnClickListener {
        private val radioGroup: RadioGroup
        private var boundItem: Item? = null

        init {
            radioGroup = itemView.findViewById(R.id.switchgroup)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundItem = widget.item

            val mappings = widget.mappings
            // inflate missing views
            for (i in radioGroup.childCount until mappings.size) {
                val view = mInflater.inflate(R.layout.widgetlist_sectionswitchitem_button,
                        radioGroup, false)
                view.setOnClickListener(this)
                radioGroup.addView(view)
            }
            // bind views
            val state = boundItem?.state?.asString
            for (i in mappings.indices) {
                val button = radioGroup.getChildAt(i) as SegmentedControlButton
                val command = mappings[i].value
                button.text = mappings[i].label
                button.tag = command
                button.isChecked = state != null && TextUtils.equals(state, command)
                button.visibility = View.VISIBLE
            }
            // hide spare views
            for (i in mappings.size until radioGroup.childCount) {
                radioGroup.getChildAt(i).visibility = View.GONE
            }
        }

        override fun onClick(view: View) {
            Util.sendItemCommand(connection.asyncHttpClient, boundItem, view.tag as String)
        }

        override fun handleRowClick() {
            val visibleChildCount = radioGroup.children.filter { v -> v.isVisible }.count()
            if (visibleChildCount == 1) {
                onClick(radioGroup[0])
            } else if (visibleChildCount == 2) {
                val state = boundItem?.state?.asString
                if (state == radioGroup[0].tag.toString()) {
                    onClick(radioGroup[1])
                } else if (state == radioGroup[1].tag.toString()) {
                    onClick(radioGroup[0])
                }
            }
        }
    }

    class RollerShutterViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                                       conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_rollershutteritem, conn, colorMapper), View.OnTouchListener {
        private var boundItem: Item? = null

        init {
            initButton(R.id.up_button, "UP")
            initButton(R.id.down_button, "DOWN")
            initButton(R.id.stop_button, "STOP")
        }

        private fun initButton(@IdRes resId: Int, command: String) {
            val button = itemView.findViewById<ImageButton>(resId)
            button.setOnTouchListener(this)
            button.tag = command
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundItem = widget.item
        }

        override fun onTouch(v: View, motionEvent: MotionEvent): Boolean {
            if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                val cmd = v.tag as String
                Util.sendItemCommand(connection.asyncHttpClient, boundItem, cmd)
            }
            return false
        }
    }

    class SetpointViewHolder internal constructor(private val inflater: LayoutInflater, parent: ViewGroup,
                                                  conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_setpointitem, conn, colorMapper), View.OnClickListener {
        private var boundWidget: Widget? = null

        init {
            valueView?.setOnClickListener(this)

            // Dialog
            itemView.findViewById<View>(R.id.down_arrow).setOnClickListener(this)
            // Up/Down buttons
            itemView.findViewById<View>(R.id.up_button).setOnClickListener(this)
            itemView.findViewById<View>(R.id.down_button).setOnClickListener(this)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundWidget = widget
        }

        override fun handleRowClick() {
            onClick(itemView)
        }

        override fun onClick(view: View) {
            val widget = boundWidget
            val state = widget?.state?.asNumber ?: return
            val minValue = widget.minValue
            val maxValue = widget.maxValue
            // This prevents an exception below, but could lead to
            // user confusion if this case is ever encountered.
            val stepSize = if (minValue == maxValue) 1F else widget.step
            val stateValue = state?.value?.toFloat()

            if (view.id == R.id.up_button || view.id == R.id.down_button) {
                if (stateValue == null) {
                    return
                }
                val newValue = if (view.id == R.id.up_button)
                    stateValue + stepSize else stateValue - stepSize
                if (newValue >= minValue && newValue <= maxValue) {
                    Util.sendItemCommand(connection.asyncHttpClient, widget.item,
                            ParsedState.NumberState.withValue(state, newValue))
                }
            } else {
                val stepCount = (Math.abs(maxValue - minValue) / stepSize).toInt() + 1
                val stepValues = arrayOfNulls<ParsedState.NumberState>(stepCount)
                val stepValueLabels = arrayOfNulls<String>(stepCount)
                var closestIndex = 0
                var closestDelta = java.lang.Float.MAX_VALUE

                for (i in stepValues.indices) {
                    val stepValue = minValue + i * stepSize
                    stepValues[i] = ParsedState.NumberState.withValue(state, stepValue)
                    stepValueLabels[i] = stepValues[i].toString()
                    if (stateValue != null && Math.abs(stateValue - stepValue) < closestDelta) {
                        closestIndex = i
                        closestDelta = Math.abs(stateValue - stepValue)
                    }
                }

                val dialogView = inflater.inflate(R.layout.dialog_numberpicker, null)
                val numberPicker = dialogView.findViewById<NumberPicker>(R.id.numberpicker)

                numberPicker.minValue = 0
                numberPicker.maxValue = stepValues.size - 1
                numberPicker.displayedValues = stepValueLabels
                numberPicker.value = closestIndex

                AlertDialog.Builder(view.context)
                        .setTitle(labelView.text)
                        .setView(dialogView)
                        .setPositiveButton(R.string.set) { dialog, which ->
                            Util.sendItemCommand(connection.asyncHttpClient,
                                    widget.item, stepValues[numberPicker.value])
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
            }
        }
    }

    class ChartViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                               private val chartTheme: CharSequence?,
                                               conn: Connection, colorMapper: ColorMapper) :
            ViewHolder(inflater, parent, R.layout.widgetlist_chartitem, conn, colorMapper) {
        private val imageView: WidgetImageView
        private val parentView: View
        private val random = Random()
        private val prefs: SharedPreferences
        private var refreshRate = 0
        private val density: Int

        init {
            imageView = itemView.findViewById(R.id.chart)
            parentView = parent

            val context = itemView.context
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(metrics)

            density = metrics.densityDpi
            prefs = PreferenceManager.getDefaultSharedPreferences(context)
        }

        override fun bind(widget: Widget) {
            val item = widget.item

            if (item != null) {
                val scalingFactor = prefs.getFloat(Constants.PREFERENCE_CHART_SCALING, 1.0f)
                val requestHighResChart = prefs.getBoolean(Constants.PREFERENCE_CHART_HQ, true)
                val actualDensity = density.toFloat() / scalingFactor
                val resDivider = if (requestHighResChart) 1 else 2

                val chartUrl = StringBuilder("chart?")
                        .append(if (item.type === Item.Type.Group) "groups=" else "items=")
                        .append(item.name)
                        .append("&period=")
                        .append(widget.period)
                        .append("&random=")
                        .append(random.nextInt())
                        .append("&dpi=")
                        .append(actualDensity.toInt() / resDivider)
                if (!TextUtils.isEmpty(widget.service)) {
                    chartUrl.append("&service=").append(widget.service)
                }
                if (chartTheme != null) {
                    chartUrl.append("&theme=").append(chartTheme)
                }
                if (widget.legend != null) {
                    chartUrl.append("&legend=").append(widget.legend)
                }

                val parentWidth = parentView.width
                if (parentWidth > 0) {
                    chartUrl.append("&w=").append(parentWidth / resDivider)
                    chartUrl.append("&h=").append(parentWidth / 2 / resDivider)
                }

                Log.d(TAG, "Chart url = $chartUrl")

                imageView.setImageUrl(connection, chartUrl.toString(), parentWidth, true)
                refreshRate = widget.refresh
            } else {
                Log.e(TAG, "Chart item is null")
                imageView.setImageDrawable(null)
                refreshRate = 0
            }
        }

        override fun start() {
            if (refreshRate > 0) {
                imageView.setRefreshRate(refreshRate)
            } else {
                imageView.cancelRefresh()
            }
        }

        override fun stop() {
            imageView.cancelRefresh()
        }
    }

    class VideoViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                               conn: Connection, colorMapper: ColorMapper) :
            ViewHolder(inflater, parent, R.layout.widgetlist_videoitem, conn, colorMapper) {
        private val videoView: VideoView

        init {
            videoView = itemView.findViewById(R.id.video)
        }

        override fun bind(widget: Widget) {
            // FIXME: check for URL changes here
            if (!videoView.isPlaying) {
                var videoUrl: String? = widget.url
                if ("hls".equals(widget.encoding, ignoreCase = true)) {
                    val state = widget.item?.state?.asString
                    if (state != null && widget.item.type == Item.Type.StringItem) {
                        videoUrl = state
                    }
                }
                Log.d(TAG, "Opening video at " + videoUrl)
                videoView.setVideoURI(Uri.parse(videoUrl))
            }
        }

        override fun start() {
            videoView.start()
        }

        override fun stop() {
            videoView.stopPlayback()
        }
    }

    class WebViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                             conn: Connection, colorMapper: ColorMapper) :
            ViewHolder(inflater, parent, R.layout.widgetlist_webitem, conn, colorMapper) {
        private val webView: WebView
        private val rowHeightPixels: Int

        init {
            webView = itemView.findViewById(R.id.webview)

            val res = itemView.context.resources
            rowHeightPixels = res.getDimensionPixelSize(R.dimen.row_height)
        }

        @SuppressLint("SetJavaScriptEnabled")
        override fun bind(widget: Widget) {
            webView.loadUrl("about:blank")
            val lp = webView.layoutParams
            val desiredHeightPixels = if (widget.height > 0)
                    widget.height * rowHeightPixels else ViewGroup.LayoutParams.WRAP_CONTENT
            if (lp.height != desiredHeightPixels) {
                lp.height = desiredHeightPixels
                webView.layoutParams = lp
            }

            val url = connection.asyncHttpClient.buildUrl(widget.url!!).toString()
            Util.initWebView(webView, connection, url)
            webView.webViewClient = AnchorWebViewClient(url,
                    connection.username, connection.password)
            webView.loadUrl(url)
        }
    }

    class ColorViewHolder internal constructor(private val mInflater: LayoutInflater, parent: ViewGroup,
                                               conn: Connection, colorMapper: ColorMapper) :
            LabeledItemBaseViewHolder(mInflater, parent, R.layout.widgetlist_coloritem, conn, colorMapper),
            View.OnTouchListener, Handler.Callback, ColorPicker.OnColorChangedListener {
        private var boundItem: Item? = null
        private val handler = Handler(this)

        init {
            initButton(R.id.up_button, "ON")
            initButton(R.id.down_button, "OFF")
            itemView.findViewById<View>(R.id.select_color_button).setOnTouchListener(this)
        }

        private fun initButton(@IdRes resId: Int, command: String) {
            val button = itemView.findViewById<ImageButton>(resId)
            button.setOnTouchListener(this)
            button.tag = command
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundItem = widget.item
        }

        override fun handleRowClick() {
            showColorPickerDialog()
        }

        override fun onTouch(v: View, motionEvent: MotionEvent): Boolean {
            if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                if (v.tag is String) {
                    val cmd = v.tag as String
                    Util.sendItemCommand(connection.asyncHttpClient, boundItem, cmd)
                } else {
                    showColorPickerDialog()
                }
            }
            return false
        }

        override fun handleMessage(msg: Message): Boolean {
            val hsv = FloatArray(3)
            Color.RGBToHSV(Color.red(msg.arg1), Color.green(msg.arg1), Color.blue(msg.arg1), hsv)
            Log.d(TAG, "New color HSV = " + hsv[0] + ", " + hsv[1] + ", " + hsv[2])
            val newColorValue = String.format(Locale.US, "%f,%f,%f",
                    hsv[0], hsv[1] * 100, hsv[2] * 100)
            Util.sendItemCommand(connection.asyncHttpClient, boundItem, newColorValue)
            return true
        }

        override fun onColorChanged(color: Int) {
            handler.removeMessages(0)
            handler.sendMessageDelayed(handler.obtainMessage(0, color, 0), 100)
        }

        private fun showColorPickerDialog() {
            val contentView = mInflater.inflate(R.layout.color_picker_dialog, null)
            val colorPicker = contentView.findViewById<ColorPicker>(R.id.picker)
            val saturationBar = contentView.findViewById<SaturationBar>(R.id.saturation_bar)
            val valueBar = contentView.findViewById<ValueBar>(R.id.value_bar)

            colorPicker.addSaturationBar(saturationBar)
            colorPicker.addValueBar(valueBar)
            colorPicker.onColorChangedListener = this
            colorPicker.showOldCenterColor = false

            val initialColor = boundItem?.state?.asHsv
            if (initialColor != null) {
                colorPicker.color = Color.HSVToColor(initialColor)
            }

            AlertDialog.Builder(contentView.context)
                    .setView(contentView)
                    .setNegativeButton(R.string.close, null)
                    .show()
        }
    }

    class MjpegVideoViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup,
                                                    conn: Connection, colorMapper: ColorMapper) :
            ViewHolder(inflater, parent, R.layout.widgetlist_videomjpegitem, conn, colorMapper) {
        private val imageView: ImageView
        private var streamer: MjpegStreamer? = null

        init {
            imageView = itemView.findViewById(R.id.mjpegimage)
        }

        override fun bind(widget: Widget) {
            streamer = if (widget.url != null) MjpegStreamer(imageView, connection, widget.url) else null
        }

        override fun start() {
            if (streamer != null) {
                streamer!!.start()
            }
        }

        override fun stop() {
            if (streamer != null) {
                streamer!!.stop()
            }
        }
    }

    class WidgetItemDecoration(context: Context) : DividerItemDecoration(context) {
        override fun suppressDividerForChild(child: View, parent: RecyclerView): Boolean {
            if (super.suppressDividerForChild(child, parent)) {
                return true
            }

            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) {
                return false
            }

            // hide dividers before and after frame widgets
            if (parent.adapter!!.getItemViewType(position) == TYPE_FRAME) {
                return true
            }
            if (position < parent.adapter!!.itemCount - 1) {
                if (parent.adapter!!.getItemViewType(position + 1) == TYPE_FRAME) {
                    return true
                }
            }

            return false
        }
    }

    @VisibleForTesting
    class ColorMapper internal constructor(context: Context) {
        private val mColorMap = HashMap<String, Int>()

        init {
            val colorNames = context.resources.getStringArray(R.array.valueColorNames)

            val tv = TypedValue()
            context.theme.resolveAttribute(R.attr.valueColors, tv, false)
            val ta = context.resources.obtainTypedArray(tv.data)

            var i = 0
            while (i < ta.length() && i < colorNames.size) {
                mColorMap[colorNames[i]] = ta.getColor(i, 0)
                i++
            }

            ta.recycle()
        }

        fun mapColor(colorName: String?): Int? {
            if (colorName == null) {
                return null
            }
            return if (colorName.startsWith("#")) {
                try {
                    Color.parseColor(colorName)
                } catch (e: IllegalArgumentException) {
                    null
                }

            } else {
                mColorMap[colorName]
            }
        }
    }

    companion object {
        private val TAG = WidgetAdapter::class.java.simpleName

        private val TYPE_GENERICITEM = 0
        private val TYPE_FRAME = 1
        private val TYPE_GROUP = 2
        private val TYPE_SWITCH = 3
        private val TYPE_TEXT = 4
        private val TYPE_SLIDER = 5
        private val TYPE_IMAGE = 6
        private val TYPE_SELECTION = 7
        private val TYPE_SECTIONSWITCH = 8
        private val TYPE_ROLLERSHUTTER = 9
        private val TYPE_SETPOINT = 10
        private val TYPE_CHART = 11
        private val TYPE_VIDEO = 12
        private val TYPE_WEB = 13
        private val TYPE_COLOR = 14
        private val TYPE_VIDEO_MJPEG = 15
        private val TYPE_LOCATION = 16
    }
}
