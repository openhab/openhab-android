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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.FragmentIntroDoneBinding
import org.openhab.habdroid.ui.IntroActivity

/**
 * Shown after the connection to the server has been established successfully.
 */
class IntroDoneFragment : Fragment() {
    private val viewModel: IntroViewModel by activityViewModels()
    private var binding: FragmentIntroDoneBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentIntroDoneBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = binding ?: return
        val url = viewModel.connectedUrl
        val version = viewModel.connectedVersion
        binding.description.text = if (version != null) {
            getString(R.string.intro_connected_description, version, url)
        } else {
            getString(R.string.intro_connected_description_no_version, url)
        }

        if (savedInstanceState == null) {
            binding.name.setText(
                viewModel.restoredServer?.name ?: viewModel.serverName ?: getString(R.string.openhab)
            )
        }

        binding.name.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishSetup()
                true
            } else {
                false
            }
        }
        binding.getStarted.setOnClickListener { finishSetup() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun finishSetup() {
        val binding = binding ?: return
        val name = binding.name.text?.toString()?.trim().orEmpty().ifEmpty { getString(R.string.openhab) }
        viewModel.saveServer(name)
        (requireActivity() as IntroActivity).finishWithNotificationPermission()
    }
}
