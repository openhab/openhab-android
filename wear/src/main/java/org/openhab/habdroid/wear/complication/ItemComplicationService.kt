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

package org.openhab.habdroid.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.NotConfiguredComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import org.json.JSONException
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.ui.WearMainActivity
import org.openhab.habdroid.wear.util.loadItem
import org.openhab.habdroid.wear.wearApplication

/**
 * Shows the state of an item. Tapping toggles switches and opens the app for other items.
 */
class ItemComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val itemName = ComplicationConfigStore(this).getItemName(request.complicationInstanceId)
            ?: return NotConfiguredComplicationData()
        val connection = wearApplication.connectionManager.awaitConnection()?.connection
            ?: return NoDataComplicationData()
        val (item, displayState) = try {
            connection.loadItem(itemName)
        } catch (e: HttpClient.HttpException) {
            Log.w(TAG, "Loading item $itemName failed", e)
            return NoDataComplicationData()
        } catch (e: JSONException) {
            Log.w(TAG, "Parsing item $itemName failed", e)
            return NoDataComplicationData()
        }

        val label = item.label?.takeIf { it.isNotEmpty() } ?: item.name
        val state = displayState?.takeIf { it.isNotEmpty() } ?: item.state?.asString ?: "-"
        val tapAction = buildTapAction(item, request.complicationInstanceId)
        return buildData(request.complicationType, label, state, item, tapAction)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildData(type, getString(R.string.complication_preview_label), "21.5 °C", null, null)

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        ComplicationConfigStore(this).remove(complicationInstanceId)
    }

    private fun buildData(
        type: ComplicationType,
        label: String,
        state: String,
        item: Item?,
        tapAction: PendingIntent?
    ): ComplicationData? {
        val description = "$label: $state".toComplicationText()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(state.toComplicationText(), description)
                .setTitle(label.toComplicationText())
                .setTapAction(tapAction)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(state.toComplicationText(), description)
                .setTitle(label.toComplicationText())
                .setTapAction(tapAction)
                .build()

            ComplicationType.RANGED_VALUE -> {
                val value = if (item ==
                    null
                ) {
                    21.5f
                } else {
                    item.state?.asNumber?.value ?: item.state?.asBrightness?.toFloat()
                }
                if (value == null) {
                    return NoDataComplicationData()
                }
                val min = item?.minimum ?: 0f
                val max = item?.maximum ?: if (item == null) 30f else 100f
                RangedValueComplicationData.Builder(value.coerceIn(min, max), min, max, description)
                    .setText(state.toComplicationText())
                    .setTitle(label.toComplicationText())
                    .setTapAction(tapAction)
                    .build()
            }

            else -> null
        }
    }

    private fun buildTapAction(item: Item, complicationId: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (item.isOfTypeOrGroupType(Item.Type.Switch)) {
            val intent = Intent(this, ComplicationTapReceiver::class.java)
                .putExtra(ComplicationTapReceiver.EXTRA_ITEM_NAME, item.name)
                .putExtra(ComplicationTapReceiver.EXTRA_COMPLICATION_ID, complicationId)
            PendingIntent.getBroadcast(this, complicationId, intent, flags)
        } else {
            val intent = Intent(this, WearMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(this, complicationId, intent, flags)
        }
    }

    private fun String.toComplicationText(): ComplicationText = PlainComplicationText.Builder(this).build()

    companion object {
        private val TAG = ItemComplicationService::class.java.simpleName

        fun requestUpdate(context: Context, vararg complicationIds: Int) {
            val requester = ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, ItemComplicationService::class.java)
            )
            if (complicationIds.isEmpty()) {
                requester.requestUpdateAll()
            } else {
                requester.requestUpdate(*complicationIds)
            }
        }
    }
}
