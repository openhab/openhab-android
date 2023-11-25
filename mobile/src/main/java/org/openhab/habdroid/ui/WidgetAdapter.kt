/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import android.text.InputType.TYPE_CLASS_NUMBER
import android.text.InputType.TYPE_CLASS_TEXT
import android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
import android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.ui.widget.AutoHeightPlayerView
import org.openhab.habdroid.ui.widget.ContextMenuAwareRecyclerView
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.ui.widget.WidgetSlider
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.IconBackground
import org.openhab.habdroid.util.MjpegStreamer
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.beautify
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getChartTheme
import org.openhab.habdroid.util.getIconFallbackColor
import org.openhab.habdroid.util.getImageWidgetScalingType
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.resolveThemedColor

/**
 * This class provides openHAB widgets adapter for list view.
 */

class WidgetAdapter(
    context: Context,
    val serverFlags: Int,
    val connection: Connection,
    private val itemClickListener: ItemClickListener,
    private val fragmentPresenter: FragmentPresenter
) : RecyclerView.Adapter<WidgetAdapter.ViewHolder>(), View.OnClickListener {
    private val items = mutableListOf<Widget>()
    val itemList: List<Widget> get() = items
    private val widgetsById = mutableMapOf<String, Widget>()
    val hasVisibleWidgets: Boolean
        get() = items.any { widget -> shouldShowWidget(widget) }

    private val inflater = LayoutInflater.from(context)
    private val chartTheme: CharSequence = context.getChartTheme(serverFlags)
    private var compactMode = false
    private var selectedPosition = RecyclerView.NO_POSITION
    private var firstVisibleWidgetPosition = RecyclerView.NO_POSITION
    private val colorMapper = ColorMapper(context)

    interface ItemClickListener {
        fun onItemClicked(widget: Widget): Boolean // returns whether click was handled
    }
    interface FragmentPresenter {
        fun showBottomSheet(sheet: AbstractWidgetBottomSheet, widget: Widget)
        fun showSelectionFragment(fragment: DialogFragment, widget: Widget)
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

    fun setCompactMode(compactMode: Boolean) {
        if (compactMode != this.compactMode) {
            this.compactMode = compactMode
            notifyDataSetChanged()
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
        val (actualViewType, compactMode) = fromInternalViewType(viewType)
        val initData = ViewHolderInitData(inflater, parent, compactMode)
        val holder = when (actualViewType) {
            TYPE_GENERICITEM -> GenericViewHolder(initData)
            TYPE_FRAME -> FrameViewHolder(initData)
            TYPE_GROUP -> TextViewHolder(initData)
            TYPE_SWITCH -> SwitchViewHolder(initData)
            TYPE_TEXT -> TextViewHolder(initData)
            TYPE_SLIDER -> SliderViewHolder(initData)
            TYPE_IMAGE -> ImageViewHolder(initData)
            TYPE_SELECTION -> SelectionViewHolder(initData)
            TYPE_SECTIONSWITCH -> SectionSwitchViewHolder(initData)
            TYPE_SECTIONSWITCH_SMALL -> SmallSectionSwitchViewHolder(initData)
            TYPE_ROLLERSHUTTER -> RollerShutterViewHolder(initData)
            TYPE_PLAYER -> PlayerViewHolder(initData)
            TYPE_SETPOINT -> SetpointViewHolder(initData)
            TYPE_CHART -> ChartViewHolder(initData)
            TYPE_VIDEO -> VideoViewHolder(initData)
            TYPE_WEB -> WebViewHolder(initData)
            TYPE_COLOR -> ColorViewHolder(initData)
            TYPE_VIDEO_MJPEG -> MjpegVideoViewHolder(initData)
            TYPE_LOCATION -> MapViewHelper.createViewHolder(initData)
            TYPE_INPUT -> InputViewHolder(initData)
            TYPE_DATETIMEINPUT -> DateTimeInputViewHolder(initData)
            TYPE_INVISIBLE -> InvisibleWidgetViewHolder(initData)
            else -> throw IllegalArgumentException("View type $viewType is not known")
        }

        holder.itemView.tag = holder

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wasStarted = holder.stop()
        holder.vhc = ViewHolderContext(connection, fragmentPresenter, colorMapper, serverFlags, chartTheme)
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
        holder.attach()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.detach()
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
            return toInternalViewType(TYPE_INVISIBLE, compactMode)
        }
        val actualViewType = when (widget.type) {
            Widget.Type.Frame -> TYPE_FRAME
            Widget.Type.Group -> TYPE_GROUP
            Widget.Type.Switch -> when {
                widget.shouldRenderAsPlayer() -> TYPE_PLAYER
                widget.mappingsOrItemOptions.size in 1..2 && !compactMode -> TYPE_SECTIONSWITCH_SMALL
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
            Widget.Type.Input -> if (widget.shouldUseDateTimePickerForInput()) TYPE_DATETIMEINPUT else TYPE_INPUT
            else -> TYPE_GENERICITEM
        }
        return toInternalViewType(actualViewType, compactMode)
    }

    data class ViewHolderInitData(
        val inflater: LayoutInflater,
        val parent: ViewGroup,
        val compactMode: Boolean
    )

    data class ViewHolderContext(
        val connection: Connection,
        val fragmentPresenter: FragmentPresenter,
        val colorMapper: ColorMapper,
        val serverFlags: Int,
        val chartTheme: CharSequence?
    )

    abstract class ViewHolder internal constructor(
        initData: ViewHolderInitData,
        @LayoutRes layoutResId: Int,
        @LayoutRes compactModeLayoutResId: Int = layoutResId
    ) : RecyclerView.ViewHolder(inflateView(initData, layoutResId, compactModeLayoutResId)) {
        internal var scope: CoroutineScope? = null
        internal var vhc: ViewHolderContext? = null
        var started = false
            private set

        protected val connection get() = requireHolderContext().connection
        protected val colorMapper get() = requireHolderContext().colorMapper
        protected val fragmentPresenter get() = requireHolderContext().fragmentPresenter

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
        fun attach() {
            start()
            scope = CoroutineScope(Dispatchers.Main + Job())
        }
        fun detach() {
            stop()
            scope?.cancel()
            scope = null
        }
        open fun onStart() {}
        open fun onStop() {}
        open fun handleRowClick() {}

        protected fun requireHolderContext() = vhc ?: throw IllegalStateException("Holder not bound")

        companion object {
            fun inflateView(
                initData: ViewHolderInitData,
                @LayoutRes layoutResId: Int,
                @LayoutRes compactModeLayoutResId: Int
            ): View {
                val usedLayoutResId = if (initData.compactMode) compactModeLayoutResId else layoutResId
                return initData.inflater.inflate(usedLayoutResId, initData.parent, false)
            }
        }
    }

    abstract class LabeledItemBaseViewHolder internal constructor(
        initData: ViewHolderInitData,
        @LayoutRes layoutResId: Int,
        @LayoutRes compactModeLayoutResId: Int = layoutResId
    ) : ViewHolder(initData, layoutResId, compactModeLayoutResId) {
        protected val labelView: TextView = itemView.findViewById(R.id.widgetlabel)
        protected val valueView: TextView? = itemView.findViewById(R.id.widgetvalue)
        protected val iconView: WidgetImageView = itemView.findViewById(R.id.widgeticon)
        protected var boundWidget: Widget? = null
            private set

        override fun bind(widget: Widget) {
            boundWidget = widget

            labelView.text = widget.label
            labelView.isVisible = widget.label.isNotEmpty()
            labelView.applyWidgetColor(widget.labelColor, colorMapper)
            if (valueView != null) {
                valueView.text = widget.stateFromLabel?.replace("\n", " ")
                valueView.isVisible = !widget.stateFromLabel.isNullOrEmpty()
                valueView.applyWidgetColor(widget.valueColor, colorMapper)
            }
            val showIcon = iconView.context.getPrefs().getBoolean(PrefKeys.SHOW_ICONS, true)
            iconView.isGone = !showIcon
            if (showIcon) {
                iconView.loadWidgetIcon(connection, widget, colorMapper)
            }
        }
    }

    abstract class HeavyDataViewHolder internal constructor(
        initData: ViewHolderInitData,
        @LayoutRes layoutResId: Int,
        @LayoutRes compactModeLayoutResId: Int = layoutResId
    ) : LabeledItemBaseViewHolder(initData, layoutResId, compactModeLayoutResId) {
        protected val widgetContentView: View = itemView.findViewById(R.id.widget_content)
        private val dataSaverView: View = itemView.findViewById(R.id.data_saver)
        private val dataSaverButton: Button = itemView.findViewById(R.id.data_saver_button)
        private val dataSaverHint: TextView = itemView.findViewById(R.id.data_saver_hint)

        override fun bind(widget: Widget) {
            super.bind(widget)
            val showLabelAndIcon = widget.label.isNotEmpty()
                && widget.labelSource == Widget.LabelSource.SitemapDefinition
            labelView.isVisible = showLabelAndIcon
            iconView.isVisible = showLabelAndIcon
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
                    Widget.Type.Mapview -> R.string.widget_type_mapview
                    else -> throw IllegalArgumentException("Cannot show data saver hint for ${widget.type}")
                }

                dataSaverHint.text = itemView.context.getString(
                    R.string.data_saver_hint,
                    widget.label.orDefaultIfEmpty(itemView.context.getString(typeResId))
                )
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

    class GenericViewHolder internal constructor(initData: ViewHolderInitData) :
        ViewHolder(initData, R.layout.widgetlist_genericitem, R.layout.widgetlist_genericitem_compact) {
        private val labelView: TextView = itemView.findViewById(R.id.widgetlabel)
        private val iconView: WidgetImageView = itemView.findViewById(R.id.widgeticon)

        override fun bind(widget: Widget) {
            labelView.text = widget.label
            labelView.applyWidgetColor(widget.labelColor, colorMapper)
            iconView.loadWidgetIcon(connection, widget, colorMapper)
        }
    }

    class InvisibleWidgetViewHolder internal constructor(initData: ViewHolderInitData) :
        ViewHolder(initData, R.layout.widgetlist_invisibleitem) {

        override fun bind(widget: Widget) {
        }
    }

    class FrameViewHolder internal constructor(initData: ViewHolderInitData) :
        ViewHolder(initData, R.layout.widgetlist_frameitem, R.layout.widgetlist_frameitem_compact) {
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

    class SwitchViewHolder internal constructor(initData: ViewHolderInitData) :
        LabeledItemBaseViewHolder(initData, R.layout.widgetlist_switchitem, R.layout.widgetlist_switchitem_compact) {
        private val switch: MaterialSwitch = itemView.findViewById(R.id.toggle)
        private var isBinding = false

        init {
            switch.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) {
                    connection.httpClient.sendItemCommand(boundWidget?.item, if (checked) "ON" else "OFF")
                }
            }
        }

        override fun bind(widget: Widget) {
            isBinding = true
            super.bind(widget)
            switch.isChecked = boundWidget?.item?.state?.asBoolean == true
            switch.thumbIconDrawable = if (boundWidget?.item?.state == null) {
                ContextCompat.getDrawable(switch.context, R.drawable.baseline_question_mark_24)
            } else {
                null
            }

            isBinding = false
        }

        override fun handleRowClick() {
            switch.toggle()
        }
    }

    class InputViewHolder internal constructor(initData: ViewHolderInitData) : LabeledItemBaseViewHolder(
        initData,
        R.layout.widgetlist_inputitem,
        R.layout.widgetlist_inputitem_compact
    ) {
        private val inputTextLayout: TextInputLayout = itemView.findViewById(R.id.widgetinput)
        private val inputText: TextInputEditText = itemView.findViewById(R.id.widgetinputvalue)
        private var isBinding = false
        private var hasChanged = false

        private var updateJob: Job? = null
        private var oldValue: String? = null

        init {
            inputText.doAfterTextChanged { if (!isBinding) hasChanged = true }
            inputText.setOnFocusChangeListener { _, hasFocus ->
                inputText.setKeyboardVisible(hasFocus)
                if (!hasFocus && hasChanged) {
                    inputText.setText(oldValue)
                }
            }
            inputText.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    inputText.setKeyboardVisible(false)
                    if (hasChanged) {
                        updateValue()
                    }
                    true
                } else {
                    false
                }
            }
            // Indicate the UoM unit not being editable
            inputTextLayout.suffixTextView.alpha = 0.5F
        }

        override fun bind(widget: Widget) {
            isBinding = true
            updateJob?.cancel()

            super.bind(widget)

            inputText.inputType = when (widget.inputHint) {
                Widget.InputTypeHint.Number -> TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_DECIMAL or TYPE_NUMBER_FLAG_SIGNED
                else -> TYPE_CLASS_TEXT
            }

            val displayState = widget.stateFromLabel?.replace("\n", "") ?: ""

            val dataState = when {
                widget.state == null -> ""
                widget.inputHint == Widget.InputTypeHint.Number -> widget.state.asNumber?.formatValue()
                displayState.isNotEmpty() -> displayState
                else -> widget.state.asString
            }
            inputText.setText(dataState)
            inputText.text?.let { inputText.setSelection(it.length) }

            inputTextLayout.placeholderText = if (widget.state != null) "" else displayState
            inputTextLayout.suffixText = when (widget.inputHint) {
                Widget.InputTypeHint.Number -> widget.state?.asNumber?.unit
                else -> null
            }

            oldValue = dataState

            inputText.applyWidgetColor(widget.valueColor, colorMapper)
            inputTextLayout.suffixTextView.applyWidgetColor(widget.valueColor, colorMapper)

            isBinding = false
        }

        override fun handleRowClick() {
            inputText.requestFocus()
            inputText.setSelection(inputText.length())
        }

        private fun updateValue() {
            val newValue = inputText.text.toString()
            // We don't have a guarantee that the command to be sent is valid,
            // therefore reset to the old value if no update is received within 1s
            updateJob?.cancel()
            updateJob = scope?.launch {
                delay(1000)
                inputText.setText(oldValue)
            }

            val item = boundWidget?.item
            when {
                item?.isOfTypeOrGroupType(Item.Type.Number) == true ||
                    item?.isOfTypeOrGroupType(Item.Type.NumberWithDimension) == true -> {
                    val state = newValue.let { ParsedState.parseAsNumber(it, item.state?.asNumber?.format) }
                    connection.httpClient.sendItemUpdate(item, state)
                }
                else -> connection.httpClient.sendItemCommand(item, newValue)
            }

            hasChanged = false
        }
    }

    class DateTimeInputViewHolder internal constructor(initData: ViewHolderInitData) : LabeledItemBaseViewHolder(
        initData,
        R.layout.widgetlist_datetimeinputitem,
        R.layout.widgetlist_datetimeinputitem_compact
    ) {
        override fun bind(widget: Widget) {
            super.bind(widget)

            val displayState = widget.stateFromLabel?.replace("\n", "")
            val dateTimeState = widget.state?.asDateTime

            valueView?.text = when {
                !displayState.isNullOrEmpty() -> displayState
                widget.inputHint == Widget.InputTypeHint.Date ->
                    dateTimeState?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                widget.inputHint == Widget.InputTypeHint.Time ->
                    dateTimeState?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                widget.inputHint == Widget.InputTypeHint.Datetime ->
                    dateTimeState?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                else -> dateTimeState?.toString()
            }
            valueView?.isVisible = !valueView?.text.isNullOrEmpty()
        }

        override fun handleRowClick() {
            val widget = boundWidget ?: return
            val dt = widget.state?.asDateTime
            when (widget.inputHint) {
                Widget.InputTypeHint.Date -> showDatePicker(widget, dt, false)
                Widget.InputTypeHint.Datetime -> showDatePicker(widget, dt, true)
                Widget.InputTypeHint.Time -> showTimePicker(widget, dt)
                else -> assert(false) // shouldn't happen, selected at view holder construction time
            }
        }

        private fun showDatePicker(widget: Widget, dt: LocalDateTime?, showTime: Boolean) {
            val date = dt?.truncatedTo(ChronoUnit.MINUTES) ?: LocalDate.now().atStartOfDay()
            val datePicker = MaterialDatePicker.Builder
                .datePicker()
                .setSelection(date.atOffset(ZoneOffset.UTC).toInstant().toEpochMilli())
                .build()
            datePicker.addOnPositiveButtonClickListener {
                val newDate = LocalDateTime
                    .ofInstant(Instant.ofEpochMilli(datePicker.selection ?: 0), ZoneOffset.UTC)
                    .withHour(date.hour)
                    .withMinute(date.minute)
                if (showTime) {
                    showTimePicker(widget, newDate)
                } else {
                    sendUpdate(widget, newDate)
                }
            }
            fragmentPresenter.showSelectionFragment(datePicker, widget)
        }

        private fun showTimePicker(widget: Widget, dt: LocalDateTime?) {
            val date = dt?.truncatedTo(ChronoUnit.MINUTES)
                ?: LocalDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneOffset.UTC)
            val timePicker = MaterialTimePicker.Builder()
                .setHour(date.hour)
                .setMinute(date.minute)
                .build()
            timePicker.addOnPositiveButtonClickListener {
                sendUpdate(widget, date.withHour(timePicker.hour).withMinute(timePicker.minute))
            }
            fragmentPresenter.showSelectionFragment(timePicker, widget)
        }

        private fun sendUpdate(widget: Widget, dateTime: LocalDateTime) {
            connection.httpClient.sendItemUpdate(widget.item, dateTime)
        }
    }

    class TextViewHolder internal constructor(initData: ViewHolderInitData) :
        LabeledItemBaseViewHolder(initData, R.layout.widgetlist_textitem, R.layout.widgetlist_textitem_compact) {
        private val rightArrow: ImageView = itemView.findViewById(R.id.right_arrow)

        override fun bind(widget: Widget) {
            super.bind(widget)
            rightArrow.isGone = widget.linkedPage == null
        }
    }

    class SliderViewHolder internal constructor(initData: ViewHolderInitData) :
        LabeledItemBaseViewHolder(initData, R.layout.widgetlist_slideritem, R.layout.widgetlist_slideritem_compact),
        WidgetSlider.UpdateListener {
        private val slider: WidgetSlider = itemView.findViewById(R.id.seekbar)

        init {
            slider.updateListener = this
        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            labelView.isGone = widget.label.isEmpty()

            val hasValidValues = widget.minValue < widget.maxValue
            slider.isVisible = hasValidValues
            if (hasValidValues) {
                slider.bindToWidget(widget, widget.item?.shouldUseSliderUpdatesDuringMove() == true)
            } else {
                Log.e(TAG, "Slider has invalid values: from '${widget.minValue}' to '${widget.maxValue}'")
            }
        }

        override fun handleRowClick() {
            val widget = boundWidget ?: return
            if (widget.switchSupport) {
                connection.httpClient.sendItemCommand(
                    widget.item,
                    if (slider.value <= widget.minValue) "ON" else "OFF"
                )
            }
        }

        override suspend fun onValueUpdate(value: Float) {
            val widget = boundWidget ?: return
            if (widget.item?.isOfTypeOrGroupType(Item.Type.Color) == true) {
                connection.httpClient.sendItemCommand(widget.item, value.beautify())
            } else {
                connection.httpClient.sendItemUpdate(widget.item, widget.state?.asNumber.withValue(value))
            }
        }
    }

    class ImageViewHolder internal constructor(private val initData: ViewHolderInitData) :
        HeavyDataViewHolder(initData, R.layout.widgetlist_imageitem), View.OnClickListener {
        private val imageView = widgetContentView as WidgetImageView
        private val prefs = imageView.context.getPrefs()

        init {
            imageView.setOnClickListener(this)
        }

        override fun canBindWithoutDataTransfer(widget: Widget): Boolean {
            return widget.url == null ||
                CacheManager.getInstance(itemView.context).isBitmapCached(
                    connection.httpClient.buildUrl(widget.url),
                    imageView.context.getIconFallbackColor(IconBackground.APP_THEME)
                )
        }

        override fun bindAfterDataSaverCheck(widget: Widget) {
            val value = widget.state?.asString

            // Make sure images fit into the content frame by scaling
            // them at max 90% of the available height
            if (initData.parent.height > 0) {
                imageView.maxHeight = (0.9f * initData.parent.height).roundToInt()
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

    class SelectionViewHolder internal constructor(initData: ViewHolderInitData) : LabeledItemBaseViewHolder(
        initData,
        R.layout.widgetlist_selectionitem,
        R.layout.widgetlist_selectionitem_compact
    ) {
        override fun bind(widget: Widget) {
            super.bind(widget)

            val stateString = widget.state?.asString
            val selectedLabel = widget.mappingsOrItemOptions.firstOrNull { mapping -> mapping.value == stateString }
            valueView?.text = selectedLabel?.label ?: stateString
            valueView?.isVisible = valueView?.text.isNullOrEmpty() != true
        }

        override fun handleRowClick() {
            val widget = boundWidget ?: return
            fragmentPresenter.showBottomSheet(SelectionBottomSheet(), widget)
        }
    }

    class SectionSwitchViewHolder internal constructor(private val initData: ViewHolderInitData) :
        LabeledItemBaseViewHolder(
            initData,
            R.layout.widgetlist_sectionswitchitem,
            R.layout.widgetlist_sectionswitchitem_compact
        ),
        View.OnClickListener {
        private val group: MaterialButtonToggleGroup = itemView.findViewById(R.id.switch_group)
        private val overflowButton: MaterialButton = itemView.findViewById(R.id.overflow_button)
        private val spareViews = mutableListOf<View>()
        private val maxButtons = itemView.resources.getInteger(R.integer.section_switch_max_buttons)

        init {
            overflowButton.setOnClickListener {
                val widget = boundWidget ?: return@setOnClickListener
                fragmentPresenter.showBottomSheet(SelectionBottomSheet(), widget)
            }
        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            val hasNoLabelAndValue = labelView.text.isEmpty() && valueView?.text?.isEmpty() != false
            labelView.isGone = hasNoLabelAndValue
            valueView?.isGone = hasNoLabelAndValue

            val mappings = widget.mappingsOrItemOptions
            val buttonCount = min(mappings.size, maxButtons)

            // remove overflow button, so it isn't counted when inflating views
            group.removeView(overflowButton)

            // inflate missing views
            while (spareViews.isNotEmpty() && group.childCount < buttonCount) {
                group.addView(spareViews.removeAt(0))
            }
            while (group.childCount < buttonCount) {
                val buttonLayout = if (initData.compactMode) {
                    R.layout.widgetlist_sectionswitchitem_button_compact
                } else {
                    R.layout.widgetlist_sectionswitchitem_button
                }
                val view = initData.inflater.inflate(buttonLayout, group, false)
                view.setOnClickListener(this)
                group.addView(view)
            }

            // remove unneeded views
            while (group.childCount > buttonCount) {
                val view = group[group.childCount - 1]
                spareViews.add(view)
                group.removeView(view)
            }

            // bind views
            mappings.slice(0 until buttonCount).forEachIndexed { index, mapping ->
                with(group[index] as MaterialButton) {
                    tag = mapping.value
                    setTextAndIcon(connection, mapping)
                }
            }

            // add overflow button if needed
            if (mappings.size > maxButtons) {
                group.addView(overflowButton)
            }

            // check selected view
            val state = widget.state?.asString
            val checkedId = group.children
                .filter { it.id != R.id.overflow_button }
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
            connection.httpClient.sendItemCommand(boundWidget?.item, view.tag as String)
        }

        override fun handleRowClick() {
            if (!group.isVisible) {
                return super.handleRowClick()
            }
            val visibleChildCount = group.children.filter { v -> v.isVisible }.count()
            if (visibleChildCount == 1) {
                onClick(group[0])
            } else if (visibleChildCount == 2) {
                val state = boundWidget?.state?.asString
                if (state == group[0].tag.toString()) {
                    onClick(group[1])
                } else if (state == group[1].tag.toString()) {
                    onClick(group[0])
                }
            }
        }
    }

    class SmallSectionSwitchViewHolder internal constructor(initData: ViewHolderInitData) :
        LabeledItemBaseViewHolder(initData, R.layout.widgetlist_smallsectionswitch_item),
        View.OnClickListener {
        private val toggles = listOf<MaterialButton>(
            itemView.findViewById(R.id.switch_one),
            itemView.findViewById(R.id.switch_two)
        )

        init {
            toggles.forEach { t ->
                t.isCheckable = true
                t.setOnClickListener(this)
            }
        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            val applyMapping = { button: MaterialButton, mapping: LabeledValue? ->
                button.isGone = mapping == null
                if (mapping != null) {
                    button.isChecked = widget.state?.asString == mapping.value
                    button.setTextAndIcon(connection, mapping)
                    button.tag = mapping.value
                }
            }
            applyMapping(toggles[0], widget.mappingsOrItemOptions[0])
            applyMapping(toggles[1], widget.mappingsOrItemOptions.getOrNull(1))
        }

        override fun onClick(view: View) {
            // Make sure one can't uncheck buttons by clicking a checked one
            (view as MaterialButton).isChecked = true
            connection.httpClient.sendItemCommand(boundWidget?.item, view.tag as String)
        }

        override fun handleRowClick() {
            val buttonToSelect = if (toggles[1].isVisible && toggles[1].isChecked) 1 else 0
            toggles[buttonToSelect].callOnClick()
        }
    }

    class RollerShutterViewHolder internal constructor(initData: ViewHolderInitData) :
        LabeledItemBaseViewHolder(
            initData,
            R.layout.widgetlist_rollershutteritem,
            R.layout.widgetlist_rollershutteritem_compact
        ),
        View.OnClickListener,
        View.OnLongClickListener {
        private val upButton = itemView.findViewById<View>(R.id.up_button)
        private val downButton = itemView.findViewById<View>(R.id.down_button)
        data class UpDownButtonState(val item: Item?, val command: String, var inLongPress: Boolean = false)

        init {
            for (b in arrayOf(upButton, downButton)) {
                b.setOnClickListener(this)
                b.setOnLongClickListener(this)
            }
            itemView.findViewById<View>(R.id.stop_button).setOnClickListener {
                connection.httpClient.sendItemCommand(boundWidget?.item, "STOP")
            }
        }

        override fun bind(widget: Widget) {
            // Our long click handling causes the view to be rebound (due to new state),
            // make sure not to clear out our state in that case
            if (widget.item?.name != boundWidget?.item?.name) {
                upButton.tag = UpDownButtonState(widget.item, "UP")
                downButton.tag = UpDownButtonState(widget.item, "DOWN")
            }
            super.bind(widget)
        }

        override fun onClick(view: View) {
            val buttonState = view.tag as UpDownButtonState
            val command = if (buttonState.inLongPress) "STOP" else buttonState.command
            connection.httpClient.sendItemCommand(buttonState.item, command)
            buttonState.inLongPress = false
        }

        override fun onLongClick(view: View): Boolean {
            val buttonState = view.tag as UpDownButtonState
            buttonState.inLongPress = true
            connection.httpClient.sendItemCommand(buttonState.item, buttonState.command)
            return false
        }

        override fun handleRowClick() {
            val widget = boundWidget ?: return
            fragmentPresenter.showBottomSheet(SliderBottomSheet(), widget)
        }
    }

    class PlayerViewHolder internal constructor(initData: ViewHolderInitData) :
        LabeledItemBaseViewHolder(initData, R.layout.widgetlist_playeritem, R.layout.widgetlist_playeritem_compact),
        View.OnClickListener {
        private val prevButton = itemView.findViewById<View>(R.id.prev_button)
        private val nextButton = itemView.findViewById<View>(R.id.next_button)
        private val playPauseButton = itemView.findViewById<View>(R.id.playpause_button)

        init {
            for (b in arrayOf(prevButton, playPauseButton, nextButton)) {
                b.setOnClickListener(this)
            }
            prevButton.tag = "PREVIOUS"
            nextButton.tag = "NEXT"
        }

        override fun bind(widget: Widget) {
            val isPlaying = widget.item?.state?.asString == "PLAY"
            playPauseButton.isActivated = isPlaying
            playPauseButton.contentDescription = itemView.context.getString(
                if (isPlaying) {
                    R.string.content_description_player_pause
                } else {
                    R.string.content_description_player_play
                }
            )
            playPauseButton.tag = if (isPlaying) "PAUSE" else "PLAY"
            super.bind(widget)
        }

        override fun onClick(view: View) {
            val command = view.tag as String
            connection.httpClient.sendItemCommand(boundWidget?.item, command)
        }
    }

    class SetpointViewHolder internal constructor(initData: ViewHolderInitData) : LabeledItemBaseViewHolder(
        initData,
        R.layout.widgetlist_setpointitem,
        R.layout.widgetlist_setpointitem_compact
    ) {
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
            fragmentPresenter.showBottomSheet(SliderBottomSheet(), widget)
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

    class ChartViewHolder internal constructor(private val initData: ViewHolderInitData) :
        HeavyDataViewHolder(initData, R.layout.widgetlist_chartitem), View.OnClickListener {
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
                widget.toChartUrl(prefs, initData.parent.width, chartTheme = theme, density = density) ?: return
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

    class VideoViewHolder internal constructor(initData: ViewHolderInitData) :
        HeavyDataViewHolder(initData, R.layout.widgetlist_videoitem),
        AnalyticsListener,
        DataSource.Factory,
        View.OnClickListener {
        private val playerView = widgetContentView as AutoHeightPlayerView
        private val loadingIndicator: View = itemView.findViewById(R.id.video_player_loading)
        private val errorView: View = itemView.findViewById(R.id.video_player_error)
        private val errorViewHint: TextView = itemView.findViewById(R.id.video_player_error_hint)
        private val errorViewButton: Button = itemView.findViewById(R.id.video_player_error_button)
        private val exoPlayer = ExoPlayer.Builder(itemView.context).build()

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

    class WebViewHolder internal constructor(initData: ViewHolderInitData) :
        HeavyDataViewHolder(initData, R.layout.widgetlist_webitem) {
        private val webView = widgetContentView as WebView
        private val progressBar: ContentLoadingProgressBar = itemView.findViewById(R.id.progress_bar)

        init {
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress == 100) {
                        progressBar.hide()
                    } else {
                        progressBar.show()
                    }
                    progressBar.progress = newProgress
                }
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        override fun bindAfterDataSaverCheck(widget: Widget) {
            val url = widget.url?.let {
                connection.httpClient.buildUrl(widget.url)
            }
            with(webView) {
                adjustForWidgetHeight(widget, 0)
                loadUrl(ConnectionWebViewClient.EMPTY_PAGE)

                if (url != null) {
                    setUpForConnection(connection, url)
                    loadUrl(url.toString())
                }
            }
        }
    }

    class ColorViewHolder internal constructor(initData: ViewHolderInitData) :
        LabeledItemBaseViewHolder(initData, R.layout.widgetlist_coloritem, R.layout.widgetlist_coloritem_compact),
        View.OnClickListener,
        View.OnLongClickListener {
        private val upButton = itemView.findViewById<View>(R.id.up_button)
        private val downButton = itemView.findViewById<View>(R.id.down_button)
        data class UpDownButtonState(
            val item: Item?,
            val shortCommand: String,
            val longCommand: String,
            var repeatJob: Job? = null
        )

        init {
            for (b in arrayOf(upButton, downButton)) {
                b.setOnClickListener(this)
                b.setOnLongClickListener(this)
            }
            val selectColorButton = itemView.findViewById<View>(R.id.select_color_button)
            selectColorButton.setOnClickListener { handleRowClick() }
        }

        override fun bind(widget: Widget) {
            // Our long click handling causes the view to be rebound (due to new state),
            // make sure not to clear out our state in that case
            if (widget.item?.name != boundWidget?.item?.name) {
                (upButton.tag as UpDownButtonState?)?.repeatJob?.cancel()
                (downButton.tag as UpDownButtonState?)?.repeatJob?.cancel()
                upButton.tag = UpDownButtonState(widget.item, "ON", "INCREASE")
                downButton.tag = UpDownButtonState(widget.item, "OFF", "DECREASE")
            }
            super.bind(widget)
        }

        override fun onClick(view: View) {
            val buttonState = view.tag as UpDownButtonState
            val repeater = buttonState.repeatJob
            if (repeater != null) {
                // end of long press
                repeater.cancel()
            } else {
                // short press
                connection.httpClient.sendItemCommand(buttonState.item, buttonState.shortCommand)
            }
            buttonState.repeatJob = null
        }

        override fun onLongClick(view: View): Boolean {
            val buttonState = view.tag as UpDownButtonState
            buttonState.repeatJob = scope?.launch {
                while (isActive) {
                    delay(250)
                    connection.httpClient.sendItemCommand(buttonState.item, buttonState.longCommand)
                }
            }
            return false
        }

        override fun handleRowClick() {
            val widget = boundWidget ?: return
            fragmentPresenter.showBottomSheet(ColorChooserBottomSheet(), widget)
        }
    }

    class MjpegVideoViewHolder internal constructor(initData: ViewHolderInitData) :
        HeavyDataViewHolder(initData, R.layout.widgetlist_videomjpegitem) {
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

    abstract class AbstractMapViewHolder(initData: ViewHolderInitData) :
        HeavyDataViewHolder(initData, R.layout.widgetlist_mapitem) {
        private val hasPositions
            get() = boundWidget?.item?.state?.asLocation != null || boundWidget?.item?.members?.isNotEmpty() == true

        protected val baseMapView: View = itemView.findViewById(R.id.widget_content)
        private val emptyView: LinearLayout = itemView.findViewById(android.R.id.empty)

        override fun bind(widget: Widget) {
            super.bind(widget)
            baseMapView.adjustForWidgetHeight(widget, 5)
            emptyView.isVisible = !hasPositions
        }

        @CallSuper
        override fun bindAfterDataSaverCheck(widget: Widget) {
            emptyView.isVisible = !hasPositions
            baseMapView.isVisible = hasPositions
        }

        override fun handleRowClick() {
            if (hasPositions) {
                openPopup()
            }
        }
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
            colorMap["primary"] = context.resolveThemedColor(R.attr.colorPrimary, 0)
            colorMap["secondary"] = context.resolveThemedColor(R.attr.colorSecondary, 0)

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
        private const val TYPE_SECTIONSWITCH_SMALL = 9
        private const val TYPE_ROLLERSHUTTER = 10
        private const val TYPE_PLAYER = 11
        private const val TYPE_SETPOINT = 12
        private const val TYPE_CHART = 13
        private const val TYPE_VIDEO = 14
        private const val TYPE_WEB = 15
        private const val TYPE_COLOR = 16
        private const val TYPE_VIDEO_MJPEG = 17
        private const val TYPE_LOCATION = 18
        private const val TYPE_INPUT = 19
        private const val TYPE_DATETIMEINPUT = 20
        private const val TYPE_INVISIBLE = 21

        private fun toInternalViewType(viewType: Int, compactMode: Boolean): Int {
            return viewType or (if (compactMode) 0x100 else 0)
        }
        private fun fromInternalViewType(viewType: Int): Pair<Int, Boolean> {
            val compactMode = (viewType and 0x100) != 0
            return Pair(viewType and 0xff, compactMode)
        }
    }
}

fun Widget.shouldRenderAsPlayer(): Boolean {
    // A 'Player' item with 'Default' type is rendered as 'Switch' with predefined mappings
    return type == Widget.Type.Switch &&
        item?.type == Item.Type.Player &&
        mappings.map { m -> m.value } == listOf("PREVIOUS", "PAUSE", "PLAY", "NEXT")
}

fun Widget.shouldUseDateTimePickerForInput(): Boolean {
    if (item?.isOfTypeOrGroupType(Item.Type.DateTime) != true) {
        return false
    }
    return inputHint == Widget.InputTypeHint.Date ||
        inputHint == Widget.InputTypeHint.Time ||
        inputHint == Widget.InputTypeHint.Datetime
}

fun Item.shouldUseSliderUpdatesDuringMove(): Boolean {
    if (
        isOfTypeOrGroupType(Item.Type.Dimmer) ||
        isOfTypeOrGroupType(Item.Type.Number) ||
        isOfTypeOrGroupType(Item.Type.Color)
    ) {
        return true
    }
    if (isOfTypeOrGroupType(Item.Type.NumberWithDimension)) {
        // Allow live updates for percent values, but not for e.g. temperatures
        return state?.asNumber?.unit == "%"
    }

    return false
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

fun HttpClient.sendItemUpdate(item: Item?, state: LocalDateTime?) {
    if (item == null || state == null) {
        return
    }
    if (item.isOfTypeOrGroupType(Item.Type.DateTime)) {
        sendItemCommand(item, state.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
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
