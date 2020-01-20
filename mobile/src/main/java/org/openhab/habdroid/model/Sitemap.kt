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

import android.os.Parcelable
import android.util.Log

import kotlinx.android.parcel.Parcelize
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.forEach
import org.openhab.habdroid.util.optStringOrNull
import org.w3c.dom.Document
import org.w3c.dom.Node

@Parcelize
data class Sitemap internal constructor(
    val name: String,
    val label: String,
    val icon: IconResource?,
    val homepageLink: String
) : Parcelable

fun Node.toSitemap(): Sitemap? {
    var label: String? = null
    var name: String? = null
    var icon: String? = null
    var homepageLink: String? = null

    childNodes.forEach { node ->
        when (node.nodeName) {
            "name" -> name = node.textContent
            "label" -> label = node.textContent
            "icon" -> icon = node.textContent
            "homepage" -> node.childNodes.forEach { pageNode ->
                if (pageNode.nodeName == "link") {
                    homepageLink = pageNode.textContent
                }
            }
        }
    }

    val finalName = name ?: return null
    val finalLink = homepageLink ?: return null
    return Sitemap(finalName, label ?: finalName, icon.toOH1IconResource(), finalLink)
}

fun JSONObject.toSitemap(): Sitemap? {
    val name = optStringOrNull("name") ?: return null
    val homepageLink = optJSONObject("homepage")?.optStringOrNull("link") ?: return null
    val label = optStringOrNull("label")
    val icon = optStringOrNull("icon")

    return Sitemap(name, label ?: name, icon.toOH2IconResource(), homepageLink)
}

fun Document.toSitemapList(): List<Sitemap> {
    val sitemapNodes = getElementsByTagName("sitemap")
    return (0 until sitemapNodes.length).mapNotNull { index -> sitemapNodes.item(index).toSitemap() }
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
    return sortedWith(Comparator { lhs, rhs ->
        when (defaultSitemapName) {
            lhs.name -> -1
            rhs.name -> 1
            else -> lhs.label.compareTo(rhs.label, true)
        }
    })
}
