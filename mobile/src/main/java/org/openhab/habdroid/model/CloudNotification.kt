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
import android.os.Parcelable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.IconBackground
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.getIconFallbackColor
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.optStringOrNull

@Parcelize
data class CloudNotification internal constructor(
    val id: String,
    val title: String,
    val message: String,
    val createdTimestamp: Long,
    val icon: IconResource?,
    val severity: String?,
    val actions: List<CloudNotificationAction>?,
    val onClickAction: CloudNotificationAction?,
    val mediaAttachmentUrl: String?
) : Parcelable {
    val idHash get() = id.hashCode()

    suspend fun loadImage(connection: Connection, context: Context, size: Int): Bitmap? {
        mediaAttachmentUrl ?: return null
        //if (mediaAttachmentUrl.startsWith("item:")) {
        //    val itemName = mediaAttachmentUrl.removePrefix("item:")
        //    val item = ItemClient.loadItem(connection, itemName)
        //
        //}
        val fallbackColor = context.getIconFallbackColor(IconBackground.APP_THEME)
        return connection.httpClient
            .get(mediaAttachmentUrl)
            .asBitmap(size, fallbackColor, ImageConversionPolicy.PreferTargetSize)
            .response
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
        id = getString("_id"),
        title = payload?.optString("title").orEmpty(),
        message = payload?.getString("message") ?: getString("message"),
        createdTimestamp = created,
        icon = payload?.optStringOrNull("icon").toOH2IconResource() ?: optStringOrNull("icon").toOH2IconResource(),
        severity = payload?.optStringOrNull("severity") ?: optStringOrNull("severity"),
        actions = payload?.optJSONArray("actions")?.map { it.toCloudNotificationAction() }?.filterNotNull(),
        onClickAction = payload?.optStringOrNull("on-click").toCloudNotificationAction(),
        mediaAttachmentUrl = payload?.optStringOrNull("media-attachment-url")
    )
}

@Parcelize
data class CloudNotificationAction internal constructor(
    val label: String,
    val action: String
) : Parcelable

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
