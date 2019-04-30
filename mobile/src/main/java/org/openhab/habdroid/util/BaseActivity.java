package org.openhab.habdroid.util;

import android.app.ActivityManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import androidx.annotation.CallSuper;
import androidx.appcompat.app.AppCompatActivity;

import org.openhab.habdroid.R;

public class BaseActivity extends AppCompatActivity {
    private static final String TAG = BaseActivity.class.getSimpleName();

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Util.getActivityThemeId(this));
        Util.checkFullscreen(this);

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
        Util.checkFullscreen(this);
    }
}
