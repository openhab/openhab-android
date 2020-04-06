/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

package org.openhab.habdroid.util

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.DisplayMetrics
import com.caverock.androidsvg.SVG
import okhttp3.ResponseBody
import java.io.IOException
import java.io.InputStream

@Throws(IOException::class)
fun ResponseBody.toBitmap(targetWidth: Int, targetHeight: Int, enforceSize: Boolean): Bitmap {
    if (!contentType().isSvg()) {
        BitmapFactory.Options().run {
            inJustDecodeBounds = true
            val byteArray = bytes()
            BitmapFactory.decodeStream(byteArray.inputStream(), null, this)

            // Calculate inSampleSize
            inSampleSize = calculateInSampleSize(this, targetWidth, targetHeight)

            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeStream(byteArray.inputStream(), null, this)
                ?: throw IOException("Bitmap decoding failed")

            return if (enforceSize) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)
            } else {
                bitmap
            }
        }
    }

    return byteStream().svgToBitmap(targetWidth)
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {

        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

@Throws(IOException::class)
fun InputStream.svgToBitmap(targetSize: Int): Bitmap {
    return try {
        val svg = SVG.getFromInputStream(this)
        val displayMetrics = Resources.getSystem().displayMetrics
        svg.renderDPI = DisplayMetrics.DENSITY_DEFAULT.toFloat()
        var density: Float? = displayMetrics.density
        svg.setDocumentHeight("100%")
        svg.setDocumentWidth("100%")
        var docWidth = (svg.documentWidth * displayMetrics.density).toInt()
        var docHeight = (svg.documentHeight * displayMetrics.density).toInt()

        if (docWidth < 0 || docHeight < 0) {
            val aspectRatio = svg.documentAspectRatio
            if (aspectRatio > 0) {
                val heightForAspect = targetSize.toFloat() / aspectRatio
                val widthForAspect = targetSize.toFloat() * aspectRatio
                if (widthForAspect < heightForAspect) {
                    docWidth = Math.round(widthForAspect)
                    docHeight = targetSize
                } else {
                    docWidth = targetSize
                    docHeight = Math.round(heightForAspect)
                }
            } else {
                docWidth = targetSize
                docHeight = targetSize
            }

            // we didn't take density into account anymore when calculating docWidth
            // and docHeight, so don't scale with it and just let the renderer
            // figure out the scaling
            density = null
        }

        if (docWidth != targetSize || docHeight != targetSize) {
            val scaleWidth = targetSize.toFloat() / docWidth
            val scaleHeight = targetSize.toFloat() / docHeight
            density = (scaleWidth + scaleHeight) / 2
        }

        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (density != null) {
            canvas.scale(density, density)
        }
        svg.renderToCanvas(canvas)
        bitmap
    } catch (e: Exception) {
        throw IOException("SVG decoding failed", e)
    }
}
