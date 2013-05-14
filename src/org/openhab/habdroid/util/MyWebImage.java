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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.loopj.android.image.SmartImage;
import com.loopj.android.image.WebImageCache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

public class MyWebImage implements SmartImage {
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private static WebImageCache webImageCache;

    private String url;
    private boolean useCache = true;
    
    private String authUsername;
    private String authPassword;
    private boolean shouldAuth = false;

    public MyWebImage(String url) {
        this.url = url;
        this.useCache = true;
    }
    
    public MyWebImage(String url, String username, String password) {
        this.url = url;
        this.useCache = true;
        this.setAuthentication(username, password);
    }
    
    public MyWebImage(String url, boolean useCache) {
    	this.url = url;
    	this.useCache = useCache;
    }

    public MyWebImage(String url, boolean useCache, String username, String password) {
    	this.url = url;
    	this.useCache = useCache;
        this.setAuthentication(username, password);
    }
    
    public Bitmap getBitmap(Context context) {
        // Don't leak context
        if(webImageCache == null) {
            webImageCache = new WebImageCache(context);
        }

        // Try getting bitmap from cache first
        Bitmap bitmap = null;
        if(url != null) {
            if (this.useCache)
            	bitmap = webImageCache.get(url);
            if(bitmap == null) {
            	Log.i("MyWebImage", "Cache for " + url + " is empty, getting image");
                bitmap = getBitmapFromUrl(url);
                if(bitmap != null && this.useCache) {
                    webImageCache.put(url, bitmap);
                }
            }
        }

        return bitmap;
    }

    private Bitmap getBitmapFromUrl(String url) {
        Bitmap bitmap = null;
        String encodedUserPassword = null;
        if (shouldAuth)
        	try {
        		String userPassword = this.authUsername + ":" + this.authPassword;
        		encodedUserPassword = Base64.encodeToString(userPassword.getBytes("UTF-8"), Base64.DEFAULT);
        	} catch (UnsupportedEncodingException e1) {
        		// TODO Auto-generated catch block
        		e1.printStackTrace();
        	}
        if (url.startsWith("https")) {
        	try {
        		HttpsURLConnection.setDefaultHostnameVerifier(getHostnameVerifier());
        		HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        		conn.setSSLSocketFactory(getSSLSocketFactory());
        		conn.setConnectTimeout(CONNECT_TIMEOUT);
        		conn.setReadTimeout(READ_TIMEOUT);
        		if (this.shouldAuth)
        			conn.setRequestProperty("Authorization", "Basic " + encodedUserPassword);
        		bitmap = BitmapFactory.decodeStream((InputStream) conn.getContent());
        	} catch(Exception e) {
        		e.printStackTrace();
        	}
        } else {
        	try {
				HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        		conn.setConnectTimeout(CONNECT_TIMEOUT);
        		conn.setReadTimeout(READ_TIMEOUT);
        		if (this.shouldAuth)
        			conn.setRequestProperty("Authorization", "Basic " + encodedUserPassword);
        		bitmap = BitmapFactory.decodeStream((InputStream) conn.getContent());
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        return bitmap;
    }

    public static void removeFromCache(String url) {
        if(webImageCache != null) {
            webImageCache.remove(url);
        }
    }
    
    public SSLSocketFactory getSSLSocketFactory() {
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted( final X509Certificate[] chain, final String authType ) {
            }
            @Override
            public void checkServerTrusted( final X509Certificate[] chain, final String authType ) {
            }
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        } };
        
        // Install the all-trusting trust manager
        SSLContext sslContext;
		try {
			sslContext = SSLContext.getInstance( "SSL" );
	        sslContext.init( null, trustAllCerts, new java.security.SecureRandom() );
	        // Create an ssl socket factory with our all-trusting manager
	        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
	        return sslSocketFactory;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
    }
    
    public HostnameVerifier getHostnameVerifier() {
        HostnameVerifier allHostsValid = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
        };
        return allHostsValid;
    }
    
    public void setAuthentication(String username, String password) {
    	this.authUsername = username;
    	this.authPassword = password;
    	if (this.authUsername.length() > 0 && this.authPassword.length() > 0)
    		this.shouldAuth = true;
    }
}