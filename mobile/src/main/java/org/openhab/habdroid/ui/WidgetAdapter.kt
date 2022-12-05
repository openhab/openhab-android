/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.ui.widget.AutoHeightPlayerView
import org.openhab.habdroid.ui.widget.ContextMenuAwareRecyclerView
import org.openhab.habdroid.ui.widget.PeriodicSignalImageButton
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.MjpegStreamer
import org.openhab.habdroid.util.beautify
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getChartTheme
import org.openhab.habdroid.util.getImageWidgetScalingType
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.orDefaultIfEmpty

/**
 * This class provides openHAB widgets adapter for list view.
 */

class WidgetAdapter(
    context: Context,
    val serverFlags: Int,
    val connection: Connection,
    private val itemClickListener: ItemClickListener,
    private val bottomSheetPresenter: DetailBottomSheetPresenter
) : RecyclerView.Adapter<WidgetAdapter.ViewHolder>(), View.OnClickListener {
    private val items = mutableListOf<Widget>()
    val itemList: List<Widget> get() = items
    private val widgetsById = mutableMapOf<String, Widget>()
    val hasVisibleWidgets: Boolean
        get() = items.any { widget -> shouldShowWidget(widget) }

    private val inflater = LayoutInflater.from(context)
    private val chartTheme: CharSequence = context.getChartTheme(serverFlags)
    private var selectedPosition = RecyclerView.NO_POSITION
    private var firstVisibleWidgetPosition = RecyclerView.NO_POSITION
    private val colorMapper = ColorMapper(context)

    interface ItemClickListener {
        fun onItemClicked(widget: Widget): Boolean // returns whether click was handled
    }
    interface DetailBottomSheetPresenter {
        fun showBottomSheet(sheet: AbstractWidgetDetailBottomSheet, widget: Widget)
    }

    @SuppressLint("NotifyDataSetChanged")
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
        updateFirstVisibleWidgetPosition()
    }

    fun updateWidget(widget: Widget) {
        val pos = items.indexOfFirst { w -> w.id == widget.id }
        if (pos >= 0) {
            updateWidgetAtPosition(pos, widget)
            updateFirstVisibleWidgetPosition()
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

    fun getItemForContextMenu(info: ContextMenuAwareRecyclerView.RecyclerContextMenuInfo): Widget? {
        return if (info.position < items.size) items[info.position] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = when (viewType) {
            TYPE_GENERICITEM -> GenericViewHolder(inflater, parent)
            TYPE_FRAME -> FrameViewHolder(inflater, parent)
            TYPE_GROUP -> GroupViewHolder(inflater, parent)
            TYPE_SWITCH -> SwitchViewHolder(inflater, parent)
            TYPE_TEXT -> TextViewHolder(inflater, parent)
            TYPE_SLIDER -> SliderViewHolder(inflater, parent)
            TYPE_IMAGE -> ImageViewHolder(inflater, parent)
            TYPE_SELECTION -> SelectionViewHolder(inflater, parent)
            TYPE_SECTIONSWITCH -> SectionSwitchViewHolder(inflater, parent)
            TYPE_SECTIONSWITCH_SINGLE -> SingleSectionSwitchViewHolder(inflater, parent)
            TYPE_ROLLERSHUTTER -> RollerShutterViewHolder(inflater, parent)
            TYPE_SETPOINT -> SetpointViewHolder(inflater, parent)
            TYPE_CHART -> ChartViewHolder(inflater, parent)
            TYPE_VIDEO -> VideoViewHolder(inflater, parent)
            TYPE_WEB -> WebViewHolder(inflater, parent)
            TYPE_COLOR -> ColorViewHolder(inflater, parent)
            TYPE_VIDEO_MJPEG -> MjpegVideoViewHolder(inflater, parent)
            TYPE_LOCATION -> MapViewHelper.createViewHolder(inflater, parent)
            TYPE_INVISIBLE -> InvisibleWidgetViewHolder(inflater, parent)
            else -> throw IllegalArgumentException("View type $viewType is not known")
        }

        holder.itemView.tag = holder

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wasStarted = holder.stop()
        holder.vhc = ViewHolderContext(connection, bottomSheetPresenter, colorMapper, serverFlags, chartTheme)
        holder.bind(items[position])
        if (holder is FrameViewHolder) {
            holder.setShownAsFirst(position == firstVisibleWidgetPosition)
        }
        with(holder.itemView) {
            isClickable = true
            isLongClickable = true
            isActivated = selectedPosition == position
            setOnClickListener(this@WidgetAdapter)
        }
        if (wasStarted) {
            holder.start()
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

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.vhc = null
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        return getItemViewType(items[position])
    }

    override fun onClick(view: View) {
        val holder = view.tag as ViewHolder
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            if (!itemClickListener.onItemClicked(items[position])) {
                holder.handleRowClick()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
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

    private fun updateFirstVisibleWidgetPosition() {
        firstVisibleWidgetPosition = items.indexOfFirst { w -> shouldShowWidget(w) }
    }

    private tailrec fun shouldShowWidget(widget: Widget): Boolean {
        if (!widget.visibility) {
            return false
        }
        if (widget.type == Widget.Type.Frame) {
            val hasVisibleChildren = items
                .filter { it.parentId == widget.id }
                .any { it.visibility }
            if (!hasVisibleChildren) {
                return false
            }
        }
        val parent = widget.parentId?.let { id -> widgetsById[id] } ?: return true
        return shouldShowWidget(parent)
    }

    private fun getItemViewType(widget: Widget): Int {
        if (!shouldShowWidget(widget)) {
            return TYPE_INVISIBLE
        }
        return when (widget.type) {
            Widget.Type.Frame -> TYPE_FRAME
            Widget.Type.Group -> TYPE_GROUP
            Widget.Type.Switch -> when {
                widget.mappingsOrItemOptions.size == 1 -> TYPE_SECTIONSWITCH_SINGLE
                widget.mappings.isNotEmpty() -> TYPE_SECTIONSWITCH
                widget.item?.isOfTypeOrGroupType(Item.Type.Switch) == true -> TYPE_SWITCH
                widget.item?.isOfTypeOrGroupType(Item.Type.Rollershutter) == true -> TYPE_ROLLERSHUTTER
                widget.mappingsOrItemOptions.isNotEmpty() -> TYPE_SECTIONSWITCH
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

    data class ViewHolderContext(
        val connection: Connection,
        val bottomSheetPresenter: DetailBottomSheetPresenter,
        val colorMapper: ColorMapper,
        val serverFlags: Int,
        val chartTheme: CharSequence?
    )

    abstract class ViewHolder internal constructor(
        inflater: LayoutInflater,
        val parent: ViewGroup,
        @LayoutRes layoutResId: Int
    ) : RecyclerView.ViewHolder(inflater.inflate(layoutResId, parent, false)) {
        internal var vhc: ViewHolderContext? = null
        var started = false
            private set

        protected val connection get() = requireHolderContext().connection
        protected val colorMapper get() = requireHolderContext().colorMapper
        protected val bottomSheetPresenter get() = requireHolderContext().bottomSheetPresenter

        abstract fun bind(widget: Widget)
        fun start() {
            if (!started) {
                onStart()
                started = true
            }
        }
        fun stop(): Boolean {
            if (!started) {
                return false
            }
            onStop()
            started = false
            return true
        }
        open fun onStart() {}
        open fun onStop() {}
        open fun handleRowClick() {}

        protected fun requireHolderContext() = vhc ?: throw IllegalStateException("Holder not bound")
    }

    abstract class LabeledItemBaseViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        @LayoutRes layoutResId: Int,
    ) : ViewHolder(inflater, parent, layoutResId) {
        protected val labelView: TextView = itemView.findViewById(R.id.widgetlabel)
        protected val valueView: TextView? = itemView.findViewById(R.id.widgetvalue)
        private val iconView: WidgetImageView = itemView.findViewById(R.id.widgeticon)
        protected var boundWidget: Widget? = null
            private set

        override fun bind(widget: Widget) {
            boundWidget = widget

            labelView.text = widget.label
            labelView.applyWidgetColor(widget.labelColor, colorMapper)
            if (valueView != null) {
                valueView.text = widget.stateFromLabel?.replace("\n", " ")
                valueView.isVisible = !widget.stateFromLabel.isNullOrEmpty()
                valueView.applyWidgetColor(widget.valueColor, colorMapper)
            }
            iconView.loadWidgetIcon(connection, widget, colorMapper)
        }
    }

    abstract class HeavyDataViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup,
        @LayoutRes layoutResId: Int,
    ) : ViewHolder(inflater, parent, layoutResId) {
        protected var boundWidget: Widget? = null
            private set
        protected val widgetContentView: View = itemView.findViewById(R.id.widget_content)
        private val dataSaverView: View = itemView.findViewById(R.id.data_saver)
        private val dataSaverButton: Button = itemView.findViewById(R.id.data_saver_button)
        private val dataSaverHint: TextView = itemView.findViewById(R.id.data_saver_hint)

        override fun bind(widget: Widget) {
            boundWidget = widget
            if (!showDataSaverPlaceholderIfNeeded(widget, canBindWithoutDataTransfer(widget))) {
                bindAfterDataSaverCheck(widget)
            }
        }

        private fun showDataSaverPlaceholderIfNeeded(widget: Widget, canBindWithoutData: Boolean): Boolean {
            val dataSaverActive = !itemView.context.determineDataUsagePolicy(connection).canDoLargeTransfers &&
                !canBindWithoutData

            dataSaverView.isVisible = dataSaverActive
            widgetContentView.isVisible = !dataSaverView.isVisible

            if (dataSaverActive) {
                dataSaverButton.setOnClickListener {
                    showDataSaverPlaceholderIfNeeded(widget, true)
                    bindAfterDataSaverCheck(widget)
                }

                @StringRes val typeResId = when (widget.type) {
                    Widget.Type.Image -> R.string.widget_type_image
                    Widget.Type.Webview -> R.string.widget_type_webview
                    Widget.Type.Video -> R.string.widget_type_video
                    Widget.Type.Chart -> R.string.widget_type_chart
                    else -> throw IllegalArgumentException("Cannot show data saver hint for ${widget.type}")
                }

                dataSaverHint.text = itemView.context.getString(R.string.data_saver_hint,
                    widget.label.orDefaultIfEmpty(itemView.context.getString(typeResId)))
            } else {
                dataSaverButton.setOnClickListener(null)
            }

            return dataSaverActive
        }

        fun handleDataUsagePolicyChange() {
            if (!itemView.context.determineDataUsagePolicy(connection).canDoLargeTransfers) {
                // Continue showing the old data, but stop any activity that might need more data transfer
                stop()
            } else {
                boundWidget?.let {
                    bind(it)
                    start()
                }
            }
        }

        internal abstract fun bindAfterDataSaverCheck(widget: Widget)
        internal open fun canBindWithoutDataTransfer(widget: Widget): Boolean = false
    }

    class GenericViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
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
    ) : ViewHolder(inflater, parent, R.layout.widgetlist_frameitem) {
        private val labelView: TextView = itemView.findViewById(R.id.widgetlabel)
        private val containerView: View = itemView.findViewById(R.id.container)
        private val spacer: View = itemView.findViewById(R.id.first_view_spacer)

        init {
            itemView.isClickable = false
        }

        override fun bind(widget: Widget) {
            val label = widget.stateFromLabel?.let {
                " [$it]"
            }.orEmpty()
            @SuppressLint("SetTextI18n")
            labelView.text = widget.label + label
            labelView.applyWidgetColor(widget.valueColor, colorMapper)
            labelView.isGone = widget.label.isEmpty()
        }

        fun setShownAsFirst(shownAsFirst: Boolean) {
            containerView.isGone = labelView.isGone && shownAsFirst
            spacer.isGone = !containerView.isGone
        }
    }

    class GroupViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_groupitem) {
        private val rightArrow: ImageView = itemView.findViewById(R.id.right_arrow)

        override fun bind(widget: Widget) {
            super.bind(widget)
            rightArrow.isGone = widget.linkedPage == null
        }
    }

    class SwitchViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_switchitem),
        View.OnTouchListener {
        private val switch: SwitchMaterial = itemView.findViewById(R.id.toggle)
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
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_textitem) {
        private val rightArrow: ImageView = itemView.findViewById(R.id.right_arrow)

        override fun bind(widget: Widget) {
            super.bind(widget)
            rightArrow.isGone = widget.linkedPage == null
        }
    }

    class SliderViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_slideritem),
        Slider.OnSliderTouchListener, Slider.OnChangeListener, LabelFormatter {
        private val slider: Slider = itemView.findViewById(R.id.seekbar)
        private var isTracking = false
        private var lastChange = 0L

        init {
            slider.addOnSliderTouchListener(this)
            slider.addOnChangeListener(this)
            slider.setLabelFormatter(this)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            labelView.isGone = widget.label.isEmpty()

            val item = widget.item
            val hasValidValues = widget.minValue < widget.maxValue
            slider.isVisible = hasValidValues
            if (!hasValidValues) {
                Log.e(TAG, "Slider has invalid values: from '${widget.minValue}' to '${widget.maxValue}'")
                return
            }

            if (item?.isOfTypeOrGroupType(Item.Type.Color) == true) {
                slider.valueTo = 100F
                slider.valueFrom = 0F
                slider.stepSize = 1F
                slider.value = item.state?.asBrightness?.toFloat() ?: 0F
                slider.isTickVisible = false
            } else {
                // Fix "The stepSize must be 0, or a factor of the valueFrom-valueTo range" exception
                slider.valueTo = widget.maxValue - (widget.maxValue - widget.minValue).rem(widget.step)
                slider.valueFrom = widget.minValue
                slider.stepSize = widget.step
                val widgetValue = item?.state?.asNumber?.value ?: slider.valueFrom

                // Fix "Value must be equal to valueFrom plus a multiple of stepSize when using stepSize"
                val stepCount = (abs(slider.valueTo - slider.valueFrom) / slider.stepSize).toInt()
                var closetValue = slider.valueFrom
                var closestDelta = Float.MAX_VALUE
                (0..stepCount).map { index ->
                    val stepValue = slider.valueFrom + index * slider.stepSize
                    if (abs(widgetValue - stepValue) < closestDelta) {
                        closetValue = stepValue
                        closestDelta = abs(widgetValue - stepValue)
                    }
                }

                slider.isTickVisible = stepCount <= 12

                Log.d(
                    TAG,
                    "Slider: valueFrom = ${slider.valueFrom}, valueTo = ${slider.valueTo}, " +
                        "stepSize = ${slider.stepSize}, stepCount = $stepCount, widgetValue = $widgetValue, " +
                        "closetValue = $closetValue, closestDelta = $closestDelta"
                )

                if (!isTracking) {
                    slider.value = closetValue
                }
            }
        }

        override fun handleRowClick() {
            val widget = boundWidget ?: return
            if (widget.switchSupport) {
                connection.httpClient.sendItemCommand(widget.item,
                    if (slider.value <= widget.minValue) "ON" else "OFF")
            }
        }

        override fun onStartTrackingTouch(slider: Slider) {
            isTracking = true
        }

        override fun onStopTrackingTouch(slider: Slider) {
            isTracking = false
            sendUpdate()
        }

        override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
            if (!fromUser) {
                return
            }
            Log.e(TAG, "${System.currentTimeMillis() - lastChange}")
            if (System.currentTimeMillis() - lastChange < 1000) {
                return
            }
            sendUpdate()
        }

        private fun sendUpdate() {
            val value = slider.value.beautify()
            Log.d(TAG, "onValueChange value = $value")
            val item = boundWidget?.item ?: return
            if (item.isOfTypeOrGroupType(Item.Type.Color)) {
                connection.httpClient.sendItemCommand(item, value)
            } else {
                connection.httpClient.sendItemUpdate(item, item.state?.asNumber.withValue(value.toFloat()))
            }
            lastChange = System.currentTimeMillis()
        }

        override fun getFormattedValue(value: Float): String {
            val item = boundWidget?.item ?: return ""
            return if (item.isOfTypeOrGroupType(Item.Type.Color)) {
                "${value.beautify()} %"
            } else {
                item.state?.asNumber.withValue(value).toString()
            }
        }
    }

    class ImageViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : HeavyDataViewHolder(inflater, parent, R.layout.widgetlist_imageitem), View.OnClickListener {
        private val imageView = widgetContentView as WidgetImageView
        private val prefs = imageView.context.getPrefs()

        init {
            imageView.setOnClickListener(this)
        }

        override fun canBindWithoutDataTransfer(widget: Widget): Boolean {
            return widget.url == null ||
                CacheManager.getInstance(itemView.context).isBitmapCached(connection.httpClient.buildUrl(widget.url))
        }

        override fun bindAfterDataSaverCheck(widget: Widget) {
            val value = widget.state?.asString

            // Make sure images fit into the content frame by scaling
            // them at max 90% of the available height
            if (parent.height > 0) {
                imageView.maxHeight = (0.9f * parent.height).roundToInt()
            } else {
                imageView.maxHeight = Integer.MAX_VALUE
            }
            imageView.setImageScalingType(prefs.getImageWidgetScalingType())

            if (value != null && value.matches("data:image/.*;base64,.*".toRegex())) {
                val dataString = value.substring(value.indexOf(",") + 1)
                imageView.setBase64EncodedImage(dataString)
            } else if (widget.url != null) {
                imageView.setImageUrl(connection, widget.url, refreshDelayInMs = widget.refresh)
            } else {
                imageView.setImageDrawable(null)
            }
        }

        override fun onStart() {
            if (itemView.context.determineDataUsagePolicy(connection).canDoRefreshes) {
                imageView.startRefreshingIfNeeded()
            } else {
                imageView.cancelRefresh()
            }
        }

        override fun onStop() {
            imageView.cancelRefresh()
        }

        override fun onClick(v: View?) {
            val context = v?.context ?: return
            boundWidget?.let { widget ->
                val intent = Intent(context, ImageWidgetActivity::class.java).apply {
                    putExtra(ImageWidgetActivity.WIDGET_LABEL, widget.label)
                    putExtra(ImageWidgetActivity.WIDGET_REFRESH, widget.refresh)
                }
                when {
                    widget.item?.link != null -> intent.putExtra(ImageWidgetActivity.WIDGET_LINK, widget.item.link)
                    widget.url != null -> intent.putExtra(ImageWidgetActivity.WIDGET_URL, widget.url)
                    else -> return@let
                }
                context.startActivity(intent)
            }
        }
    }

    class SelectionViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_selectionitem) {
        override fun bind(widget: Widget) {
            super.bind(widget)

            val stateString = widget.item?.state?.asString
            val selectedLabel = widget.mappingsOrItemOptions.firstOrNull { mapping -> mapping.value == stateString }
            valueView?.text = selectedLabel?.label ?: stateString
            valueView?.isVisible = valueView?.text.isNullOrEmpty() != true
        }

        override fun handleRowClick() {
            val widget = boundWidget ?: return
            bottomSheetPresenter.showBottomSheet(SelectionBottomSheet(), widget)
        }
    }

    class SectionSwitchViewHolder internal constructor(
        private val inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_sectionswitchitem),
        View.OnClickListener {
        private val group: MaterialButtonToggleGroup = itemView.findViewById(R.id.switch_group)
        private val spareViews = mutableListOf<View>()
        private val maxButtons = itemView.resources.getInteger(R.integer.section_switch_max_buttons)

        override fun bind(widget: Widget) {
            super.bind(widget)

            val hasNoLabelAndValue = labelView.text.isEmpty() && valueView?.text?.isEmpty() != false
            labelView.isGone = hasNoLabelAndValue
            valueView?.isGone = hasNoLabelAndValue

            val mappings = widget.mappingsOrItemOptions
            val buttonCount = min(mappings.size, maxButtons)
            val neededViews = if (mappings.size <= maxButtons) mappings.size else maxButtons + 1

            // inflate missing views
            while (spareViews.isNotEmpty() && group.childCount < neededViews) {
                group.addView(spareViews.removeAt(0))
            }
            while (group.childCount < neededViews) {
                val view = inflater.inflate(R.layout.widgetlist_sectionswitchitem_button, group, false)
                view.setOnClickListener(this)
                group.addView(view)
            }

            // bind views
            mappings.slice(0 until buttonCount).forEachIndexed { index, mapping ->
                with(group[index] as MaterialButton) {
                    text = mapping.label
                    tag = mapping.value
                    isVisible = true
                }
            }
            if (mappings.size > maxButtons) {
                // overflow button
                with(group[maxButtons] as MaterialButton) {
                    text = "⋯"
                    tag = null
                    isVisible = true
                    isCheckable = false
                }
            }

            // remove unneeded views
            while (group.childCount > neededViews) {
                val view = group[group.childCount - 1]
                spareViews.add(view)
                group.removeView(view)
            }

            // check selected view
            val state = widget.item?.state?.asString
            val checkedId = group.children
                .filter { it.tag == state }
                .map { it.id }
                .firstOrNull()

            if (checkedId == null) {
                group.clearChecked()
            } else {
                group.check(checkedId)
            }

            group.isVisible = true
        }

        override fun onClick(view: View) {
            val tag = view.tag
            if (tag != null) {
                // Make sure one can't uncheck buttons by clicking a checked one
                (view as MaterialButton).isChecked = true
                connection.httpClient.sendItemCommand(boundWidget?.item, view.tag as String)
            } else {
                val widget = boundWidget ?: return
                bottomSheetPresenter.showBottomSheet(SelectionBottomSheet(), widget)
            }
        }

        override fun handleRowClick() {
            if (!group.isVisible) {
                return super.handleRowClick()
            }
            val visibleChildCount = group.children.filter { v -> v.isVisible }.count()
            if (visibleChildCount == 1) {
                onClick(group[0])
            } else if (visibleChildCount == 2) {
                val state = boundWidget?.item?.state?.asString
                if (state == group[0].tag.toString()) {
                    onClick(group[1])
                } else if (state == group[1].tag.toString()) {
                    onClick(group[0])
                }
            }
        }
    }

    class SingleSectionSwitchViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_singlesectionswitch_item) {
        private val toggle: MaterialButton = itemView.findViewById(R.id.switch_single)

        init {
            toggle.isCheckable = true
        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            val mapping = widget.mappingsOrItemOptions[0]
            toggle.text = mapping.label
            toggle.isChecked = widget.item?.state?.asString == mapping.value
            toggle.setOnClickListener {
                toggle.isChecked = true
                connection.httpClient.sendItemCommand(widget.item, mapping.value)
            }
        }

        override fun handleRowClick() {
            toggle.callOnClick()
        }
    }

    class RollerShutterViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_rollershutteritem), View.OnTouchListener {
        init {
            val buttonCommandMap =
                mapOf(R.id.up_button to "UP", R.id.down_button to "DOWN", R.id.stop_button to "STOP")
            for ((id, command) in buttonCommandMap) {
                val button = itemView.findViewById<View>(id)
                button.setOnTouchListener(this)
                button.tag = command
            }
        }

        override fun onTouch(v: View, motionEvent: MotionEvent): Boolean {
            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    val pressedTime = motionEvent.eventTime - motionEvent.downTime
                    if (pressedTime > ViewConfiguration.getLongPressTimeout() && v.tag != "STOP") {
                        connection.httpClient.sendItemCommand(boundWidget?.item, "STOP")
                    }
                }
                MotionEvent.ACTION_DOWN -> {
                    connection.httpClient.sendItemCommand(boundWidget?.item, v.tag as String)
                }
            }
            return false
        }
    }

    class SetpointViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_setpointitem) {
        init {
            itemView.findViewById<View>(R.id.widgetvalue).setOnClickListener { openSelection() }
            itemView.findViewById<View>(R.id.select_button).setOnClickListener { openSelection() }
            itemView.findViewById<View>(R.id.up_button).setOnClickListener { handleUpDown(false) }
            itemView.findViewById<View>(R.id.down_button).setOnClickListener { handleUpDown(true) }
        }

        override fun handleRowClick() {
            openSelection()
        }

        private fun openSelection() {
            val widget = boundWidget ?: return
            bottomSheetPresenter.showBottomSheet(SetpointBottomSheet(), widget)
        }

        private fun handleUpDown(down: Boolean) {
            val widget = boundWidget
            val state = widget?.state?.asNumber
            val stateValue = state?.value
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
        parent: ViewGroup
    ) : HeavyDataViewHolder(inflater, parent, R.layout.widgetlist_chartitem), View.OnClickListener {
        private val chart = widgetContentView as WidgetImageView
        private val prefs: SharedPreferences
        private val density: Int

        init {
            val context = itemView.context
            density = context.resources.configuration.densityDpi
            prefs = context.getPrefs()
            chart.setOnClickListener(this)
        }

        override fun bindAfterDataSaverCheck(widget: Widget) {
            val item = widget.item
            if (item == null) {
                Log.e(TAG, "Chart item is null")
                chart.setImageDrawable(null)
                return
            }

            val theme = requireHolderContext().chartTheme
            val chartUrl =
                widget.toChartUrl(prefs, parent.width, chartTheme = theme, density = density) ?: return
            Log.d(TAG, "Chart url = $chartUrl")
            chart.setImageUrl(connection, chartUrl, refreshDelayInMs = widget.refresh, forceLoad = true)
        }

        override fun onStart() {
            if (itemView.context.determineDataUsagePolicy(connection).canDoRefreshes) {
                chart.startRefreshingIfNeeded()
            } else {
                chart.cancelRefresh()
            }
        }

        override fun onStop() {
            chart.cancelRefresh()
        }

        override fun onClick(v: View?) {
            val context = v?.context ?: return
            boundWidget?.let {
                val intent = Intent(context, ChartWidgetActivity::class.java)
                intent.putExtra(ChartWidgetActivity.EXTRA_WIDGET, it)
                intent.putExtra(ChartWidgetActivity.EXTRA_SERVER_FLAGS, requireHolderContext().serverFlags)
                context.startActivity(intent)
            }
        }
    }

    class VideoViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup) :
        HeavyDataViewHolder(inflater, parent, R.layout.widgetlist_videoitem),
        AnalyticsListener,
        DataSource.Factory,
        View.OnClickListener {
        private val playerView = widgetContentView as AutoHeightPlayerView
        private val loadingIndicator: View = itemView.findViewById(R.id.video_player_loading)
        private val errorView: View = itemView.findViewById(R.id.video_player_error)
        private val errorViewHint: TextView = itemView.findViewById(R.id.video_player_error_hint)
        private val errorViewButton: Button = itemView.findViewById(R.id.video_player_error_button)
        private val exoPlayer = ExoPlayer.Builder(parent.context).build()

        init {
            playerView.player = exoPlayer
            errorViewButton.setOnClickListener(this)
        }

        override fun bindAfterDataSaverCheck(widget: Widget) {
            loadVideo(widget, false)
        }

        override fun onStart() {
            if (itemView.context.determineDataUsagePolicy(connection).autoPlayVideos) {
                exoPlayer.play()
            }
        }

        override fun onStop() {
            exoPlayer.pause()
        }

        private fun loadVideo(widget: Widget, forceReload: Boolean) {
            playerView.isVisible = true
            errorView.isVisible = false
            loadingIndicator.isVisible = true

            val isHls = widget.encoding.equals("hls", ignoreCase = true)
            val url = if (isHls) {
                val state = widget.item?.state?.asString
                if (state != null && widget.item.type == Item.Type.StringItem) {
                    state
                } else {
                    widget.url
                }
            } else {
                widget.url
            }
            val factory = if (isHls) {
                playerView.useController = false
                HlsMediaSource.Factory(this)
            } else {
                playerView.useController = true
                ProgressiveMediaSource.Factory(this)
            }

            val mediaItem = url?.let { MediaItem.fromUri(it) }
            val mediaSource = mediaItem?.let { factory.createMediaSource(it) }

            if (exoPlayer.currentMediaItem == mediaItem && !forceReload) {
                exoPlayer.play()
                return
            }

            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            if (mediaSource == null) {
                return
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.addAnalyticsListener(this)
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean
        ) {
            super.onLoadError(eventTime, loadEventInfo, mediaLoadData, error, wasCanceled)
            Log.e(TAG, "onLoadError()", error)
            handleError()
        }

        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            Log.e(TAG, "onPlayerError()", error)
            handleError()
        }

        private fun handleError() {
            loadingIndicator.isVisible = false
            playerView.isVisible = false
            val label = boundWidget?.label.orDefaultIfEmpty(itemView.context.getString(R.string.widget_type_video))
            errorViewHint.text = itemView.context.getString(R.string.error_video_player, label)
            errorView.isVisible = true
        }

        override fun createDataSource(): DataSource {
            val dataSource = DefaultHttpDataSource.Factory()
                .setUserAgent(HttpClient.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()

            connection.httpClient.authHeader?.let { dataSource.setRequestProperty("Authorization", it) }
            return dataSource
        }

        override fun onClick(v: View?) {
            boundWidget?.let { loadVideo(it, true) }
        }
    }

    class WebViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : HeavyDataViewHolder(inflater, parent, R.layout.widgetlist_webitem) {
        private val webView = widgetContentView as WebView
        private val progressBar: ContentLoadingProgressBar = itemView.findViewById(R.id.progress_bar)

        @SuppressLint("SetJavaScriptEnabled")
        override fun bindAfterDataSaverCheck(widget: Widget) {
            val url = widget.url?.let {
                connection.httpClient.buildUrl(widget.url)
            }
            with(webView) {
                adjustForWidgetHeight(widget, 0)
                loadUrl(ConnectionWebViewClient.EMPTY_PAGE)

                if (url == null) {
                    return
                }
                setUpForConnection(connection, url) { progress ->
                    if (progress == 100) {
                        progressBar.hide()
                    } else {
                        progressBar.show()
                    }
                    progressBar.progress = progress
                }
                loadUrl(url.toString())
            }
        }
    }

    class ColorViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_coloritem), View.OnClickListener {
        init {
            val buttonCommandInfoList =
                arrayOf(
                    Triple(R.id.up_button, "ON", "INCREASE"),
                    Triple(R.id.down_button, "OFF", "DECREASE")
                )
            for ((id, clickCommand, longClickHoldCommand) in buttonCommandInfoList) {
                val button = itemView.findViewById<PeriodicSignalImageButton>(id)
                button.clickCommand = clickCommand
                button.longClickHoldCommand = longClickHoldCommand
                button.callback = { _, value: String? ->
                    value?.let { connection.httpClient.sendItemCommand(boundWidget?.item, value) }
                }
            }

            val selectColorButton = itemView.findViewById<View>(R.id.select_color_button)
            selectColorButton.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            handleRowClick()
        }

        override fun handleRowClick() {
            val widget = boundWidget ?: return
            bottomSheetPresenter.showBottomSheet(ColorChooserBottomSheet(), widget)
        }
    }

    class MjpegVideoViewHolder internal constructor(
        inflater: LayoutInflater,
        parent: ViewGroup
    ) : HeavyDataViewHolder(inflater, parent, R.layout.widgetlist_videomjpegitem) {
        private val imageView = widgetContentView as ImageView
        private var streamer: MjpegStreamer? = null

        override fun bindAfterDataSaverCheck(widget: Widget) {
            streamer = widget.url?.let { MjpegStreamer(imageView, connection, it) }
        }

        override fun onStart() {
            streamer?.start()
        }

        override fun onStop() {
            streamer?.stop()
        }
    }

    abstract class AbstractMapViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
    ) : LabeledItemBaseViewHolder(inflater, parent, R.layout.widgetlist_mapitem) {
        private val hasPositions
            get() = boundWidget?.item?.state?.asLocation != null || boundWidget?.item?.members?.isNotEmpty() == true

        protected val baseMapView: View = itemView.findViewById(R.id.mapview)
        private val emptyView: LinearLayout = itemView.findViewById(android.R.id.empty)
        private val dataSaverView: View = itemView.findViewById(R.id.data_saver)
        private val dataSaverButton: Button = itemView.findViewById(R.id.data_saver_button)
        private val dataSaverHint: TextView = itemView.findViewById(R.id.data_saver_hint)

        override fun bind(widget: Widget) {
            super.bind(widget)
            baseMapView.adjustForWidgetHeight(widget, 5)
            handleDataSaver(false)
        }

        private fun handleDataSaver(overrideDataSaver: Boolean) {
            val widget = boundWidget ?: return
            val dataSaverActive = !itemView.context.determineDataUsagePolicy(connection).canDoLargeTransfers &&
                !overrideDataSaver

            dataSaverView.isVisible = dataSaverActive && hasPositions
            baseMapView.isVisible = !dataSaverView.isVisible && hasPositions
            emptyView.isVisible = !dataSaverView.isVisible && !hasPositions

            if (dataSaverActive) {
                dataSaverButton.setOnClickListener {
                    handleDataSaver(true)
                }

                dataSaverHint.text = itemView.context.getString(R.string.data_saver_hint,
                    widget.label.orDefaultIfEmpty(itemView.context.getString(R.string.widget_type_mapview)))
            } else {
                dataSaverButton.setOnClickListener(null)
                bindAfterDataSaverCheck(widget)
            }
        }

        fun handleDataUsagePolicyChange() {
            boundWidget?.let { bind(it) }
        }

        override fun handleRowClick() {
            if (hasPositions) {
                openPopup()
            }
        }

        protected abstract fun bindAfterDataSaverCheck(widget: Widget)
        protected abstract fun openPopup()
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
        private const val TYPE_SECTIONSWITCH_SINGLE = 9
        private const val TYPE_ROLLERSHUTTER = 10
        private const val TYPE_SETPOINT = 11
        private const val TYPE_CHART = 12
        private const val TYPE_VIDEO = 13
        private const val TYPE_WEB = 14
        private const val TYPE_COLOR = 15
        private const val TYPE_VIDEO_MJPEG = 16
        private const val TYPE_LOCATION = 17
        private const val TYPE_INVISIBLE = 18
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
    setImageUrl(
        connection,
        widget.icon.toUrl(context, context.determineDataUsagePolicy(connection).loadIconsWithState)
    )
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

fun HttpClient.sendItemCommand(item: Item?, command: String): Job? {
    val url = item?.link ?: return null
    return GlobalScope.launch {
        try {
            post(url, command).close()
            Log.d(WidgetAdapter.TAG, "Command '$command' was sent successfully to $url")
        } catch (e: HttpClient.HttpException) {
            Log.e(WidgetAdapter.TAG, "Sending command $command to $url failed: status ${e.statusCode}", e)
        }
    }
}

