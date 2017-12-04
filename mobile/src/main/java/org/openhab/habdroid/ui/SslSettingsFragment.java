package org.openhab.habdroid.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.Preference;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.security.keystore.KeyProperties;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;

import java.security.cert.X509Certificate;

public class SslSettingsFragment extends OpenHABPreferencesActivity.SettingsFragment {
    @Override
    protected String getTitle() {
        return getString(R.string.settings_openhab_sslsettings);
    }

    @Override
    protected void updateAndInitPreferences() {
        addPreferencesFromResource(R.xml.ssl_preferences);

        final Preference sslClientCert = getPreferenceScreen().findPreference(Constants.PREFERENCE_SSLCLIENTCERT);
        final Preference sslClientCertHowTo = getPreferenceScreen().findPreference(Constants.PREFERENCE_SSLCLIENTCERT_HOWTO);

        updateSslClientCertSummary(sslClientCert);

        final KeyChainAliasCallback keyChainAliasCallback = new KeyChainAliasCallback() {
            @Override
            public void alias(String alias) {
                sslClientCert.getSharedPreferences().edit().putString(sslClientCert.getKey(), alias).apply();
                updateSslClientCertSummary(sslClientCert);
            }
        };

        sslClientCert.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                sslClientCert.getSharedPreferences().edit().putString(sslClientCert.getKey(), null).apply();

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                    KeyChain.choosePrivateKeyAlias(getActivity(),
                            keyChainAliasCallback,
                            new String[]{"RSA", "DSA"},
                            null,
                            getPreferenceString(Constants.PREFERENCE_ALTURL, null),
                            -1, null);
                } else {
                    KeyChain.choosePrivateKeyAlias(getActivity(),
                            keyChainAliasCallback,
                            new String[]{KeyProperties.KEY_ALGORITHM_RSA, KeyProperties.KEY_ALGORITHM_EC},
                            null,
                            Uri.parse(getPreferenceString(Constants.PREFERENCE_ALTURL, null)),
                            null);
                }

                return true;
            }
        });

        sslClientCertHowTo.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Uri howToUri = Uri.parse(getString(R.string.settings_openhab_sslclientcert_howto_url));
                Intent intent = new Intent(Intent.ACTION_VIEW, howToUri);
                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                }
                return true;
            }
        });
    }

    private void updateSslClientCertSummary(final Preference sslClientCert) {
        final String certAlias = getPreferenceString(sslClientCert, null);

        new AsyncTask<Preference, Void, X509Certificate>() {
            @Override
            protected X509Certificate doInBackground(Preference... preferences) {
                try {
                    if (certAlias != null) {
                        X509Certificate[] certificates = KeyChain.getCertificateChain(
                                getActivity(), certAlias);
                        if (certificates != null && certificates.length > 0) {
                            return certificates[0];
                        }
                    }
                    return null;
                } catch (KeyChainException | InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(X509Certificate x509Certificate) {
                if (x509Certificate != null) {
                    sslClientCert.setSummary(x509Certificate.getSubjectDN().toString());
                } else {
                    sslClientCert.setSummary(getString(R.string.settings_openhab_none));
                }
            }
        }.execute(sslClientCert);
    }
}
