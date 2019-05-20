/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.forEach
import org.w3c.dom.Node

/**
 * This class provides datasource for openHAB widgets from sitemap page.
 * It uses a sitemap page XML document to create a list of widgets
 */

class WidgetDataSource(private val iconFormat: String) {
    private val allWidgets = ArrayList<Widget>()
    var title: String = ""
        private set
    var id: String? = null
        private set
    var icon: String? = null
        private set
    var link: String? = null
        private set

    val widgets: List<Widget>
        get() {
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
                obj -> allWidgets.addAll(obj.collectWidgets(null, iconFormat))
            }
            id = jsonObject.optString("id", null)
            title = jsonObject.optString("title", id.orEmpty())
            icon = jsonObject.optString("icon", null)
            link = jsonObject.optString("link", null)
        } catch (e: JSONException) {
            Log.d(TAG, e.message, e)
        }
    }

    companion object {
        private val TAG = WidgetDataSource::class.java.simpleName
    }
}
