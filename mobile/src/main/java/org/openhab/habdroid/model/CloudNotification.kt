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

package org.openhab.habdroid.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcelable
import android.util.Base64
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.parcelize.Parcelize
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.IconBackground
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.getIconFallbackColor
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.optStringOrNull

@Parcelize
data class CloudNotificationId internal constructor(
    val persistedId: String,
    val referenceId: String?
) : Parcelable {
    val notificationId get() = (referenceId ?: persistedId).hashCode()
}

@Parcelize
data class CloudNotification internal constructor(
    val id: CloudNotificationId,
    val title: String,
    val message: String,
    val createdTimestamp: Long,
    val icon: IconResource?,
    val tag: String?,
    val actions: List<CloudNotificationAction>?,
    val onClickAction: CloudNotificationAction?,
    val mediaAttachmentUrl: String?
) : Parcelable {
    suspend fun loadImage(connection: Connection, context: Context, size: Int): Bitmap? {
        if (mediaAttachmentUrl == null) {
            return null
        }
        val itemStateFromMedia = if (mediaAttachmentUrl.startsWith("item:")) {
            val itemName = mediaAttachmentUrl.removePrefix("item:")
            val item = ItemClient.loadItem(connection, itemName)
            item?.state?.asString
        } else {
            null
        }
        if (itemStateFromMedia != null && itemStateFromMedia.toHttpUrlOrNull() == null) {
            // media attachment is an item, but item state is not a URL -> interpret as base64 encoded image
            return bitmapFromBase64(itemStateFromMedia)
        }
        val fallbackColor = context.getIconFallbackColor(IconBackground.APP_THEME)
        return connection.httpClient
            .get(itemStateFromMedia ?: mediaAttachmentUrl)
            .asBitmap(size, fallbackColor, ImageConversionPolicy.PreferTargetSize)
            .response
    }

    private fun bitmapFromBase64(itemState: String): Bitmap? {
        return try {
            val data = Base64.decode(itemState, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

@Throws(JSONException::class)
fun JSONObject.toCloudNotification(): CloudNotification {
    var created: Long = 0
    if (has("created")) {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        try {
            created = format.parse(getString("created"))?.time ?: 0
        } catch (e: ParseException) {
            // keep created at 0
        }
    }

    val payload = optJSONObject("payload")
    return CloudNotification(
        id = CloudNotificationId(getString("_id"), optStringOrNull("reference-id")),
        title = payload?.optString("title").orEmpty(),
        message = payload?.getString("message") ?: getString("message"),
        createdTimestamp = created,
        icon = payload?.optStringOrNull("icon").toOH2IconResource() ?: optStringOrNull("icon").toOH2IconResource(),
        tag = payload?.optStringOrNull("tag") ?: optStringOrNull("severity"),
        actions = payload?.optJSONArray("actions")?.map { it.toCloudNotificationAction() }?.filterNotNull(),
        onClickAction = payload?.optStringOrNull("on-click").toCloudNotificationAction(),
        mediaAttachmentUrl = payload?.optStringOrNull("media-attachment-url")
    )
}

@Parcelize
data class CloudNotificationAction internal constructor(
    val label: String,
    private val internalAction: String
) : Parcelable {
    sealed class Action {
        class UrlAction(val url: String) : Action()
        class ItemCommandAction(val itemName: String, val command: String) : Action()
        class UiCommandAction(val command: String) : Action()
        object NoAction : Action()
    }

    val action: Action get() {
        val split = internalAction.split(":", limit = 3)
        return when {
            split[0] == "command" && split.size == 3 ->
                Action.ItemCommandAction(split[1], split[2])
            internalAction.startsWith("http://") || internalAction.startsWith("https://") ->
                Action.UrlAction(internalAction)
            split[0] == "ui" && split.size == 3 -> {
                Action.UiCommandAction("${split[1]}${split[2]}")
            }
            split[0] == "ui" && split.size == 2 -> {
                Action.UiCommandAction("navigate:${split[1]}")
            }
            else -> Action.NoAction
        }
    }
}

fun String?.toCloudNotificationAction(): CloudNotificationAction? {
    val split = this?.split("=", limit = 2)
    if (split?.size != 2) {
        return null
    }
    return CloudNotificationAction(split.component1(), split.component2())
}

fun JSONObject?.toCloudNotificationAction(): CloudNotificationAction? {
    val action = this?.optStringOrNull("action") ?: return null
    val title = optStringOrNull("title") ?: return null
    return CloudNotificationAction(title, action)
}
