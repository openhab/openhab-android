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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.MjpegInputStream
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.orDefaultIfEmpty

private const val TAG = "TvMediaWidgets"
private val MEDIA_HEIGHT = 240.dp

/**
 * Focusable card for image, chart and video widgets. Clicking it shows the content in full screen.
 */
@Composable
fun TvMediaRow(widget: Widget, connection: Connection, serverFlags: Int) {
    var showFullscreen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    // Use a dark background when focused, as transparent charts use light text
    Surface(
        onClick = { showFullscreen = true },
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            focusedContainerColor = colors.surfaceVariant,
            focusedContentColor = colors.onSurface
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, colors.primary), shape = shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (widget.label.isNotEmpty()) {
                Text(
                    text = widget.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            MediaContent(widget, connection, serverFlags, MEDIA_HEIGHT)
        }
    }
    if (showFullscreen) {
        Dialog(
            onDismissRequest = { showFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                onClick = { showFullscreen = false },
                modifier = Modifier.fillMaxSize(),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black,
                    focusedContainerColor = Color.Black,
                    pressedContainerColor = Color.Black
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MediaContent(widget, connection, serverFlags, null)
                }
            }
        }
    }
}

@Composable
private fun MediaContent(widget: Widget, connection: Connection, serverFlags: Int, height: Dp?) {
    val modifier = if (height != null) Modifier.fillMaxWidth().height(height) else Modifier.fillMaxSize()
    when {
        widget.type == Widget.Type.Chart -> ChartContent(widget, connection, serverFlags, modifier)

        widget.type == Widget.Type.Video && "mjpeg".equals(widget.encoding, ignoreCase = true) ->
            MjpegContent(widget, connection, modifier)

        widget.type == Widget.Type.Video -> VideoContent(widget, connection, modifier)

        else -> ImageContent(widget, connection, modifier)
    }
}

@Composable
private fun ImageContent(widget: Widget, connection: Connection, modifier: Modifier) {
    val state = widget.state?.asString
    if (state != null && state.matches("data:image/.*;base64,.*".toRegex())) {
        val bitmap = remember(state) {
            val data = Base64.decode(state.substring(state.indexOf(",") + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        }
        BitmapContent(bitmap, modifier)
    } else {
        BoxWithConstraints(modifier = modifier) {
            val bitmap = rememberRemoteBitmap(widget.url, connection, constraints.maxWidth, widget.refresh)
            BitmapContent(bitmap, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ChartContent(widget: Widget, connection: Connection, serverFlags: Int, modifier: Modifier) {
    val context = LocalContext.current
    // TV always uses a dark theme
    val chartTheme = if (serverFlags and ServerProperties.SERVER_FLAG_TRANSPARENT_CHARTS == 0) {
        "dark"
    } else {
        "dark_transparent"
    }
    BoxWithConstraints(modifier = modifier) {
        val url = widget.toChartUrl(
            context.getPrefs(),
            constraints.maxWidth,
            height = constraints.maxHeight,
            chartTheme = chartTheme,
            density = context.resources.configuration.densityDpi
        )
        val bitmap = rememberRemoteBitmap(url, connection, constraints.maxWidth, widget.refresh)
        BitmapContent(bitmap, Modifier.fillMaxSize())
    }
}

@Composable
private fun BitmapContent(bitmap: ImageBitmap?, modifier: Modifier) {
    Box(modifier = modifier) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Loads a bitmap and reloads it every [refreshMs] milliseconds, if set
 */
@Composable
private fun rememberRemoteBitmap(
    url: String?,
    connection: Connection,
    sizeInPixels: Int,
    refreshMs: Int
): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url, connection, sizeInPixels) {
        if (url == null) {
            return@produceState
        }
        while (isActive) {
            try {
                value = connection.httpClient
                    .get(url, caching = HttpClient.CachingMode.AVOID_CACHE)
                    .asBitmap(sizeInPixels, 0, ImageConversionPolicy.PreferTargetSize)
                    .response
                    .asImageBitmap()
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Loading $url failed", e)
            }
            if (refreshMs <= 0) {
                break
            }
            delay(refreshMs.toLong())
        }
    }
    return bitmap
}

@Composable
private fun MjpegContent(widget: Widget, connection: Connection, modifier: Modifier) {
    val url = widget.url
    val frame by produceState<ImageBitmap?>(initialValue = null, url, connection) {
        if (url == null) {
            return@produceState
        }
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    val result = connection.httpClient.get(url)
                    MjpegInputStream(result.response.byteStream()).use { stream ->
                        while (isActive) {
                            stream.readMjpegFrame()?.let { bitmap: Bitmap -> value = bitmap.asImageBitmap() }
                        }
                    }
                } catch (e: HttpClient.HttpException) {
                    Log.e(TAG, "MJPEG streaming from $url failed", e)
                    // No point in continuing if the server returned failure
                    break
                } catch (e: IOException) {
                    Log.e(TAG, "MJPEG streaming from $url was interrupted", e)
                }
            }
        }
    }
    BitmapContent(frame, modifier)
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoContent(widget: Widget, connection: Connection, modifier: Modifier) {
    val context = LocalContext.current
    var hasError by remember(widget.url, widget.state) { mutableStateOf(false) }
    val isHls = widget.encoding.equals("hls", ignoreCase = true)
    val url = if (isHls && widget.item?.type == Item.Type.StringItem && widget.item.state != null) {
        widget.item.state.asString
    } else {
        widget.url
    }
    val absoluteUrl = remember(url, connection) {
        try {
            url?.let { connection.httpClient.buildUrl(it).toString() }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid video URL '$url'", e)
            null
        }
    }

    if (absoluteUrl == null || hasError) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            val label = widget.label.orDefaultIfEmpty(stringResource(R.string.widget_type_video))
            Text(stringResource(R.string.error_video_player, label))
        }
        return
    }

    val player = remember(absoluteUrl) {
        val dataSourceFactory = DataSource.Factory {
            DefaultHttpDataSource.Factory()
                .setUserAgent(HttpClient.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()
                .apply { connection.httpClient.authHeader?.let { setRequestProperty("Authorization", it) } }
        }
        val mediaSourceFactory = if (isHls) {
            HlsMediaSource.Factory(dataSourceFactory)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(absoluteUrl)))
            playWhenReady = true
            prepare()
        }
    }
    var aspectRatio by remember(player) { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playing $absoluteUrl failed", error)
                hasError = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Use a TextureView, as a SurfaceView isn't shown correctly inside of scaled Compose layers
        AndroidView(
            factory = { viewContext -> TextureView(viewContext).also { view -> player.setVideoTextureView(view) } },
            modifier = Modifier.aspectRatio(aspectRatio)
        )
    }
}
