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

package org.openhab.habdroid.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.databinding.FragmentAboutLibrariesBinding
import org.openhab.habdroid.databinding.LibraryListItemBinding

class AboutLibrariesFragment : Fragment() {
    private lateinit var binding: FragmentAboutLibrariesBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAboutLibrariesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val libraries = loadLibraries(requireContext())

            binding.list.apply {
                layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
                adapter = LibraryAdapter(layoutInflater, libraries)
                isVisible = true
            }

            binding.progress.isVisible = false
        }
    }

    private suspend fun loadLibraries(context: Context) = withContext(Dispatchers.IO) {
        Libs.Builder()
            .withContext(context)
            .build()
            .libraries
    }

    private class LibraryAdapter(private val inflater: LayoutInflater, private val libraries: List<Library>) :
        RecyclerView.Adapter<LibraryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            LibraryViewHolder(LibraryListItemBinding.inflate(inflater, parent, false))

        override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
            holder.bind(libraries[position])
        }

        override fun getItemCount() = libraries.size
    }

    private class LibraryViewHolder(private val binding: LibraryListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(library: Library) {
            binding.name.text = library.name

            binding.creator.text = library.developers.firstOrNull()?.name
            binding.creator.isVisible = binding.creator.text.isNotEmpty()

            library.description
                ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) }
                .let { binding.description.text = it }
            binding.description.isVisible = binding.description.text.isNotEmpty()
            binding.descriptionDivider.isVisible = binding.description.isVisible

            binding.version.text = library.artifactVersion

            binding.license.text = library.licenses.joinToString(",") { it.name }
            binding.license.isVisible = binding.license.text.isNotEmpty()
            binding.bottomDivider.isVisible = binding.license.isVisible
            binding.license.isClickable = false

            val license = library.licenses.firstOrNull()
            val licenseContent = license?.licenseContent
            when {
                licenseContent?.isNotEmpty() == true -> {
                    binding.license.setOnClickListener {
                        MaterialAlertDialogBuilder(itemView.context)
                            .setMessage(HtmlCompat.fromHtml(licenseContent, HtmlCompat.FROM_HTML_MODE_LEGACY))
                            .show()
                    }
                }

                license?.url != null -> {
                    binding.license.setOnClickListener {
                        val browserIntent = Intent(Intent.ACTION_VIEW, license.url?.toUri())
                        itemView.context.startActivity(browserIntent)
                    }
                }

                else -> {}
            }

            binding.root.isClickable = false
            val website = library.website?.takeIf { it.isNotEmpty() }
                ?: library.scm?.url?.takeIf { it.isNotEmpty() }
            if (website != null) {
                binding.root.setOnClickListener {
                    val browserIntent = Intent(Intent.ACTION_VIEW, website.toUri())
                    itemView.context.startActivity(browserIntent)
                }
            }
        }
    }
}
