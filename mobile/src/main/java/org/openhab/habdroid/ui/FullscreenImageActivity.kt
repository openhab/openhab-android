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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.orDefaultIfEmpty

class FullscreenImageActivity : AbstractBaseActivity() {
    private lateinit var imageView: PhotoView
    private lateinit var widget: Widget

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_image)

        widget = intent.getParcelableExtra(WIDGET)!!

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = widget.label.orDefaultIfEmpty(getString(R.string.widget_type_image))

        imageView = findViewById(R.id.activity_content)
    }

    override fun onResume() {
        super.onResume()
        loadImage()
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
                loadImage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadImage() {
        val value = widget.state?.asString

        if (value != null && value.matches("data:image/.*;base64,.*".toRegex())) {
            Log.d(TAG, "Load image from value")
            val dataString = value.substring(value.indexOf(",") + 1)
            val data = Base64.decode(dataString, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            imageView.setImageBitmap(bitmap)
        } else if (widget.url != null) {
            Log.d(TAG, "Load image from url")
            val connection = ConnectionFactory.activeUsableConnection?.connection
            if (connection == null) {
                Log.d(TAG, "Got no connection")
                finish()
                return
            }

            CoroutineScope(Dispatchers.IO + Job()).launch {
                val displayMetrics = resources.displayMetrics
                val size = max(displayMetrics.widthPixels, displayMetrics.heightPixels)
                val bitmap = try {
                    connection
                        .httpClient
                        .get(widget.url!!)
                        .asBitmap(size, ImageConversionPolicy.PreferTargetSize)
                        .response
                } catch (e: HttpClient.HttpException) {
                    Log.d(TAG, "Failed to load image", e)
                    finish()
                    return@launch
                }
                Handler(Looper.getMainLooper()).post {
                    imageView.setImageBitmap(bitmap)
                }
            }
        } else {
            finish()
        }
    }

    override fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean {
        return mode == ScreenLockMode.Enabled
    }

    companion object {
        private val TAG = FullscreenImageActivity::class.java.simpleName

        const val WIDGET = "widget"
    }
}
