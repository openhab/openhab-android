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

package org.openhab.habdroid.ui

import android.os.Bundle
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.util.AsyncServiceResolver
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.addToPrefs
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.resolveThemedColor

class IntroActivity : AppIntro(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Util.getActivityThemeId(this))
        super.onCreate(savedInstanceState)

        if (getPrefs().getBoolean(PrefKeys.RECENTLY_RESTORED, false)) {
            Log.d(TAG, "Show restore intro")
            addSlide(
                R.string.intro_welcome_back,
                R.string.intro_app_restored,
                R.drawable.ic_openhab_appicon_340dp
            )
        } else {
            Log.d(TAG, "Show regular intro")
            addSlide(
                R.string.intro_welcome,
                R.string.intro_whatis,
                R.drawable.ic_openhab_appicon_340dp
            )
            addSlide(
                R.string.mainmenu_openhab_voice_recognition,
                R.string.intro_voice_description,
                R.drawable.ic_twotone_keyboard_voice_themed_340dp
            )
            addSlide(
                R.string.intro_nfc,
                R.string.intro_nfc_description,
                R.drawable.ic_nfc_themed_340dp
            )
            addSlide(
                R.string.tiles_for_quick_settings,
                R.string.intro_quick_tile_description,
                R.drawable.ic_twotone_library_books_themed_340dp

            )
            addSlide(
                R.string.intro_send_device_info,
                R.string.intro_send_device_info_description,
                R.drawable.ic_twotone_access_alarm_themed_340dp

            )

            if (getPrefs().getConfiguredServerIds().isEmpty()) {
                Log.d(TAG, "Starting discovery")
                val resolver = AsyncServiceResolver(
                    this,
                    AsyncServiceResolver.OPENHAB_SERVICE_TYPE,
                    this
                )
                launch {
                    resolver.resolve()?.addToPrefs(this@IntroActivity)
                }
            } else {
                Log.d(TAG, "Don't start discovery, because there's already at least one server configured")
            }
        }

        // Change bar color
        setBarColor(resolveThemedColor(R.attr.colorPrimary))
        setSeparatorColor(resolveThemedColor(R.attr.colorPrimaryDark))
    }

    /**
     * Must be overridden to ensure that the intro will be closed when clicking on "SKIP"
     * @param currentFragment
     */
    override fun onSkipPressed(currentFragment: Fragment?) {
        Log.d(TAG, "onSkipPressed()")
        super.onSkipPressed(currentFragment)
        finish()
    }

    /**
     * Must be overridden to ensure that the intro will be closed when clicking on "DONE"
     * @param currentFragment
     */
    override fun onDonePressed(currentFragment: Fragment?) {
        Log.d(TAG, "onDonePressed()")
        super.onDonePressed(currentFragment)
        finish()
    }

    override fun finish() {
        Log.d(TAG, "finish()")
        getPrefs().edit {
            putBoolean(PrefKeys.FIRST_START, false)
            putBoolean(PrefKeys.RECENTLY_RESTORED, false)
        }
        super.finish()
    }

    /**
     * Add slide with fixed fonts and colors
     * @param title
     * @param description
     * @param imageDrawable
     */
    private fun addSlide(@StringRes title: Int, @StringRes description: Int, @DrawableRes imageDrawable: Int) {
        val colorText = resolveThemedColor(R.attr.colorOnBackground)
        val colorBackground = resolveThemedColor(android.R.attr.colorBackground)

        addSlide(AppIntroFragment.newInstance(getString(title),
            null, // Title font: null => default
            getString(description),
            null, // Description font: null => default
            imageDrawable,
            colorBackground, // Background color
            colorText, // Title color
            colorText)) // Description color
    }

    companion object {
        private val TAG = IntroActivity::class.java.simpleName
    }
}
