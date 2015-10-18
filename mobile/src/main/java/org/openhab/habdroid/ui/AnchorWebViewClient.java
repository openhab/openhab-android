
package org.openhab.habdroid.ui;

import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.HttpAuthHandler;

class AnchorWebViewClient extends WebViewClient {
    private static final String TAG = "AnchorWebViewClient";
    private String anchor = null;
    private String username;
    private String password;

    public AnchorWebViewClient(String url, String username, String password) {
        this.username = username;
        this.password = password;
        int pos = url.lastIndexOf("#") + 1;
        if(pos != 0 && pos<url.length()) {
            this.anchor = url.substring(pos);
            Log.d(TAG, "Found anchor " + anchor + " from url "+ url);
        } else {
            Log.d(TAG, "Did not find anchor from url "+ url);
        }
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        handler.proceed(this.username, this.password);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (anchor != null && !anchor.isEmpty()) {
            Log.d(TAG, "Now jumping to anchor " + anchor);
            view.loadUrl("javascript:location.hash = '#" + anchor + "';");
        }
    }
}
