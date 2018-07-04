package org.openhab.habdroid.ui.widget;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.openhab.habdroid.R;

import java.security.cert.X509Certificate;

public class SSLClientCertificatePreference extends Preference {
    private Activity mActivity;
    private String mCurrentAlias;
    private ImageView mHelpIcon;

    public SSLClientCertificatePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SSLClientCertificatePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @TargetApi(21)
    public SSLClientCertificatePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        assert context instanceof Activity;
        mActivity = (Activity) context;
        setWidgetLayoutResource(R.layout.ssl_client_cert_pref);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);

        mHelpIcon = view.findViewById(R.id.help_icon);

        final Context context = getContext();
        Uri howToUri = Uri.parse(context.getString(R.string.settings_openhab_sslclientcert_howto_url));
        final Intent intent = new Intent(Intent.ACTION_VIEW, howToUri);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            mHelpIcon.setOnClickListener(v -> context.startActivity(intent));
            updateHelpIconAlpha();
        } else {
            mHelpIcon.setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String defaultString = (String) defaultValue;
        setValue(restorePersistedValue ? getPersistedString(defaultString) : defaultString);
    }

    @Override
    protected void onClick() {
        final String[] keyTypes = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? new String[] { KeyProperties.KEY_ALGORITHM_RSA, KeyProperties.KEY_ALGORITHM_EC }
                : new String[] { "RSA", "DSA"};
        KeyChain.choosePrivateKeyAlias(mActivity, this::handleAliasChosen,
                keyTypes, null, null, -1, null);
    }

    @Override
    public void onDependencyChanged(Preference dependency, boolean disableDependent) {
        super.onDependencyChanged(dependency, disableDependent);
        updateHelpIconAlpha();
    }

    private void updateHelpIconAlpha() {
        if (mHelpIcon != null) {
            mHelpIcon.setAlpha(isEnabled() ? 1.0f : 0.5f);
        }
    }

    private void handleAliasChosen(String alias) {
        if (callChangeListener(alias)) {
            setValue(alias);
        }
    }

    private void setValue(String value) {
        boolean changed = !TextUtils.equals(mCurrentAlias, value);
        if (changed || mCurrentAlias == null) {
            mCurrentAlias = value;
            persistString(value);
            updateSummary(value);
            if (changed) {
                notifyChanged();
            }
        }
    }

    private void updateSummary(final String alias) {
        new AsyncTask<Preference, Void, X509Certificate>() {
            @Override
            protected X509Certificate doInBackground(Preference... preferences) {
                try {
                    if (alias != null) {
                        X509Certificate[] certificates =
                                KeyChain.getCertificateChain(getContext(), alias);
                        if (certificates != null && certificates.length > 0) {
                            return certificates[0];
                        }
                    }
                    return null;
                } catch (KeyChainException | InterruptedException e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(X509Certificate cert) {
                if (cert != null) {
                    setSummary(cert.getSubjectDN().toString());
                } else {
                    setSummary(getContext().getString(R.string.settings_openhab_none));
                }
            }
        }.execute();
    }
}
