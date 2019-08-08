/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.webkit.WebView
import android.widget.*
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.larswerkman.holocolorpicker.ColorPicker
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.*
import org.openhab.habdroid.ui.widget.ContextMenuAwareRecyclerView
import org.openhab.habdroid.ui.widget.DividerItemDecoration
import org.openhab.habdroid.ui.widget.ExtendedSpinner
import org.openhab.habdroid.ui.widget.SegmentedControlButton
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.util.*
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil

/**
 * This class provides openHAB widgets adapter for list view.
 */

class WidgetAdapter(
    context: Context,
    private val connection: Connection,
    private val itemClickListener: ItemClickListener
) : RecyclerView.Adapter<WidgetAdapter.ViewHolder>(), View.OnClickListener {
    private val items = mutableListOf<Widget>()
    val itemList: List<Widget> get() = items
    private val widgetsById = mutableMapOf<String, Widget>()

    private val inflater = LayoutInflater.from(context)
    private val chartTheme: CharSequence
    private var selectedPosition = -1
    private val colorMapper = ColorMapper(context)

    interface ItemClickListener {
        fun onItemClicked(widget: Widget): Boolean // returns whether click was handled
    }

    init {
        val tv = TypedValue()
        context.theme.resolveAttribute(R.attr.chartTheme, tv, true)
        chartTheme = tv.string
    }

    fun update(widgets: List<Widget>, forceFullUpdate: Boolean) {
        val compatibleUpdate = !forceFullUpdate &&
            widgets.size == items.size &&
            widgets.filterIndexed { i, widget -> getItemViewType(widget) != getItemViewType(items[i]) }.isEmpty()

        if (compatibleUpdate) {
            widgets.forEachIndexed { index, widget ->
                if (items[index] != widget) {
                    updateWidgetAtPosition(index, widget)
                }
            }
        } else {
            items.clear()
            items.addAll(widgets)
            widgetsById.clear()
            widgets.forEach { w -> widgetsById[w.id] = w }
            notifyDataSetChanged()
        }
    }

    fun updateWidget(widget: Widget) {
        val pos = items.indexOfFirst { w -> w.id == widget.id }
        if (pos >= 0) {
            updateWidgetAtPosition(pos, widget)
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

    fun getItemForContextMenu(info : ContextMenuAwareRecyclerView.RecyclerContextMenuInfo): Widget? {
        return if (info.position < items.size) items[info.position] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = when (viewType) {
            TYPE_GENERICITEM -> GenericViewHolder(inflater, parent, connection, colorMapper)
            TYPE_FRAME -> FrameViewHolder(inflater, parent, colorMapper)
            TYPE_GROUP -> GroupViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SWITCH -> SwitchViewHolder(inflater, parent, connection, colorMapper)
            TYPE_TEXT -> TextViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SLIDER -> SliderViewHolder(inflater, parent, connection, colorMapper)
            TYPE_IMAGE -> ImageViewHolder(inflater, parent, connection)
            TYPE_SELECTION -> SelectionViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SECTIONSWITCH -> SectionSwitchViewHolder(inflater, parent, connection, colorMapper)
            TYPE_ROLLERSHUTTER -> RollerShutterViewHolder(inflater, parent, connection, colorMapper)
            TYPE_SETPOINT -> SetpointViewHolder(inflater, parent, connection, colorMapper)
            TYPE_CHART -> ChartViewHolder(inflater, parent, chartTheme, connection)
            TYPE_VIDEO -> VideoViewHolder(inflater, parent)
            TYPE_WEB -> WebViewHolder(inflater, parent, connection)
            TYPE_COLOR -> ColorViewHolder(inflater, parent, connection, colorMapper)
            TYPE_VIDEO_MJPEG -> MjpegVideoViewHolder(inflater, parent, connection)
            TYPE_LOCATION -> MapViewHelper.createViewHolder(inflater, parent, connection, colorMapper)
            TYPE_INVISIBLE -> InvisibleWidgetViewHolder(inflater, parent)
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
        with(holder.itemView) {
            isClickable = true
            isLongClickable = true
            isActivated = selectedPosition == position
            setOnClickListener(this@WidgetAdapter)
        }
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

    override fun getItemViewType(position: Int): Int {
        return getItemViewType(items[position])
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

    private fun updateWidgetAtPosition(position: Int, widget: Widget) {
        val oldWidget = items[position]
        items[position] = widget
        widgetsById[widget.id] = widget
        // If visibility of a container with at least one child changes, refresh the whole list to make sure
        // the child visibility is also updated. Otherwise it's sufficient to update the single widget only.
        if (oldWidget.visibility != widget.visibility && items.any { w -> w.parentId == widget.id }) {
            notifyDataSetChanged()
        } else {
            notifyItemChanged(position)
        }
    }

    private tailrec fun isWidgetIncludingAllParentsVisible(widget: Widget): Boolean {
        if (!widget.visibility) {
            return false
        }
        val parent = widget.parentId?.let { id -> widgetsById[id] } ?: return true
        return isWidgetIncludingAllParentsVisible(parent)
    }

    private fun getItemViewType(widget: Widget): Int {
        if (!isWidgetIncludingAllParentsVisible(widget)) {
            return TYPE_INVISIBLE
        }
        return when (widget.type) {
            Widget.Type.Frame -> TYPE_FRAME
            Widget.Type.Group -> TYPE_GROUP
            Widget.Type.Switch -> when {
                widget.mappingsOrItemOptions.isNotEmpty() -> TYPE_SECTIONSWITCH
                widget.item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true -> TYPE_ROLLERSHUTTER
                else -> TYPE_SWITCH
            }
            Widget.Type.Text -> TYPE_TEXT
            Widget.Type.Slider -> TYPE_SLIDER
            Widget.Type.Image -> TYPE_IMAGE
            Widget.Type.Selection -> TYPE_SELECTION
            Widget.Type.Setpoint -> TYPE_SETPOINT
            Widget.Type.Chart -> TYPE_CHART
            Widget.Type.Video -> when {
                "mjpeg".equals(widget.encoding, ignoreCase = true) -> TYPE_VIDEO_MJPEG
                else -> TYPE_VIDEO
            }
            Widget.Type.Webview -> TYPE_WEB
            Widget.Type.Colorpicker -> TYPE_COLOR
            Widget.Type.Mapview -> TYPE_LOCATION
            else -> TYPE_GENERICITEM
        }
    }

    abstract class ViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        @LayoutRes layoutResId: Int
    ) : RecyclerView.ViewHolder(inflater.inflate(layoutResId, parent, false)) {
        abstract fun bind(widget: Widget)
        open fun start() {}
        open fun stop() {}
        open fun handleRowClick() {}
    }

    abstract class LabeledItemBaseViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        @LayoutRes layoutResId: Int,
        private val connection: Connection,
        private val colorMapper: ColorMapper
    ) : ViewHolder(inflater, parent, layoutResId) {
        protected val labelView: TextView = itemView.findViewById(R.id.widgetlabel)
        private val valueView: TextView? = itemView.findViewById(R.id.widgetvalue)
        private val iconView: WidgetImageView = itemView.findViewById(R.id.widgeticon)

        override fun bind(widget: Widget) {
            val splitString = widget.label.split("[", "]")
            labelView.text = splitString.firstOrNull()
            labelView.applyWidgetColor(widget.labelColor, colorMapper)
            if (valueView != null) {
                valueView.text = splitString.elementAtOrNull(1)
                valueView.isVisible = splitString.size > 1
                valueView.applyWidgetColor(widget.valueColor, colorMapper)
            }
            iconView.loadWidgetIcon(connection, widget, colorMapper)
        }
    }

    class GenericViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection,
        private val colorMapper: ColorMapper
    ) : ViewHolder(inflater, parent, R.layout.widgetlist_genericitem) {
        private val labelView: TextView = itemView.findViewById(R.id.widgetlabel)
        private val iconView: WidgetImageView = itemView.findViewById(R.id.widgeticon)

        override fun bind(widget: Widget) {
            labelView.text = widget.label
            labelView.applyWidgetColor(widget.labelColor, colorMapper)
            iconView.loadWidgetIcon(connection, widget, colorMapper)
        }
    }

    class InvisibleWidgetViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup) :
        ViewHolder(inflater, parent, R.layout.widgetlist_invisibleitem) {

        override fun bind(widget: Widget) {
        }
    }

    class FrameViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        private val colorMapper: ColorMapper
    ) : ViewHolder(inflater, parent, R.layout.widgetlist_frameitem) {
        private val divider: View = itemView.findViewById(R.id.divider)
        private val spacer: View = itemView.findViewById(R.id.spacer)
        private val labelView: TextView = itemView.findViewById(R.id.widgetlabel)

        init {
            itemView.isClickable = false
        }

        override fun bind(widget: Widget) {
            labelView.text = widget.label
            labelView.applyWidgetColor(widget.valueColor, colorMapper)
            // hide empty frames
            itemView.isVisible = widget.label.isNotEmpty()
        }

        fun setShownAsFirst(shownAsFirst: Boolean) {
            divider.isVisible = !shownAsFirst
            spacer.isVisible = shownAsFirst
        }
    }

    class GroupViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        conn: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_groupitem, conn, colorMapper) {
        private val rightArrow: ImageView = itemView.findViewById(R.id.right_arrow)

        override fun bind(widget: Widget) {
            super.bind(widget)
            rightArrow.isVisible = widget.linkedPage != null
        }
    }

    class SwitchViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_switchitem, connection, colorMapper),
        View.OnTouchListener {
        private val switch: SwitchCompat = itemView.findViewById(R.id.toggle)
        private var boundItem: Item? = null

        init {
            switch.setOnTouchListener(this)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundItem = widget.item
            switch.isChecked = boundItem?.state?.asBoolean == true
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
            connection.httpClient.sendItemCommand(boundItem, if (switch.isChecked) "OFF" else "ON")
        }
    }

    class TextViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        connection: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_textitem, connection, colorMapper) {
        private val rightArrow: ImageView = itemView.findViewById(R.id.right_arrow)

        override fun bind(widget: Widget) {
            super.bind(widget)
            rightArrow.isVisible = widget.linkedPage != null
        }
    }

    class SliderViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_slideritem, connection, colorMapper),
        SeekBar.OnSeekBarChangeListener {
        private val seekBar: SeekBar = itemView.findViewById(R.id.seekbar)
        private var boundWidget: Widget? = null

        init {
            seekBar.setOnSeekBarChangeListener(this)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundWidget = widget

            val stepCount = (widget.maxValue - widget.minValue) / widget.step
            seekBar.max = Math.ceil(stepCount.toDouble()).toInt()
            seekBar.progress = 0

            val item = widget.item
            val state = item?.state ?: return

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
            if (boundWidget?.switchSupport == true) {
                connection.httpClient.sendItemCommand(boundWidget?.item,
                    if (seekBar.progress == 0) "ON" else "OFF")
            }
        }

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            // no-op
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            Log.d(TAG, "onStartTrackingTouch position = ${seekBar.progress}")
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            val progress = seekBar.progress
            Log.d(TAG, "onStopTrackingTouch position = $progress")
            val widget = boundWidget
            val item = widget?.item ?: return
            val newValue = widget.minValue + widget.step * progress
            connection.httpClient.sendItemUpdate(item, item.state?.asNumber.withValue(newValue))
        }
    }

    class ImageViewHolder internal constructor(
        inflater: LayoutInflater,
        private val parent: ViewGroup,
        private val connection: Connection
    ) : ViewHolder(inflater, parent, R.layout.widgetlist_imageitem) {
        private val imageView: WidgetImageView = itemView.findViewById(R.id.image)
        private var refreshRate: Int = 0

        override fun bind(widget: Widget) {
            val value = widget.state?.asString

            // Make sure images fit into the content frame by scaling
            // them at max 90% of the available height
            if (parent.height > 0) {
                imageView.maxHeight = Math.round(0.9f * parent.height)
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
                imageView.setImageUrl(connection, widget.url, parent.width)
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

    class SelectionViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_selectionitem, connection, colorMapper),
        ExtendedSpinner.OnSelectionUpdatedListener {
        private val spinner: ExtendedSpinner = itemView.findViewById(R.id.spinner)
        private var boundItem: Item? = null
        private var boundMappings: List<LabeledValue> = emptyList()

        init {
            spinner.onSelectionUpdatedListener = this
        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            boundItem = widget.item
            boundMappings = widget.mappingsOrItemOptions

            val stateString = boundItem?.state?.asString
            val spinnerArray = boundMappings.map { mapping -> mapping.label }.toMutableList()
            var spinnerSelectedIndex = boundMappings.indexOfFirst { mapping -> mapping.value == stateString }

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
            Log.d(TAG, "Spinner item click on index $position")
            if (position >= boundMappings.size) {
                return
            }
            val (value) = boundMappings[position]
            Log.d(TAG, "Spinner onItemSelected found match with $value")
            connection.httpClient.sendItemCommand(boundItem, value)
        }
    }

    class SectionSwitchViewHolder internal constructor(
        private val inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_sectionswitchitem, connection, colorMapper),
        View.OnClickListener {
        private val radioGroup: RadioGroup = itemView.findViewById(R.id.switch_group)
        private var boundItem: Item? = null

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundItem = widget.item

            val mappings = widget.mappingsOrItemOptions
            // inflate missing views
            for (i in radioGroup.childCount until mappings.size) {
                val view = inflater.inflate(R.layout.widgetlist_sectionswitchitem_button, radioGroup, false)
                view.setOnClickListener(this)
                radioGroup.addView(view)
            }
            // bind views
            val state = boundItem?.state?.asString
            mappings.forEachIndexed { index, mapping ->
                with(radioGroup[index] as SegmentedControlButton) {
                    text = mapping.label
                    tag = mapping.value
                    isChecked = mapping.value == state
                    isVisible = true
                }
            }
            // hide spare views
            for (i in mappings.size until radioGroup.childCount) {
                radioGroup[i].isVisible = false
            }
        }

        override fun onClick(view: View) {
            connection.httpClient.sendItemCommand(boundItem, view.tag as String)
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

    class RollerShutterViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_rollershutteritem, connection, colorMapper),
        View.OnTouchListener {
        private var boundItem: Item? = null

        init {
            val buttonCommandMap =
                mapOf(R.id.up_button to "UP", R.id.down_button to "DOWN", R.id.stop_button to "STOP")
            for ((id, command) in buttonCommandMap) {
                val button = itemView.findViewById<View>(id)
                button.setOnTouchListener(this)
                button.tag = command
            }
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundItem = widget.item
        }

        override fun onTouch(v: View, motionEvent: MotionEvent): Boolean {
            if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                connection.httpClient.sendItemCommand(boundItem, v.tag as String)
            }
            return false
        }
    }

    class SetpointViewHolder internal constructor(
        private val inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_setpointitem, connection, colorMapper) {
        private var boundWidget: Widget? = null

        init {
            itemView.findViewById<View>(R.id.widgetvalue).setOnClickListener { openSelection() }
            itemView.findViewById<View>(R.id.down_arrow).setOnClickListener { openSelection() }
            itemView.findViewById<View>(R.id.up_button).setOnClickListener { handleUpDown(false) }
            itemView.findViewById<View>(R.id.down_button).setOnClickListener { handleUpDown(true) }
        }

        override fun bind(widget: Widget) {
            super.bind(widget)
            boundWidget = widget
        }

        override fun handleRowClick() {
            openSelection()
        }

        private fun openSelection() {
            val widget = boundWidget ?: return
            val state = widget.state?.asNumber
            val stateValue = state?.value?.toFloat() ?: widget.minValue
            // This prevents an exception below, but could lead to
            // user confusion if this case is ever encountered.
            val stepSize = if (widget.minValue == widget.maxValue) 1F else widget.step
            val stepCount = (abs(widget.maxValue - widget.minValue) / stepSize).toInt()
            var closestIndex = 0
            var closestDelta = java.lang.Float.MAX_VALUE

            val stepValues: List<ParsedState.NumberState> = (0..stepCount).map { index ->
                val stepValue = widget.minValue + index * stepSize
                if (abs(stateValue - stepValue) < closestDelta) {
                    closestIndex = index
                    closestDelta = abs(stateValue - stepValue)
                }
                state.withValue(stepValue)
            }

            val dialogView = inflater.inflate(R.layout.dialog_numberpicker, null)
            val picker = dialogView.findViewById<NumberPicker>(R.id.number_picker).apply {
                minValue = 0
                maxValue = stepValues.size - 1
                displayedValues = stepValues.map { item -> item.toString() }.toTypedArray()
                value = closestIndex
            }

            AlertDialog.Builder(itemView.context)
                .setTitle(labelView.text)
                .setView(dialogView)
                .setPositiveButton(R.string.set) { _, _ ->
                    connection.httpClient.sendItemUpdate(widget.item, stepValues[picker.value])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun handleUpDown(down: Boolean) {
            val widget = boundWidget
            val state = widget?.state?.asNumber
            val stateValue = state?.value?.toFloat()
            val newValue = when {
                stateValue == null -> widget?.minValue ?: return
                down -> stateValue - widget.step
                else -> stateValue + widget.step
            }

            if (newValue >= widget.minValue && newValue <= widget.maxValue) {
                connection.httpClient.sendItemUpdate(widget.item, state.withValue(newValue))
            }
        }
    }

    class ChartViewHolder internal constructor(
        inflater: LayoutInflater,
        private val parent: ViewGroup,
        private val chartTheme: CharSequence?,
        private val connection: Connection
    ) : ViewHolder(inflater, parent, R.layout.widgetlist_chartitem) {
        private val chart: WidgetImageView = itemView.findViewById(R.id.chart)
        private val random = Random()
        private val prefs: SharedPreferences
        private var refreshRate = 0
        private val density: Int

        init {
            val context = itemView.context
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(metrics)

            density = metrics.densityDpi
            prefs = context.getPrefs()
        }

        override fun bind(widget: Widget) {
            val item = widget.item
            if (item == null) {
                Log.e(TAG, "Chart item is null")
                chart.setImageDrawable(null)
                refreshRate = 0
                return
            }

            val actualDensity = density.toFloat() / prefs.getChartScalingFactor()
            val resDivider = if (prefs.shouldRequestHighResChart()) 1 else 2

            val chartUrl = StringBuilder("chart?")
                .append(if (item.type === Item.Type.Group) "groups=" else "items=")
                .append(item.name)
                .append("&period=")
                .append(widget.period)
                .append("&random=")
                .append(random.nextInt())
                .append("&dpi=")
                .append(actualDensity.toInt() / resDivider)
            if (widget.service.isNotEmpty()) {
                chartUrl.append("&service=").append(widget.service)
            }
            if (chartTheme != null) {
                chartUrl.append("&theme=").append(chartTheme)
            }
            if (widget.legend != null) {
                chartUrl.append("&legend=").append(widget.legend)
            }

            val parentWidth = parent.width
            if (parentWidth > 0) {
                chartUrl.append("&w=").append(parentWidth / resDivider)
                chartUrl.append("&h=").append(parentWidth / 2 / resDivider)
            }

            Log.d(TAG, "Chart url = $chartUrl")

            chart.setImageUrl(connection, chartUrl.toString(), parentWidth, forceLoad = true)
            refreshRate = widget.refresh
        }

        override fun start() {
            if (refreshRate > 0) {
                chart.setRefreshRate(refreshRate)
            } else {
                chart.cancelRefresh()
            }
        }

        override fun stop() {
            chart.cancelRefresh()
        }
    }

    class VideoViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup) :
        ViewHolder(inflater, parent, R.layout.widgetlist_videoitem) {
        private val videoView: VideoView = itemView.findViewById(R.id.video)

        override fun bind(widget: Widget) {
            // FIXME: check for URL changes here
            if (!videoView.isPlaying) {
                var videoUrl = widget.url
                if ("hls".equals(widget.encoding, ignoreCase = true)) {
                    val state = widget.item?.state?.asString
                    if (state != null && widget.item.type == Item.Type.StringItem) {
                        videoUrl = state
                    }
                }
                Log.d(TAG, "Opening video at $videoUrl")
                videoView.setVideoURI(videoUrl?.toUri())
            }
        }

        override fun start() {
            videoView.start()
        }

        override fun stop() {
            videoView.stopPlayback()
        }
    }

    class WebViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection
    ) : ViewHolder(inflater, parent, R.layout.widgetlist_webitem) {
        private val webView: WebView = itemView.findViewById(R.id.webview)

        @SuppressLint("SetJavaScriptEnabled")
        override fun bind(widget: Widget) {
            val url = connection.httpClient.buildUrl(widget.url!!).toString()
            with(webView) {
                adjustForWidgetHeight(widget, 0)
                loadUrl("about:blank")

                setUpForConnection(connection, url)
                webViewClient = AnchorWebViewClient(url, connection.username, connection.password)
                loadUrl(url)
            }
        }
    }

    class ColorViewHolder internal constructor(
        private val inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection,
        colorMapper: ColorMapper
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_coloritem, connection, colorMapper),
        View.OnTouchListener, Handler.Callback, ColorPicker.OnColorChangedListener {
        private var boundItem: Item? = null
        private val handler = Handler(this)

        init {
            val buttonCommandMap =
                mapOf(R.id.up_button to "ON", R.id.down_button to "OFF", R.id.select_color_button to null)
            for ((id, command) in buttonCommandMap) {
                val button = itemView.findViewById<View>(id)
                button.setOnTouchListener(this)
                button.tag = command
            }
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
                    connection.httpClient.sendItemCommand(boundItem, v.tag as String)
                } else {
                    showColorPickerDialog()
                }
            }
            return false
        }

        override fun handleMessage(msg: Message): Boolean {
            val hsv = FloatArray(3)
            Color.RGBToHSV(Color.red(msg.arg1), Color.green(msg.arg1), Color.blue(msg.arg1), hsv)
            Log.d(TAG, "New color HSV = ${hsv[0]}, ${hsv[1]}, ${hsv[2]}")
            val newColorValue = String.format(Locale.US, "%f,%f,%f",
                hsv[0], hsv[1] * 100, hsv[2] * 100)
            connection.httpClient.sendItemCommand(boundItem, newColorValue)
            return true
        }

        override fun onColorChanged(color: Int) {
            handler.removeMessages(0)
            handler.sendMessageDelayed(handler.obtainMessage(0, color, 0), 100)
        }

        private fun showColorPickerDialog() {
            val contentView = inflater.inflate(R.layout.color_picker_dialog, null)
            val picker = contentView.findViewById<ColorPicker>(R.id.picker).apply {
                addSaturationBar(contentView.findViewById(R.id.saturation_bar))
                addValueBar(contentView.findViewById(R.id.value_bar))
                onColorChangedListener = this@ColorViewHolder
                showOldCenterColor = false
            }

            val initialColor = boundItem?.state?.asHsv?.toColor()
            if (initialColor != null) {
                picker.color = initialColor
            }

            AlertDialog.Builder(contentView.context)
                .setView(contentView)
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    class MjpegVideoViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        private val connection: Connection
    ) : ViewHolder(inflater, parent, R.layout.widgetlist_videomjpegitem) {
        private val imageView: ImageView = itemView.findViewById(R.id.mjpeg_image)
        private var streamer: MjpegStreamer? = null

        override fun bind(widget: Widget) {
            streamer = if (widget.url != null) MjpegStreamer(imageView, connection, widget.url) else null
        }

        override fun start() {
            streamer?.start()
        }

        override fun stop() {
            streamer?.stop()
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
            val adapter = parent.adapter
            val noDividerTypes = intArrayOf(TYPE_FRAME, TYPE_INVISIBLE)
            if (adapter != null) {
                if (adapter.getItemViewType(position) in noDividerTypes) {
                    return true
                }
                if (position < adapter.itemCount - 1) {
                    if (adapter.getItemViewType(position + 1) in noDividerTypes) {
                        return true
                    }
                }
            }

            return false
        }
    }

    @VisibleForTesting
    class ColorMapper internal constructor(context: Context) {
        private val colorMap = HashMap<String, Int>()

        init {
            val colorNames = context.resources.getStringArray(R.array.valueColorNames)

            val tv = TypedValue()
            context.theme.resolveAttribute(R.attr.valueColors, tv, false)
            val ta = context.resources.obtainTypedArray(tv.data)

            var i = 0
            while (i < ta.length() && i < colorNames.size) {
                colorMap[colorNames[i]] = ta.getColor(i, 0)
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
                colorMap[colorName]
            }
        }
    }

    companion object {
        internal val TAG = WidgetAdapter::class.java.simpleName

        private const val TYPE_GENERICITEM = 0
        private const val TYPE_FRAME = 1
        private const val TYPE_GROUP = 2
        private const val TYPE_SWITCH = 3
        private const val TYPE_TEXT = 4
        private const val TYPE_SLIDER = 5
        private const val TYPE_IMAGE = 6
        private const val TYPE_SELECTION = 7
        private const val TYPE_SECTIONSWITCH = 8
        private const val TYPE_ROLLERSHUTTER = 9
        private const val TYPE_SETPOINT = 10
        private const val TYPE_CHART = 11
        private const val TYPE_VIDEO = 12
        private const val TYPE_WEB = 13
        private const val TYPE_COLOR = 14
        private const val TYPE_VIDEO_MJPEG = 15
        private const val TYPE_LOCATION = 16
        private const val TYPE_INVISIBLE = 17
    }
}

