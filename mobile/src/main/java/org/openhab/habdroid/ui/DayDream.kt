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
import android.graphics.Rect
import android.os.Build
import android.service.dreams.DreamService
import android.text.Html
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.DaydreamBinding
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getStringOrNull

class DayDream : DreamService() {
    private var dreamScope: CoroutineScope? = null
    private var binding: DaydreamBinding? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = false
        isFullscreen = true
        isScreenBright = getPrefs().getBoolean(PrefKeys.DAY_DREAM_BRIGHT_SCREEN, true)

        val binding = DaydreamBinding.inflate(LayoutInflater.from(this)).also {
            this.binding = it
        }
        binding.container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            dreamScope?.launch {
                moveToRandomPosition()
            }
        }

        setContentView(binding.root)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        binding = null
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()

        setupDateView()

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also {
            this.dreamScope = it
        }

        getPrefs().getStringOrNull(PrefKeys.DAY_DREAM_ITEM)?.let { item ->
            scope.launch {
                listenForTextItem(item, scope)
            }
        }

        scope.launch {
            do {
                moveToRandomPosition()
                delay(if (BuildConfig.DEBUG) 10.seconds else 1.minutes)
            } while (isActive)
        }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        dreamScope?.cancel()
        dreamScope = null
    }

    private fun setupDateView() {
        val binding = binding ?: return
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE, MMMM d, yyyy")
        binding.date.format12Hour = pattern
        binding.date.format24Hour = pattern
    }

    private suspend fun listenForTextItem(item: String, scope: CoroutineScope) {
        val connection = getConnectionFactory().primaryFlow.first().conn?.connection ?: return

        val initialText = try {
            ItemClient.loadItem(connection, item)?.state?.asString.orEmpty()
        } catch (_: HttpClient.HttpException) {
            getString(R.string.screensaver_error_loading_item, item)
        }
        setText(initialText)

        ItemClient.listenForItemChange(scope, connection, item, ItemClient.EventType.StateChanged)
            .consumeEach { (_, state) ->
                Log.d(TAG, "Got state by event: $state")
                setText(state)
            }
    }

    private fun setText(text: String) {
        val textView = binding?.text ?: return
        textView.isVisible = text.isNotEmpty()
        textView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text.replace("\n", "<br>"), Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(text.replace("\n", "<br>"))
        }
        if (!textView.isFullyVisible()) {
            moveToRandomPosition()
        }
    }

    private fun moveToRandomPosition() {
        val binding = binding ?: return
        binding.wrapper.apply {
            fadeOut()
            moveViewToRandomPosition(binding.container)
            fadeIn()
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

    companion object {
        private val TAG = DayDream::class.java.simpleName
    }
}
