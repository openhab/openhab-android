/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui.activity

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.ui.AnchorWebViewClient
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.setUpForConnection

class WebViewFragment : Fragment(), ConnectionFactory.UpdateListener {
    private var webView: WebView? = null
    private lateinit var urlToLoad: String
    private lateinit var urlForError: String
    private var shortcutInfo: ShortcutInfoCompat? = null

    val titleResId: Int
        @StringRes get() = arguments!!.getInt(KEY_PAGE_TITLE)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments!!
        webView = view.findViewById(R.id.webview)
        urlToLoad = args.getString(KEY_URL_LOAD) as String
        urlForError = args.getString(KEY_URL_ERROR) as String
        val action = args.getString(KEY_SHORTCUT_ACTION)
        @StringRes val label = args.getInt(KEY_SHORTCUT_LABEL)
        @DrawableRes val icon = args.getInt(KEY_SHORTCUT_ICON_RES)
        action?.let {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(action)
            shortcutInfo = ShortcutInfoCompat.Builder(view.context, action)
                .setShortLabel(view.context.getString(label))
                .setIcon(IconCompat.createWithResource(context, icon))
                .setIntent(intent)
                .build()
        }

        val retryButton = view.findViewById<TextView>(R.id.retry_button)
        retryButton.setOnClickListener { loadWebsite() }
        val error = view.findViewById<TextView>(R.id.empty_message)
        error.text = getString(args.getInt(KEY_ERROR))

        if (savedInstanceState != null) {
            loadWebsite(savedInstanceState.getString(KEY_CURRENT_URL, urlToLoad))
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

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        shortcutInfo.let { inflater?.inflate(R.menu.webview_menu, menu) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.webview_add_shortcut -> {
                pinShortcut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun pinShortcut() = GlobalScope.launch {
        val context = context ?: return@launch
        val info = shortcutInfo ?: return@launch
        val success = ShortcutManagerCompat.requestPinShortcut(context, info, null)
        withContext(Dispatchers.Main) {
            if (success) {
                Toasty.success(context, R.string.home_shortcut_success_pinning).show()
            } else {
                Toasty.error(context, R.string.home_shortcut_error_pinning).show()
            }
        }
    }

    override fun onAvailableConnectionChanged() {
        loadWebsite()
    }

    override fun onCloudConnectionChanged(connection: CloudConnection?) {
        // no-op
    }

    fun goBack(): Boolean {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
            return true
        }
        return false
    }

    private fun loadWebsite(urlToLoad: String = this.urlToLoad) {
        val conn = ConnectionFactory.usableConnectionOrNull
        if (conn == null) {
            updateViewVisibility(error = true, loading = false)
            return
        }
        updateViewVisibility(error = false, loading = true)

        val webView = webView ?: return
        val url = conn.httpClient.buildUrl(urlToLoad).toString()

        webView.webViewClient = object : AnchorWebViewClient(url, conn.username, conn.password) {
            override fun onPageFinished(view: WebView, url: String) {
                updateViewVisibility(error = false, loading = false)
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                val errorUrl = request.url.toString()
                Log.e(TAG, "onReceivedError() on URL: $errorUrl")
                if (errorUrl.endsWith(urlForError)) {
                    updateViewVisibility(error = true, loading = false)
                }
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                Log.e(TAG, "onReceivedError() (deprecated) on URL: $failingUrl")
                updateViewVisibility(error = true, loading = false)
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

    companion object {
        private val TAG = WebViewFragment::class.java.simpleName

        private const val KEY_CURRENT_URL = "url"
        private const val KEY_PAGE_TITLE = "page_title"
        private const val KEY_ERROR = "error"
        private const val KEY_URL_LOAD = "url_load"
        private const val KEY_URL_ERROR = "url_error"
        private const val KEY_SHORTCUT_ACTION = "shortcut_action"
        private const val KEY_SHORTCUT_LABEL = "shortcut_label"
        private const val KEY_SHORTCUT_ICON_RES = "shortcut_icon_res"

        fun newInstance(
            @StringRes pageTitle: Int,
            @StringRes errorMessage: Int,
            urlToLoad: String,
            urlForError: String,
            shortcutAction: String? = null,
            shortcutLabel: Int = 0,
            shortcutIconRes: Int = 0
        ): WebViewFragment {
            val f = WebViewFragment()
            f.arguments = bundleOf(
                KEY_PAGE_TITLE to pageTitle,
                KEY_ERROR to errorMessage,
                KEY_URL_LOAD to urlToLoad,
                KEY_URL_ERROR to urlForError,
                KEY_SHORTCUT_ACTION to shortcutAction,
                KEY_SHORTCUT_LABEL to shortcutLabel,
                KEY_SHORTCUT_ICON_RES to shortcutIconRes)
            return f
        }
    }
}
