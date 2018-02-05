package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;

import org.openhab.habdroid.R;

public class IntroActivity extends AppIntro {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add slides
        addOHSlide(R.string.intro_welcome,
                R.string.intro_whatis, R.drawable
                        .ic_openhab_appicon_340dp);
        addOHSlide(R.string.intro_themes, R.string.intro_themes_description, R.drawable.themes);
        addOHSlide(R.string.settings_openhab_demomode, R.string.info_demo_mode, R.drawable.demo_mode);
        // Change bar color
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            setBarColor(getColor(R.color.openhab_orange));
            setSeparatorColor(getColor(R.color.openhab_orange_dark));
        } else {
            setBarColor(getResources().getColor(R.color.openhab_orange));
            setSeparatorColor(getResources().getColor(R.color.openhab_orange_dark));
        }
    }

    /**
     * Must be overridden to ensure that the intro will be closed when clicking on "SKIP"
     * @param currentFragment
     */
    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        finish();
    }

    /**
     * Must be overridden to ensure that the intro will be closed when clicking on "DONE"
     * @param currentFragment
     */
    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        finish();
    }

    private void addOHSlide(@StringRes int title, @StringRes int description,
                            @DrawableRes int imageDrawable) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            addSlide(AppIntroFragment.newInstance(getString(title), null,
                    getString(description), null, imageDrawable,
                    getColor(R.color.grey_300),
                    getColor(R.color.openhab_orange),
                    getColor(R.color.openhab_orange)));
        } else {
            addSlide(AppIntroFragment.newInstance(getString(title), null,
                    getString(description), null, imageDrawable,
                    getResources().getColor(R.color.grey_300),
                    getResources().getColor(R.color.openhab_orange),
                    getResources().getColor(R.color.openhab_orange)));
        }
    }
}