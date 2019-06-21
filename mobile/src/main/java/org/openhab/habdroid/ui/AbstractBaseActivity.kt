package org.openhab.habdroid.ui

import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import org.openhab.habdroid.R
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.getPrefs
import kotlin.coroutines.CoroutineContext

abstract class AbstractBaseActivity : AppCompatActivity(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job
    protected open val forceNonFullscreen = false

    // If we are 4.4 we can use fullscreen mode and Daydream features
    protected val isFullscreenEnabled: Boolean
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            false
        } else getPrefs().getBoolean(Constants.PREFERENCE_FULLSCREEN, false)

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

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
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
        uiOptions = if (isEnabled && !forceNonFullscreen) {
            uiOptions or flags
        } else {
            uiOptions and flags.inv()
        }
        window.decorView.systemUiVisibility = uiOptions
    }
}
