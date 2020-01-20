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

import kotlinx.android.parcel.Parcelize
import org.json.JSONObject
import org.openhab.habdroid.util.forEach
import org.openhab.habdroid.util.optStringOrNull
import org.w3c.dom.Node

/**
 * This is a class to hold information about openHAB linked page.
 */

@Parcelize
data class LinkedPage(
    val id: String,
    val title: String,
    val icon: IconResource?,
    val link: String
) : Parcelable {
    companion object {
        internal fun build(
            id: String,
            title: String?,
            icon: IconResource?,
            link: String
        ): LinkedPage {
            val actualTitle = if (title != null && title.indexOf('[') > 0)
                title.substring(0, title.indexOf('[')) else title
            return LinkedPage(id, actualTitle.orEmpty(), icon, link)
        }
    }
}

fun Node.toLinkedPage(): LinkedPage? {
    var id: String? = null
    var title: String? = null
    var icon: String? = null
    var link: String? = null

    childNodes.forEach { node ->
        when (node.nodeName) {
            "id" -> id = node.textContent
            "title" -> title = node.textContent
            "icon" -> icon = node.textContent
            "link" -> link = node.textContent
        }
    }

    val finalId = id ?: return null
    val finalLink = link ?: return null
    return LinkedPage.build(finalId, title, icon.toOH1IconResource(), finalLink)
}

fun JSONObject?.toLinkedPage(): LinkedPage? {
    if (this == null) {
        return null
    }
    val icon = optStringOrNull("icon")
    return LinkedPage.build(
        getString("id"),
        optStringOrNull("title"),
        icon.toOH2IconResource(),
        getString("link"))
}
