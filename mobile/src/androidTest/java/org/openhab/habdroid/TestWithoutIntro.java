package org.openhab.habdroid;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.openhab.habdroid.util.Constants;

public abstract class TestWithoutIntro extends ProgressbarAwareTest {
    @Override
    @Before
    public void setup() {
        SharedPreferences.Editor edit = PreferenceManager
                .getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext())
                .edit();

        edit.putString(Constants.PREFERENCE_SITEMAP_NAME, "");
        if (preselectSitemap()) {
            edit.putString(Constants.PREFERENCE_SITEMAP_NAME, "demo");
            edit.putString(Constants.PREFERENCE_SITEMAP_LABEL, "Main Menu");
        }

        edit.putBoolean(Constants.PREFERENCE_DEMOMODE, true);
        edit.putBoolean(Constants.PREFERENCE_FIRST_START, false).commit();

        super.setup();
    }

    protected boolean preselectSitemap() {
        return false;
    }
}
