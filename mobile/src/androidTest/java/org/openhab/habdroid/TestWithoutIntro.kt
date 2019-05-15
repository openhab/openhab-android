package org.openhab.habdroid

import android.preference.PreferenceManager
import androidx.core.content.edit
import androidx.test.InstrumentationRegistry

import org.junit.Before
import org.openhab.habdroid.util.Constants

abstract class TestWithoutIntro : ProgressbarAwareTest() {
    @Before
    override fun setup() {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext()).edit {
            putString(Constants.PREFERENCE_SITEMAP_NAME, "")
            if (preselectSitemap()) {
                putString(Constants.PREFERENCE_SITEMAP_NAME, "demo")
                putString(Constants.PREFERENCE_SITEMAP_LABEL, "Main Menu")
            }

            putBoolean(Constants.PREFERENCE_DEMOMODE, true)
            putBoolean(Constants.PREFERENCE_FIRST_START, false).commit()
        }

        super.setup()
        setupRegisterIdlingResources()
    }

    protected open fun preselectSitemap(): Boolean {
        return false
    }
}
