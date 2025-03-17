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

import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.Html
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getStringOrNull

class DayDream :
    DreamService(),
    CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job
    private var moveTextJob: Job? = null
    private lateinit var textView: TextView
    private lateinit var wrapper: LinearLayout
    private lateinit var container: FrameLayout

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = applicationContext.getPrefs().getBoolean(PrefKeys.DAY_DREAM_BRIGHT_SCREEN, true)
        setContentView(R.layout.daydream)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val item = applicationContext.getPrefs().getStringOrNull(PrefKeys.DAY_DREAM_ITEM)

        textView = findViewById(R.id.text)
        wrapper = findViewById(R.id.wrapper)
        container = findViewById(R.id.container)
        setupDateView()

        launch {
            item?.let { listenForTextItem(it) }
        }
    }

    private fun setupDateView() {
        val dateView: TextClock = findViewById(R.id.date)
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE, MMMM d, yyyy")
        dateView.format12Hour = pattern
        dateView.format24Hour = pattern
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        moveTextIfRequired()
    }

    private suspend fun listenForTextItem(item: String) {
        ConnectionFactory.waitForInitialization()
        val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return

        moveText()
        val initialText = try {
            ItemClient.loadItem(connection, item)?.state?.asString.orEmpty()
        } catch (e: HttpClient.HttpException) {
            getString(R.string.screensaver_error_loading_item, item)
        }
        setText(initialText)

        ItemClient.listenForItemChange(this, connection, item) { _, payload ->
            val state = payload.getString("value")
            Log.d(TAG, "Got state by event: $state")
            setText(state)
        }
    }

    private fun setText(text: String) {
        textView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text.replace("\n", "<br>"), Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(text.replace("\n", "<br>"))
        }
        moveTextIfRequired()
    }

    private fun moveText() {
        moveTextJob?.cancel()
        wrapper.fadeOut()
        wrapper.moveViewToRandomPosition(container)
        wrapper.fadeIn()
        moveTextJob = launch {
            delay(if (BuildConfig.DEBUG) 10.seconds else 1.minutes)
            moveText()
        }
    }

    private fun moveTextIfRequired() {
        Handler(Looper.getMainLooper()).post {
            if (!textView.isFullyVisible()) {
                moveText()
            }
        }
    }

    private fun View.moveViewToRandomPosition(container: FrameLayout) {
        val randomX = randomIntFromZero(container.width - width)
        val randomY = randomIntFromZero(container.height - height)

        x = randomX.toFloat()
        y = randomY.toFloat()
    }

    private fun randomIntFromZero(until: Int): Int {
        // Fix "random range is empty" exception
        if (until == 0) {
            return 0
        }
        return Random.nextInt(0, until)
    }

    private fun View.fadeOut() {
        val animator = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0f)
        animator.duration = if (BuildConfig.DEBUG) 500 else 2000
        animator.start()
    }

    private fun View.fadeIn() {
        val animator = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f)
        animator.duration = if (BuildConfig.DEBUG) 500 else 2000
        animator.start()
    }

    private fun View.isFullyVisible(): Boolean {
        val rect = Rect()
        val isVisible = this.getGlobalVisibleRect(rect)
        val viewHeight = this.height
        val viewWidth = this.width

        return isVisible && rect.height() == viewHeight && rect.width() == viewWidth
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
    }

    companion object {
        private val TAG = DayDream::class.java.simpleName
    }
}
