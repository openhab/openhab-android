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

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import java.util.ArrayList
import java.util.HashSet

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

    val widgets: ArrayList<Widget>
        get() {
            val result = ArrayList<Widget>()
            val firstLevelWidgetIds = HashSet<String>()
            for ((widgetId, parentId) in allWidgets) {
                if (parentId == null) {
                    firstLevelWidgetIds.add(widgetId)
                }
            }
            for (widget in allWidgets) {
                val parentId = widget.parentId
                if (parentId == null || firstLevelWidgetIds.contains(parentId)) {
                    result.add(widget)
                }
            }
            return result
        }

    fun setSourceNode(rootNode: Node?) {
        if (rootNode == null) {
            return
        }
        if (rootNode.hasChildNodes()) {
            val childNodes = rootNode.childNodes
            for (i in 0 until childNodes.length) {
                val childNode = childNodes.item(i)
                when (childNode.nodeName) {
                    "widget" -> allWidgets.addAll(childNode.collectWidgets(null))
                    "title" -> title = childNode.textContent ?: ""
                    "id" -> id = childNode.textContent
                    "icon" -> icon = childNode.textContent
                    "link" -> link = childNode.textContent
                    else -> { }
                }
            }
        }
    }

    fun setSourceJson(jsonObject: JSONObject) {
        if (!jsonObject.has("widgets")) {
            return
        }
        try {
            val jsonWidgetArray = jsonObject.getJSONArray("widgets")
            for (i in 0 until jsonWidgetArray.length()) {
                val widgetJson = jsonWidgetArray.getJSONObject(i)
                allWidgets.addAll(widgetJson.collectWidgets(null, iconFormat))
            }
            id = jsonObject.optString("id", null)
            title = jsonObject.optString("title", id ?: "")
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
