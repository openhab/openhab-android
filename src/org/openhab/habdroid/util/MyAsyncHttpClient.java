/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
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
