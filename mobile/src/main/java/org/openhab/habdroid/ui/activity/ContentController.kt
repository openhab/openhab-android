/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AnimRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.CloudNotificationListFragment
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.PreferencesActivity
import org.openhab.habdroid.ui.WidgetListFragment
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.RemoteLog
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isDebugModeEnabled
import java.util.ArrayList
import java.util.HashSet
import java.util.Stack

/**
 * Controller class for the content area of [MainActivity]
 *
 * It manages the stack of widget lists shown, and shows error UI if needed.
 * The layout of the content area is up to the respective subclasses.
 */
abstract class ContentController protected constructor(private val activity: MainActivity) :
    PageConnectionHolderFragment.ParentCallback {
    protected val fm: FragmentManager = activity.supportFragmentManager

    private var noConnectionFragment: Fragment? = null
    protected var defaultProgressFragment: Fragment
    private val connectionFragment: PageConnectionHolderFragment
    private var temporaryPage: Fragment? = null
    private var currentSitemap: Sitemap? = null
    protected var sitemapFragment: WidgetListFragment? = null
    protected val pageStack = Stack<Pair<LinkedPage, WidgetListFragment>>()
    private val pendingDataLoadUrls = HashSet<String>()

    override val isDetailedLoggingEnabled get() = activity.getPrefs().isDebugModeEnabled()
    override val serverProperties get() = activity.serverProperties

    /**
     * Get title describing current UI state
     *
     * @return Title to show in action bar, or null if none can be determined
     */
    val currentTitle get() = when {
        noConnectionFragment != null -> null
        temporaryPage is CloudNotificationListFragment -> activity.getString(R.string.app_notifications)
        temporaryPage is WebViewFragment -> activity.getString((temporaryPage as WebViewFragment).titleResId)
        temporaryPage != null -> null
        else -> fragmentForTitle?.title
    }

    protected abstract val fragmentForTitle: WidgetListFragment?

    protected val overridingFragment get() = when {
        temporaryPage != null -> temporaryPage
        noConnectionFragment != null -> noConnectionFragment
        else -> null
    }

    init {
        var connectionFragment = fm.findFragmentByTag("connections") as PageConnectionHolderFragment?
        if (connectionFragment == null) {
            connectionFragment = PageConnectionHolderFragment()
            fm.commit {
                add(connectionFragment, "connections")
            }
        }
        this.connectionFragment = connectionFragment

        defaultProgressFragment = ProgressFragment.newInstance(null, 0)
        connectionFragment.setCallback(this)
    }

    /**
     * Saves the controller's instance state
     * To be called from the onSaveInstanceState callback of the activity
     *
     * @param state Bundle to save state into
     */
    fun onSaveInstanceState(state: Bundle) {
        RemoteLog.d(TAG, "onSaveInstanceState()")
        val pages = ArrayList<LinkedPage>()
        for ((page, fragment) in pageStack) {
            pages.add(page)
            if (fragment.isAdded) {
                fm.putFragment(state, makeStateKeyForPage(page), fragment)
            }
        }
        state.putParcelable(STATE_KEY_SITEMAP, currentSitemap)
        sitemapFragment?.let { page ->
            if (page.isAdded) {
                fm.putFragment(state, STATE_KEY_SITEMAP_FRAGMENT, page)
            }
        }
        if (defaultProgressFragment.isAdded) {
            fm.putFragment(state, STATE_KEY_PROGRESS_FRAGMENT, defaultProgressFragment)
        }
        state.putParcelableArrayList(STATE_KEY_PAGES, pages)
        temporaryPage?.let { page ->
            fm.putFragment(state, STATE_KEY_TEMPORARY_PAGE, page)
        }
        noConnectionFragment?.let { page ->
            if (page.isAdded) {
                fm.putFragment(state, STATE_KEY_ERROR_FRAGMENT, page)
            }
        }
    }

    /**
     * Restore instance state previously saved by onSaveInstanceState
     * To be called from the onRestoreInstanceState or onCreate callbacks of the activity
     *
     * @param state Bundle including previously saved state
     */
    open fun onRestoreInstanceState(state: Bundle) {
        RemoteLog.d(TAG, "onRestoreInstanceState()")
        currentSitemap = state.getParcelable(STATE_KEY_SITEMAP)
        currentSitemap?.let { sitemap ->
            sitemapFragment = fm.getFragment(state, STATE_KEY_SITEMAP_FRAGMENT) as WidgetListFragment?
                ?: makeSitemapFragment(sitemap)
        }
        val progressFragment = fm.getFragment(state, STATE_KEY_PROGRESS_FRAGMENT)
        if (progressFragment != null) {
            defaultProgressFragment = progressFragment
        }

        pageStack.clear()
        state.getParcelableArrayList<LinkedPage>(STATE_KEY_PAGES)?.forEach { page ->
            val f = fm.getFragment(state, makeStateKeyForPage(page)) as WidgetListFragment?
            pageStack.add(Pair(page, f ?: makePageFragment(page)))
        }
        temporaryPage = fm.getFragment(state, STATE_KEY_TEMPORARY_PAGE)
        noConnectionFragment = fm.getFragment(state, STATE_KEY_ERROR_FRAGMENT)
    }

    /**
     * Show contents of a sitemap
     * Sets up UI to show the sitemap's contents
     *
     * @param sitemap Sitemap to show
     */
    fun openSitemap(sitemap: Sitemap) {
        RemoteLog.d(TAG, "openSitemap()", remoteOnly = true)
        Log.d(TAG, "Opening sitemap $sitemap (current: $currentSitemap)")
        currentSitemap = sitemap
        // First clear the old fragment stack to show the progress spinner...
        pageStack.clear()
        sitemapFragment = null
        temporaryPage = null
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
        // ... and clear remaining page connections ...
        updateConnectionState()
        // ...then create the new sitemap fragment and trigger data loading.
        val newFragment = makeSitemapFragment(sitemap)
        sitemapFragment = newFragment
        handleNewWidgetFragment(newFragment)
    }

    /**
     * Follow a link in a sitemap page
     * Sets up UI to show the contents of the given page
     *
     * @param page Page link to follow
     * @param source Fragment this action was triggered from
     */
    open fun openPage(page: LinkedPage, source: WidgetListFragment) {
        RemoteLog.d(TAG, "openPage(LinkedPage, WidgetListFragment)", remoteOnly = true)
        Log.d(TAG, "Opening page $page")
        val f = makePageFragment(page)
        while (!pageStack.isEmpty() && pageStack.peek().second !== source) {
            pageStack.pop()
        }
        pageStack.push(Pair(page, f))
        handleNewWidgetFragment(f)
        activity.setProgressIndicatorVisible(true)
    }

    /**
     * Follow a sitemap page link via URL
     * If a page with the given URL is already present in the back stack,
     * that page is brought to the front; otherwise a temporary page with showing
     * the contents of the linked page is opened.
     *
     * @param url URL to follow
     */
    fun openPage(url: String) {
        RemoteLog.d(TAG, "openPage(String)", remoteOnly = true)
        val matchingPageIndex = pageStack.indexOfFirst { entry -> entry.first.link == url }
        Log.d(TAG, "Opening page $url (present at $matchingPageIndex)")

        temporaryPage = null
        if (matchingPageIndex >= 0) {
            for (i in matchingPageIndex + 1 until pageStack.size) {
                pageStack.pop()
            }
            updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
            updateConnectionState()
            activity.updateTitle()
        } else {
            // we didn't find it
            val page = LinkedPage("", "", null, url)
            val f = makePageFragment(page)
            pageStack.clear()
            pageStack.push(Pair(page, f))
            handleNewWidgetFragment(f)
            activity.setProgressIndicatorVisible(true)
        }
    }

    fun showHabPanel() {
        showTemporaryPage(WebViewFragment.newInstance(R.string.mainmenu_openhab_habpanel,
            R.string.habpanel_error,
            "/habpanel/index.html", "/rest/events",
            MainActivity.ACTION_HABPANEL_SELECTED, R.string.mainmenu_openhab_habpanel, R.mipmap.ic_shortcut_habpanel))
    }

    /**
     * Indicate to the user that no network connectivity is present.
     *
     * @param message Error message to show
     * @param shouldSuggestEnablingWifi
     */
    fun indicateNoNetwork(message: CharSequence, shouldSuggestEnablingWifi: Boolean) {
        RemoteLog.d(TAG, "Indicate no network (message $message)")
        resetState()
        noConnectionFragment = if (shouldSuggestEnablingWifi) {
            EnableWifiNetworkFragment.newInstance(message)
        } else {
            NoNetworkFragment.newInstance(message)
        }
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
        activity.updateTitle()
    }

    /**
     * Indicate to the user that server configuration is missing.
     *
     * @param resolveAttempted Indicate if discovery was attempted, but not successful
     */
    fun indicateMissingConfiguration(resolveAttempted: Boolean) {
        RemoteLog.d(TAG, "Indicate missing configuration (resolveAttempted $resolveAttempted)")
        resetState()
        val wifiManager = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        noConnectionFragment = MissingConfigurationFragment.newInstance(activity,
            resolveAttempted, wifiManager.isWifiEnabled)
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
        activity.updateTitle()
    }

    /**
     * Indicate to the user that there was a failure in talking to the server
     *
     * @param message Error message to show
     */
    fun indicateServerCommunicationFailure(message: CharSequence) {
        RemoteLog.d(TAG, "Indicate server failure (message $message)")
        noConnectionFragment = CommunicationFailureFragment.newInstance(message)
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
        activity.updateTitle()
    }

    /**
     * Clear the error previously set by [indicateServerCommunicationFailure]
     */
    fun clearServerCommunicationFailure() {
        RemoteLog.d(TAG, "clearServerCommunicationFailure()")
        if (noConnectionFragment is CommunicationFailureFragment) {
            noConnectionFragment = null
            resetState()
            updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
            activity.updateTitle()
        }
    }

    /**
     * Update the used connection.
     * To be called when the available connection changes.
     *
     * @param connection New connection to use; might be null if none is currently available
     * @param progressMessage Message to show to the user if no connection is available
     */
    fun updateConnection(connection: Connection?, progressMessage: CharSequence?, @DrawableRes icon: Int) {
        RemoteLog.d(TAG, "Update to connection $connection (message $progressMessage)")
        noConnectionFragment = if (connection == null)
            ProgressFragment.newInstance(progressMessage, icon) else null
        resetState()
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
        // Make sure dropped fragments are destroyed immediately to get their views recycled
        fm.executePendingTransactions()
    }

    /**
     * Open a temporary page showing the notification list
     *
     * @param highlightedId ID of notification to be highlighted initially
     */
    fun openNotifications(highlightedId: String?) {
        showTemporaryPage(CloudNotificationListFragment.newInstance(highlightedId))
    }

    /**
     * Recreate all UI state
     * To be called from the activity's onCreate callback if the used controller changes
     */
    fun recreateFragmentState() {
        fm.commitNow {
            fm.fragments
                .filterNot { f -> f.retainInstance }
                .forEach { f -> remove(f) }
        }
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
    }

    /**
     * Inflate controller views
     * To be called after activity content view inflation
     *
     * @param stub View stub to inflate controller views into
     */
    abstract fun inflateViews(stub: ViewStub)

    /**
     * Ask the connection controller to deliver content updates for a given page
     *
     * @param pageUrl URL of the content page
     * @param forceReload Whether to discard previously cached state
     */
    fun triggerPageUpdate(pageUrl: String, forceReload: Boolean) {
        connectionFragment.triggerUpdate(pageUrl, forceReload)
    }

    /**
     * Checks whether the controller currently can consume the back key
     *
     * @return true if back key can be consumed, false otherwise
     */
    fun canGoBack(): Boolean {
        return temporaryPage != null || !pageStack.empty()
    }

    /**
     * Consumes the back key
     * To be called from activity onBackKeyPressed callback
     *
     * @return true if back key was consumed, false otherwise
     */
    fun goBack(): Boolean {
        if (temporaryPage is WebViewFragment) {
            if ((temporaryPage as WebViewFragment).goBack()) {
                return true
            }
        }
        if (temporaryPage != null) {
            temporaryPage = null
            activity.updateTitle()
            updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
            updateConnectionState()
            return true
        }
        if (!pageStack.empty()) {
            pageStack.pop()
            activity.updateTitle()
            updateFragmentState(FragmentUpdateReason.BACK_NAVIGATION)
            updateConnectionState()
            return true
        }
        return false
    }

    override fun onPageUpdated(pageUrl: String, pageTitle: String?, widgets: List<Widget>) {
        Log.d(TAG, "Got update for URL $pageUrl, pending $pendingDataLoadUrls")
        val fragment = findWidgetFragmentForUrl(pageUrl)
        fragment?.updateTitle(pageTitle.orEmpty())
        fragment?.updateWidgets(widgets)
        if (pendingDataLoadUrls.remove(pageUrl) && pendingDataLoadUrls.isEmpty()) {
            activity.setProgressIndicatorVisible(false)
            activity.updateTitle()
            updateFragmentState(if (pageStack.isEmpty())
                FragmentUpdateReason.PAGE_UPDATE else FragmentUpdateReason.PAGE_ENTER)
        }
    }

    override fun onWidgetUpdated(pageUrl: String, widget: Widget) {
        findWidgetFragmentForUrl(pageUrl)?.updateWidget(widget)
    }

    override fun onPageTitleUpdated(pageUrl: String, title: String) {
        findWidgetFragmentForUrl(pageUrl)?.updateTitle(title)
    }

    override fun onLoadFailure(error: HttpClient.HttpException) {
        val url = error.request.url.toString()
        val errorMessage = activity.getHumanReadableErrorMessage(url, error.statusCode, error, false)
            .toString()

        RemoteLog.d(TAG, "onLoadFailure() with message $errorMessage")
        noConnectionFragment = CommunicationFailureFragment.newInstance(
            activity.getString(R.string.error_sitemap_generic_load_error, errorMessage))
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE)
        activity.updateTitle()
        if (pendingDataLoadUrls.remove(error.originalUrl) && pendingDataLoadUrls.isEmpty()) {
            activity.setProgressIndicatorVisible(false)
        }
    }

    override fun onSseFailure() {
        activity.showSnackbar(R.string.error_sse_failed)
    }

    internal abstract fun executeStateUpdate(reason: FragmentUpdateReason, allowStateLoss: Boolean)

    private fun updateFragmentState(reason: FragmentUpdateReason) {
        // Allow state loss if activity is still started, as we'll get
        // another onSaveInstanceState() callback on activity stop
        executeStateUpdate(reason, activity.isStarted)
        collectWidgetFragments().forEach { f -> f.closeAllDialogs() }
    }

    private fun handleNewWidgetFragment(f: WidgetListFragment) {
        pendingDataLoadUrls.add(f.displayPageUrl)
        // no fragment update yet; fragment state will be updated when data arrives
        updateConnectionState()
        activity.updateTitle()
    }

    private fun showTemporaryPage(page: Fragment) {
        temporaryPage = page
        updateFragmentState(FragmentUpdateReason.TEMPORARY_PAGE)
        updateConnectionState()
        activity.updateTitle()
    }

    private fun updateConnectionState() {
        val pageUrls = collectWidgetFragments().map { f -> f.displayPageUrl }
        pendingDataLoadUrls.retainAll { url -> pageUrls.contains(url) }
        connectionFragment.updateActiveConnections(pageUrls, activity.connection)
    }

    private fun resetState() {
        currentSitemap = null
        sitemapFragment = null
        pageStack.clear()
        updateConnectionState()
    }

    private fun findWidgetFragmentForUrl(url: String): WidgetListFragment? {
        return collectWidgetFragments().firstOrNull { f -> f.displayPageUrl == url }
    }

    private fun collectWidgetFragments(): List<WidgetListFragment> {
        val result = ArrayList<WidgetListFragment>()
        sitemapFragment?.let { result.add(it) }
        for ((_, fragment) in pageStack) {
            result.add(fragment)
        }
        return result
    }

    private fun makeSitemapFragment(sitemap: Sitemap): WidgetListFragment {
        return WidgetListFragment.withPage(sitemap.homepageLink, sitemap.label)
    }

    private fun makePageFragment(page: LinkedPage): WidgetListFragment {
        return WidgetListFragment.withPage(page.link, page.title)
    }

    internal enum class FragmentUpdateReason {
        PAGE_ENTER,
        BACK_NAVIGATION,
        TEMPORARY_PAGE,
        PAGE_UPDATE
    }

    internal class CommunicationFailureFragment : StatusFragment() {
        override fun onClick(view: View) {
            (activity as MainActivity).retryServerPropertyQuery()
        }

        companion object {
            fun newInstance(message: CharSequence): CommunicationFailureFragment {
                val f = CommunicationFailureFragment()
                f.arguments = buildArgs(message, R.string.try_again_button,
                    R.drawable.ic_openhab_appicon_340dp /* FIXME */, false)
                return f
            }
        }
    }

    internal class ProgressFragment : StatusFragment() {
        override fun onClick(view: View) {
            // No-op, we don't show the button
        }

        companion object {
            fun newInstance(message: CharSequence?, @DrawableRes image: Int): ProgressFragment {
                val f = ProgressFragment()
                f.arguments = buildArgs(message, 0, image, true)
                return f
            }
        }
    }

    internal class NoNetworkFragment : StatusFragment() {
        override fun onClick(view: View) {
            ConnectionFactory.restartNetworkCheck()
            activity?.recreate()
        }

        companion object {
            fun newInstance(message: CharSequence): NoNetworkFragment {
                val f = NoNetworkFragment()
                f.arguments = buildArgs(message, R.string.try_again_button,
                    R.drawable.ic_network_strength_off_outline_black_24dp, false)
                return f
            }
        }
    }

    internal class EnableWifiNetworkFragment : StatusFragment() {
        override fun onClick(view: View) {
            (activity as MainActivity).enableWifiAndIndicateStartup()
        }

        companion object {
            fun newInstance(message: CharSequence): EnableWifiNetworkFragment {
                val f = EnableWifiNetworkFragment()
                f.arguments = buildArgs(message, R.string.enable_wifi_button,
                    R.drawable.ic_wifi_strength_off_outline_grey_24dp, false)
                return f
            }
        }
    }

    internal class MissingConfigurationFragment : StatusFragment() {
        override fun onClick(view: View) {
            when {
                view.id == R.id.button1 -> {
                    // Primary button always goes to settings
                    val preferencesIntent = Intent(activity, PreferencesActivity::class.java)
                    startActivity(preferencesIntent)
                }
                arguments?.getBoolean(KEY_RESOLVE_ATTEMPTED) == true -> {
                    // If we attempted resolving, secondary button enables demo mode
                    context?.apply {
                        getPrefs().edit {
                            putBoolean(PrefKeys.DEMO_MODE, true)
                        }
                    }
                }
                arguments?.getBoolean(KEY_WIFI_ENABLED) == true -> {
                    // If Wifi is enabled, secondary button suggests retrying
                    ConnectionFactory.restartNetworkCheck()
                    activity?.recreate()
                }
                else -> {
                    // If Wifi is disabled, secondary button suggests enabling Wifi
                    (activity as MainActivity?)?.enableWifiAndIndicateStartup()
                }
            }
        }

        companion object {
            fun newInstance(
                context: Context,
                resolveAttempted: Boolean,
                hasWifiEnabled: Boolean
            ): MissingConfigurationFragment {
                val f = MissingConfigurationFragment()
                val args = when {
                    resolveAttempted -> buildArgs(context.getString(R.string.configuration_missing),
                        R.string.go_to_settings_button, R.string.enable_demo_mode_button,
                        R.drawable.ic_home_search_outline_grey_340dp, false)
                    hasWifiEnabled -> buildArgs(context.getString(R.string.no_remote_server),
                        R.string.go_to_settings_button, R.string.try_again_button,
                        R.drawable.ic_network_strength_off_outline_black_24dp, false)
                    else -> buildArgs(context.getString(R.string.no_remote_server),
                        R.string.go_to_settings_button, R.string.enable_wifi_button,
                        R.drawable.ic_wifi_strength_off_outline_grey_24dp, false)
                }
                args.putBoolean(KEY_RESOLVE_ATTEMPTED, resolveAttempted)
                args.putBoolean(KEY_WIFI_ENABLED, hasWifiEnabled)
                f.arguments = args

                return f
            }
        }
    }

    internal abstract class StatusFragment : Fragment(), View.OnClickListener {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val arguments = requireArguments()
            val view = inflater.inflate(R.layout.fragment_status, container, false)

            val descriptionText = view.findViewById<TextView>(R.id.description)
            descriptionText.text = arguments.getCharSequence(KEY_MESSAGE)
            descriptionText.isVisible = !descriptionText.text.isNullOrEmpty()

            view.findViewById<View>(R.id.progress).isVisible = arguments.getBoolean(KEY_PROGRESS)

            val watermark = view.findViewById<ImageView>(R.id.image)
            @DrawableRes val drawableResId = arguments.getInt(KEY_DRAWABLE)
            if (drawableResId != 0) {
                val drawable = ContextCompat.getDrawable(view.context, drawableResId)
                drawable?.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(view.context, R.color.empty_list_text_color),
                    PorterDuff.Mode.SRC_IN)
                watermark.setImageDrawable(drawable)
            } else {
                watermark.isVisible = false
            }

            for ((id, key) in mapOf(R.id.button1 to KEY_BUTTON_1_TEXT, R.id.button2 to KEY_BUTTON_2_TEXT)) {
                val button = view.findViewById<Button>(id)
                val buttonTextResId = arguments.getInt(key)
                if (buttonTextResId != 0) {
                    button.setText(buttonTextResId)
                    button.setOnClickListener(this)
                } else {
                    button.isVisible = false
                }
            }

            return view
        }

        companion object {
            internal const val KEY_MESSAGE = "message"
            internal const val KEY_DRAWABLE = "drawable"
            internal const val KEY_BUTTON_1_TEXT = "button1text"
            internal const val KEY_BUTTON_2_TEXT = "button2text"
            internal const val KEY_PROGRESS = "progress"
            internal const val KEY_RESOLVE_ATTEMPTED = "resolveAttempted"
            internal const val KEY_WIFI_ENABLED = "wifiEnabled"

            internal fun buildArgs(
                message: CharSequence?,
                @StringRes buttonTextResId: Int,
                @DrawableRes drawableResId: Int,
                showProgress: Boolean
            ): Bundle {
                return buildArgs(message, buttonTextResId,
                    0, drawableResId, showProgress)
            }

            internal fun buildArgs(
                message: CharSequence?,
                @StringRes button1TextResId: Int,
                @StringRes button2TextResId: Int,
                @DrawableRes drawableResId: Int,
                showProgress: Boolean
            ): Bundle {
                return bundleOf(
                    KEY_MESSAGE to message,
                    KEY_DRAWABLE to drawableResId,
                    KEY_BUTTON_1_TEXT to button1TextResId,
                    KEY_BUTTON_2_TEXT to button2TextResId,
                    KEY_PROGRESS to showProgress
                )
            }
        }
    }

    companion object {
        private val TAG = ContentController::class.java.simpleName

        private const val STATE_KEY_SITEMAP = "controllerSitemap"
        private const val STATE_KEY_PAGES = "controllerPages"
        private const val STATE_KEY_SITEMAP_FRAGMENT = "sitemapFragment"
        private const val STATE_KEY_PROGRESS_FRAGMENT = "progressFragment"
        private const val STATE_KEY_ERROR_FRAGMENT = "errorFragment"
        private const val STATE_KEY_TEMPORARY_PAGE = "temporaryPage"
        private fun makeStateKeyForPage(page: LinkedPage) = "pageFragment-${page.link}"

        @AnimRes
        internal fun determineEnterAnim(reason: FragmentUpdateReason): Int {
            return when (reason) {
                FragmentUpdateReason.PAGE_ENTER -> R.anim.slide_in_right
                FragmentUpdateReason.TEMPORARY_PAGE -> R.anim.slide_in_bottom
                FragmentUpdateReason.BACK_NAVIGATION -> R.anim.slide_in_left
                else -> 0
            }
        }

        @AnimRes
        internal fun determineExitAnim(reason: FragmentUpdateReason): Int {
            return when (reason) {
                FragmentUpdateReason.PAGE_ENTER -> R.anim.slide_out_left
                FragmentUpdateReason.TEMPORARY_PAGE -> R.anim.slide_out_bottom
                FragmentUpdateReason.BACK_NAVIGATION -> R.anim.slide_out_right
                else -> 0
            }
        }
    }
}
