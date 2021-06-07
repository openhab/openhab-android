/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.orDefaultIfEmpty

class FullscreenImageActivity : AbstractBaseActivity() {
    private lateinit var imageView: PhotoView
    private var refreshJob: Job? = null
    private var delay: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_image)

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title =
            intent.getStringExtra(WIDGET_LABEL).orDefaultIfEmpty(getString(R.string.widget_type_image))

        imageView = findViewById(R.id.activity_content)
        delay = intent.getIntExtra(WIDGET_REFRESH, 0).toLong()
    }

    override fun onResume() {
        super.onResume()

        launch(Dispatchers.Main) {
            if (determineDataUsagePolicy().canDoRefreshes && delay != 0L) {
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
                CoroutineScope(Dispatchers.IO + Job()).launch {
                    loadImage()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun loadImage() {
        val connection = ConnectionFactory.activeUsableConnection?.connection
        if (connection == null) {
            Log.d(TAG, "Got no connection")
            return finish()
        }

        val widgetUrl = intent.getStringExtra(WIDGET_URL)
        val bitmap = if (widgetUrl != null) {
            Log.d(TAG, "Load image from url")
            val displayMetrics = resources.displayMetrics
            val size = max(displayMetrics.widthPixels, displayMetrics.heightPixels)
            try {
                connection
                    .httpClient
                    .get(widgetUrl)
                    .asBitmap(size, ImageConversionPolicy.PreferTargetSize)
                    .response
            } catch (e: HttpClient.HttpException) {
                Log.d(TAG, "Failed to load image", e)
                return finish()
            }

        } else {
            val link = intent.getStringExtra(WIDGET_LINK)!!
            val widgetState = JSONObject(
                connection
                    .httpClient
                    .get(link)
                    .asText()
                    .response
            ).getString("state") ?: return finish()

            if (widgetState.matches("data:image/.*;base64,.*".toRegex())) {
                Log.d(TAG, "Load image from value")
                val dataString = widgetState.substring(widgetState.indexOf(",") + 1)
                val data = Base64.decode(dataString, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(data, 0, data.size)
            } else {
                null
            }
        }

        bitmap ?: return finish()

        // Restore zoom after image refresh
        val matrix = Matrix()
        imageView.getSuppMatrix(matrix)
        imageView.setImageBitmap(bitmap)
        imageView.setSuppMatrix(matrix)
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

    override fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean {
        return mode == ScreenLockMode.Enabled
    }

    companion object {
        private val TAG = FullscreenImageActivity::class.java.simpleName

        const val WIDGET_LABEL = "widget_label"
        const val WIDGET_REFRESH = "widget_refresh"
        const val WIDGET_URL = "widget_url"
        const val WIDGET_LINK = "widget_link"
    }
}
