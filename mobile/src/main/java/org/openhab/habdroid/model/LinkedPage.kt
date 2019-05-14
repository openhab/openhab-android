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
data class LinkedPage(val id: String?, val title: String?, val icon: String?, val link: String) : Parcelable {
    companion object {
        internal fun build(id: String?, title: String?, icon: String?, link: String?): LinkedPage? {
            if (link == null) {
                return null
            }
            val actualTitle = if (title != null && title.indexOf('[') > 0)
                    title.substring(0, title.indexOf('[')) else title
            return LinkedPage(id, actualTitle, icon, link)
        }
    }
}

fun Node.toLinkedPage(): LinkedPage? {
    var id: String? = null
    var title: String? = null
    var icon: String? = null
    var link: String? = null

    if (hasChildNodes()) {
        for (i in 0 until childNodes.length) {
            with (childNodes.item(i)) {
                when (nodeName) {
                    "id" -> id = textContent
                    "title" -> title = textContent
                    "icon" -> icon = textContent
                    "link" -> link = textContent
                }
            }
        }
    }

    return LinkedPage.build(id, title, icon, link)
}

fun JSONObject?.toLinkedPage(): LinkedPage? {
    if (this == null) {
        return null
    }
    return LinkedPage.build(optString("id", null),
            optString("title", null),
            optString("icon", null),
            optString("link", null))
}

