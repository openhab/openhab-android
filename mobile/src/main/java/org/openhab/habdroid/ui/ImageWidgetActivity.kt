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

package org.openhab.habdroid.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.faltenreich.skeletonlayout.Skeleton
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.IconBackground
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getIconFallbackColor
import org.openhab.habdroid.util.orDefaultIfEmpty

class ImageWidgetActivity : AbstractBaseActivity() {
    private lateinit var imageView: PhotoView
    private lateinit var skeleton: Skeleton
    private var connection: Connection? = null
    private var refreshJob: Job? = null
    private var delay: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_image)

        supportActionBar?.title =
            intent.getStringExtra(WIDGET_LABEL).orDefaultIfEmpty(getString(R.string.widget_type_image))

        imageView = findViewById(R.id.photo_view)
        skeleton = findViewById(R.id.activity_content)

        delay = intent.getIntExtra(WIDGET_REFRESH, 0).toLong()
    }

    override fun onResume() {
        super.onResume()

        connection = ConnectionFactory.activeUsableConnection?.connection
        if (connection == null) {
            finish()
            return
        }

        launch(Dispatchers.Main) {
            if (determineDataUsagePolicy(connection).canDoRefreshes && delay != 0L) {
                scheduleRefresh()
            } else {
                loadImage()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "onCreateOptionsMenu()")
        menuInflater.inflate(R.menu.fullscreen_image_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                super.onOptionsItemSelected(item)
            }
            R.id.refresh -> {
                launch {
                    loadImage()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun loadImage() {
        val widgetUrl = intent.getStringExtra(WIDGET_URL)
        val conn = connection ?: return finish()
        skeleton.showSkeleton()

        val bitmap = withContext(Dispatchers.IO) {
            if (widgetUrl != null) {
                Log.d(TAG, "Load image from url")
                val displayMetrics = resources.displayMetrics
                val size = max(displayMetrics.widthPixels, displayMetrics.heightPixels)
                try {
                    conn.httpClient
                        .get(widgetUrl)
                        .asBitmap(
                            size,
                            getIconFallbackColor(IconBackground.APP_THEME),
                            ImageConversionPolicy.PreferTargetSize
                        )
                        .response
                } catch (e: HttpClient.HttpException) {
                    Log.d(TAG, "Failed to load image", e)
                    null
                }
            } else {
                val link = intent.getStringExtra(WIDGET_LINK)!!
                val widgetState = try {
                    withContext(Dispatchers.IO) {
                        JSONObject(
                            conn.httpClient
                                .get(link)
                                .asText()
                                .response
                        ).getString("state")
                    }
                } catch (e: HttpClient.HttpException) {
                    Log.d(TAG, "Failed to load image", e)
                    null
                }

                if (widgetState != null && widgetState.matches("data:image/.*;base64,.*".toRegex())) {
                    Log.d(TAG, "Load image from value")
                    val dataString = widgetState.substring(widgetState.indexOf(",") + 1)
                    val data = Base64.decode(dataString, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(data, 0, data.size)
                } else {
                    null
                }
            }
        }

        if (bitmap == null) {
            finish()
        } else {
            // Restore zoom after image refresh
            val matrix = Matrix()
            imageView.getSuppMatrix(matrix)
            imageView.setImageBitmap(bitmap)
            imageView.setSuppMatrix(matrix)
            skeleton.showOriginal()
        }
    }

    private fun scheduleRefresh() {
        Log.d(TAG, "scheduleRefresh()")
        refreshJob = launch {
            loadImage()
            delay(delay)
            Log.d(TAG, "refreshJob after delay")
            scheduleRefresh()
        }
    }

    override fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean = mode == ScreenLockMode.Enabled

    companion object {
        private val TAG = ImageWidgetActivity::class.java.simpleName

        const val WIDGET_LABEL = "widget_label"
        const val WIDGET_REFRESH = "widget_refresh"
        const val WIDGET_URL = "widget_url"
        const val WIDGET_LINK = "widget_link"
    }
}
