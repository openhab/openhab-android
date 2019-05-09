package org.openhab.habdroid;

import android.preference.PreferenceManager;
import androidx.test.InstrumentationRegistry;

import org.openhab.habdroid.util.Constants;

public abstract class TestWithIntro extends ProgressbarAwareTest {
    @Override
    public void setup() {
        PreferenceManager
                .getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext())
                .edit()
                .putString(Constants.INSTANCE.getPREFERENCE_SITEMAP_NAME(), "")
                .putBoolean(Constants.INSTANCE.getPREFERENCE_DEMOMODE(), true)
                .putBoolean(Constants.INSTANCE.getPREFERENCE_FIRST_START(), true)
                .commit();

        super.setup();
    }
}
