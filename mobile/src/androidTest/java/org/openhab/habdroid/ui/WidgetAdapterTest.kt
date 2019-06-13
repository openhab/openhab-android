package org.openhab.habdroid.ui

import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.InstrumentationRegistry
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.WidgetAdapter.ColorMapper

class WidgetAdapterTest {
    private val context = InstrumentationRegistry.getTargetContext()

    @Test
    fun testColorMappingDarkTheme() {
        val colorMapper = ColorMapper(ContextThemeWrapper(context, R.style.HABDroid_Basic_ui_dark))
        testMapping(colorMapper, "Map #ffffff", "#ffffff", -0x1)
        testMapping(colorMapper, "Must return \"null\" for invalid colors", "#fffzzz", null)
        testMapping(colorMapper, "Map white => #ffffff in dark themes", "white", -0x1)
        testMapping(colorMapper, "Map red => #ff0000 in dark themes", "red", -0x10000)
        testMapping(colorMapper, "Map yellow => #ffff00 in dark themes", "yellow", -0x100)
    }

    @Test
    fun testColorMappingBrightTheme() {
        val colorMapper = ColorMapper(ContextThemeWrapper(context, R.style.HABDroid_Basic_ui))
        testMapping(colorMapper, "Map #ffffff", "#ffffff", -0x1)
        testMapping(colorMapper, "Must return \"null\" for invalid colors", "#fffzzz", null)
        testMapping(colorMapper, "Map white => #000000 in bright themes", "white", -0x1000000)
        testMapping(colorMapper, "Map red => #ff0000 in bright themes", "red", -0x10000)
        testMapping(colorMapper, "Map yellow => #fdd835 in bright themes", "yellow", -0x227cb)
    }

    private fun testMapping(mapper: ColorMapper, message: String, value: String, expected: Int?) {
        assertEquals(message, expected, mapper.mapColor(value))
    }
}
