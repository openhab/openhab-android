/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model

import android.os.Parcelable

import kotlinx.android.parcel.Parcelize
import org.json.JSONObject
import org.w3c.dom.Node

@Parcelize
data class Sitemap internal constructor(val name: String, val label: String, val link: String?,
                                        val icon: String?, val iconPath: String,
                                        val homepageLink: String): Parcelable {}

fun Node.toSitemap(): Sitemap? {
    var label: String? = null
    var name: String? = null
    var icon: String? = null
    var link: String? = null
    var homepageLink: String? = null

    if (hasChildNodes()) {
        for (i in 0 until childNodes.length) {
            with (childNodes.item(i)) {
                when (nodeName) {
                    "name" -> name = textContent
                    "label" -> label = textContent
                    "link" -> link = textContent
                    "icon" -> icon = textContent
                    "homepage" -> if (hasChildNodes()) {
                        val homepageNodes = childNodes
                        for (j in 0 until homepageNodes.length) {
                            val homepageChildNode = homepageNodes.item(j)
                            if (homepageChildNode.nodeName == "link") {
                                homepageLink = homepageChildNode.textContent
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    val finalName = name ?: return null
    val finalLink = homepageLink ?: return null
    return Sitemap(finalName, label ?: finalName, link, icon,
            String.format("images/%s.png", icon), finalLink)
}

fun JSONObject.toSitemap(): Sitemap? {
    val name = optString("name", null) ?: return null
    val homepageLink = optJSONObject("homepage")?.optString("link", null) ?: return null
    val label = optString("label", null)
    val icon = optString("icon", null)
    val link = optString("link", null)

    return Sitemap(name, label ?: name, link, icon,
            String.format("icon/%s", icon), homepageLink)
}
