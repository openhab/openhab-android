package org.openhab.habdroid.model

import android.net.Uri

data class NfcTag(val sitemap: String?, val item: String?, val label: String?,
                  val state: String?, val mappedState: String?) {
    companion object {
        val SCHEME = "openhab"
        val QUERY_PARAMETER_ITEM_NAME = "i"
        val DEPRECATED_QUERY_PARAMETER_ITEM_NAME = "item"
        val QUERY_PARAMETER_STATE = "s"
        val DEPRECATED_QUERY_PARAMETER_STATE = "command"
        val QUERY_PARAMETER_MAPPED_STATE = "m"
        val QUERY_PARAMETER_ITEM_LABEL = "l"
    }
}

fun Uri.toTagData(): NfcTag? {
    if (scheme != NfcTag.SCHEME) {
        return null
    }
    val item = if (queryParameterNames.contains(NfcTag.DEPRECATED_QUERY_PARAMETER_ITEM_NAME))
        getQueryParameter(NfcTag.DEPRECATED_QUERY_PARAMETER_ITEM_NAME)
    else
        getQueryParameter(NfcTag.QUERY_PARAMETER_ITEM_NAME)
    val label = getQueryParameter(NfcTag.QUERY_PARAMETER_ITEM_LABEL)
    val state = if (queryParameterNames.contains(NfcTag.DEPRECATED_QUERY_PARAMETER_STATE))
        getQueryParameter(NfcTag.DEPRECATED_QUERY_PARAMETER_STATE)
    else
        getQueryParameter(NfcTag.QUERY_PARAMETER_STATE)
    val mappedState = getQueryParameter(NfcTag.QUERY_PARAMETER_MAPPED_STATE)
    val sitemapPath = path
    val sitemap = if (sitemapPath?.isNotEmpty() == true) sitemapPath else null

    return NfcTag(sitemap, item, label, state, mappedState)
}
