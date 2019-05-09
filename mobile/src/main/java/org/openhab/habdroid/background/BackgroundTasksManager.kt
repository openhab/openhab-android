package org.openhab.habdroid.background

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Parcelable
import android.preference.PreferenceManager
import android.util.Log

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.android.parcel.Parcelize
import org.openhab.habdroid.R
import org.openhab.habdroid.model.NfcTag

import org.openhab.habdroid.ui.widget.ItemUpdatingPreference
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.Util

import java.util.Arrays
import java.util.HashMap

class BackgroundTasksManager : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive() with intent " + intent.action)

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
        }
    }

    @Parcelize
    internal data class RetryInfo(val tag: String, val itemName: String, val value: String) : Parcelable

    private class PrefsListener constructor(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
        private val context: Context

        init {
            this.context = context.applicationContext
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
            if (Constants.PREFERENCE_DEMOMODE == key) {
                if (prefs.getBoolean(key, false)) {
                    // demo mode was enabled -> cancel all uploads and clear DB
                    // to clear out notifications
                    val wm = WorkManager.getInstance()
                    wm.cancelAllWorkByTag(WORKER_TAG_ITEM_UPLOADS)
                    wm.pruneWork()
                } else {
                    // demo mode was disabled -> reschedule uploads
                    for (knownKey in KNOWN_KEYS) {
                        scheduleWorker(context, knownKey)
                    }
                }
            } else if (KNOWN_KEYS.contains(key)) {
                scheduleWorker(context, key)
            }
        }
    }

    companion object {
        private val TAG = BackgroundTasksManager::class.java.simpleName

        internal val ACTION_RETRY_UPLOAD = "org.openhab.habdroid.background.action.RETRY_UPLOAD"
        internal val EXTRA_RETRY_INFOS = "retryInfos"

        private val WORKER_TAG_ITEM_UPLOADS = "itemUploads"
        val WORKER_TAG_PREFIX_NFC = "nfc-"

        internal val KNOWN_KEYS = Arrays.asList(
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

            prefsListener = PrefsListener(context)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        }

        fun enqueueNfcUpdateIfNeeded(context: Context, tag: NfcTag?) {
            if (tag != null && tag.sitemap == null && tag.item != null && tag.state != null) {
                val message = if (tag.label?.isEmpty() ?: false)
                    context.getString(R.string.nfc_tag_recognized_label, tag.label)
                else
                    context.getString(R.string.nfc_tag_recognized_item, tag.item)
                Util.showToast(context, message);
                enqueueItemUpload(WORKER_TAG_PREFIX_NFC + tag.item, tag.item, tag.state);
            }
        }

        private fun scheduleWorker(context: Context, key: String) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val setting: Pair<Boolean, String>?

            if (prefs.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
                setting = null // Don't attempt any uploads in demo mode
            } else {
                setting = ItemUpdatingPreference.parseValue(prefs.getString(key, null))
            }

            if (setting == null || !setting.first) {
                WorkManager.getInstance().cancelAllWorkByTag(key)
                return
            }

            val getter = VALUE_GETTER_MAP[key] ?: return

            val prefix = prefs.getString(Constants.PREFERENCE_SEND_DEVICE_INFO_PREFIX, "") as String
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
