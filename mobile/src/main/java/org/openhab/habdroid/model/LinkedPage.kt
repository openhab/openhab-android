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

/**
 * This is a class to hold information about openHAB linked page.
 */

@Parcelize
data class LinkedPage(val id: String?, val title: String?, val icon: String?, val link: String?) : Parcelable {
    companion object {
        fun fromXml(startNode: Node): LinkedPage {
            var id: String? = null
            var title: String? = null
            var icon: String? = null
            var link: String? = null

            if (startNode.hasChildNodes()) {
                val childNodes = startNode.childNodes
                for (i in 0 until childNodes.length) {
                    val childNode = childNodes.item(i)
                    when (childNode.nodeName) {
                        "id" -> id = childNode.textContent
                        "title" -> title = childNode.textContent
                        "icon" -> icon = childNode.textContent
                        "link" -> link = childNode.textContent
                        else -> { }
                    }
                }
            }

            return build(id, title, icon, link)
        }

        fun fromJson(jsonObject: JSONObject?): LinkedPage? {
            if (jsonObject == null) {
                return null
            }
            return build(jsonObject.optString("id", null),
                    jsonObject.optString("title", null),
                    jsonObject.optString("icon", null),
                    jsonObject.optString("link", null))
        }

        private fun build(id: String?, title: String?, icon: String?, link: String?): LinkedPage {
            val actualTitle = if (title != null && title.indexOf('[') > 0)
                    title.substring(0, title.indexOf('[')) else title
            return LinkedPage(id, actualTitle, icon, link)
        }
    }
}
