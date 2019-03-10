package org.openhab.habdroid.ui.activity;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.CloudConnection;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.ui.AnchorWebViewClient;
import org.openhab.habdroid.util.Util;

public class WebViewFragment extends Fragment implements ConnectionFactory.UpdateListener {
    private static final String TAG = WebViewFragment.class.getSimpleName();

    private static final String KEY_CURRENT_URL = "url";
    private static final String KEY_PAGE_TITLE = "page_title";
    private static final String KEY_ERROR = "error";
    private static final String KEY_URL_LOAD = "url_load";
    private static final String KEY_URL_ERROR = "url_error";

    private String mUrltoLoad;
    private String mUrlForError;
    private Connection mConnection;
    private WebView mWebView;

    public static WebViewFragment newInstance(@StringRes int pageTitle,
            @StringRes int errorMessage, String urltoLoad, String urlForError) {
        WebViewFragment f = new WebViewFragment();
        Bundle args = new Bundle();
        args.putInt(KEY_PAGE_TITLE, pageTitle);
        args.putInt(KEY_ERROR, errorMessage);
        args.putString(KEY_URL_LOAD, urltoLoad);
        args.putString(KEY_URL_ERROR, urlForError);
        f.setArguments(args);
        return f;
    }

    public @StringRes int getTitleResId() {
        return getArguments().getInt(KEY_PAGE_TITLE);
    }

    public boolean goBack() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return false;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fullscreenwebview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        mUrltoLoad = args.getString(KEY_URL_LOAD);
        mUrlForError = args.getString(KEY_URL_ERROR);

        TextView retryButton = view.findViewById(R.id.retry_button);
        retryButton.setOnClickListener(v -> loadWebsite());
        TextView error = view.findViewById(R.id.empty_message);
        error.setText(getString(args.getInt(KEY_ERROR)));

        if (savedInstanceState != null) {
            loadWebsite(savedInstanceState.getString(KEY_CURRENT_URL, mUrltoLoad));
        } else {
            loadWebsite();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.onResume();
            mWebView.resumeTimers();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWebView != null) {
            mWebView.onPause();
            mWebView.pauseTimers();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mWebView != null) {
            outState.putString(KEY_CURRENT_URL, mWebView.getUrl());
        }
    }

    private void loadWebsite() {
        loadWebsite(mUrltoLoad);
    }

    private void loadWebsite(String urlToLoad) {
        View view = getView();
        if (view == null) {
            return;
        }
        try {
            mConnection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            updateViewVisibility(true, false);
            return;
        }

        if (mConnection == null) {
            updateViewVisibility(true, false);
            return;
        }
        updateViewVisibility(false, true);

        String url = mConnection.getAsyncHttpClient().buildUrl(urlToLoad).toString();

        mWebView = view.findViewById(R.id.webview);

        mWebView.setWebViewClient(new AnchorWebViewClient(url,
                mConnection.getUsername(), mConnection.getPassword()) {
            @Override
            public void onPageFinished(WebView view, String url) {
                updateViewVisibility(false, false);
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                String url = request.getUrl().toString();
                Log.e(TAG, "onReceivedError() on URL: " + url);
                if (url.endsWith(mUrlForError)) {
                    updateViewVisibility(true, false);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                Log.e(TAG, "onReceivedError() (deprecated) on URL: " + failingUrl);
                updateViewVisibility(true, false);
            }
        });
        Util.applyAuthentication(mWebView, mConnection, url);
        mWebView.setWebChromeClient(new WebChromeClient());
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.loadUrl(url);
        mWebView.setBackgroundColor(Color.TRANSPARENT);
    }

    private void updateViewVisibility(boolean error, boolean loading) {
        View view = getView();
        if (view == null) {
            return;
        }
        view.findViewById(R.id.webview).setVisibility(error ? View.GONE : View.VISIBLE);
        view.findViewById(android.R.id.empty).setVisibility(error ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.progress).setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAvailableConnectionChanged() {
        loadWebsite();
    }

    @Override
    public void onCloudConnectionChanged(CloudConnection connection) {
        // no-op
    }
}
