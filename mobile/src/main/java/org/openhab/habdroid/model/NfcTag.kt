package org.openhab.habdroid.model

import android.net.Uri
import android.text.TextUtils

import com.google.auto.value.AutoValue

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

        fun fromTagData(uri: Uri?): NfcTag? {
            if (uri == null || SCHEME != uri.scheme) {
                return null
            }
            val sitemap = uri.path
            val item = if (TextUtils.isEmpty(uri.getQueryParameter(DEPRECATED_QUERY_PARAMETER_ITEM_NAME)))
                uri.getQueryParameter(QUERY_PARAMETER_ITEM_NAME)
            else
                uri.getQueryParameter(DEPRECATED_QUERY_PARAMETER_ITEM_NAME)
            val label = uri.getQueryParameter(QUERY_PARAMETER_ITEM_LABEL)
            val state = if (TextUtils.isEmpty(uri.getQueryParameter(DEPRECATED_QUERY_PARAMETER_STATE)))
                uri.getQueryParameter(QUERY_PARAMETER_STATE)
            else
                uri.getQueryParameter(DEPRECATED_QUERY_PARAMETER_STATE)
            val mappedState = uri.getQueryParameter(QUERY_PARAMETER_MAPPED_STATE)

            val actualSitemap = if (!TextUtils.isEmpty(sitemap) && TextUtils.isEmpty(item)) sitemap else null
            return NfcTag(actualSitemap, item, label, state, mappedState)
        }
    }
}
