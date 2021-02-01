/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.connection.DemoConnection
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.ui.ConnectionWebViewClient
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.setUpForConnection
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.isDarkModeActive

abstract class AbstractWebViewFragment : Fragment(), ConnectionFactory.UpdateListener {
    private var webView: WebView? = null
    private var callback: ParentCallback? = null
    private var actionBar: ActionBar? = null
    var isStackRoot = false
        private set
    var title: String? = null
        private set

    abstract val titleRes: Int
    abstract val multiServerTitleRes: Int
    abstract val urlToLoad: String
    abstract val urlForError: String
    abstract val shortcutInfo: ShortcutInfoCompat
    abstract val errorMessageRes: Int

    fun init(activity: MainActivity, callback: ParentCallback, isStackRoot: Boolean) {
        this.callback = callback
        this.isStackRoot = isStackRoot

        val prefs = activity.getPrefs()
        val activeServerId = prefs.getActiveServerId()
        title = if (prefs.getConfiguredServerIds().size <= 1 || activity.connection is DemoConnection) {
            activity.getString(titleRes)
        } else {
            val activeServerName = ServerConfiguration.load(prefs, activity.getSecretPrefs(), activeServerId)?.name
            activity.getString(multiServerTitleRes, activeServerName)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        actionBar = (activity as? MainActivity)?.supportActionBar

        webView = view.findViewById(R.id.webview)

        val retryButton = view.findViewById<Button>(R.id.retry_button)
        retryButton.setOnClickListener { loadWebsite() }
        val error = view.findViewById<TextView>(R.id.empty_message)
        error.text = getString(errorMessageRes)

        if (savedInstanceState != null) {
            webView?.restoreState(savedInstanceState)
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
        (activity as MainActivity?)?.setDrawerLocked(true)
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        webView?.pauseTimers()
        (activity as MainActivity?)?.setDrawerLocked(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView?.url?.let { outState.putString(KEY_CURRENT_URL, it) }
        webView?.saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
            inflater.inflate(R.menu.webview_menu, menu)
        }
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
        val success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        withContext(Dispatchers.Main) {
            if (success) {
                (activity as? MainActivity)?.showSnackbar(
                    MainActivity.SNACKBAR_TAG_SHORTCUT_INFO,
                    R.string.home_shortcut_success_pinning,
                    Snackbar.LENGTH_SHORT
                )
            } else {
                (activity as? MainActivity)?.showSnackbar(
                    MainActivity.SNACKBAR_TAG_SHORTCUT_INFO,
                    R.string.home_shortcut_error_pinning,
                    Snackbar.LENGTH_LONG
                )
            }
        }
    }

    override fun onActiveConnectionChanged() {
        loadWebsite()
    }

    override fun onPrimaryConnectionChanged() {
        // no-op
    }

    override fun onActiveCloudConnectionChanged(connection: CloudConnection?) {
        // no-op
    }

    override fun onPrimaryCloudConnectionChanged(connection: CloudConnection?) {
        // no-op
    }

    fun goBack(): Boolean {
        if (webView?.canGoBack() == true) {
            val oldUrl = webView?.url
            do {
                webView?.goBack()
                // Skip redundant history entries while going back
            } while (webView?.url == oldUrl && webView?.canGoBack() == true)
            return true
        }
        actionBar?.show()
        return false
    }

    fun canGoBack(): Boolean {
        return webView?.canGoBack() == true
    }

    private fun loadWebsite(urlToLoad: String = this.urlToLoad) {
        val conn = ConnectionFactory.activeUsableConnection?.connection
        if (conn == null) {
            updateViewVisibility(error = true, loading = false)
            return
        }
        updateViewVisibility(error = false, loading = true)

        val webView = webView ?: return
        val url = modifyUrl(conn.httpClient.buildUrl(urlToLoad))

        webView.setUpForConnection(conn, url) { progress ->
            if (progress == 100) {
                updateViewVisibility(error = false, loading = false)
            } else {
                updateViewVisibility(error = false, loading = true)
            }
        }
        webView.setBackgroundColor(Color.TRANSPARENT)

        val jsInterface = if (ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
            OHAppInterfaceWithPin(requireContext(), this)
        } else {
            OHAppInterface(requireContext(), this)
        }
        webView.addJavascriptInterface(jsInterface, "OHApp")

        webView.webViewClient = object : ConnectionWebViewClient(conn) {
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
        webView.loadUrl(url.toString())
    }

    open fun modifyUrl(orig: HttpUrl): HttpUrl {
        return orig
    }

    private fun updateViewVisibility(error: Boolean, loading: Boolean) {
        webView?.isVisible = !error
        view?.findViewById<View>(android.R.id.empty)?.isVisible = error
        view?.findViewById<View>(R.id.progress)?.isVisible = loading
    }

    private fun hideActionBar() {
        GlobalScope.launch(Dispatchers.Main) {
            actionBar?.hide()
        }
    }

    private fun closeFragment() {
        GlobalScope.launch(Dispatchers.Main) {
            actionBar?.show()
            callback?.closeFragment()
        }
    }

    open class OHAppInterface(private val context: Context, private val fragment: AbstractWebViewFragment) {
        @JavascriptInterface
        fun preferTheme(): String {
            return "md" // Material design
        }

        @JavascriptInterface
        fun preferDarkMode(): String {
            val nightMode = if (context.isDarkModeActive()) "dark" else "light"
            Log.d(TAG, "preferDarkMode(): $nightMode")
            return nightMode
        }

        @JavascriptInterface
        fun exitToApp() {
            Log.d(TAG, "exitToApp()")
            fragment.closeFragment()
        }

        @JavascriptInterface
        fun goFullscreen() {
            Log.d(TAG, "goFullscreen()")
            fragment.hideActionBar()
        }

        companion object {
            @JvmStatic
            protected val TAG: String = OHAppInterface::class.java.simpleName
        }
    }

    class OHAppInterfaceWithPin(
        context: Context,
        private val fragment: AbstractWebViewFragment
    ) : OHAppInterface(context, fragment) {
        @JavascriptInterface
        fun pinToHome() {
            Log.d(TAG, "pinToHome()")
            fragment.pinShortcut()
        }
    }

    companion object {
        private val TAG = AbstractWebViewFragment::class.java.simpleName

        private const val KEY_CURRENT_URL = "url"
    }

    interface ParentCallback {
        fun closeFragment()
    }
}
