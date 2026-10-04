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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.FragmentIntroServerSelectBinding
import org.openhab.habdroid.databinding.IntroProbeAttemptItemBinding
import org.openhab.habdroid.databinding.IntroServerItemBinding
import org.openhab.habdroid.ui.IntroActivity
import org.openhab.habdroid.ui.intro.IntroViewModel.AttemptStatus
import org.openhab.habdroid.util.resolveThemedColor

/**
 * Lets the user choose a discovered server or enter the address of a server
 * and shows the progress while trying to connect to it.
 */
class IntroServerSelectFragment : Fragment() {
    private val viewModel: IntroViewModel by activityViewModels()
    private var binding: FragmentIntroServerSelectBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentIntroServerSelectBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = binding ?: return
        val introActivity = requireActivity() as IntroActivity

        val restoredServer = viewModel.restoredServer
        if (restoredServer != null) {
            binding.title.setText(R.string.intro_welcome_back)
            binding.description.setText(R.string.intro_app_restored)
            if (savedInstanceState == null) {
                val restoredUrl = restoredServer.localPath?.url ?: restoredServer.remotePath?.url
                binding.address.setText(restoredUrl)
            }
        }

        binding.address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                connectToEnteredAddress()
                true
            } else {
                false
            }
        }
        binding.address.doAfterTextChanged { binding.addressLayout.error = null }
        binding.connect.setOnClickListener { connectToEnteredAddress() }
        binding.useMyopenhab.setOnClickListener {
            viewModel.connectToMyOpenHab()
            onConnectionStarted()
        }
        binding.tryDemo.setOnClickListener {
            viewModel.enableDemoMode()
            introActivity.finish()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.discoveryState.collect { state -> updateDiscoveredServers(state) } }
                viewModel.probeState.collect { state -> updateProbeState(state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun connectToEnteredAddress() {
        val binding = binding ?: return
        if (viewModel.connectToAddress(binding.address.text?.toString().orEmpty())) {
            onConnectionStarted()
        } else {
            binding.addressLayout.error = getString(R.string.intro_server_address_invalid)
        }
    }

    private fun updateDiscoveredServers(state: IntroViewModel.DiscoveryState) {
        val binding = binding ?: return
        val servers = (state as? IntroViewModel.DiscoveryState.Finished)?.servers.orEmpty()
        binding.discoverySection.isVisible = state != IntroViewModel.DiscoveryState.NotStarted
        binding.discoveryRunning.isVisible = state !is IntroViewModel.DiscoveryState.Finished
        binding.noServers.isVisible = state is IntroViewModel.DiscoveryState.Finished && servers.isEmpty()

        binding.serverList.removeAllViews()
        servers.forEach { server ->
            val item = IntroServerItemBinding.inflate(layoutInflater, binding.serverList, true)
            item.name.text = server.name
            item.host.text = server.host
            item.root.setOnClickListener {
                viewModel.connectToDiscoveredServer(server)
                onConnectionStarted()
            }
        }
    }

    private fun onConnectionStarted() {
        val binding = binding ?: return
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.address.windowToken, 0)
        binding.address.clearFocus()
        binding.root.post { binding.root.smoothScrollTo(0, binding.probeSection.top) }
    }

    private fun updateProbeState(state: IntroViewModel.ProbeState) {
        val binding = binding ?: return
        val failed = !state.isRunning && state.attempts.isNotEmpty() &&
            state.result !is ServerProber.Result.Reachable && state.result !is ServerProber.Result.AuthRequired
        binding.probeSection.isVisible = state.attempts.isNotEmpty()
        binding.probeProgress.isVisible = state.isRunning
        binding.probeFailed.isVisible = failed
        binding.connect.isEnabled = !state.isRunning

        binding.attempts.removeAllViews()
        state.attempts.forEach { attempt ->
            val item = IntroProbeAttemptItemBinding.inflate(layoutInflater, binding.attempts, true)
            item.url.text = attempt.url
            item.progress.isVisible = attempt.status == AttemptStatus.RUNNING
            item.statusIcon.isVisible = attempt.status != AttemptStatus.RUNNING
            item.statusIcon.setImageResource(
                when (attempt.status) {
                    AttemptStatus.SUCCESS -> R.drawable.ic_check_24dp
                    AttemptStatus.FAILED -> R.drawable.ic_clear_themed_24dp
                    else -> 0
                }
            )
            val iconColor = requireContext().resolveThemedColor(
                if (attempt.status == AttemptStatus.FAILED) R.attr.colorError else R.attr.colorPrimary
            )
            item.statusIcon.imageTintList = ColorStateList.valueOf(iconColor)
            val message = when (attempt.status) {
                AttemptStatus.SKIPPED -> getString(R.string.intro_attempt_skipped)
                else -> attempt.message
            }
            item.message.isVisible = message != null
            item.message.text = message
            item.root.alpha = if (attempt.status == AttemptStatus.PENDING || attempt.status == AttemptStatus.SKIPPED) {
                0.5f
            } else {
                1f
            }
        }
    }
}
