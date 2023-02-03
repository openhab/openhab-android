/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.DynamicColors
import com.google.android.material.internal.EdgeToEdgeUtils
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.getActivityThemeId
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getScreenLockMode
import org.openhab.habdroid.util.hasPermissions
import org.openhab.habdroid.util.resolveThemedColor

abstract class AbstractBaseActivity : AppCompatActivity(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job
    protected open val forceNonFullscreen = false
    private var authPrompt: AuthPrompt? = null
    protected var lastSnackbar: Snackbar? = null
        private set
    private var snackbarQueue = mutableListOf<Snackbar>()
    var appBarLayout: AppBarLayout? = null
        private set

    protected val isFullscreenEnabled: Boolean
        get() = getPrefs().getBoolean(PrefKeys.FULLSCREEN, false)

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getActivityThemeId())
        if (getPrefs().getBoolean(PrefKeys.DYNAMIC_COLORS, false)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)
    }

    @CallSuper
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setNavigationBarColor()

        appBarLayout = findViewById(R.id.appbar_layout)
        appBarLayout?.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(this)
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

    fun enableDrawingBehindStatusBar() {
        EdgeToEdgeUtils.applyEdgeToEdge(window, true)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_content)) { view, insets ->
            view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }
    }

    @Suppress("DEPRECATION") // TODO: Replace deprecated function
    fun setFullscreen(isEnabled: Boolean = isFullscreenEnabled) {
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

    @Suppress("DEPRECATION") // TODO: Replace deprecated function
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
            val nextSnackbar = snackbarQueue.removeFirstOrNull()
            nextSnackbar?.show()
            lastSnackbar = nextSnackbar
        }

        if (tag.isEmpty()) {
            throw IllegalArgumentException("Tag is empty")
        }

        val snackbar = Snackbar.make(findViewById(R.id.coordinator), message, duration)
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

    /**
     * Requests permissions if not already granted. Makes sure to comply with
     *     * Google Play Store policy
     *     * Android R background location permission
     */
    fun requestPermissionsIfRequired(permissions: Array<String>?, requestCode: Int) {
        var permissionsToRequest = permissions
            ?.filter { !hasPermissions(arrayOf(it)) }
            ?.toTypedArray()

        if (permissionsToRequest.isNullOrEmpty() || hasPermissions(permissionsToRequest)) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            permissionsToRequest.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            if (permissionsToRequest.size > 1) {
                Log.d(TAG, "Remove background location from permissions to request")
                permissionsToRequest = permissionsToRequest.toMutableList().apply {
                    remove(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }.toTypedArray()
            } else {
                showSnackbar(
                    PreferencesActivity.SNACKBAR_TAG_BG_TASKS_MISSING_PERMISSION_LOCATION,
                    getString(
                        R.string.settings_background_tasks_permission_denied_background_location,
                        packageManager.backgroundPermissionOptionLabel
                    ),
                    Snackbar.LENGTH_LONG,
                    android.R.string.ok
                ) {
                    Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                        startActivity(this)
                    }
                }
                return
            }
        }

        if (
            permissionsToRequest.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ||
            permissionsToRequest.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
            permissionsToRequest.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            Log.d(TAG, "Show dialog to inform user about location permissions")
            AlertDialog.Builder(this)
                .setMessage(R.string.settings_location_permissions_required)
                .setPositiveButton(R.string.settings_background_tasks_permission_allow) { _, _ ->
                    Log.d(TAG, "Request ${permissionsToRequest.contentToString()} permission")
                    ActivityCompat.requestPermissions(this, permissionsToRequest, requestCode)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    onRequestPermissionsResult(
                        requestCode,
                        permissionsToRequest,
                        intArrayOf(PackageManager.PERMISSION_DENIED)
                    )
                }
                .show()
        } else {
            Log.d(TAG, "Request ${permissionsToRequest.contentToString()} permission")
            ActivityCompat.requestPermissions(this, permissionsToRequest, requestCode)
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
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
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
