package org.openhab.habdroid

import android.preference.PreferenceManager
import androidx.core.content.edit
import androidx.test.InstrumentationRegistry

import org.openhab.habdroid.util.Constants

abstract class TestWithIntro : ProgressbarAwareTest() {
    override fun setup() {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext()).edit {
            putString(Constants.PREFERENCE_SITEMAP_NAME, "")
            putBoolean(Constants.PREFERENCE_DEMOMODE, true)
            putBoolean(Constants.PREFERENCE_FIRST_START, true)

        }

        super.setup()
    }
}
