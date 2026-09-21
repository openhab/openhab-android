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

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getPrefs

/**
 * Servers may share an origin (e.g. all myopenhab.org users, or the same local IP at different sites),
 * so HTTP cache, HTTP auth cache, cookies and DOM storage must not be shared between them.
 */
@SuppressLint("RequiresFeature")
class WebViewManager(private val appContext: Application) {
    // Lazy, as this loads the WebView implementation
    private val hasProfileSupport by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }

    // SharedPreferences only holds a weak reference to its listeners
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PrefKeys.ACTIVE_SERVER_ID && !hasProfileSupport) {
            WebView(appContext).clearCache(true)
            WebViewDatabase.getInstance(appContext).clearHttpAuthUsernamePassword()
        }
    }

    /**
     * Must be called on app start: The HTTP cache is persisted, so a server change is missed otherwise
     * if it happens before the first WebView usage.
     */
    fun start() {
        appContext.getPrefs().registerOnSharedPreferenceChangeListener(prefsListener)
    }

    /**
     * Must be called before the WebView is used for anything else.
     */
    fun setUpForActiveServer(webView: WebView) {
        val serverId = appContext.getPrefs().getActiveServerId()
        if (hasProfileSupport) {
            WebViewCompat.setProfile(webView, buildProfileName(serverId))
        }
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
        } catch (_: IllegalStateException) {
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
}
