package org.openhab.habdroid.ui;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.v7.view.ContextThemeWrapper;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABWidgetAdapter.ColorMapper;

public class WidgetAdapterTest {
    private Context mContext = InstrumentationRegistry.getTargetContext();

    @Test
    public void testColorMappingDarkTheme() {
        ColorMapper colorMapper = new ColorMapper(new ContextThemeWrapper(mContext, R.style.HABDroid_Basic_ui_dark));
        assertEquals("Map #ffffff", Integer.valueOf(0xffffffff), colorMapper.mapColor("#ffffff"));
        assertEquals("Must return \"null\" for invalid colors", null, colorMapper.mapColor("#fffzzz"));
        assertEquals("Map white => #ffffff in dark themes", Integer.valueOf(0xffffffff), colorMapper.mapColor("white"));
        assertEquals("Map red => #ff0000 in dark themes", Integer.valueOf(0xffff0000), colorMapper.mapColor("red"));
        assertEquals("Map yellow => #ffff00 in dark themes", Integer.valueOf(0xffffff00), colorMapper.mapColor("yellow"));
    }

    @Test
    public void testColorMappingBrightTheme() {
        ColorMapper colorMapper = new ColorMapper(new ContextThemeWrapper(mContext, R.style.HABDroid_Basic_ui));
        assertEquals("Map #ffffff", Integer.valueOf(0xffffffff), colorMapper.mapColor("#ffffff"));
        assertEquals("Must return \"null\" for invalid colors", null, colorMapper.mapColor("#fffzzz"));
        assertEquals("Map white => #000000 in bright themes", Integer.valueOf(0xff000000), colorMapper.mapColor("white"));
        assertEquals("Map red => #ff0000 in bright themes", Integer.valueOf(0xffff0000), colorMapper.mapColor("red"));
        assertEquals("Map yellow => #fdd835 in bright themes", Integer.valueOf(0xfffdd835), colorMapper.mapColor("yellow"));
    }
}
