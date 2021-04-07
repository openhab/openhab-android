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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.connection.DemoConnection
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.ui.ConnectionWebViewClient
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.setUpForConnection
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.isDarkModeActive
import org.openhab.habdroid.util.toRelativeUrl

abstract class AbstractWebViewFragment : Fragment(), ConnectionFactory.UpdateListener, CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job
    private var webView: WebView? = null
    private var callback: ParentCallback? = null
    private var actionBar: ActionBar? = null
    var isStackRoot = false
        private set
    var title: String? = null
        private set

    abstract val titleRes: Int
    abstract val multiServerTitleRes: Int
    abstract val errorMessageRes: Int
    abstract val urlToLoad: String
    abstract val urlForError: String
    open val avoidAuthentication = false
    abstract val lockDrawer: Boolean
    abstract val shortcutIcon: Int
    abstract val shortcutAction: String
    private val shortcutInfo: ShortcutInfoCompat
        get() {
            val context = requireContext()
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SERVER_ID, context.getPrefs().getActiveServerId())
                .setAction(shortcutAction)

            webView?.url?.toHttpUrlOrNull()?.let {
                intent.putExtra(MainActivity.EXTRA_SUBPAGE, it.toRelativeUrl())
            }

            return ShortcutInfoCompat.Builder(context, "$shortcutAction-${System.currentTimeMillis()}")
                .setShortLabel(title!!)
                .setIcon(IconCompat.createWithResource(context, shortcutIcon))
                .setIntent(intent)
                .build()
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val prefs = context.getPrefs()
        val activeServerId = prefs.getActiveServerId()
        title = if (
            prefs.getConfiguredServerIds().size <= 1 ||
            ConnectionFactory.activeUsableConnection?.connection is DemoConnection
        ) {
            context.getString(titleRes)
        } else {
            val activeServerName = ServerConfiguration.load(prefs, context.getSecretPrefs(), activeServerId)?.name
            context.getString(multiServerTitleRes, activeServerName)
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

        isStackRoot = requireArguments().getBoolean(KEY_IS_STACK_ROOT)

        val retryButton = view.findViewById<Button>(R.id.retry_button)
        retryButton.setOnClickListener { loadWebsite() }
        val error = view.findViewById<TextView>(R.id.empty_message)
        error.text = getString(errorMessageRes)

        val subpage = requireArguments().getString(KEY_SUBPAGE)
        when {
            savedInstanceState != null -> {
                webView?.restoreState(savedInstanceState)
                loadWebsite(savedInstanceState.getString(KEY_CURRENT_URL, urlToLoad))
            }
            subpage != null -> {
                loadWebsite(subpage)
            }
            else -> {
                loadWebsite()
            }
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
        if (lockDrawer) {
            (activity as MainActivity?)?.setDrawerLocked(true)
        }
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        webView?.pauseTimers()
        if (lockDrawer) {
            (activity as MainActivity?)?.setDrawerLocked(false)
        }
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

    fun setCallback(callback: ParentCallback) {
        this.callback = callback
    }

    private fun pinShortcut() {
        val context = context ?: return
        askForShortcutTitle(context, shortcutInfo) {
            val success = ShortcutManagerCompat.requestPinShortcut(context, it, null)
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

    @SuppressLint("RestrictedApi")
    private fun askForShortcutTitle(
        context: Context,
        orig: ShortcutInfoCompat,
        callback: (newTitle: ShortcutInfoCompat) -> Unit
    ) {
        val input = EditText(context).apply {
            text = SpannableStringBuilder(orig.shortLabel)
            inputType = InputType.TYPE_CLASS_TEXT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
            setPadding(context.resources.dpToPixel(8f).toInt())
        }

        val customDialog = AlertDialog.Builder(context)
            .setTitle(getString(R.string.home_shortcut_title))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                callback(
                    ShortcutInfoCompat.Builder(orig)
                        .setShortLabel(input.text)
                        .build()
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.setOnFocusChangeListener { _, hasFocus ->
            val mode = if (hasFocus)
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            else
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            customDialog.window?.setSoftInputMode(mode)
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

        webView.setUpForConnection(conn, url, avoidAuthentication) { progress ->
            Log.d(TAG, "progressCallback: progress = $progress")
            if (progress == 100) {
                updateViewVisibility(error = null, loading = false)
            } else {
                updateViewVisibility(error = null, loading = true)
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

    private fun updateViewVisibility(error: Boolean?, loading: Boolean) {
        error?.let {
            webView?.isVisible = !error
            view?.findViewById<View>(android.R.id.empty)?.isVisible = error
        }
        view?.findViewById<View>(R.id.progress)?.isVisible = loading
    }

    private fun hideActionBar() {
        actionBar?.hide()
    }

    private fun closeFragment() {
        actionBar?.show()
        callback?.closeFragment()
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
            fragment.launch {
                fragment.closeFragment()
            }
        }

        @JavascriptInterface
        fun goFullscreen() {
            Log.d(TAG, "goFullscreen()")
            fragment.launch {
                fragment.hideActionBar()
            }
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
            fragment.launch {
                fragment.pinShortcut()
            }
        }
    }

    companion object {
        private val TAG = AbstractWebViewFragment::class.java.simpleName

        private const val KEY_CURRENT_URL = "url"
        const val KEY_IS_STACK_ROOT = "is_stack_root"
        const val KEY_SUBPAGE = "subpage"
    }

    interface ParentCallback {
        fun closeFragment()
    }
}
