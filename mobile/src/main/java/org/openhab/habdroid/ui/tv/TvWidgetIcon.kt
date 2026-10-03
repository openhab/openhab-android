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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ImageConversionPolicy

@Composable
fun TvWidgetIcon(icon: IconResource?, connection: Connection, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val sizeInPixels = with(LocalDensity.current) { size.roundToPx() }
    val url = icon?.toUrl(context, true)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url, connection) {
        value = if (url == null) {
            null
        } else {
            try {
                connection.httpClient
                    .get(url, caching = HttpClient.CachingMode.DEFAULT)
                    .asBitmap(sizeInPixels, AndroidColor.WHITE, ImageConversionPolicy.PreferTargetSize)
                    .response
                    .asImageBitmap()
            } catch (e: HttpClient.HttpException) {
                null
            }
        }
    }
    Box(modifier = modifier.size(size)) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}
