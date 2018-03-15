package org.openhab.habdroid.ui;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABWidgetAdapter.ColorMapper;

public class WidgetAdapterTest {
    private Context context = InstrumentationRegistry.getTargetContext();

    @Test
    public void testColorMappingDarkTheme() {
        context.setTheme(R.style.HABDroid_Basic_ui_dark);
        ColorMapper colorMapper = new ColorMapper(context);
        assertEquals(Integer.valueOf(0xffffffff), colorMapper.mapColor("#ffffff"));
        assertEquals(null, colorMapper.mapColor("#fffzzz"));
        assertEquals(Integer.valueOf(0xffffffff), colorMapper.mapColor("white"));
        assertEquals(Integer.valueOf(0xffff0000), colorMapper.mapColor("red"));
        assertEquals(Integer.valueOf(0xffffff00), colorMapper.mapColor("yellow"));
    }

    @Test
    public void testColorMappingBrightTheme() {
        context.setTheme(R.style.HABDroid_Basic_ui);
        ColorMapper colorMapper = new ColorMapper(context);
        assertEquals(Integer.valueOf(0xffffffff), colorMapper.mapColor("#ffffff"));
        assertEquals(null, colorMapper.mapColor("#fffzzz"));
        assertEquals(Integer.valueOf(0xff000000), colorMapper.mapColor("white"));
        assertEquals(Integer.valueOf(0xffff0000), colorMapper.mapColor("red"));
        assertEquals(Integer.valueOf(0xfffdd835), colorMapper.mapColor("yellow"));
    }
}
