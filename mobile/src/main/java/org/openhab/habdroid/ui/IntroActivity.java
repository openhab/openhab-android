package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;

import org.openhab.habdroid.R;

public class IntroActivity extends AppIntro {
    int colorOpenHABOrange;
    int colorOpenHABOrangeDark;
    int colorGrey;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        colorOpenHABOrange = ContextCompat.getColor(this, R.color.openhab_orange);
        colorOpenHABOrangeDark = ContextCompat.getColor(this, R.color.openhab_orange_dark);
        colorGrey = ContextCompat.getColor(this, R.color.grey_300);

        // Add slides
        addOHSlide(R.string.intro_welcome,
                R.string.intro_whatis,
                R.drawable.ic_openhab_appicon_340dp);
        addOHSlide(R.string.intro_themes,
                R.string.intro_themes_description,
                R.drawable.themes);
        addOHSlide(R.string.intro_discovery,
                R.string.intro_discovery_summary,
                R.drawable.demo_mode);
        // Change bar color
        setBarColor(colorOpenHABOrange);
        setSeparatorColor(colorOpenHABOrangeDark);
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

    /**
     * Add slide with fixed fonts and colors
     * @param title
     * @param description
     * @param imageDrawable
     */
    private void addOHSlide(@StringRes int title, @StringRes int description,
                            @DrawableRes int imageDrawable) {
            addSlide(AppIntroFragment.newInstance(getString(title),
                    null,       /* Title font: null => default */
                    getString(description), /* Description */
                    null,       /* Description font: null => default */
                    imageDrawable,          /* Image */
                    colorGrey,              /* Background color */
                    colorOpenHABOrange,     /* Title color */
                    colorOpenHABOrange));   /* Description color */

    }
}