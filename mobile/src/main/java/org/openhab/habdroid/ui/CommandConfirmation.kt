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
import android.view.LayoutInflater
import androidx.core.view.isGone
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.openhab.habdroid.databinding.BottomSheetCommandConfirmationBinding
import org.openhab.habdroid.model.Widget

/**
 * Runs [onConfirmed] right away if the widget doesn't require command confirmation (`confirmCmd` in the sitemap),
 * otherwise asks the user first. [onCanceled] is invoked if the user dismisses the sheet without confirming.
 */
fun Context.confirmWidgetCommandIfNeeded(widget: Widget, onConfirmed: () -> Unit, onCanceled: () -> Unit = {}) {
    val message = widget.commandConfirmMessage
    if (message == null) {
        onConfirmed()
        return
    }
    var confirmed = false
    val sheet = BottomSheetDialog(this)
    val binding = BottomSheetCommandConfirmationBinding.inflate(LayoutInflater.from(sheet.context))
    binding.title.apply {
        text = widget.label
        isGone = widget.label.isEmpty()
    }
    binding.message.text = message
    binding.cancelButton.setOnClickListener { sheet.dismiss() }
    binding.confirmButton.setOnClickListener {
        confirmed = true
        sheet.dismiss()
        onConfirmed()
    }
    sheet.apply {
        setContentView(binding.root)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        setOnDismissListener {
            if (!confirmed) {
                onCanceled()
            }
        }
        show()
    }
}
