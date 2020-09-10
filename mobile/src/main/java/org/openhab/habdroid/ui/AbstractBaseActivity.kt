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

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import org.openhab.habdroid.R
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getScreenLockMode
import org.openhab.habdroid.util.resolveThemedColor

abstract class AbstractBaseActivity : AppCompatActivity(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job
    protected open val forceNonFullscreen = false
    private var authPrompt: AuthPrompt? = null
    protected var lastSnackbar: Snackbar? = null
        private set
    private var snackbarQueue = mutableListOf<Snackbar>()

    protected val isFullscreenEnabled: Boolean
        get() = getPrefs().getBoolean(PrefKeys.FULLSCREEN, false)

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Util.getActivityThemeId(this))

        val colorPrimary = resolveThemedColor(R.attr.colorPrimary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setTaskDescription(ActivityManager.TaskDescription(
                getString(R.string.app_name),
                R.mipmap.icon,
                colorPrimary))
        } else {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(
                getString(R.string.app_name),
                BitmapFactory.decodeResource(resources, R.mipmap.icon),
                colorPrimary))
        }

        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setNavigationBarColor()
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        promptForDevicePasswordIfRequired()
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        setFullscreen()
    }

    fun setFullscreen(isEnabled: Boolean = isFullscreenEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insetsController = window.insetsController ?: return
            insetsController.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isEnabled && !forceNonFullscreen) {
                insetsController.hide(WindowInsets.Type.systemBars())
            } else {
                insetsController.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            fun setFullscreenPreR() {
                var uiOptions = window.decorView.systemUiVisibility
                val flags = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
                uiOptions = if (isEnabled && !forceNonFullscreen) {
                    uiOptions or flags
                } else {
                    uiOptions and flags.inv()
                }
                window.decorView.systemUiVisibility = uiOptions
            }

            setFullscreenPreR()
        }
    }

    private fun setNavigationBarColor() {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        @ColorInt val black = ContextCompat.getColor(this, R.color.black)
        @ColorInt val windowColor = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            resolveThemedColor(android.R.attr.windowBackground, black)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                resolveThemedColor(android.R.attr.windowBackground, black)
            } else {
                black
            }
        }
        window.navigationBarColor = windowColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val flags = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                0
            } else {
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            }
            window.insetsController
                ?.setSystemBarsAppearance(flags, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        } else {
            @Suppress("DEPRECATION")
            fun setNavBarColorPreR() {
                val uiOptions = window.decorView.systemUiVisibility
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    0
                }
                window.decorView.systemUiVisibility = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                    uiOptions and flags.inv()
                } else {
                    uiOptions or flags
                }
            }

            setNavBarColorPreR()
        }
    }

    internal fun showSnackbar(
        tag: String,
        @StringRes messageResId: Int,
        @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_LONG,
        @StringRes actionResId: Int = 0,
        onClickListener: (() -> Unit)? = null
    ) {
        showSnackbar(tag, getString(messageResId), duration, actionResId, onClickListener)
    }

    protected fun showSnackbar(
        tag: String,
        message: String,
        @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_LONG,
        @StringRes actionResId: Int = 0,
        onClickListener: (() -> Unit)? = null
    ) {
        fun showNextSnackbar() {
            if (lastSnackbar?.isShown == true || snackbarQueue.isEmpty()) {
                Log.d(TAG, "No next snackbar to show")
                return
            }
            val nextSnackbar = snackbarQueue.removeAt(0)
            nextSnackbar.show()
            lastSnackbar = nextSnackbar
        }

        if (tag.isEmpty()) {
            throw IllegalArgumentException("Tag is empty")
        }

        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, duration)
        if (actionResId != 0 && onClickListener != null) {
            snackbar.setAction(actionResId) { onClickListener() }
        }
        snackbar.view.tag = tag
        snackbar.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onShown(transientBottomBar: Snackbar?) {
                super.onShown(transientBottomBar)
                Log.d(TAG, "Show snackbar with tag $tag")
            }

            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                showNextSnackbar()
            }
        })
        hideSnackbar(tag)
        Log.d(TAG, "Queue snackbar with tag $tag")
        snackbarQueue.add(snackbar)
        showNextSnackbar()
    }

    protected fun hideSnackbar(tag: String) {
        snackbarQueue.firstOrNull { it.view.tag == tag }?.let { snackbar ->
            Log.d(TAG, "Remove snackbar with tag $tag from queue")
            snackbarQueue.remove(snackbar)
        }
        if (lastSnackbar?.view?.tag == tag) {
            Log.d(TAG, "Hide snackbar with tag $tag")
            lastSnackbar?.dismiss()
            lastSnackbar = null
        }
    }

    private fun promptForDevicePassword() {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) km.isDeviceSecure else km.isKeyguardSecure
        if (locked) {
            authPrompt = AuthPrompt()
            authPrompt?.authenticate()
        }
    }

    internal open fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean {
        return mode != ScreenLockMode.Disabled
    }

    private fun promptForDevicePasswordIfRequired() {
        if (authPrompt != null) {
            return
        }
        if (doesLockModeRequirePrompt(getPrefs().getScreenLockMode(this))) {
            if (timestampNeedsReauth(lastAuthenticationTimestamp)) {
                promptForDevicePassword()
            }
        } else {
            // Reset last authentication timestamp when going from an activity requiring authentication to an
            // activity that does not require authentication, so that the prompt will re-appear when going back
            // to the activity requiring authentication
            lastAuthenticationTimestamp = 0L
        }
    }

    private fun timestampNeedsReauth(ts: Long) =
        ts == 0L || SystemClock.elapsedRealtime() - ts > AUTHENTICATION_VALIDITY_PERIOD

    private inner class AuthPrompt : BiometricPrompt.AuthenticationCallback() {
        private val contentView = findViewById<View>(R.id.activity_content)
        private val prompt = BiometricPrompt(this@AbstractBaseActivity, Dispatchers.Main.asExecutor(), this)

        fun authenticate() {
            val descriptionResId = if (getPrefs().getScreenLockMode(contentView.context) == ScreenLockMode.KioskMode) {
                R.string.screen_lock_unlock_preferences_description
            } else {
                R.string.screen_lock_unlock_screen_description
            }
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setDescription(getString(descriptionResId))
                .setDeviceCredentialAllowed(true)
                .build()
            contentView.isInvisible = true
            prompt.authenticate(info)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            finish()
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            lastAuthenticationTimestamp = SystemClock.elapsedRealtime()
            contentView.isInvisible = false
            authPrompt = null
        }
    }

    companion object {
        private val TAG = AbstractBaseActivity::class.java.simpleName
        private const val AUTHENTICATION_VALIDITY_PERIOD = 2 * 60 * 1000L

        var lastAuthenticationTimestamp = 0L
    }
}
