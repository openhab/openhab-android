package org.openhab.habdroid.ui;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABWidgetAdapter.ColorMapper;
import org.openhab.habdroid.util.Constants;

public class WidgetAdapterTest {
    private Context context = InstrumentationRegistry.getTargetContext();
    @Before
    public void setupTest() {

    }
    @Test
    public void testColorMappingDarkTheme() {
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putString(Constants.PREFERENCE_THEME,
                        context.getString(R.string.theme_value_basic_ui_dark))
                .commit();

        ColorMapper colorMapper = new ColorMapper(context);
        assertEquals(Integer.valueOf(2), colorMapper.mapColor("#ffffff"));
        assertEquals(null, colorMapper.mapColor("#fffzzz"));
        assertEquals(Integer.valueOf(2), colorMapper.mapColor("white"));
        assertEquals(Integer.valueOf(2), colorMapper.mapColor("red"));
        assertEquals(Integer.valueOf(2), colorMapper.mapColor("yellow"));
    }

    @Test
    public void testColorMappingBrightTheme() {
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putString(Constants.PREFERENCE_THEME,
                        context.getString(R.string.theme_value_basic_ui))
                .commit();

        ColorMapper colorMapper = new ColorMapper(context);
        assertEquals(Integer.valueOf(2), colorMapper.mapColor("#ffffff"));
        assertEquals(null, colorMapper.mapColor("#fffzzz"));
        assertEquals(Integer.valueOf(2), colorMapper.mapColor("white"));
        assertEquals(Integer.valueOf(2), colorMapper.mapColor("red"));
        assertEquals(Integer.valueOf(2), colorMapper.mapColor("yellow"));
    }
}
