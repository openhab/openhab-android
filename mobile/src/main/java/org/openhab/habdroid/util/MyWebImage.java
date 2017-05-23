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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.loopj.android.image.SmartImage;
import com.loopj.android.image.WebImageCache;

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

public class MyWebImage implements SmartImage {
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private static WebImageCache webImageCache;

    private String url;
    private boolean useCache = true;
    
    private String authUsername;
    private String authPassword;
    private boolean shouldAuth = false;

    public MyWebImage(String url, String username, String password) {
        this.url = url;
        this.useCache = true;
        this.setAuthentication(username, password);
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
                final String iconFormat = PreferenceManager.getDefaultSharedPreferences(context).getString("iconFormatType","PNG");
                bitmap = getBitmapFromUrl(context, url, iconFormat);
                if(bitmap != null && this.useCache) {
                    webImageCache.put(url, bitmap);
                }
            }
        }

        return bitmap;
    }

    private Bitmap getBitmapFromUrl(Context context, String url, String iconFormat) {

        Bitmap bitmap = null;
        String encodedUserPassword = null;
        if (shouldAuth)
        	try {
        		String userPassword = this.authUsername + ":" + this.authPassword;
        		encodedUserPassword = Base64.encodeToString(userPassword.getBytes("UTF-8"), Base64.NO_WRAP);
        	} catch (UnsupportedEncodingException e1) {
        		// TODO Auto-generated catch block
        		e1.printStackTrace();
        	}
        if (url.startsWith("https")) {
        	try {
        		HttpsURLConnection.setDefaultHostnameVerifier(getHostnameVerifier());
        		HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        		conn.setSSLSocketFactory(getSSLSocketFactory(context));
        		conn.setConnectTimeout(CONNECT_TIMEOUT);
        		conn.setReadTimeout(READ_TIMEOUT);
        		if (this.shouldAuth)
        			conn.setRequestProperty("Authorization", "Basic " + encodedUserPassword);
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    throw new Exception("Bad https response status: " + responseCode);
                }
                else {
                    InputStream is = (InputStream) conn.getContent();
                    bitmap = getBitmapFromInputStream(iconFormat, is);                }
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
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    throw new Exception("Bad http response status: " + responseCode);
                }
                else {
                    InputStream is = (InputStream) conn.getContent();
                    bitmap = getBitmapFromInputStream(iconFormat, is);                }
			} catch (Exception e) {
				e.printStackTrace();
			}
        }
        return bitmap;
    }

    private Bitmap getBitmapFromInputStream(String iconFormat, InputStream is) {
        Bitmap bitmap;
        if("SVG".equals(iconFormat)) {
            bitmap = getBitmapFromSvgInputstream(is);
        }else {
            bitmap = BitmapFactory.decodeStream(is);
        }
        return bitmap;
    }

    private Bitmap getBitmapFromSvgInputstream(InputStream is) {
        Bitmap bitmap = null;
        try {
            SVG svg = SVG.getFromInputStream(is);
                double width = svg.getDocumentViewBox().width();
                double height = svg.getDocumentViewBox().height();

                bitmap = Bitmap.createBitmap((int) Math.ceil(width), (int) Math.ceil(height), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//drawARGB(0,0,0,0);//drawRGB(255, 255, 255);
                svg.renderToCanvas(canvas);
        } catch (SVGParseException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    public static void removeFromCache(String url) {
        if(webImageCache != null) {
            webImageCache.remove(url);
        }
    }
    
    public SSLSocketFactory getSSLSocketFactory(Context context) {
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
	        sslContext.init(MyKeyManager.getInstance(context), trustAllCerts, new java.security.SecureRandom() );
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
    	if (this.authUsername != null && (this.authUsername.length() > 0 && this.authPassword.length() > 0))
    		this.shouldAuth = true;
    }
}
