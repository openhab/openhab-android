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

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.optStringOrNull

@Parcelize
data class Sitemap internal constructor(
    val name: String,
    val label: String,
    val icon: IconResource?,
    val homepageLink: String
) : Parcelable

fun JSONObject.toSitemap(): Sitemap? {
    val name = optStringOrNull("name") ?: return null
    val homepageLink = optJSONObject("homepage")?.optStringOrNull("link") ?: return null
    val label = optStringOrNull("label")
    val icon = optStringOrNull("icon")

    return Sitemap(name, label ?: name, icon.toIconResource(), homepageLink)
}

fun JSONArray.toSitemapList(): List<Sitemap> {
    return (0 until length()).mapNotNull { index ->
        var result: Sitemap? = null
        try {
            val sitemap = getJSONObject(index).toSitemap()
            if (sitemap != null && (sitemap.name != "_default" || length() == 1)) {
                result = sitemap
            }
        } catch (e: JSONException) {
            Log.d(Sitemap::class.java.simpleName, "Error while parsing sitemap", e)
        }
        result
    }
}

fun List<Sitemap>.sortedWithDefaultName(defaultSitemapName: String): List<Sitemap> {
    // Sort by site name label, the default sitemap should be the first one
    return sortedWith { lhs, rhs ->
        when (defaultSitemapName) {
            lhs.name -> -1
            rhs.name -> 1
            else -> lhs.label.compareTo(rhs.label, true)
        }
    }
}
