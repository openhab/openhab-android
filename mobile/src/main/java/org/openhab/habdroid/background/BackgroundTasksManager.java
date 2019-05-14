package org.openhab.habdroid.background;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.NfcTag;
import org.openhab.habdroid.ui.widget.ItemUpdatingPreference;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;
import org.openhab.habdroid.ui.ItemPickerActivity;
import org.openhab.habdroid.util.TaskerIntent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class BackgroundTasksManager extends BroadcastReceiver {
    private static final String TAG = BackgroundTasksManager.class.getSimpleName();

    static final String ACTION_RETRY_UPLOAD =
            "org.openhab.habdroid.background.action.RETRY_UPLOAD";
    static final String EXTRA_RETRY_INFOS = "retryInfos";

    private static final String WORKER_TAG_ITEM_UPLOADS = "itemUploads";
    static final String WORKER_TAG_PREFIX_NFC = "nfc-";
    private static final String WORKER_TAG_PREFIX_TASKER = "tasker-";

    static final List<String> KNOWN_KEYS = Arrays.asList(
        Constants.PREFERENCE_ALARM_CLOCK
    );

    private interface ValueGetter {
        String getValue(Context context);
    }
    private static final HashMap<String, ValueGetter> VALUE_GETTER_MAP = new HashMap<>();

    // need to keep a ref for this to avoid it being GC'ed
    // (SharedPreferences only keeps a WeakReference)
    private static PrefsListener sPrefsListener;

    public static void initialize(Context context) {
        final WorkManager workManager = WorkManager.getInstance();
        LiveData<List<WorkInfo>> infoLiveData =
                workManager.getWorkInfosByTagLiveData(WORKER_TAG_ITEM_UPLOADS);
        infoLiveData.observeForever(new NotificationUpdateObserver(context));

        sPrefsListener = new PrefsListener(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(sPrefsListener);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive() with intent " + intent.getAction());

        if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)) {
            Log.d(TAG, "Alarm clock changed");
            scheduleWorker(context, Constants.PREFERENCE_ALARM_CLOCK);
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            Log.d(TAG, "Locale changed, recreate notification channels");
            NotificationUpdateObserver.createNotificationChannels(context);
        } else if (ACTION_RETRY_UPLOAD.equals(action)) {
            List<RetryInfo> retryInfos = intent.getParcelableArrayListExtra(EXTRA_RETRY_INFOS);
            for (RetryInfo info : retryInfos) {
                enqueueItemUpload(info.mTag, info.mItemName, info.mValue);
            }
        } else if (TaskerIntent.ACTION_QUERY_CONDITION.equals(action)
                || TaskerIntent.ACTION_FIRE_SETTING.equals(action)) {
            Bundle bundle = intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE);
            if (bundle == null) {
                return;
            }
            String itemName = bundle.getString(ItemPickerActivity.EXTRA_ITEM_NAME);
            String state = bundle.getString(ItemPickerActivity.EXTRA_ITEM_STATE);
            if (TextUtils.isEmpty(itemName) || TextUtils.isEmpty(state)) {
                return;
            }
            enqueueItemUpload(WORKER_TAG_PREFIX_TASKER + itemName, itemName, state);
        }
    }

    private static void scheduleWorker(Context context, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final Pair<Boolean, String> setting;

        if (prefs.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
            setting = null; // Don't attempt any uploads in demo mode
        } else {
            setting = ItemUpdatingPreference.parseValue(prefs.getString(key, null));
        }

        if (setting == null || !setting.first) {
            WorkManager.getInstance().cancelAllWorkByTag(key);
            return;
        }

        ValueGetter getter = VALUE_GETTER_MAP.get(key);
        if (getter == null) {
            return;
        }

        String prefix = prefs.getString(Constants.PREFERENCE_SEND_DEVICE_INFO_PREFIX, "");
        enqueueItemUpload(key, prefix + setting.second, getter.getValue(context));
    }

    private static void enqueueItemUpload(String tag, String itemName, String value) {
        final Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        final OneTimeWorkRequest workRequest =
                new OneTimeWorkRequest.Builder(ItemUpdateWorker.class)
                .setConstraints(constraints)
                .addTag(tag)
                .addTag(WORKER_TAG_ITEM_UPLOADS)
                .setInputData(ItemUpdateWorker.buildData(itemName, value))
                .build();

        final WorkManager workManager = WorkManager.getInstance();
        Log.d(TAG, "Scheduling work for tag " + tag);
        workManager.cancelAllWorkByTag(tag);
        workManager.enqueue(workRequest);
    }

    public static void enqueueNfcUpdateIfNeeded(Context context, @Nullable NfcTag tag) {
        if (tag != null && tag.sitemap() == null) {
            String message;
            if (TextUtils.isEmpty(tag.label())) {
                message = context.getString(R.string.nfc_tag_recognized_item, tag.item());
            } else {
                message = context.getString(R.string.nfc_tag_recognized_label, tag.label());
            }
            Util.showToast(context, message);
            enqueueItemUpload(WORKER_TAG_PREFIX_NFC + tag.item(), tag.item(), tag.state());
        }
    }

    static class RetryInfo implements Parcelable {
        final String mTag;
        final String mItemName;
        final String mValue;

        RetryInfo(String tag, String itemName, String value) {
            mTag = tag;
            mItemName = itemName;
            mValue = value;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeString(mTag);
            out.writeString(mItemName);
            out.writeString(mValue);
        }

        public static Parcelable.Creator<RetryInfo> CREATOR = new Parcelable.Creator<RetryInfo>() {
            @Override
            public RetryInfo createFromParcel(Parcel in) {
                return new RetryInfo(in.readString(), in.readString(), in.readString());
            }

            @Override
            public RetryInfo[] newArray(int size) {
                return new RetryInfo[size];
            }
        };
    }

    private static class PrefsListener
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Context mContext;

        private PrefsListener(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (Constants.PREFERENCE_DEMOMODE.equals(key)) {
                if (prefs.getBoolean(key, false)) {
                    // demo mode was enabled -> cancel all uploads and clear DB
                    // to clear out notifications
                    final WorkManager wm = WorkManager.getInstance();
                    wm.cancelAllWorkByTag(WORKER_TAG_ITEM_UPLOADS);
                    wm.pruneWork();
                } else {
                    // demo mode was disabled -> reschedule uploads
                    for (String knownKey : KNOWN_KEYS) {
                        scheduleWorker(mContext, knownKey);
                    }
                }
            } else if (KNOWN_KEYS.contains(key)) {
                scheduleWorker(mContext, key);
            }
        }
    }

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            VALUE_GETTER_MAP.put(Constants.PREFERENCE_ALARM_CLOCK, context -> {
                AlarmManager alarmManager =
                        (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                AlarmManager.AlarmClockInfo info = alarmManager.getNextAlarmClock();
                return String.valueOf(info != null ? info.getTriggerTime() : 0);
            });
        }
    }
}
