/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

package org.openhab.habdroid.background.tiles

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Parcelable
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.background.ItemUpdateWorker
import org.openhab.habdroid.background.tiles.AbstractTileService.Companion.getPrefKeyForId
import org.openhab.habdroid.ui.PreferencesActivity
import org.openhab.habdroid.util.getPrefs

@RequiresApi(Build.VERSION_CODES.N)
abstract class AbstractTileService : TileService() {
    @Suppress("PropertyName") @VisibleForTesting abstract val ID: Int
    private var subtitleUpdateJob: Job? = null
    private val lifeCycleOwner = object : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this).apply {
            handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        override fun getLifecycle(): Lifecycle {
            return lifecycleRegistry
        }

        fun startListening() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun stopListening() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    override fun onStartListening() {
        Log.d(TAG, "onStartListening()")
        qsTile?.let { updateTile(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val workManager = WorkManager.getInstance(applicationContext)
            val infoLiveData =
                workManager.getWorkInfosByTagLiveData(BackgroundTasksManager.WORKER_TAG_PREFIX_TILE_ID + ID)
            infoLiveData.observe(lifeCycleOwner) {
                updateTileSubtitle()
            }

            updateTileSubtitle()
        }
    }

    override fun onStopListening() {
        Log.d(TAG, "onStopListening()")
    }

    override fun onTileAdded() {
        Log.d(TAG, "onTileAdded()")
        qsTile?.let { updateTile(it) }
    }

    override fun onTileRemoved() {
        Log.d(TAG, "onTileRemoved()")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()
        lifeCycleOwner.stopListening()
        lifeCycleOwner.destroy()
    }

    override fun onClick() {
        Log.d(TAG, "onClick()")
        val data = getPrefs().getTileData(ID)
        if (data?.item?.isNotEmpty() == true && data.state.isNotEmpty()) {
            lifeCycleOwner.startListening()
            if (data.requireUnlock && isLocked) {
                unlockAndRun { BackgroundTasksManager.enqueueTileUpdate(this, data, ID) }
            } else {
                BackgroundTasksManager.enqueueTileUpdate(this, data, ID)
            }
        } else {
            Intent(this, PreferencesActivity::class.java).apply {
                action = ACTION_QS_TILE_PREFERENCES
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(this)
            }
        }
    }

    private fun updateTile(tile: Tile) {
        Log.d(TAG, "updateTile()")
        val data = getPrefs().getTileData(ID)

        tile.apply {
            state = Tile.STATE_INACTIVE
            label = data?.tileLabel ?: getString(R.string.tile_number, ID)
            icon = Icon.createWithResource(this@AbstractTileService, getIconRes(applicationContext, data?.icon))
            updateTile()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateTileSubtitle() {
        Log.d(TAG, "updateTileSubtitle()")

        val lastInfo = WorkManager
            .getInstance(applicationContext)
            .getWorkInfosByTag(BackgroundTasksManager.WORKER_TAG_PREFIX_TILE_ID + ID)
            .get()
            .lastOrNull()
        var lastWorkInfoState = lastInfo?.state
        val timestamp = lastInfo?.outputData?.getLong(ItemUpdateWorker.OUTPUT_DATA_TIMESTAMP, 0) ?: 0
        if (lastWorkInfoState?.isFinished == true && timestamp < System.currentTimeMillis() - 5 * 1000) {
            lastWorkInfoState = null
        }
        var updateSubtitleLaterAgain = false
        val statusRes = when (lastWorkInfoState) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> getString(R.string.item_update_short_status_waiting)
            WorkInfo.State.RUNNING -> getString(R.string.item_update_short_status_sending)
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                updateSubtitleLaterAgain = true
                getString(R.string.item_update_short_status_failed)
            }
            WorkInfo.State.SUCCEEDED -> {
                updateSubtitleLaterAgain = true
                ItemUpdateWorker.getShortItemUpdateSuccessMessage(
                    this,
                    lastInfo?.outputData?.getString(ItemUpdateWorker.OUTPUT_DATA_SENT_VALUE).orEmpty()
                )
            }
            null -> ""
        }
        qsTile?.apply {
            subtitle = statusRes
            updateTile()
        }
        if (statusRes.isEmpty()) {
            lifeCycleOwner.stopListening()
        }
        subtitleUpdateJob?.cancel()
        if (updateSubtitleLaterAgain) {
            subtitleUpdateJob = GlobalScope.launch(Dispatchers.Main) {
                delay(6 * 1000)
                updateTileSubtitle()
            }
        }
    }

    companion object {
        private val TAG = AbstractTileService::class.java.simpleName
        const val TILE_COUNT = 12

        fun getPrefKeyForId(id: Int) = "tile_data_$id"

        @DrawableRes fun getIconRes(context: Context, icon: String?): Int = when (icon) {
            context.getString(R.string.tile_icon_bed_value) -> R.drawable.ic_bed_outline_black_24dp
            context.getString(R.string.tile_icon_sofa_value) -> R.drawable.ic_sofa_black_24dp
            context.getString(R.string.tile_icon_bath_value) -> R.drawable.ic_paper_roll_outline_black_24dp
            context.getString(R.string.tile_icon_house_value) -> R.drawable.ic_home_outline_grey_24dp
            context.getString(R.string.tile_icon_tree_value) -> R.drawable.ic_tree_outline_grey_24dp
            context.getString(R.string.tile_icon_light_switch_value) -> R.drawable.ic_light_switch_black_24dp
            context.getString(R.string.tile_icon_bulb_value) -> R.drawable.ic_lightbulb_outline_black_24dp
            context.getString(R.string.tile_icon_lamp_ceiling_value) -> R.drawable.ic_ceiling_light_black_24dp
            context.getString(R.string.tile_icon_lamp_floor_value) -> R.drawable.ic_floor_lamp_black_24dp
            context.getString(R.string.tile_icon_lamp_bedside_value) -> R.drawable.ic_lamp_black_24dp
            context.getString(R.string.tile_icon_lamp_outdoor_value) -> R.drawable.ic_outdoor_lamp_black_24dp
            context.getString(R.string.tile_icon_garage_value) -> R.drawable.ic_garage_black_24dp
            context.getString(R.string.tile_icon_roller_shutter_value) -> R.drawable.ic_window_shutter_black_24dp
            context.getString(R.string.tile_icon_battery_value) -> R.drawable.ic_battery_outline_grey_24dp
            context.getString(R.string.tile_icon_lock_value) -> R.drawable.ic_lock_outline_grey_24dp
            context.getString(R.string.tile_icon_camera_value) -> R.drawable.ic_webcam_black_24dp
            context.getString(R.string.tile_icon_tv_value) -> R.drawable.ic_tv_black_24dp
            context.getString(R.string.tile_icon_radio_value) -> R.drawable.ic_outline_radio_24dp
            context.getString(R.string.tile_icon_wifi_value) -> R.drawable.ic_wifi_strength_outline_grey_24dp
            context.getString(R.string.tile_icon_phone_value) -> R.drawable.ic_phone_outline_grey_24dp
            context.getString(R.string.tile_icon_cloud_upload_value) -> R.drawable.ic_cloud_upload_outline_grey_24dp
            context.getString(R.string.tile_icon_microphone_value) -> R.drawable.ic_microphone_outline_white_24dp
            context.getString(R.string.tile_icon_power_plug_value) -> R.drawable.ic_power_plug_outline_grey_24dp
            context.getString(R.string.tile_icon_color_palette_value) -> R.drawable.ic_palette_outline_grey_24dp
            context.getString(R.string.tile_icon_switch_value) -> R.drawable.ic_power_settings_black_24dp
            context.getString(R.string.tile_icon_earth_value) -> R.drawable.ic_earth_grey_24dp
            context.getString(R.string.tile_icon_star_value) -> R.drawable.ic_star_border_grey_24dp
            context.getString(R.string.tile_icon_clock_value) -> R.drawable.ic_access_time_white_24dp
            context.getString(R.string.tile_icon_alarm_clock_value) -> R.drawable.ic_alarm_grey_24dp
            context.getString(R.string.tile_icon_magnifier_value) -> R.drawable.ic_search_white_24dp
            context.getString(R.string.tile_icon_baby_value) -> R.drawable.ic_baby_black_24dp
            context.getString(R.string.tile_icon_child_value) -> R.drawable.ic_account_child_black_24dp
            context.getString(R.string.tile_icon_man_value) -> R.drawable.ic_face_outline_black_24dp
            context.getString(R.string.tile_icon_woman_value) -> R.drawable.ic_face_woman_outline_black_24dp
            context.getString(R.string.tile_icon_person_value) -> R.drawable.ic_person_outline_grey_24dp
            context.getString(R.string.tile_icon_people_value) -> R.drawable.ic_people_outline_grey_24dp
            context.getString(R.string.tile_icon_chat_value) -> R.drawable.ic_forum_outline_grey_24dp
            context.getString(R.string.tile_icon_settings_value) -> R.drawable.ic_settings_outline_grey_24dp
            context.getString(R.string.tile_icon_shield_value) -> R.drawable.ic_security_grey_24dp
            context.getString(R.string.tile_icon_bell_value) -> R.drawable.ic_bell_outline_grey_24dp
            context.getString(R.string.tile_icon_dashboard_value) -> R.drawable.ic_view_dashboard_outline_grey_24dp
            else -> R.drawable.ic_openhab_appicon_24dp
        }

        fun requestTileUpdate(context: Context, id: Int) {
            val data = context.getPrefs().getTileData(id)
            val tileService = ComponentName(
                context,
                getClassNameForId(id)
            )
            val tileServiceState = if (data != null) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            context.packageManager.setComponentEnabledSetting(
                tileService,
                tileServiceState,
                PackageManager.DONT_KILL_APP
            )
            requestListeningState(context, tileService)
        }

        @VisibleForTesting fun getClassNameForId(id: Int) = "org.openhab.habdroid.background.tiles.TileService$id"
        fun getIdFromClassName(className: String) =
            className.substringAfter("org.openhab.habdroid.background.tiles.TileService").toInt()
    }
}

@Parcelize
data class TileData(
    val item: String,
    val state: String,
    val label: String,
    val tileLabel: String,
    val mappedState: String,
    val icon: String,
    val requireUnlock: Boolean
) : Parcelable {
    fun isValid(): Boolean {
        return item.isNotEmpty() &&
            label.isNotEmpty() &&
            tileLabel.isNotEmpty() &&
            mappedState.isNotEmpty() &&
            icon.isNotEmpty()
    }
}

fun SharedPreferences.getTileData(id: Int): TileData? {
    val tileString = getString(getPrefKeyForId(id), null) ?: return null
    return try {
        val obj = JSONObject(tileString)
        val item = obj.getString("item")
        val state = obj.getString("state")
        val label = obj.getString("label")
        val tileLabel = obj.getString("tileLabel")
        val mappedState = obj.getString("mappedState")
        val icon = obj.getString("icon")
        val requireUnlock = obj.getBoolean("requireUnlock")
        TileData(item, state, label, tileLabel, mappedState, icon, requireUnlock)
    } catch (e: JSONException) {
        null
    }
}

fun SharedPreferences.Editor.putTileData(id: Int, data: TileData?): SharedPreferences.Editor {
    if (data == null) {
        putString(getPrefKeyForId(id), null)
    } else {
        val obj = JSONObject()
            .put("item", data.item)
            .put("state", data.state)
            .put("label", data.label)
            .put("tileLabel", data.tileLabel)
            .put("mappedState", data.mappedState)
            .put("icon", data.icon)
            .put("requireUnlock", data.requireUnlock)
            .toString()
        putString(getPrefKeyForId(id), obj)
    }
    return this
}
