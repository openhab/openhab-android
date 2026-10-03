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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.FragmentIntroAuthBinding
import org.openhab.habdroid.ui.IntroActivity
import org.openhab.habdroid.util.getHumanReadableErrorMessage

/**
 * Asks for an API token or username and password, if the server requires authentication.
 */
class IntroAuthFragment : Fragment() {
    private val viewModel: IntroViewModel by activityViewModels()
    private var binding: FragmentIntroAuthBinding? = null

    private val useToken get() = !viewModel.isMyOpenHab && binding?.tabs?.selectedTabPosition == TAB_TOKEN

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentIntroAuthBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = binding ?: return

        if (viewModel.isMyOpenHab) {
            binding.description.setText(R.string.intro_sign_in_myopenhab_description)
            binding.tabs.isVisible = false
            binding.usernameLayout.setHint(R.string.intro_email)
            binding.username.inputType = EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or EditorInfo.TYPE_CLASS_TEXT
            binding.passwordLayout.helperText = null
        } else {
            binding.description.text = getString(R.string.intro_sign_in_description, viewModel.connectedUrl)
            binding.tabs.addTab(binding.tabs.newTab().setText(R.string.intro_api_token))
            binding.tabs.addTab(binding.tabs.newTab().setText(R.string.intro_username_password))
            binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = updateInputVisibility()
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
        updateInputVisibility()

        binding.signIn.setOnClickListener { signIn() }
        binding.changeAddress.setOnClickListener { (requireActivity() as IntroActivity).showServerSelection() }
        val doneListener = { _: View, actionId: Int, _: android.view.KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                signIn()
                true
            } else {
                false
            }
        }
        binding.token.setOnEditorActionListener(doneListener)
        binding.password.setOnEditorActionListener(doneListener)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.probeState.collect { state -> updateState(state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun updateInputVisibility() {
        val binding = binding ?: return
        binding.tokenLayout.isVisible = useToken
        binding.credentials.isVisible = !useToken
    }

    private fun signIn() {
        val binding = binding ?: return
        if (useToken) {
            val token = binding.token.text?.toString()?.trim().orEmpty()
            if (token.isEmpty()) {
                return
            }
            // openHAB accepts API tokens as username with an empty password
            viewModel.signIn(token, null)
        } else {
            val username = binding.username.text?.toString()?.trim().orEmpty()
            if (username.isEmpty()) {
                return
            }
            viewModel.signIn(username, binding.password.text?.toString())
        }
    }

    private fun updateState(state: IntroViewModel.ProbeState) {
        val binding = binding ?: return
        val isSigningIn = state.isRunning && state.usedCredentials
        binding.progress.isVisible = isSigningIn
        binding.signIn.isEnabled = !isSigningIn

        val result = state.result
        val error = when {
            !state.usedCredentials || state.isRunning -> null
            state.credentialsRejected -> getString(R.string.intro_credentials_rejected)
            result is ServerProber.Result.Failed ->
                requireContext().getHumanReadableErrorMessage(result.url, result.statusCode, result.error, false)
            result is ServerProber.Result.NotOpenHab -> getString(R.string.intro_attempt_not_openhab)
            else -> null
        }
        binding.error.isVisible = error != null
        binding.error.text = error
    }

    companion object {
        private const val TAB_TOKEN = 0
    }
}
