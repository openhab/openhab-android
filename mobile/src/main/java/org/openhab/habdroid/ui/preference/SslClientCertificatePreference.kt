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

package org.openhab.habdroid.ui.preference

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.security.KeyChain
import android.security.KeyChainException
import android.security.keystore.KeyProperties
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.setupHelpIcon
import org.openhab.habdroid.ui.updateHelpIconAlpha
import org.openhab.habdroid.util.ToastType
import org.openhab.habdroid.util.showToast

class SslClientCertificatePreference constructor(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    private var currentAlias: String? = null
    private var helpIcon: ImageView? = null

    init {
        widgetLayoutResource = R.layout.help_icon_pref
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        if (holder != null) {
            helpIcon = holder.itemView.findViewById(R.id.help_icon)
            helpIcon?.setupHelpIcon(context.getString(R.string.settings_openhab_sslclientcert_howto_url),
                    context.getString(R.string.settings_openhab_sslclientcert_howto_summary))
            helpIcon?.updateHelpIconAlpha(isEnabled)
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        setValue(getPersistedString(defaultValue as String?))
    }

    override fun onClick() {
        val keyTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(KeyProperties.KEY_ALGORITHM_RSA, KeyProperties.KEY_ALGORITHM_EC)
        } else {
            arrayOf("RSA", "DSA")
        }

        try {
            KeyChain.choosePrivateKeyAlias(getActivity(), { handleAliasChosen(it) }, keyTypes, null, null, -1, null)
        } catch (e: ActivityNotFoundException) {
            context.showToast(R.string.settings_openhab_sslclientcert_not_supported, ToastType.ERROR)
        }
    }

    override fun onDependencyChanged(dependency: Preference, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        helpIcon?.updateHelpIconAlpha(isEnabled)
    }

    private fun getActivity(): Activity {
        var c = context
        loop@ while (c != null) {
            when (c) {
                is Activity -> return c
                is ContextThemeWrapper -> c = c.baseContext
                else -> break@loop
            }
        }
        throw IllegalStateException("Unknown context $c")
    }

    private fun handleAliasChosen(alias: String?) {
        if (callChangeListener(alias)) {
            setValue(alias)
        }
    }

    private fun setValue(value: String?) {
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
                null
            } catch (e: InterruptedException) {
                null
            }
        }
        summary = cert?.subjectDN?.toString() ?: context.getString(R.string.settings_openhab_none)
    }
}
