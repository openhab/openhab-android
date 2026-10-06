/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui.bridge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable

/**
 * Icons of Main UI's icon fonts (f7:name, material:name), drawn from the bundled fonts. Both fonts
 * map an icon name to its glyph via ligatures.
 */
object IconFontDrawable {
    private const val ICON_SIZE_DP = 24f
    private val typefaces = mutableMapOf<String, Typeface?>()

    fun create(context: Context, icon: String, tint: ColorStateList?): Drawable? {
        val (prefix, name) = icon.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
        val fontAsset = when (prefix) {
            "f7" -> "fonts/Framework7Icons-Regular.ttf"
            "material" -> "fonts/MaterialIcons-Regular.ttf"
            else -> return null
        }
        val typeface = typefaces.getOrPut(fontAsset) {
            try {
                Typeface.createFromAsset(context.assets, fontAsset)
            } catch (e: RuntimeException) {
                null
            }
        } ?: return null

        val size = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size.toFloat()
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "liga"
        }
        // A name the font doesn't know renders as text instead of a single glyph
        if (paint.measureText(name) > size * 1.5f) {
            return null
        }
        val bitmap: Bitmap = createBitmap(size, size)
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(name, size / 2f, baseline, paint)
        val drawable: BitmapDrawable = bitmap.toDrawable(context.resources)
        return DrawableCompat.wrap(drawable).also { DrawableCompat.setTintList(it, tint) }
    }
}
