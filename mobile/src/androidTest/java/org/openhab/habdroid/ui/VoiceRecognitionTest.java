package org.openhab.habdroid.ui;

import android.content.Context;
import android.speech.SpeechRecognizer;
import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.ViewInteraction;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assume.assumeTrue;

public class VoiceRecognitionTest extends TestWithoutIntro {
    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule =
            new ActivityTestRule<>(MainActivity.class, true, false);

    @Before
    public void checkVoiceRecognitionAvailableOnDevice() {
        final Context context = InstrumentationRegistry.getTargetContext();
        assumeTrue("Voice recognition not available, skipping tests for it.",
                SpeechRecognizer.isRecognitionAvailable(context));
    }

    @Test
    public void checkVoiceAvailbility() {
        ViewInteraction voice = onView(allOf(
                withId(R.id.mainmenu_voice_recognition),
                withContentDescription("Voice recognition"),
                isDisplayed()));
        voice.check(matches(isDisplayed()));
    }

    @Override
    protected boolean preselectSitemap() {
        return true;
    }
}
