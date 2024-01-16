/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
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
import org.openhab.habdroid.util.applyUserSelectedTheme
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getScreenLockMode
import org.openhab.habdroid.util.hasPermissions
import org.openhab.habdroid.util.resolveThemedColor

abstract class AbstractBaseActivity : AppCompatActivity(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job
    protected open val forceNonFullscreen = false
    private var authPrompt: AuthPrompt? = null
    private lateinit var coordinator: CoordinatorLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: View
    lateinit var appBarLayout: AppBarLayout
        private set
    private lateinit var appBarBackground: Drawable
    private lateinit var insetsController: WindowInsetsControllerCompat
    private var lastInsets: WindowInsetsCompat? = null
    protected var lastSnackbar: Snackbar? = null
        private set
    private var snackbarQueue = mutableListOf<Snackbar>()

    var appBarShown = true
        set(value) {
            field = value
            // ScrollingViewBehavior assigns the AppBarLayout height as offset to other views (here: activity content)
            // even if the ABL is set to 'gone', hence we have to do this ugly workaround
            appBarLayout.layoutParams.height = if (value) ViewGroup.LayoutParams.WRAP_CONTENT else 0
            applyPaddingsForWindowInsets()
        }

    protected val isFullscreenEnabled: Boolean
        get() = getPrefs().getBoolean(PrefKeys.FULLSCREEN, false)

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        applyUserSelectedTheme()
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        toolbar = findViewById(R.id.openhab_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        enableDrawingBehindStatusBar()

        coordinator = findViewById(R.id.coordinator)
        content = findViewById(R.id.activity_content)
        insetsController = WindowInsetsControllerCompat(window, coordinator)

        setNavigationBarColor()

        appBarBackground = MaterialShapeDrawable.createWithElevationOverlay(this)
        appBarLayout = findViewById(R.id.appbar_layout)
        appBarLayout.statusBarForeground = appBarBackground
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
        if (isEnabled) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun enableDrawingBehindStatusBar() {
        EdgeToEdgeUtils.applyEdgeToEdge(window, true)
        // Set up a listener to get the window insets so we can apply it to our views. It's important this listener
        // is applied to the toolbar for a combination of reasons:
        // 1) toolbar must be set fitsSystemWindows=true, as otherwise AppBarLayout does its own insets management,
        //    which conflicts with ours
        // 2) if the toolbar is set to fitsSystemWindow=true, it must not consume insets by itself, as otherwise
        //    it applies the insets to its own padding, which we do not want
        // 3) if the activity contains a DrawerLayout, it does also own insets handling, starting with its first child
        // -> Conclusion is that a) we need a listener on toolbar which consumes the insets, and b) we need a listener
        //    on something early in the hierarchy to get the full insets
        // -> Putting the listener on the toolbar fulfills both a) and b)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { _, insets ->
            lastInsets = insets
            applyPaddingsForWindowInsets()
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun applyPaddingsForWindowInsets() {
        // On API levels < 30, insets visibility isn't factored in correctly, so getInsets() returns the
        // status bar and navigation bar insets there even if they're not currently visible due to us enabling
        // fullscreen mode. Work around this by manually checking the fullscreen mode in those cases.
        val insets = if (Build.VERSION.SDK_INT < 30 && isFullscreenEnabled) {
            Insets.NONE
        } else {
            val insetsType = WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.displayCutout()
            lastInsets?.getInsets(insetsType) ?: Insets.NONE
        }
        // AppBarLayout uses its own insets calculations, which doesn't factor in status bar visibility on API < 30
        // (basically the same issue as above). To make sure it doesn't draw the background of the status bar (which
        // it thinks is present) over the actual toolbar, unset the status bar background if we think the status bar
        // is not to be shown.
        appBarLayout.statusBarForeground = if (insets.top > 0) appBarBackground else null
        appBarLayout.updatePadding(top = insets.top)
        content.updatePadding(top = if (appBarShown) 0 else insets.top)
        coordinator.updatePadding(bottom = insets.bottom)
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

        insetsController.isAppearanceLightNavigationBars = currentNightMode != Configuration.UI_MODE_NIGHT_YES
    }

    internal fun showSnackbar(
        tag: String,
        @StringRes messageResId: Int,
        @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_LONG,
        @StringRes actionResId: Int = 0,
        onDismissListener: (() -> Unit)? = null,
        onClickListener: (() -> Unit)? = null
    ) {
        showSnackbar(tag, getString(messageResId), duration, actionResId, onDismissListener, onClickListener)
    }

    protected fun showSnackbar(
        tag: String,
        message: String,
        @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_LONG,
        @StringRes actionResId: Int = 0,
        onDismissListener: (() -> Unit)? = null,
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

        val snackbar = Snackbar.make(coordinator, message, duration)
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
                if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                    onDismissListener?.invoke()
                }
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
    fun requestPermissionsIfRequired(
        permissions: Array<String>?,
        launcher: ActivityResultLauncher<Array<String>>,
        rationaleDialogCancelCallback: (() -> Unit)? = null
    ) {
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
                    Snackbar.LENGTH_INDEFINITE,
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
                    launcher.launch(permissionsToRequest)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    rationaleDialogCancelCallback?.invoke()
                }
                .show()
        } else {
            Log.d(TAG, "Request ${permissionsToRequest.contentToString()} permission")
            launcher.launch(permissionsToRequest)
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
