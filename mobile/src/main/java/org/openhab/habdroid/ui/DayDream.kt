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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
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

        val eventSubscription = connection.httpClient.makeSse(
            // Support for both the "openhab" and the older "smarthome" root topic by using a wildcard
            connection.httpClient.buildUrl("rest/events?topics=*/items/$item/command")
        )

        textView.text = try {
            ItemClient.loadItem(connection, item)?.state?.asString.orEmpty()
        } catch (e: HttpClient.HttpException) {
            getString(R.string.screensaver_error_loading_item, item)
        }

        try {
            while (isActive) {
                try {
                    val event = JSONObject(eventSubscription.getNextEvent())
                    if (event.optString("type") == "ALIVE") {
                        Log.d(TAG, "Got ALIVE event")
                        continue
                    }
                    val topic = event.getString("topic")
                    val topicPath = topic.split('/')
                    // Possible formats:
                    // - openhab/items/<item>/statechanged
                    // - openhab/items/<group item>/<item>/statechanged
                    // When an update for a group is sent, there's also one for the individual item.
                    // Therefore always take the element on index two.
                    if (topicPath.size !in 4..5) {
                        throw JSONException("Unexpected topic path $topic")
                    }
                    val state = JSONObject(event.getString("payload")).getString("value")
                    Log.d(TAG, "Got state by event: $state")
                    textView.text = state
                } catch (e: JSONException) {
                    Log.e(TAG, "Failed parsing JSON of state change event", e)
                }
            }
        } finally {
            eventSubscription.cancel()
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
