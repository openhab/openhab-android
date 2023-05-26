/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.preference.widgets

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.security.KeyChain
import android.security.KeyChainException
import android.security.keystore.KeyProperties
import android.util.AttributeSet
import android.util.Log
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.ui.setupHelpIcon

class SslClientCertificatePreference constructor(context: Context, attrs: AttributeSet) :
    Preference(context, attrs), CoroutineScope by CoroutineScope(Dispatchers.Main) {
    private var currentAlias: String? = null
    private var helpIcon: ImageView? = null

    init {
        widgetLayoutResource = R.layout.help_icon_pref
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        helpIcon = holder.itemView.findViewById(R.id.help_icon)
        helpIcon?.setupHelpIcon(
            context.getString(R.string.settings_openhab_sslclientcert_howto_url),
            R.string.settings_openhab_sslclientcert_howto_summary
        )
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        setValue(getPersistedString(defaultValue as String?))
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary(currentAlias)
    }

    @SuppressLint("WrongConstant")
    override fun onClick() {
        val keyTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(KeyProperties.KEY_ALGORITHM_RSA, KeyProperties.KEY_ALGORITHM_EC)
        } else {
            arrayOf("RSA", "DSA")
        }

        try {
            Log.d(TAG, "Query for key types: ${keyTypes.contentToString()}")
            KeyChain.choosePrivateKeyAlias(getActivity(), { handleAliasChosen(it) }, keyTypes, null, null, -1, null)
        } catch (e: ActivityNotFoundException) {
            (getActivity() as PreferencesActivity).showSnackbar(
                PreferencesActivity.SNACKBAR_TAG_CLIENT_SSL_NOT_SUPPORTED,
                R.string.settings_openhab_sslclientcert_not_supported,
                Snackbar.LENGTH_LONG
            )
        }
    }

    private fun getActivity(): Activity {
        var c = context
        loop@ while (true) {
            when (c) {
                is Activity -> return c
                is ContextThemeWrapper -> c = c.baseContext
                else -> break@loop
            }
        }
        throw IllegalStateException("Unknown context $c")
    }

    private fun handleAliasChosen(alias: String?) = launch {
        Log.d(TAG, "handleAliasChosen($alias)")
        if (callChangeListener(alias)) {
            setValue(alias)
        }
    }

    fun setValue(value: String?) {
        val changed = value != currentAlias
        if (changed || currentAlias == null) {
            currentAlias = value
            persistString(value)
            updateSummary(value)
            if (changed) {
                notifyChanged()
            }
        }
    }

    private fun updateSummary(alias: String?) = GlobalScope.launch(Dispatchers.Main) {
        val cert = withContext(Dispatchers.Default) {
            try {
                if (alias != null) {
                    val certificates = KeyChain.getCertificateChain(context, alias)
                    certificates?.firstOrNull()
                } else {
                    null
                }
            } catch (e: KeyChainException) {
                Log.d(TAG, "Error getting key for summary", e)
                null
            } catch (e: InterruptedException) {
                Log.d(TAG, "Error getting key for summary", e)
                null
            }
        }
        Log.d(TAG, "Got cert $cert for alias $alias")
        summary = cert?.subjectDN?.toString() ?: context.getString(R.string.settings_openhab_none)
    }

    companion object {
        private val TAG = SslClientCertificatePreference::class.java.simpleName
    }
}
