/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.intro

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.FragmentIntroInfoBinding
import org.openhab.habdroid.databinding.IntroFeatureRowBinding
import org.openhab.habdroid.databinding.IntroInfoPageBinding
import org.openhab.habdroid.ui.IntroActivity
import org.openhab.habdroid.util.resolveThemedColor

/**
 * Pages that introduce openHAB and the app. Server discovery runs in the background meanwhile.
 */
class IntroInfoFragment : Fragment() {
    private val viewModel: IntroViewModel by activityViewModels()
    private var binding: FragmentIntroInfoBinding? = null

    private data class Page(
        @StringRes val title: Int,
        @StringRes val description: Int?,
        @DrawableRes val image: Int,
        val tintImage: Boolean = true,
        val features: List<Pair<Int, Int>> = emptyList()
    )

    private val pages = listOf(
        Page(R.string.intro_welcome, R.string.intro_welcome_description, R.drawable.ic_openhab_appicon_340dp, false),
        Page(
            R.string.intro_integrate,
            R.string.intro_integrate_description,
            R.drawable.ic_power_plug_outline_grey_24dp
        ),
        Page(R.string.intro_automate, R.string.intro_automate_description, R.drawable.ic_calendar_clock_24dp),
        Page(R.string.intro_privacy, R.string.intro_privacy_description, R.drawable.ic_shield_key_outline_grey_24dp),
        Page(
            R.string.intro_app_features,
            null,
            R.drawable.ic_view_dashboard_outline_grey_24dp,
            features = listOf(
                R.drawable.ic_view_dashboard_outline_grey_24dp to R.string.intro_feature_ui,
                R.drawable.ic_microphone_outline_white_24dp to R.string.intro_voice_description,
                R.drawable.ic_nfc_grey_24dp to R.string.intro_feature_nfc,
                R.drawable.ic_card_multiple_outline to R.string.intro_feature_tiles,
                R.drawable.ic_alarm_grey_24dp to R.string.intro_feature_device_info
            )
        )
    )

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding?.pager?.let { it.currentItem = it.currentItem - 1 }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentIntroInfoBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = binding ?: return
        val introActivity = requireActivity() as IntroActivity

        binding.pager.adapter = PageAdapter()
        createIndicatorDots(binding.indicator)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateForPage(position)
            }
        })
        updateForPage(binding.pager.currentItem)

        binding.skip.setOnClickListener { introActivity.showServerSelection() }
        binding.next.setOnClickListener {
            if (binding.pager.currentItem < pages.size - 1) {
                binding.pager.currentItem += 1
            } else {
                introActivity.showServerSelection()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.discoveryState.collect { state -> updateDiscoveryStatus(state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun updateForPage(position: Int) {
        val binding = binding ?: return
        val isLast = position == pages.size - 1
        binding.next.setText(if (isLast) R.string.intro_set_up else R.string.intro_next)
        binding.skip.isVisible = !isLast
        backCallback.isEnabled = position > 0
        val activeColor = requireContext().resolveThemedColor(R.attr.colorPrimary)
        val inactiveColor = requireContext().resolveThemedColor(R.attr.colorOutline)
        for (i in 0 until binding.indicator.childCount) {
            (binding.indicator.getChildAt(i).background as GradientDrawable)
                .setColor(if (i == position) activeColor else inactiveColor)
        }
    }

    private fun createIndicatorDots(container: LinearLayout) {
        val size = resources.getDimensionPixelSize(R.dimen.intro_indicator_size)
        container.removeAllViews()
        repeat(pages.size) {
            val dot = View(requireContext())
            dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            container.addView(
                dot,
                LinearLayout.LayoutParams(size, size).apply { marginStart = size / 2; marginEnd = size / 2 }
            )
        }
    }

    private fun updateDiscoveryStatus(state: IntroViewModel.DiscoveryState) {
        val binding = binding ?: return
        val found = state is IntroViewModel.DiscoveryState.Finished && state.servers.isNotEmpty()
        binding.discoveryProgress.isVisible = state !is IntroViewModel.DiscoveryState.Finished
        binding.discoveryIcon.isVisible = found
        binding.discoveryText.setText(
            when {
                state !is IntroViewModel.DiscoveryState.Finished -> R.string.intro_discovery_running
                found -> R.string.intro_discovery_found
                else -> R.string.intro_discovery_none
            }
        )
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageViewHolder>() {
        override fun getItemCount() = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PageViewHolder(IntroInfoPageBinding.inflate(layoutInflater, parent, false))

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(pages[position])
        }
    }

    private inner class PageViewHolder(private val pageBinding: IntroInfoPageBinding) :
        RecyclerView.ViewHolder(pageBinding.root) {
        fun bind(page: Page) {
            pageBinding.image.setImageResource(page.image)
            pageBinding.image.imageTintList = if (page.tintImage) {
                ColorStateList.valueOf(
                    requireContext().resolveThemedColor(R.attr.colorPrimary)
                )
            } else {
                null
            }
            pageBinding.title.setText(page.title)
            pageBinding.description.isVisible = page.description != null
            page.description?.let { pageBinding.description.setText(it) }

            pageBinding.features.isVisible = page.features.isNotEmpty()
            pageBinding.features.removeAllViews()
            page.features.forEach { (icon, text) ->
                val row = IntroFeatureRowBinding.inflate(layoutInflater, pageBinding.features, true)
                row.icon.setImageResource(icon)
                row.text.setText(text)
            }
        }
    }
}
