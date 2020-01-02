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

import android.net.Uri

data class NfcTag(
    val sitemap: String?,
    val item: String?,
    val label: String?,
    val state: String?,
    val mappedState: String?
) {
    companion object {
        const val SCHEME = "openhab"
        const val QUERY_PARAMETER_ITEM_NAME = "i"
        const val DEPRECATED_QUERY_PARAMETER_ITEM_NAME = "item"
        const val QUERY_PARAMETER_STATE = "s"
        const val DEPRECATED_QUERY_PARAMETER_STATE = "command"
        const val QUERY_PARAMETER_MAPPED_STATE = "m"
        const val QUERY_PARAMETER_ITEM_LABEL = "l"
    }
}

fun Uri.toTagData(): NfcTag? {
    if (scheme != NfcTag.SCHEME) {
        return null
    }
    val item = if (NfcTag.DEPRECATED_QUERY_PARAMETER_ITEM_NAME in queryParameterNames)
        getQueryParameter(NfcTag.DEPRECATED_QUERY_PARAMETER_ITEM_NAME)
    else
        getQueryParameter(NfcTag.QUERY_PARAMETER_ITEM_NAME)

    val state = if (NfcTag.DEPRECATED_QUERY_PARAMETER_STATE in queryParameterNames)
        getQueryParameter(NfcTag.DEPRECATED_QUERY_PARAMETER_STATE)
    else
        getQueryParameter(NfcTag.QUERY_PARAMETER_STATE)

    val label = getQueryParameter(NfcTag.QUERY_PARAMETER_ITEM_LABEL)
    val mappedState = getQueryParameter(NfcTag.QUERY_PARAMETER_MAPPED_STATE)
    val sitemapPath = path
    val sitemap = if (sitemapPath?.isNotEmpty() == true) sitemapPath else null

    return NfcTag(sitemap, item, label, state, mappedState)
}
