package org.openhab.habdroid.util;

import android.content.Context;
import android.preference.PreferenceManager;

import com.loopj.android.http.SyncHttpClient;

import org.apache.http.conn.ssl.SSLSocketFactory;

import javax.net.ssl.SSLContext;

import de.duenndns.ssl.MemorizingTrustManager;

/**
 * Created by tamon on 08.05.15.
 */
public class MySyncHttpClient extends SyncHttpClient {

    private SSLContext sslContext;
    private SSLSocketFactory sslSocketFactory;

    public MySyncHttpClient(Context ctx) {
        super();
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, MemorizingTrustManager.getInstanceList(ctx), new java.security.SecureRandom());
            sslSocketFactory = new MySSLSocketFactory(sslContext);
            if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(Constants.PREFERENCE_SSLHOST, false)) {
                sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            }
            this.setSSLSocketFactory(sslSocketFactory);
        } catch (Exception ex) {
        }
    }
}
