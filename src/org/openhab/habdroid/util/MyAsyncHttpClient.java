/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Victor Belov
 *  @since 1.4.0
 *
 */

package org.openhab.habdroid.util;

import android.content.Context;
import android.preference.PreferenceManager;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLSocketFactory;

import com.loopj.android.http.AsyncHttpClient;

import de.duenndns.ssl.MemorizingTrustManager;

public class MyAsyncHttpClient extends AsyncHttpClient {
	
	private SSLContext sslContext;
	private SSLSocketFactory sslSocketFactory;
	
	public MyAsyncHttpClient(Context ctx) {
		super(ctx);
		try {
	        sslContext = SSLContext.getInstance("TLS");
	        sslContext.init(null, MemorizingTrustManager.getInstanceList(ctx), new java.security.SecureRandom());
	        sslSocketFactory = new MySSLSocketFactory(sslContext);
            if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("default_openhab_sslhost", false))
                sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
	        this.setSSLSocketFactory(sslSocketFactory);
	    } catch (Exception ex) {
	    }
	}
}
