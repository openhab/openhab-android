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
import android.service.dreams.DreamService
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getStringOrNull

class DayDream : DreamService(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job
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

        launch {
            item?.let { listenForTextItem(it) }
        }
        launch {
            moveText()
        }
    }

    private suspend fun listenForTextItem(item: String) {
        ConnectionFactory.waitForInitialization()
        val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return

        textView.text = try {
            ItemClient.loadItem(connection, item)?.state?.asString.orEmpty()
        } catch (e: HttpClient.HttpException) {
            getString(R.string.screensaver_error_loading_item, item)
        }

        ItemClient.listenForItemChange(this, connection, item) { _, payload ->
            val state = payload.getString("value")
            Log.d(TAG, "Got state by event: $state")
            textView.text = state
        }
    }

    private suspend fun moveText() {
        wrapper.fadeOut()
        wrapper.moveViewToRandomPosition(container)
        wrapper.fadeIn()
        delay(1.minutes)
        moveText()
    }

    private fun View.moveViewToRandomPosition(container: FrameLayout) {
        val randomX = Random.nextInt(0, container.width - width)
        val randomY = Random.nextInt(0, container.height - height)

        x = randomX.toFloat()
        y = randomY.toFloat()
    }

    private fun View.fadeOut() {
        val animator = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0f)
        animator.duration = 2000
        animator.start()
    }

    private fun View.fadeIn() {
        val animator = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f)
        animator.duration = 2000
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
    }

    companion object {
        private val TAG = DayDream::class.java.simpleName
    }
}
