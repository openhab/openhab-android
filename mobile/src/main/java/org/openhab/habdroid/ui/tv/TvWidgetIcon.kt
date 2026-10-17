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
package org.openhab.habdroid.ui.tv

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody.Companion.toResponseBody
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.isSvg
import org.openhab.habdroid.util.toBitmap

private class LoadedIcon(val bitmap: ImageBitmap, val usesContentColor: Boolean)

@Composable
fun TvWidgetIcon(icon: IconResource?, connection: Connection, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val sizeInPixels = with(LocalDensity.current) { size.roundToPx() }
    val url = icon?.toUrl(context, true)
    val loadedIcon by produceState<LoadedIcon?>(initialValue = null, url, connection) {
        value = if (url == null) null else loadIcon(connection, url, sizeInPixels)
    }
    Box(modifier = modifier.size(size)) {
        loadedIcon?.let { loaded ->
            Image(
                bitmap = loaded.bitmap,
                contentDescription = null,
                // Tint while drawing, so the icon follows the content color without being loaded again when focused
                colorFilter = if (loaded.usesContentColor) ColorFilter.tint(LocalContentColor.current) else null,
                modifier = Modifier.size(size)
            )
        }
    }
}

private suspend fun loadIcon(connection: Connection, url: String, sizeInPixels: Int): LoadedIcon? = try {
    val result = connection.httpClient.get(url, caching = HttpClient.CachingMode.DEFAULT)
    val contentType = result.response.contentType()
    val bytes = try {
        withContext(Dispatchers.IO) { result.response.bytes() }
    } finally {
        result.close()
    }
    // Monochrome SVG icons, e.g. from Material Design Icons, are drawn in the current text color
    val usesContentColor = contentType.isSvg() && bytes.decodeToString().contains("currentColor")
    val bitmap = withContext(Dispatchers.IO) {
        bytes.toResponseBody(contentType)
            .toBitmap(sizeInPixels, AndroidColor.WHITE, ImageConversionPolicy.PreferTargetSize)
    }
    LoadedIcon(bitmap.asImageBitmap(), usesContentColor)
} catch (e: HttpClient.HttpException) {
    null
} catch (e: IOException) {
    null
}
