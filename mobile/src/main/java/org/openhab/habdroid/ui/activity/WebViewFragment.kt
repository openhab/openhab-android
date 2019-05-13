package org.openhab.habdroid.ui.activity

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment

import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.connection.exception.ConnectionException
import org.openhab.habdroid.ui.AnchorWebViewClient
import org.openhab.habdroid.ui.setUpForConnection

class WebViewFragment : Fragment(), ConnectionFactory.UpdateListener {
    private var webView: WebView? = null
    private lateinit var urltoLoad: String
    private lateinit var urlForError: String
    private var connection: Connection? = null

    val titleResId: Int
        @StringRes get() = arguments!!.getInt(KEY_PAGE_TITLE)

    fun goBack(): Boolean {
        if (webView?.canGoBack() ?: false) {
            webView?.goBack()
            return true
        }
        return false
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_fullscreenwebview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments!!
        urltoLoad = args.getString(KEY_URL_LOAD) as String
        urlForError = args.getString(KEY_URL_ERROR) as String

        val retryButton = view.findViewById<TextView>(R.id.retry_button)
        retryButton.setOnClickListener { v -> loadWebsite() }
        val error = view.findViewById<TextView>(R.id.empty_message)
        error.text = getString(args.getInt(KEY_ERROR))

        if (savedInstanceState != null) {
            loadWebsite(savedInstanceState.getString(KEY_CURRENT_URL, urltoLoad))
        } else {
            loadWebsite()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.destroy()
        webView = null
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        webView?.pauseTimers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val currentUrl = webView?.url
        if (currentUrl != null) {
            outState.putString(KEY_CURRENT_URL, currentUrl)
        }
    }

    private fun loadWebsite(urlToLoad: String = urltoLoad) {
        try {
            connection = ConnectionFactory.usableConnection
        } catch (e: ConnectionException) {
            updateViewVisibility(true, false)
            return
        }

        val conn = connection
        if (conn == null) {
            updateViewVisibility(true, false)
            return
        }
        updateViewVisibility(false, true)

        val webView = webView ?: return
        val url = conn.asyncHttpClient.buildUrl(urlToLoad).toString()

        webView.webViewClient = object : AnchorWebViewClient(url, conn.username, conn.password) {
            override fun onPageFinished(view: WebView, url: String) {
                updateViewVisibility(false, false)
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            override fun onReceivedError(view: WebView, request: WebResourceRequest,
                                         error: WebResourceError) {
                val errorUrl = request.url.toString()
                Log.e(TAG, "onReceivedError() on URL: $errorUrl")
                if (errorUrl.endsWith(urlForError)) {
                    updateViewVisibility(true, false)
                }
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String,
                                         failingUrl: String) {
                Log.e(TAG, "onReceivedError() (deprecated) on URL: $failingUrl")
                updateViewVisibility(true, false)
            }
        }
        webView.setUpForConnection(conn, url)
        webView.loadUrl(url)
        webView.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun updateViewVisibility(error: Boolean, loading: Boolean) {
        webView?.isVisible = !error
        view?.findViewById<View>(android.R.id.empty)?.isVisible = error
        view?.findViewById<View>(R.id.progress)?.isVisible = loading
    }

    override fun onAvailableConnectionChanged() {
        loadWebsite()
    }

    override fun onCloudConnectionChanged(connection: CloudConnection?) {
        // no-op
    }

    companion object {
        private val TAG = WebViewFragment::class.java.simpleName

        private val KEY_CURRENT_URL = "url"
        private val KEY_PAGE_TITLE = "page_title"
        private val KEY_ERROR = "error"
        private val KEY_URL_LOAD = "url_load"
        private val KEY_URL_ERROR = "url_error"

        fun newInstance(@StringRes pageTitle: Int,
                        @StringRes errorMessage: Int, urltoLoad: String, urlForError: String): WebViewFragment {
            val f = WebViewFragment()
            f.arguments = bundleOf(
                    KEY_PAGE_TITLE to pageTitle,
                    KEY_ERROR to errorMessage,
                    KEY_URL_LOAD to urltoLoad,
                    KEY_URL_ERROR to urlForError)
            return f
        }
    }
}
