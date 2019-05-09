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
data class Sitemap(val name: String, val label: String, val link: String?,
                   val icon: String?, val iconPath: String, val homepageLink: String?): Parcelable {
    companion object {
        fun fromXml(startNode: Node): Sitemap? {
            var label: String? = null
            var name: String? = null
            var icon: String? = null
            var link: String? = null
            var homepageLink: String? = null

            if (startNode.hasChildNodes()) {
                val childNodes = startNode.childNodes
                for (i in 0 until childNodes.length) {
                    val childNode = childNodes.item(i)
                    when (childNode.nodeName) {
                        "name" -> name = childNode.textContent
                        "label" -> label = childNode.textContent
                        "link" -> link = childNode.textContent
                        "icon" -> icon = childNode.textContent
                        "homepage" -> if (childNode.hasChildNodes()) {
                            val homepageNodes = childNode.childNodes
                            for (j in 0 until homepageNodes.length) {
                                val homepageChildNode = homepageNodes.item(j)
                                if (homepageChildNode.nodeName == "link") {
                                    homepageLink = homepageChildNode.textContent
                                    break
                                }
                            }
                        }
                        else -> {
                        }
                    }
                }
            }

            if (name == null) {
                return null
            }
            return Sitemap(name, label ?: name, link, icon,
                    String.format("images/%s.png", icon), homepageLink)
        }

        fun fromJson(jsonObject: JSONObject): Sitemap? {
            val name = jsonObject.optString("name", null)
            val label = jsonObject.optString("label", null)
            val icon = jsonObject.optString("icon", null)
            val link = jsonObject.optString("link", null)
            val homepageObject = jsonObject.optJSONObject("homepage")
            val homepageLink = homepageObject?.optString("link", null)

            if (name == null) {
                return null
            }

            return Sitemap(name, label ?: name, link, icon,
                    String.format("icon/%s", icon), homepageLink)
        }
    }
}
