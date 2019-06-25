package org.openhab.habdroid.background

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Parcelable
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.android.parcel.Parcelize
import org.openhab.habdroid.R
import org.openhab.habdroid.model.NfcTag
import org.openhab.habdroid.ui.ItemPickerActivity
import org.openhab.habdroid.ui.preference.toItemUpdatePrefValue
import org.openhab.habdroid.util.*
import java.util.*

class BackgroundTasksManager : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive() with intent ${intent.action}")

        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                Log.d(TAG, "Alarm clock changed")
                scheduleWorker(context, Constants.PREFERENCE_ALARM_CLOCK)
            }
            Intent.ACTION_LOCALE_CHANGED -> {
                Log.d(TAG, "Locale changed, recreate notification channels")
                NotificationUpdateObserver.createNotificationChannels(context)
            }
            ACTION_RETRY_UPLOAD -> {
                val retryInfos = intent.getParcelableArrayListExtra<RetryInfo>(EXTRA_RETRY_INFOS)
                for (info in retryInfos) {
                    enqueueItemUpload(info.tag, info.itemName, info.value)
                }
            }
            TaskerIntent.ACTION_QUERY_CONDITION, TaskerIntent.ACTION_FIRE_SETTING -> {
                if (!context.getPrefs().isTaskerPluginEnabled()) {
                    Log.d(TAG, "Tasker plugin is disabled")
                    return
                }
                val bundle = intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE) ?: return
                val itemName = bundle.getString(ItemPickerActivity.EXTRA_ITEM_NAME)
                val state = bundle.getString(ItemPickerActivity.EXTRA_ITEM_STATE)
                if (itemName.isNullOrEmpty() || state.isNullOrEmpty()) {
                    return
                }
                enqueueItemUpload(WORKER_TAG_PREFIX_TASKER + itemName, itemName, state)
            }
        }
    }

    @Parcelize
    internal data class RetryInfo(val tag: String, val itemName: String, val value: String) : Parcelable

    private class PrefsListener constructor(private val context: Context) :
        SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
            when {
                key == Constants.PREFERENCE_DEMOMODE && prefs.isDemoModeEnabled() -> {
                    // demo mode was enabled -> cancel all uploads and clear DB
                    // to clear out notifications
                    with(WorkManager.getInstance()) {
                        cancelAllWorkByTag(WORKER_TAG_ITEM_UPLOADS)
                        pruneWork()
                    }
                }
                key == Constants.PREFERENCE_DEMOMODE && !prefs.isDemoModeEnabled() -> {
                    // demo mode was disabled -> reschedule uploads
                    for (knownKey in KNOWN_KEYS) {
                        scheduleWorker(context, knownKey)
                    }
                }
                key in KNOWN_KEYS -> scheduleWorker(context, key)
            }
        }
    }

    companion object {
        private val TAG = BackgroundTasksManager::class.java.simpleName

        internal const val ACTION_RETRY_UPLOAD = "org.openhab.habdroid.background.action.RETRY_UPLOAD"
        internal const val EXTRA_RETRY_INFOS = "retryInfos"

        private const val WORKER_TAG_ITEM_UPLOADS = "itemUploads"
        const val WORKER_TAG_PREFIX_NFC = "nfc-"
        private const val WORKER_TAG_PREFIX_TASKER = "tasker-"

        internal val KNOWN_KEYS = listOf(
            Constants.PREFERENCE_ALARM_CLOCK
        )
        private val VALUE_GETTER_MAP = HashMap<String, (Context) -> String>()

        // need to keep a ref for this to avoid it being GC'ed
        // (SharedPreferences only keeps a WeakReference)
        private lateinit var prefsListener: PrefsListener

        fun initialize(context: Context) {
            val workManager = WorkManager.getInstance()
            val infoLiveData = workManager.getWorkInfosByTagLiveData(WORKER_TAG_ITEM_UPLOADS)
            infoLiveData.observeForever(NotificationUpdateObserver(context))

            prefsListener = PrefsListener(context.applicationContext)
            context.getPrefs().registerOnSharedPreferenceChangeListener(prefsListener)
        }

        fun enqueueNfcUpdateIfNeeded(context: Context, tag: NfcTag?) {
            if (tag != null && tag.sitemap == null && tag.item != null && tag.state != null) {
                val message = if (tag.label?.isEmpty() == true)
                    context.getString(R.string.nfc_tag_recognized_label, tag.label)
                else
                    context.getString(R.string.nfc_tag_recognized_item, tag.item)
                Util.showToast(context, message)
                enqueueItemUpload(WORKER_TAG_PREFIX_NFC + tag.item, tag.item, tag.state)
            }
        }

        private fun scheduleWorker(context: Context, key: String) {
            val prefs = context.getPrefs()
            val setting = if (prefs.isDemoModeEnabled()) {
                Pair(false, "") // Don't attempt any uploads in demo mode
            } else {
                prefs.getString(key, null).toItemUpdatePrefValue()
            }

            if (!setting.first) {
                WorkManager.getInstance().cancelAllWorkByTag(key)
                return
            }

            val getter = VALUE_GETTER_MAP[key] ?: return

            val prefix = prefs.getString(Constants.PREFERENCE_SEND_DEVICE_INFO_PREFIX)
            enqueueItemUpload(key, prefix + setting.second, getter(context))
        }

        private fun enqueueItemUpload(tag: String, itemName: String, value: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequest.Builder(ItemUpdateWorker::class.java)
                .setConstraints(constraints)
                .addTag(tag)
                .addTag(WORKER_TAG_ITEM_UPLOADS)
                .setInputData(ItemUpdateWorker.buildData(itemName, value))
                .build()

            val workManager = WorkManager.getInstance()
            Log.d(TAG, "Scheduling work for tag $tag")
            workManager.cancelAllWorkByTag(tag)
            workManager.enqueue(workRequest)
        }

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                VALUE_GETTER_MAP[Constants.PREFERENCE_ALARM_CLOCK] = { context ->
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val info = alarmManager.nextAlarmClock
                    (info?.triggerTime ?: 0).toString()
                }
            }
        }
    }
}
