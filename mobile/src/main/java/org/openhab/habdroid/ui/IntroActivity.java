package org.openhab.habdroid.ui;

import android.os.Bundle;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import org.openhab.habdroid.R;

public class IntroActivity extends AppIntro {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add slides
        addSlide(R.string.intro_welcome,
                R.string.intro_whatis,
                R.drawable.ic_openhab_appicon_340dp);
        addSlide(R.string.intro_themes,
                R.string.intro_themes_description,
                R.drawable.ic_color_lens_orange_340dp);
        addSlide(R.string.mainmenu_openhab_voice_recognition,
                R.string.intro_voice_description,
                R.drawable.ic_mic_orange_340dp);
        addSlide(R.string.intro_nfc,
                R.string.intro_nfc_description,
                R.drawable.ic_nfc_orange_340dp);

        // Change bar color
        setBarColor(ContextCompat.getColor(this, R.color.openhab_orange));
        setSeparatorColor(ContextCompat.getColor(this, R.color.openhab_orange_dark));
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
    private void addSlide(@StringRes int title, @StringRes int description,
            @DrawableRes int imageDrawable) {
        @ColorInt int greyColor = ContextCompat.getColor(this, R.color.grey_300);
        @ColorInt int blackColor = ContextCompat.getColor(this, R.color.black);

        addSlide(AppIntroFragment.newInstance(getString(title),
                null, // Title font: null => default
                getString(description),
                null, // Description font: null => default
                imageDrawable,
                greyColor, // Background color
                blackColor, // Title color
                // Description color
                ContextCompat.getColor(this, R.color.black))); // Description color
    }
}