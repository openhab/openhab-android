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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.ActivityImageBinding
import org.openhab.habdroid.model.CloudMessage
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.parcelable

class NotificationImageActivity : AbstractBaseActivity() {
    private lateinit var binding: ActivityImageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notification = intent.parcelable<CloudMessage.CloudNotification>(EXTRA_NOTIFICATION)
        if (notification == null) {
            finish()
            return
        }

        supportActionBar?.title = notification.title.ifEmpty { getString(R.string.app_notifications) }
        if (notification.createdTimestamp > 0L) {
            supportActionBar?.subtitle = DateUtils.formatDateTime(
                this,
                notification.createdTimestamp,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
            )
        }

        launch {
            loadImage(notification, intent.getBooleanExtra(EXTRA_USE_PRIMARY_SERVER, false))
        }
    }

    override fun inflateBinding(): CommonBinding {
        binding = ActivityImageBinding.inflate(layoutInflater)
        return CommonBinding(binding.root, binding.appBar, binding.coordinator, binding.skeleton)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private suspend fun loadImage(notification: CloudMessage.CloudNotification, usePrimaryServer: Boolean) {
        val cacheManager = CacheManager.getInstance(this)
        val persistedId = notification.id.persistedId
        var bitmap = cacheManager.getNotificationImageFromMemory(persistedId)
        if (bitmap == null) {
            binding.skeleton.showSkeleton()
            bitmap = cacheManager.loadNotificationImageFromDisk(persistedId, notification.createdTimestamp)
        }
        if (bitmap == null) {
            val connInfo = getConnectionFactory().let { if (usePrimaryServer) it.currentPrimary else it.currentActive }
            val conn = connInfo?.usableConnection ?: connInfo?.cloud?.connection
            if (conn != null) {
                val size = max(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
                val context = this
                bitmap = withContext(Dispatchers.IO) { notification.loadImage(conn, context, size) }
            }
        }
        if (bitmap == null) {
            finish()
            return
        }
        binding.photoView.setImageBitmap(bitmap)
        binding.skeleton.showOriginal()
    }

    override fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean = mode == ScreenLockMode.Enabled

    companion object {
        private const val EXTRA_NOTIFICATION = "notification"
        private const val EXTRA_USE_PRIMARY_SERVER = "use_primary_server"

        fun createIntent(
            context: Context,
            notification: CloudMessage.CloudNotification,
            usePrimaryServer: Boolean
        ): Intent = Intent(context, NotificationImageActivity::class.java)
            .putExtra(EXTRA_NOTIFICATION, notification)
            .putExtra(EXTRA_USE_PRIMARY_SERVER, usePrimaryServer)
    }
}
