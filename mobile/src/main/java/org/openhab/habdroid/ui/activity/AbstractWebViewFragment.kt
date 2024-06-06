/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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
import org.openhab.habdroid.ui.AbstractBaseActivity
import org.openhab.habdroid.ui.ConnectionWebViewClient
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.setUpForConnection
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.hasPermissions
import org.openhab.habdroid.util.isDarkModeActive
import org.openhab.habdroid.util.toRelativeUrl

abstract class AbstractWebViewFragment : Fragment(), ConnectionFactory.UpdateListener, CoroutineScope, MenuProvider {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job
    private var webView: WebView? = null
    private var callback: ParentCallback? = null
    private val mainActivity get() = context as MainActivity?
    var isStackRoot = false
        private set
    var title: String? = null
        private set
    var wantsActionBar = true
        private set

    private val permissionRequester = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = pendingPermissionRequests.remove(results.keys) ?: return@registerForActivityResult
        val grantedResources = permsToWebResources(results.filter { (_, v) -> v }.keys.toTypedArray())
        if (grantedResources.isEmpty()) {
            request.deny()
        } else {
            request.grant(grantedResources)
        }
    }

    private val pendingPermissionRequests = mutableMapOf<Set<String>, PermissionRequest>()

    abstract val titleRes: Int
    abstract val errorMessageRes: Int
    abstract val urlToLoad: String
    abstract val pathForError: String
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
        title = context.getString(titleRes)
        if (
            prefs.getConfiguredServerIds().size > 1 &&
            ConnectionFactory.activeUsableConnection?.connection !is DemoConnection
        ) {
            val activeServerName = ServerConfiguration.load(prefs, context.getSecretPrefs(), activeServerId)?.name
            title = getString(R.string.ui_on_server, title, activeServerName)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        webView = view.findViewById(R.id.webview)
        webView?.settings?.mediaPlaybackRequiresUserGesture = false
        webView?.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                Log.d(TAG, "progressCallback: progress = $newProgress")
                if (newProgress == 100) {
                    updateViewVisibility(null, null)
                } else {
                    updateViewVisibility(null, newProgress)
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val requestedPerms = request.resources
                    .map { res -> PERMISSION_REQUEST_MAPPING.get(res) }
                    .filterNotNull()
                    .flatten()
                    .toTypedArray()

                if (requestedPerms.isEmpty()) {
                    Log.w(TAG, "Requested unknown permissions ${request.resources}")
                    request.deny()
                } else if (requireContext().hasPermissions(requestedPerms)) {
                    request.grant(permsToWebResources(requestedPerms))
                } else {
                    (activity as AbstractBaseActivity).showSnackbar(
                        SNACKBAR_TAG_WEBVIEW_PERMISSIONS,
                        R.string.webview_snackbar_permissions_missing,
                        Snackbar.LENGTH_INDEFINITE,
                        R.string.settings_background_tasks_permission_allow,
                        { request.deny() }
                    ) {
                        pendingPermissionRequests[requestedPerms.toSet()] = request
                        permissionRequester.launch(requestedPerms)
                    }
                }
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d(TAG, "${message.message()} -- From line ${message.lineNumber()} of ${message.sourceId()}")
                return true
            }
        }

        isStackRoot = requireArguments().getBoolean(KEY_IS_STACK_ROOT)

        val retryButton = view.findViewById<Button>(R.id.retry_button)
        retryButton.setOnClickListener {
            Log.d(TAG, "Retry button clicked, reload website")
            loadWebsite()
        }
        val error = view.findViewById<TextView>(R.id.empty_message)
        error.text = getString(errorMessageRes)

        val subpage = requireArguments().getString(KEY_SUBPAGE)
        when {
            savedInstanceState != null -> {
                val savedUrl = savedInstanceState.getString(KEY_CURRENT_URL, urlToLoad)
                Log.d(TAG, "Load website from savedInstanceState: $savedUrl")
                webView?.restoreState(savedInstanceState)
                loadWebsite(savedUrl)
            }
            subpage != null -> {
                Log.d(TAG, "Load subpage: $subpage")
                loadWebsite(subpage)
            }
            else -> {
                Log.d(TAG, "Load default website")
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
            mainActivity?.setDrawerLocked(true)
        }
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        webView?.pauseTimers()
        if (lockDrawer) {
            mainActivity?.setDrawerLocked(false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView?.url?.let { outState.putString(KEY_CURRENT_URL, it) }
        webView?.saveState(outState)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
            inflater.inflate(R.menu.webview_menu, menu)
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.webview_add_shortcut -> {
                pinShortcut()
                true
            }
            else -> false
        }
    }

    fun setCallback(callback: ParentCallback) {
        this.callback = callback
    }

    private fun pinShortcut() {
        if (!isAdded) {
            return
        }
        val f = ShortcutTitleBottomSheet()
        f.show(childFragmentManager, "shortcut_title")
    }

    // called from ShortcutTitleBottomSheet
    private fun createShortcut(info: ShortcutInfoCompat) {
        val context = context ?: return
        val success = ShortcutManagerCompat.requestPinShortcut(context, info, null)
        val textResId = if (success) R.string.home_shortcut_success_pinning else R.string.home_shortcut_error_pinning
        val duration = if (success) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG
        mainActivity?.showSnackbar(MainActivity.SNACKBAR_TAG_SHORTCUT_INFO, textResId, duration)
    }

    override fun onActiveConnectionChanged() {
        Log.d(TAG, "onActiveConnectionChanged()")
        loadWebsite()
    }

    override fun onPrimaryConnectionChanged() {
        Log.d(TAG, "onPrimaryConnectionChanged()")
        // no-op
    }

    override fun onActiveCloudConnectionChanged(connection: CloudConnection?) {
        Log.d(TAG, "onActiveCloudConnectionChanged()")
        // no-op
    }

    override fun onPrimaryCloudConnectionChanged(connection: CloudConnection?) {
        Log.d(TAG, "onPrimaryCloudConnectionChanged()")
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
        return false
    }

    fun canGoBack(): Boolean {
        return webView?.canGoBack() == true
    }

    private fun loadWebsite(urlToLoad: String = this.urlToLoad) {
        val conn = ConnectionFactory.activeUsableConnection?.connection
        if (conn == null) {
            updateViewVisibility(true, null)
            return
        }
        updateViewVisibility(false, 0)

        val webView = webView ?: return
        val url = modifyUrl(conn.httpClient.buildUrl(urlToLoad))

        webView.setUpForConnection(conn, url, avoidAuthentication)
        webView.setBackgroundColor(Color.TRANSPARENT)

        val jsInterface = if (ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
            OHAppInterfaceWithPin(requireContext(), this)
        } else {
            OHAppInterface(requireContext(), this)
        }
        webView.addJavascriptInterface(jsInterface, "OHApp")

        webView.webViewClient = object : ConnectionWebViewClient(conn) {
            private fun handleError(url: Uri) {
                if (url.path == pathForError) {
                    updateViewVisibility(true, null)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                Log.e(TAG, "onReceivedError() on URL: ${request.url}")
                handleError(request.url)
            }

            @Deprecated(message = "Function is called on older Android versions")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                Log.e(TAG, "onReceivedError() (deprecated) on URL: $failingUrl")
                // This deprecated version is only called for the main resource, so no need to check for 'pathForError' here
                updateViewVisibility(true, null)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                Log.e(TAG, "onReceivedHttpError() on URL: ${request.url}")
                handleError(request.url)
            }
        }
        webView.loadUrl(url.toString())
    }

    open fun modifyUrl(orig: HttpUrl): HttpUrl {
        return orig
    }

    /**
     * Change the visibility of the progress and error indicators and the WebView.
     * @param error null if the error state didn't change, true if an error occurred, false if an error was cleared.
     * @param loadingProgress null if no loading happens, current progress otherwise.
     */
    private fun updateViewVisibility(error: Boolean?, loadingProgress: Int?) {
        error?.let {
            webView?.isVisible = !error
            view?.findViewById<View>(android.R.id.empty)?.isVisible = error
        }
        view?.findViewById<ProgressBar>(R.id.progress)?.apply {
            isVisible = loadingProgress != null
            progress = loadingProgress ?: 0
        }
    }

    private fun hideActionBar() {
        wantsActionBar = false
        callback?.updateActionBarState()
    }

    private fun closeFragment() {
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

        private const val SNACKBAR_TAG_WEBVIEW_PERMISSIONS = "webviewPermissions"

        private val PERMISSION_REQUEST_MAPPING = mapOf(
            PermissionRequest.RESOURCE_AUDIO_CAPTURE to listOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.MODIFY_AUDIO_SETTINGS
            ),
            PermissionRequest.RESOURCE_VIDEO_CAPTURE to listOf(
                android.Manifest.permission.CAMERA
            )
        )

        private fun permsToWebResources(androidPermissions: Array<String>) = PERMISSION_REQUEST_MAPPING
            .filter { (_, perms) -> perms.all { perm -> androidPermissions.contains(perm) } }
            .keys
            .toTypedArray()

        private const val KEY_CURRENT_URL = "url"
        const val KEY_IS_STACK_ROOT = "is_stack_root"
        const val KEY_SUBPAGE = "subpage"
    }

    interface ParentCallback {
        fun closeFragment()

        fun updateActionBarState()
    }

    class ShortcutTitleBottomSheet : BottomSheetDialogFragment() {
        private val parent get() = parentFragment as AbstractWebViewFragment
        private lateinit var origInfo: ShortcutInfoCompat
        private lateinit var editor: EditText

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            origInfo = parent.shortcutInfo
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.bottom_sheet_shortcut_label, container, false)
            editor = view.findViewById(R.id.editor)
            return view
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            editor.setText(origInfo.shortLabel, TextView.BufferType.EDITABLE)
            editor.requestFocus()

            view.findViewById<View>(R.id.cancel_button).setOnClickListener {
                dismissAllowingStateLoss()
            }
            view.findViewById<View>(R.id.save).setOnClickListener {
                save()
                dismissAllowingStateLoss()
            }
        }

        private fun save() {
            val label = if (editor.text.isNullOrEmpty()) " " else editor.text
            val newInfo = ShortcutInfoCompat.Builder(origInfo)
                .setShortLabel(label)
                .build()
            parent.createShortcut(newInfo)
        }
    }
}
