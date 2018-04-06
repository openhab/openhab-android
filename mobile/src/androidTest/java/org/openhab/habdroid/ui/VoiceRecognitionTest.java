package org.openhab.habdroid.ui;

import android.speech.SpeechRecognizer;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assume.assumeTrue;

public class VoiceRecognitionTest extends TestWithoutIntro {
    @Rule
    public ActivityTestRule<OpenHABMainActivity> mActivityTestRule = new ActivityTestRule<>
            (OpenHABMainActivity.class, true, false);

    @Before
    public void checkVoiceRecognitionAvailableOnDevice() {
        assumeTrue("Voice recognition not available, skipping tests for it.",
                SpeechRecognizer.isRecognitionAvailable(InstrumentationRegistry.getTargetContext()));
    }

    @Test
    public void checkVoiceAvailbility () {
        ViewInteraction voice = onView(
                allOf(withId(R.id.mainmenu_voice_recognition), withContentDescription("Voice recognition"), isDisplayed()));
        voice.check(matches(isDisplayed()));
    }

    @Override
    protected boolean preselectSitemap() {
        return true;
    }
}
