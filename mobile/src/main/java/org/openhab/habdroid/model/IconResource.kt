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
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import kotlinx.android.parcel.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.appendQueryParameter
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs
import java.util.Locale

@Parcelize
class IconResource internal constructor(
    internal val icon: String,
    internal val isOh2: Boolean,
    internal val customState: String?
) : Parcelable {
    fun toUrl(context: Context, includeState: Boolean): String {
        return toUrl(includeState, context.getPrefs().getIconFormat())
    }

    @VisibleForTesting
    fun toUrl(includeState: Boolean, iconFormat: IconFormat): String {
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
            .appendQueryParameter("anyFormat", true)

        if (!customState.isNullOrEmpty() && includeState) {
            builder.appendQueryParameter("state", customState)
        }

        return builder.build().toString()
    }

    fun withCustomState(state: String): IconResource {
        return IconResource(icon, isOh2, state)
    }
}

fun SharedPreferences.getIconResource(key: String): IconResource? {
    val iconString = getString(key, null) ?: return null
    return try {
        val obj = JSONObject(iconString)
        val icon = obj.getString("icon")
        val isOh2 = obj.getInt("ohversion") == 2
        val customState = obj.optString("state")
        IconResource(icon, isOh2, customState)
    } catch (e: JSONException) {
        null
    }
}

fun SharedPreferences.Editor.putIconResource(key: String, icon: IconResource?): SharedPreferences.Editor {
    if (icon == null) {
        putString(key, null)
    } else {
        val iconString = JSONObject()
            .put("icon", icon.icon)
            .put("ohversion", if (icon.isOh2) 2 else 1)
            .put("state", icon.customState)
            .toString()
        putString(key, iconString)
    }
    return this
}

fun String?.toOH1IconResource(): IconResource? {
    return if (this != null && this != "none") IconResource(this, false, null) else null
}

fun String?.toOH2IconResource(): IconResource? {
    return if (this != null && this != "none") IconResource(this, true, null) else null
}

internal fun String?.toOH2WidgetIconResource(item: Item?, type: Widget.Type, hasMappings: Boolean): IconResource? {
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