fun View.adjustForWidgetHeight(widget: Widget, fallbackRowCount: Int) {
    val desiredHeightPixels = when {
        widget.height > 0 -> widget.height * resources.getDimensionPixelSize(R.dimen.row_height)
        fallbackRowCount > 0 -> fallbackRowCount * resources.getDimensionPixelSize(R.dimen.row_height)
        else -> ViewGroup.LayoutParams.WRAP_CONTENT
    }

    val lp = layoutParams
    if (lp.height != desiredHeightPixels) {
        lp.height = desiredHeightPixels
        layoutParams = lp
    }
}

fun TextView.applyWidgetColor(colorName: String?, mapper: WidgetAdapter.ColorMapper) {
    val origColor = getTag(R.id.originalColor) as ColorStateList?
    val color = mapper.mapColor(colorName)
    if (color != null) {
        if (origColor == null) {
            setTag(R.id.originalColor, textColors)
        }
        setTextColor(color)
    } else if (origColor != null) {
        setTextColor(origColor)
        setTag(R.id.originalColor, null)
    }
}

fun WidgetImageView.loadWidgetIcon(connection: Connection, widget: Widget, mapper: WidgetAdapter.ColorMapper) {
    if (widget.icon == null) {
        setImageDrawable(null)
        return
    }
    // This is needed to escape possible spaces and everything according to rfc2396
    val iconUrl = Uri.encode(widget.iconPath, "/?=&")
    setImageUrl(connection, iconUrl, resources.getDimensionPixelSize(R.dimen.notificationlist_icon_size))
    val color = mapper.mapColor(widget.iconColor)
    if (color != null) {
        setColorFilter(color)
    } else {
        clearColorFilter()
    }
}

fun HttpClient.sendItemUpdate(item: Item?, state: ParsedState.NumberState?) {
    if (item == null || state == null) {
        return
    }
    if (item.isOfTypeOrGroupType(Item.Type.NumberWithDimension)) {
        // For number items, include unit (if present) in command
        sendItemCommand(item, state.toString(Locale.US))
    } else {
        // For all other items, send the plain value
        sendItemCommand(item, state.formatValue())
    }
}

fun HttpClient.sendItemCommand(item: Item?, command: String) {
    val url = item?.link ?: return
    GlobalScope.launch {
        try {
            post(url, command, "text/plain;charset=UTF-8")
            Log.d(WidgetAdapter.TAG, "Command '$command' was sent successfully to $url")
        } catch (e: HttpClient.HttpException) {
            Log.e(WidgetAdapter.TAG, "Sending command $command to $url failed: status ${e.statusCode}", e)
        }
    }
}
