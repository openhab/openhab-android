/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.content.Context;
import android.preference.PreferenceManager;

import com.loopj.android.http.SyncHttpClient;

import javax.net.ssl.SSLContext;

import cz.msebera.android.httpclient.conn.ssl.SSLSocketFactory;
import de.duenndns.ssl.MemorizingTrustManager;

public class MySyncHttpClient extends SyncHttpClient {

	private SSLContext sslContext;
	private SSLSocketFactory sslSocketFactory;

	public MySyncHttpClient(Context ctx) {
        super();
		try {
	        sslContext = SSLContext.getInstance("TLS");
	        sslContext.init(MyKeyManager.getInstance(ctx), MemorizingTrustManager.getInstanceList(ctx), new java.security.SecureRandom());
	        sslSocketFactory = new MySSLSocketFactory(sslContext);
            if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(Constants.PREFERENCE_SSLHOST, false))
                sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
	        this.setSSLSocketFactory(sslSocketFactory);
	    } catch (Exception ex) {
	    }
	}
}
