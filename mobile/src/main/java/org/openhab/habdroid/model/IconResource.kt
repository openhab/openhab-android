/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import android.graphics.Color
import android.net.Uri
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import kotlinx.android.parcel.Parcelize
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs
import java.util.Locale

@Parcelize
class IconResource internal constructor(
    private val icon: String,
    private val isOh2: Boolean,
    private val customState: String?
) : Parcelable {
    fun toUrl(context: Context): String {
        return toUrl(context.getPrefs().getIconFormat())
    }

    @VisibleForTesting
    fun toUrl(iconFormat: IconFormat): String {
        if (!isOh2) {
            return "images/$icon.png"
        }

        val suffix = when (iconFormat) {
            IconFormat.Png -> "PNG"
            IconFormat.Svg -> "SVG"
        }

        val builder = Uri.Builder()
            .path("icon/")
            .appendPath(icon)
            .appendQueryParameter("format", suffix)
            .appendQueryParameter("anyFormat", "true")

        if (!customState.isNullOrEmpty()) {
            builder.appendQueryParameter("state", customState)
        }

        return builder.build().toString()
    }

    fun withCustomState(state: String): IconResource {
        return IconResource(icon, isOh2, state)
    }
}

fun String?.toOH1IconResource(): IconResource? {
    return if (this != null && this != "none") IconResource(this, false, null) else null
}

fun String?.toOH2IconResource(
    item: Item? = null,
    type: Widget.Type? = Widget.Type.Unknown,
    hasMappings: Boolean = false
): IconResource? {
    if (this == null || this == "none") {
        return null
    }

    val itemState = item?.state
    var iconState = itemState?.asString.orEmpty()
    if (itemState != null) {
        if (item.isOfTypeOrGroupType(Item.Type.Color)) {
            // For items that control a color item fetch the correct icon
            if (type == Widget.Type.Slider || type == Widget.Type.Switch && !hasMappings) {
                try {
                    iconState = itemState.asBrightness.toString()
                    if (type == Widget.Type.Switch) {
                        iconState = if (iconState == "0") "OFF" else "ON"
                    }
                } catch (e: Exception) {
                    iconState = "OFF"
                }
            } else if (itemState.asHsv != null) {
                val color = itemState.asHsv.toColor()
                iconState = String.format(
                    Locale.US, "#%02x%02x%02x",
                    Color.red(color), Color.green(color), Color.blue(color))
            }
        } else if (type == Widget.Type.Switch && !hasMappings && !item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
            // For switch items without mappings (just ON and OFF) that control a dimmer item
            // and which are not ON or OFF already, set the state to "OFF" instead of 0
            // or to "ON" to fetch the correct icon
            iconState = if (itemState.asString == "0" || itemState.asString == "OFF") "OFF" else "ON"
        }
    }

    return IconResource(this, true, iconState)
}

enum class IconFormat {
    Png,
    Svg
}
