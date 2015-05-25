
package org.openhab.habdroid.ui;

import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

class AnchorWebViewClient extends WebViewClient {
    private static final String TAG = "AnchorWebViewClient";
    private String anchor = null;

    public AnchorWebViewClient(String url) {
        int pos = url.lastIndexOf("#") + 1;
        if(pos != 0 && pos<url.length()) {
            this.anchor = url.substring(pos);
            Log.d(TAG, "Found anchor " + anchor + " from url "+ url);
        } else {
            Log.d(TAG, "Did not find anchor from url "+ url);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (anchor != null && !anchor.isEmpty()) {
            Log.d(TAG, "Now jumping to anchor " + anchor);
            view.loadUrl("javascript:location.hash = '#" + anchor + "';");
        }
    }
}
