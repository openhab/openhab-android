package org.openhab.habdroid.ui;

import android.content.Context;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.InstrumentationRegistry;

import org.junit.Test;
import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.WidgetAdapter.ColorMapper;

import static junit.framework.Assert.assertEquals;

public class WidgetAdapterTest {
    private Context mContext = InstrumentationRegistry.getTargetContext();

    @Test
    public void testColorMappingDarkTheme() {
        final ColorMapper colorMapper =
                new ColorMapper(new ContextThemeWrapper(mContext, R.style.HABDroid_Basic_ui_dark));
        testMapping(colorMapper, "Map #ffffff", "#ffffff", 0xffffffff);
        testMapping(colorMapper, "Must return \"null\" for invalid colors", "#fffzzz", null);
        testMapping(colorMapper, "Map white => #ffffff in dark themes", "white", 0xffffffff);
        testMapping(colorMapper, "Map red => #ff0000 in dark themes", "red", 0xffff0000);
        testMapping(colorMapper, "Map yellow => #ffff00 in dark themes", "yellow", 0xffffff00);
    }

    @Test
    public void testColorMappingBrightTheme() {
        final ColorMapper colorMapper =
                new ColorMapper(new ContextThemeWrapper(mContext, R.style.HABDroid_Basic_ui));
        testMapping(colorMapper, "Map #ffffff", "#ffffff", 0xffffffff);
        testMapping(colorMapper, "Must return \"null\" for invalid colors", "#fffzzz", null);
        testMapping(colorMapper, "Map white => #000000 in bright themes", "white", 0xff000000);
        testMapping(colorMapper, "Map red => #ff0000 in bright themes", "red", 0xffff0000);
        testMapping(colorMapper, "Map yellow => #fdd835 in bright themes", "yellow", 0xfffdd835);
    }

    private static void testMapping(ColorMapper mapper, String message,
            String value, Integer expected) {
        assertEquals(message, expected != null ? Integer.valueOf(expected) : null,
                mapper.mapColor(value));
    }
}
