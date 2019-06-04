package org.openhab.habdroid.ui;

import android.app.ActivityManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.CallSuper;
import androidx.appcompat.app.AppCompatActivity;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

public abstract class AbstractBaseActivity extends AppCompatActivity {
    private static final String TAG = AbstractBaseActivity.class.getSimpleName();
    private boolean mForceNonFullscreen = false;

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Util.getActivityThemeId(this));
        checkFullscreen();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
            setTaskDescription(new ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    BitmapFactory.decodeResource(getResources(), R.mipmap.icon),
                    typedValue.data));
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkFullscreen();
    }

    /**
     * Activities, that aren't called from an app component directly, e.g. through a third-party app
     * can use this function to avoid being shown in full screen. Must be called before
     * {@link #onCreate(Bundle)}
     */
    protected void forceNonFullscreen() {
        mForceNonFullscreen = true;
    }

    protected void checkFullscreen() {
        checkFullscreen(isFullscreenEnabled());
    }

    protected void checkFullscreen(boolean isEnabled) {
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        final int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (isEnabled && !mForceNonFullscreen) {
            uiOptions |= flags;
        } else {
            uiOptions &= ~flags;
        }
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }

    protected boolean isFullscreenEnabled() {
        // If we are 4.4 we can use fullscreen mode and Daydream features
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return false;
        }
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Constants.PREFERENCE_FULLSCREEN, false);
    }
}
