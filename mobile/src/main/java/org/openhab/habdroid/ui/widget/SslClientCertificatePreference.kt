package org.openhab.habdroid.ui.widget

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.preference.Preference
import android.security.KeyChain
import android.security.KeyChainException
import android.security.keystore.KeyProperties
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.setupHelpIcon
import org.openhab.habdroid.ui.updateHelpIconAlpha
import java.security.cert.X509Certificate

class SslClientCertificatePreference : Preference {
    private lateinit var activity: Activity
    private var currentAlias: String? = null
    private var helpIcon: ImageView? = null

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    @TargetApi(21)
    constructor(context: Context, attrs: AttributeSet,
                defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context)
    }

    private fun init(context: Context) {
        assert(context is Activity)
        activity = context as Activity
        widgetLayoutResource = R.layout.help_icon_pref
    }

    override fun onCreateView(parent: ViewGroup): View {
        val view = super.onCreateView(parent)

        helpIcon = view.findViewById(R.id.help_icon)
        helpIcon?.setupHelpIcon(context.getString(R.string.settings_openhab_sslclientcert_howto_url),
                context.getString(R.string.settings_openhab_sslclientcert_howto_summary))
        helpIcon?.updateHelpIconAlpha(isEnabled)

        return view
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any) {
        val defaultString = defaultValue as String
        setValue(if (restorePersistedValue) getPersistedString(defaultString) else defaultString)
    }

    override fun onClick() {
        val keyTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            arrayOf(KeyProperties.KEY_ALGORITHM_RSA, KeyProperties.KEY_ALGORITHM_EC)
        else
            arrayOf("RSA", "DSA")
        KeyChain.choosePrivateKeyAlias(activity, { handleAliasChosen(it) },
                keyTypes, null, null, -1, null)
    }

    override fun onDependencyChanged(dependency: Preference, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        helpIcon?.updateHelpIconAlpha(isEnabled)
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

    private fun updateSummary(alias: String?) {
        object : AsyncTask<Preference, Void, X509Certificate>() {
            override fun doInBackground(vararg preferences: Preference): X509Certificate? {
                try {
                    if (alias != null) {
                        val certificates = KeyChain.getCertificateChain(context, alias)
                        if (certificates != null && certificates.size > 0) {
                            return certificates[0]
                        }
                    }
                    return null
                } catch (e: KeyChainException) {
                    return null
                } catch (e: InterruptedException) {
                    return null
                }

            }

            override fun onPostExecute(cert: X509Certificate?) {
                summary = cert?.subjectDN?.toString()
                        ?: context.getString(R.string.settings_openhab_none)
            }
        }.execute()
    }
}
