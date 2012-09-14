package org.openhab.habdroid.util;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLSocketFactory;

import com.loopj.android.http.AsyncHttpClient;

public class MyAsyncHttpClient extends AsyncHttpClient {
	
	private SSLContext sslContext;
	private SSLSocketFactory sslSocketFactory;
	
	public MyAsyncHttpClient() {
		super();
		try {
	        X509TrustManager tm = new X509TrustManager() { 
	            public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
	            }

	            public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
	            }

	            public X509Certificate[] getAcceptedIssuers() {
	                return null;
	            }
	        };
	        sslContext = SSLContext.getInstance("TLS");
	        sslContext.init(null, new TrustManager[]{tm}, null);
	        sslSocketFactory = new MySSLSocketFactory(sslContext);
	        sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
	        this.setSSLSocketFactory(sslSocketFactory);
	    } catch (Exception ex) {
	    }
	}
}
