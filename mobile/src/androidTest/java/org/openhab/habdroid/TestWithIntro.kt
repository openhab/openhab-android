package org.openhab.habdroid

import androidx.core.content.edit
import androidx.test.InstrumentationRegistry

import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.getPrefs

abstract class TestWithIntro : ProgressbarAwareTest() {
    override fun setup() {
        InstrumentationRegistry.getTargetContext().getPrefs().edit {
            putString(Constants.PREFERENCE_SITEMAP_NAME, "")
            putBoolean(Constants.PREFERENCE_DEMOMODE, true)
            putBoolean(Constants.PREFERENCE_FIRST_START, true)
        }

        super.setup()
    }
}
