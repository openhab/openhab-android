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
                R.drawable.ic_color_lens_orange_340dp);
        addOHSlide(R.string.mainmenu_openhab_voice_recognition,
                R.string.intro_voice_description,
                R.drawable.ic_mic_orange_340dp);
        addOHSlide(R.string.intro_nfc,
                R.string.intro_nfc_description,
                R.drawable.ic_nfc_orange_340dp);
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
                    // Title font: null => default
                    null,
                    getString(description),
                    // Description font: null => default
                    null,
                    imageDrawable,
                    // Background color
                    colorGrey,
                    // Title color
                    ContextCompat.getColor(getApplicationContext(), R.color.black),
                    // Description color
                    ContextCompat.getColor(getApplicationContext(), R.color.black)));

    }
}