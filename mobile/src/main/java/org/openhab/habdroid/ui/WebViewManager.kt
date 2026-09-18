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

package org.openhab.habdroid.ui

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getPrefs

/**
 * Servers may share an origin (e.g. all myopenhab.org users, or the same local IP at different sites),
 * so HTTP cache, HTTP auth cache, cookies and DOM storage must not be shared between them.
 */
class WebViewManager private constructor(private val appContext: Context) {
    private val hasProfileSupport = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    private var lastServerId: Int? = null

    /**
     * Must be called before the WebView is used for anything else.
     */
    fun setUpForActiveServer(webView: WebView) {
        val serverId = appContext.getPrefs().getActiveServerId()
        if (hasProfileSupport) {
            WebViewCompat.setProfile(webView, buildProfileName(serverId))
        } else if (serverId != lastServerId) {
            // The HTTP cache is persisted, so it may hold data of another server on first use as well
            webView.clearCache(true)
            WebViewDatabase.getInstance(appContext).clearHttpAuthUsernamePassword()
        }
        lastServerId = serverId
    }

    fun clearCaches() {
        WebView(appContext).clearCache(true)
        if (hasProfileSupport) {
            ProfileStore.getInstance().allProfileNames
                .filter { it != Profile.DEFAULT_PROFILE_NAME }
                .forEach { name -> clearCache(name) }
        }
    }

    fun deleteDataForServer(serverId: Int) {
        if (!hasProfileSupport) {
            return
        }
        val name = buildProfileName(serverId)
        val store = ProfileStore.getInstance()
        try {
            store.deleteProfile(name)
        } catch (e: IllegalStateException) {
            // Profile is in use by a WebView. Server IDs are reused, so don't leave the data for the next server.
            store.getProfile(name)?.let { profile ->
                profile.cookieManager.removeAllCookies(null)
                profile.webStorage.deleteAllData()
                clearCache(name)
            }
        }
    }

    private fun clearCache(profileName: String) {
        val webView = WebView(appContext)
        WebViewCompat.setProfile(webView, profileName)
        webView.clearCache(true)
        webView.destroy()
    }

    private fun buildProfileName(serverId: Int) = "server_$serverId"

    companion object {
        private var instance: WebViewManager? = null

        fun getInstance(context: Context): WebViewManager {
            val inst = instance ?: WebViewManager(context.applicationContext)
            instance = inst
            return inst
        }
    }
}
