package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
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
        addOHSlide(R.string.intro_discovery, R.string.intro_discovery_summary, R.drawable.demo_mode);
        // Change bar color
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            setBarColor(getColor(R.color.openhab_orange));
            setSeparatorColor(getColor(R.color.openhab_orange_dark));
        } else {
            setBarColor(getResources().getColor(R.color.openhab_orange));
            setSeparatorColor(getResources().getColor(R.color.openhab_orange_dark));
        }
    }

    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        finish();
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        finish();
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
        // Do something when the slide changes.
    }

    private void addOHSlide(int title, int description, @DrawableRes int imageDrawable) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            addSlide(AppIntroFragment.newInstance(getString(title), "Helvetica",
                    getString(description), "Helvetica", imageDrawable, getColor(R.color.grey_300),
                    getColor(R.color.openhab_orange), getColor(R.color.openhab_orange)));
        } else {
            addSlide(AppIntroFragment.newInstance(getString(title), "Helvetica",
                    getString(description), "Helvetica", imageDrawable,
                    getResources().getColor(R.color.grey_300),
                    getResources().getColor(R.color.openhab_orange),
                    getResources().getColor(R.color.openhab_orange)));
        }
    }
}