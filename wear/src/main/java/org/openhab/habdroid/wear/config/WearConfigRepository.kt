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

package org.openhab.habdroid.wear.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dev.spght.encryptedprefs.EncryptedSharedPreferences
import dev.spght.encryptedprefs.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.core.wear.WearConfig
import org.openhab.habdroid.model.ServerPath

/**
 * Stores the config received from the phone. Credentials are part of the config, so it's stored encrypted.
 */
class WearConfigRepository(private val context: Context) {
    sealed interface Settings {
        /**
         * No config has been received from the phone yet
         */
        data object NotConfigured : Settings

        data class Configured(val config: WearConfig, val isDemo: Boolean) : Settings
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences(
            context = context,
            fileName = "wear_config_encrypted",
            masterKey = MasterKey(context)
        )
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val config get() = (settings.value as? Settings.Configured)?.config

    /**
     * Store the config received from the phone. This ends the demo mode.
     *
     * @param config The new config, or null if the phone has no server configured
     */
    fun saveConfigFromPhone(config: WearConfig?) {
        Log.d(TAG, "Received config from phone: $config")
        prefs.edit {
            putString(KEY_CONFIG, config?.toJson())
            putBoolean(KEY_DEMO, false)
        }
        _settings.value = load()
    }

    fun setDemoMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DEMO, enabled) }
        _settings.value = load()
    }

    /**
     * Read the config from the Data Layer, e.g. if the app was installed after the phone published it.
     * Asks the phone app to publish its config if there's none yet.
     */
    suspend fun syncFromDataLayer() {
        try {
            val items = Wearable.getDataClient(context).dataItems.await()
            val item = items.firstOrNull { item -> item.uri.path == WearConfig.DATA_PATH }
            val json = item?.let {
                DataMapItem.fromDataItem(it)
                    .dataMap
                    .getString(WearConfig.DATA_KEY_CONFIG)
            }
            items.release()

            if (json != null) {
                val config = WearConfig.fromJson(json)
                if (config != this.config && !isDemo) {
                    saveConfigFromPhone(config)
                }
            } else {
                requestConfigFromPhone()
            }
        } catch (e: Exception) {
            // Thrown if Play services or the Wearable API aren't available
            Log.w(TAG, "Unable to read config from Data Layer", e)
        }
    }

    suspend fun requestConfigFromPhone() {
        sendMessageToPhone(WearConfig.MESSAGE_PATH_REQUEST_CONFIG)
    }

    /**
     * @return true if the message has been sent to at least one phone
     */
    suspend fun sendMessageToPhone(path: String): Boolean = try {
        val nodes = Wearable.getCapabilityClient(context)
            .getCapability(WearConfig.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
        Log.d(TAG, "Send $path to ${nodes.size} phone(s)")
        nodes.forEach { node -> Wearable.getMessageClient(context).sendMessage(node.id, path, null).await() }
        nodes.isNotEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Unable to send message $path to phone", e)
        false
    }

    private val isDemo get() = (settings.value as? Settings.Configured)?.isDemo == true

    private fun load(): Settings {
        if (prefs.getBoolean(KEY_DEMO, false)) {
            return Settings.Configured(DEMO_CONFIG, true)
        }
        val config = prefs.getString(KEY_CONFIG, null)
            ?.let { json -> WearConfig.fromJson(json) }
            ?.takeIf { config -> config.hasConnection }
            ?: return Settings.NotConfigured
        return Settings.Configured(config, false)
    }

    companion object {
        private val TAG = WearConfigRepository::class.java.simpleName
        private const val KEY_CONFIG = "config"
        private const val KEY_DEMO = "demo"

        private val DEMO_CONFIG = WearConfig(
            localPath = null,
            remotePath = ServerPath("https://demo.openhab.org/", null, null),
            sitemapName = "demo",
            complicationGroup = null,
            trustedCertificates = emptyList()
        )
    }
}
