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

package org.openhab.habdroid.wear.util

import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.IconFormat
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.model.sortedWithDefaultName

private const val SOURCE_ID_BASE = "org.openhab.android.wear"

/**
 * @param name Name of the sitemap selected on the phone
 * @return The selected sitemap, or the first sitemap if none is selected or it doesn't exist on the server
 */
fun ServerProperties.findWearSitemap(name: String?): Sitemap? =
    sitemaps.firstOrNull { sitemap -> sitemap.name == name } ?: sitemaps.sortedWithDefaultName("").firstOrNull()

fun buildSourceId(sitemap: Sitemap?, pageUrl: String?): String {
    if (sitemap == null) {
        return SOURCE_ID_BASE
    }
    val pageId = pageUrl?.substringAfterLast('/')
    return "org.openhab.ui.basic$${sitemap.name}:$pageId=>$SOURCE_ID_BASE"
}

fun IconResource.toUrl(serverFlags: Int, sizePixels: Int): String {
    val format = if (serverFlags and ServerProperties.SERVER_FLAG_SUPPORTS_ANY_FORMAT_ICON != 0) {
        IconFormat.Svg
    } else {
        IconFormat.Png
    }
    return toUrl(true, format, sizePixels)
}

val Connection.isLocal get() = connectionType == Connection.TYPE_LOCAL
