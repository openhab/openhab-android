package org.openhab.habdroid.ui

import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity

import org.openhab.habdroid.R
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.Util

abstract class AbstractBaseActivity : AppCompatActivity() {
    // If we are 4.4 we can use fullscreen mode and Daydream features
    protected val isFullscreenEnabled: Boolean
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            false
        } else PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Constants.PREFERENCE_FULLSCREEN, false)

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Util.getActivityThemeId(this))
        checkFullscreen()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val typedValue = TypedValue()
            theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
            setTaskDescription(ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    BitmapFactory.decodeResource(resources, R.mipmap.icon),
                    typedValue.data))
        }

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        checkFullscreen()
    }

    @JvmOverloads
    fun checkFullscreen(isEnabled: Boolean = isFullscreenEnabled) {
        var uiOptions = window.decorView.systemUiVisibility
        val flags = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        if (isEnabled) {
            uiOptions = uiOptions or flags
        } else {
            uiOptions = uiOptions and flags.inv()
        }
        window.decorView.systemUiVisibility = uiOptions
    }
}
