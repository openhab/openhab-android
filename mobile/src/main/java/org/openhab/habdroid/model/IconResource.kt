/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import java.util.Locale
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.util.appendQueryParameter
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getStringOrNull

@Parcelize
data class IconResource internal constructor(
    internal val icon: String,
    internal val isOh2: Boolean,
    internal val customState: String
) : Parcelable {
    fun toUrl(context: Context, includeState: Boolean): String {
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.widgetlist_icon_size)
        return toUrl(includeState, context.getPrefs().getIconFormat(), iconSize)
    }

    @VisibleForTesting
    fun toUrl(includeState: Boolean, iconFormat: IconFormat, desiredSizePixels: Int): String {
        if (!isOh2) {
            return "images/$icon.png"
        }

        var iconSource = "oh"
        var iconSet = "classic"
        var iconName = "none"

        val segments = icon.split(":", limit = 3)
        when (segments.size) {
            1 -> iconName = segments[0]
            2 -> {
                iconSource = segments[0]
                iconName = segments[1]
            }
            3 -> {
                iconSource = segments[0]
                iconSet = segments[1]
                iconName = segments[2]
            }
        }

        val builder = Uri.Builder()

        when (iconSource) {
            "oh" -> {
                val suffix = when (iconFormat) {
                    IconFormat.Png -> "PNG"
                    IconFormat.Svg -> "SVG"
                }

                builder.path("icon")
                       .appendPath(iconName)
                       .appendQueryParameter("format", suffix)
                       .appendQueryParameter("anyFormat", true)
                       .appendQueryParameter("iconset", iconSet)

                if (customState.isNotEmpty() && includeState) {
                    builder.appendQueryParameter("state", customState)
                }
            }
            "if", "iconify", "material" -> {
                if (iconSource == "material") {
                    iconSet = "mdi"
                }
                builder.scheme("https")
                       .authority("api.iconify.design")
                       .path(iconSet)
                       .appendPath("$iconName.svg")
                       .appendQueryParameter("height", desiredSizePixels.toString())
            }
            else -> builder.path("icon/")
        }

        return builder.build().toString()
    }

    fun withCustomState(state: String): IconResource {
        return IconResource(icon, isOh2, state)
    }
}

fun SharedPreferences.getIconResource(key: String): IconResource? {
    val iconString = getStringOrNull(key) ?: return null
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
    return if (isNullOrEmpty() || this == "none") null else IconResource(this, false, "")
}

fun String?.toOH2IconResource(): IconResource? {
    return if (isNullOrEmpty() || this == "none") null else IconResource(this, true, "")
}

internal fun String?.toOH2WidgetIconResource(
    state: ParsedState?,
    item: Item?,
    type: Widget.Type,
    hasMappings: Boolean,
    useState: Boolean
): IconResource? {
    if (isNullOrEmpty() || this == "none") {
        return null
    }

    val stateToUse = state ?: item?.state
    val iconState = when {
        !useState || item == null -> null
        // For NULL states, we send 'null' as state when fetching the icon (BasicUI set a predecent for doing so)
        stateToUse == null -> "null"
        // Number items need to use state formatted as per their state description
        item.isOfTypeOrGroupType(Item.Type.Number) || item.isOfTypeOrGroupType(Item.Type.NumberWithDimension)-> {
            stateToUse.asNumber?.toString(Locale.US)
        }
        item.isOfTypeOrGroupType(Item.Type.Color) -> when {
            // Color sliders just use the brightness part of the color
            type == Widget.Type.Slider -> stateToUse.asBrightness.toString()
            // Color toggles should behave similarly to the logic below (but using the brightness value)
            type == Widget.Type.Switch && !hasMappings -> if (stateToUse.asBrightness == 0) "OFF" else "ON"
            stateToUse.asHsv != null -> {
                val color = stateToUse.asHsv.toColor()
                String.format(Locale.US, "#%02x%02x%02x", Color.red(color), Color.green(color), Color.blue(color))
            }
            else -> stateToUse.asString
        }
        type == Widget.Type.Switch && !hasMappings && !item.isOfTypeOrGroupType(Item.Type.Rollershutter) -> {
            // For switch items without mappings (just ON and OFF) that control a dimmer item
            // and which are not ON or OFF already, set the state to "OFF" instead of 0
            // or to "ON" to fetch the correct icon
            if (stateToUse.asString == "0" || stateToUse.asString == "OFF") "OFF" else "ON"
        }
        else -> stateToUse.asString
    }

    return IconResource(this, true, iconState.orEmpty())
}

enum class IconFormat {
    Png,
    Svg
}
