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

package org.openhab.habdroid.ui.preference.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkInfo
import java.text.DateFormat
import org.openhab.habdroid.R
import org.openhab.habdroid.background.ItemUpdateWorker
import org.openhab.habdroid.ui.preference.CustomDialogPreference
import org.openhab.habdroid.util.getPrefixForBgTasks
import org.openhab.habdroid.util.getPrefs

class ItemUpdatingPreference(context: Context, attrs: AttributeSet?) : ItemAndTogglePreference(context, attrs),
    CustomDialogPreference {
    fun startObserving(lifecycleOwner: LifecycleOwner) {
        val infoLiveData = workManager.getWorkInfosByTagLiveData(key)
        infoLiveData.observe(lifecycleOwner) {
            updateSummaryAndIcon()
        }
        updateSummaryAndIcon()
    }

    override fun updateSummaryAndIcon() {
        val value = value ?: return
        val summary = if (value.first) summaryOn else summaryOff
        val prefix = context.getPrefs().getPrefixForBgTasks()
        val lastUpdateSummarySuffix = buildLastUpdateSummary().let { lastUpdate ->
            if (lastUpdate != null) "\n$lastUpdate" else ""
        }
        setSummary(summary.orEmpty().format(prefix + value.second) + lastUpdateSummarySuffix)

        val icon = if (value.first) iconOn else iconOff
        if (icon != null) {
            setIcon(icon)
        }
    }

    private fun buildLastUpdateSummary(): String? {
        if (value?.first != true) {
            return null
        }
        val lastWork = workManager.getWorkInfosByTag(key)
            .get()
            .lastOrNull { workInfo -> workInfo.state == WorkInfo.State.SUCCEEDED }
            ?: return null

        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val ts = lastWork.outputData.getLong(ItemUpdateWorker.OUTPUT_DATA_TIMESTAMP, 0)
        val value = lastWork.outputData.getString(ItemUpdateWorker.OUTPUT_DATA_SENT_VALUE)
        return context.getString(R.string.item_update_summary_success, value, dateFormat.format(ts))
    }
}
