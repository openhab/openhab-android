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

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.forEach
import org.openhab.habdroid.util.optStringOrNull
import org.w3c.dom.Node

/**
 * This class provides datasource for openHAB widgets from sitemap page.
 * It uses a sitemap page XML document to create a list of widgets
 */

class WidgetDataSource() {
    private val allWidgets = ArrayList<Widget>()
    var title: String = ""
        private set
    var id: String? = null
        private set
    var icon: String? = null
        private set
    var link: String? = null
        private set

    val widgets: List<Widget> get() {
        val firstLevelWidgetIds = allWidgets
            .filter { w -> w.parentId == null }
            .map { w -> w.id }
            .toSet()
        return allWidgets
            .filter { w -> w.parentId == null || w.parentId in firstLevelWidgetIds }
    }

    fun setSourceNode(rootNode: Node?) {
        if (rootNode == null) {
            return
        }
        rootNode.childNodes.forEach { node ->
            when (node.nodeName) {
                "widget" -> allWidgets.addAll(node.collectWidgets(null))
                "title" -> title = node.textContent.orEmpty()
                "id" -> id = node.textContent
                "icon" -> icon = node.textContent
                "link" -> link = node.textContent
                else -> { }
            }
        }
    }

    fun setSourceJson(jsonObject: JSONObject) {
        if (!jsonObject.has("widgets")) {
            return
        }
        try {
            jsonObject.getJSONArray("widgets").forEach {
                    obj -> allWidgets.addAll(obj.collectWidgets(null))
            }
            id = jsonObject.optStringOrNull("id")
            title = jsonObject.optString("title", id.orEmpty())
            icon = jsonObject.optStringOrNull("icon")
            link = jsonObject.optStringOrNull("link")
        } catch (e: JSONException) {
            Log.d(TAG, e.message, e)
        }
    }

    companion object {
        private val TAG = WidgetDataSource::class.java.simpleName
    }
}
