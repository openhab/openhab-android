package org.openhab.habdroid;

import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;

import org.openhab.habdroid.util.Constants;

public abstract class TestWithIntro extends ProgressbarAwareTest {
    @Override
    public void setup() {
        PreferenceManager
                .getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext())
                .edit()
                .putString(Constants.PREFERENCE_SITEMAP_NAME, "")
                .putBoolean(Constants.PREFERENCE_DEMOMODE, true)
                .putBoolean(Constants.PREFERENCE_FIRST_START, true)
                .commit();

        super.setup();
    }
}
