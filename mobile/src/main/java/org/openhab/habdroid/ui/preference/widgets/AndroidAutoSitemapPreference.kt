/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.preference.widgets

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.databinding.AutoSitemapListItemBinding
import org.openhab.habdroid.databinding.PrefDialogAndroidAutoBinding
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.ui.preference.CustomDialogPreference
import org.openhab.habdroid.util.getConnectionFactory

class AndroidAutoSitemapPreference(context: Context, attrs: AttributeSet) :
    DialogPreference(context, attrs),
    CustomDialogPreference {
    private var summaryOn: String?
    private val summaryOff: String?

    private var value: SitemapInfo? = null

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ItemAndTogglePreference).apply {
            summaryOn = getString(R.styleable.ItemAndTogglePreference_summaryEnabled)
            summaryOff = getString(R.styleable.ItemAndTogglePreference_summaryDisabled)
            recycle()
        }

        dialogTitle = null
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedString(null)?.toSitemapInfo()
        updateSummary()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? = a.getString(index).toSitemapInfo()

    override fun createDialog(): DialogFragment = PrefDialogFragment.newInstance(key)

    fun setValue(newValue: SitemapInfo?) {
        if (callChangeListener(newValue)) {
            if (shouldPersist()) {
                val persistedValue = newValue?.let { "${it.name}|${it.label}" }
                persistString(persistedValue)
            }
            this.value = newValue
            updateSummary()
        }
    }

    private fun updateSummary() {
        val summary = when (val v = value) {
            null -> summaryOff
            else -> summaryOn?.format(v.label)
        }
        setSummary(summary)
    }

    class PrefDialogFragment :
        PreferenceDialogFragmentCompat(),
        CompoundButton.OnCheckedChangeListener {
        private lateinit var binding: PrefDialogAndroidAutoBinding
        private var sitemapsLoaded = false
        private var sitemapLoadJob: Job? = null
        private var selectedSitemap: SitemapInfo? = null

        override fun onCreateDialogView(context: Context): View {
            val inflater = LayoutInflater.from(activity)
            val pref = preference as AndroidAutoSitemapPreference

            binding = PrefDialogAndroidAutoBinding.inflate(inflater)
            binding.enabled.setOnCheckedChangeListener(this)

            binding.enabled.isChecked = pref.value != null
            onCheckedChanged(binding.enabled, binding.enabled.isChecked)

            return binding.root
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val pref = preference as AndroidAutoSitemapPreference
                if (binding.enabled.isChecked) {
                    pref.setValue(selectedSitemap)
                } else {
                    pref.setValue(null)
                }
            }
        }

        override fun onStart() {
            super.onStart()
            updateOkButtonState()
        }

        override fun onCheckedChanged(button: CompoundButton, checked: Boolean) {
            updateVisibilities(checked)
            if (checked && sitemapLoadJob == null) {
                sitemapLoadJob = lifecycleScope.launch {
                    val sitemapsResult = button.context.getConnectionFactory().primaryFlow
                        .first { it.conn != null }
                        .let { loadSitemaps(it.conn!!) }

                    sitemapsResult.onSuccess { sitemaps ->
                        prepareSitemapRecyclerView(sitemaps)
                        sitemapsLoaded = true
                        updateVisibilities(true)
                        updateOkButtonState()
                    }
                    sitemapsResult.onFailure { cause ->
                        // TODO: error
                    }
                    sitemapLoadJob = null
                }
            } else if (!checked) {
                sitemapLoadJob?.cancel()
                sitemapLoadJob = null
            }
            updateOkButtonState()
        }

        private fun updateVisibilities(enabled: Boolean) {
            binding.progress.isVisible = enabled && !sitemapsLoaded
            binding.sitemapLabel.isVisible = enabled && sitemapsLoaded
            binding.sitemapList.isVisible = enabled && sitemapsLoaded
        }

        private fun prepareSitemapRecyclerView(sitemaps: List<Sitemap>) {
            val prefValue = (preference as AndroidAutoSitemapPreference).value
            val context = binding.sitemapList.context
            val sitemapInfos = sitemaps.map { SitemapInfo(it.name, it.label.ifEmpty { it.name }) }
            selectedSitemap = sitemapInfos.firstOrNull { it.name == prefValue?.name }

            binding.sitemapList.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                adapter = SitemapListAdapter(context, sitemapInfos, selectedSitemap) { item ->
                    selectedSitemap = item
                    updateOkButtonState()
                }
            }
        }

        private suspend fun loadSitemaps(connResult: ConnectionFactory.ConnectionResult) = when {
            connResult.connection != null -> when (val result = ServerProperties.fetch(connResult.connection)) {
                is ServerProperties.Companion.PropsSuccess -> Result.success(result.props.sitemaps)
                is ServerProperties.Companion.PropsFailure -> Result.failure(result.error)
            }

            connResult.failureReason != null -> Result.failure(connResult.failureReason)

            else -> throw IllegalStateException()
        }

        private fun updateOkButtonState() {
            val dialog = this.dialog
            if (dialog is AlertDialog) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                    !binding.enabled.isChecked || selectedSitemap != null
            }
        }

        private class SitemapListAdapter(
            context: Context,
            private val sitemaps: List<SitemapInfo>,
            var selectedItem: SitemapInfo?,
            private val selectionListener: (SitemapInfo) -> Unit
        ) : RecyclerView.Adapter<SitemapViewHolder>() {
            private val inflater = LayoutInflater.from(context)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SitemapViewHolder {
                val binding = AutoSitemapListItemBinding.inflate(inflater, parent, false)
                return SitemapViewHolder(binding)
            }

            override fun onBindViewHolder(holder: SitemapViewHolder, position: Int) {
                val info = sitemaps[position]
                holder.itemView.setOnClickListener {
                    selectedItem = info
                    selectionListener(info)
                    notifyDataSetChanged()
                }
                holder.bind(info, info == selectedItem)
            }

            override fun getItemCount() = sitemaps.size
        }

        private class SitemapViewHolder(private val binding: AutoSitemapListItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            private val background: MaterialShapeDrawable

            init {
                val tintList = ContextCompat.getColorStateList(itemView.context, R.color.list_item_background_tint)
                val radius = itemView.context.resources.getDimension(R.dimen.list_item_background_corner_radius)
                background = MaterialShapeDrawable.createWithElevationOverlay(itemView.context, 0f, tintList)
                background.shapeAppearanceModel = ShapeAppearanceModel.Builder()
                    .setAllCornerSizes(radius)
                    .build()
                binding.root.background = background
            }

            fun bind(info: SitemapInfo, selected: Boolean) {
                binding.root.isSelected = selected
                binding.label.text = info.label
            }
        }

        companion object {
            fun newInstance(key: String): PrefDialogFragment {
                val f = PrefDialogFragment()
                f.arguments = bundleOf(ARG_KEY to key)
                return f
            }
        }
    }

    data class SitemapInfo(val name: String, val label: String)
}

fun String?.toSitemapInfo(): AndroidAutoSitemapPreference.SitemapInfo? {
    val pos = this?.indexOf('|')
    if (pos == null || pos < 0) {
        return null
    }
    return AndroidAutoSitemapPreference.SitemapInfo(substring(0, pos), substring(pos + 1))
}
