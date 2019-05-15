/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui

import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.forEach
import androidx.core.view.isInvisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView

import com.google.android.material.snackbar.Snackbar
import okhttp3.Headers
import okhttp3.Request
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.core.OnUpdateBroadcastReceiver
import org.openhab.habdroid.core.VoiceService
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.connection.DemoConnection
import org.openhab.habdroid.core.connection.exception.ConnectionException
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException
import org.openhab.habdroid.model.*
import org.openhab.habdroid.ui.activity.ContentController
import org.openhab.habdroid.ui.homescreenwidget.VoiceWidget
import org.openhab.habdroid.ui.homescreenwidget.VoiceWidgetWithIcon
import org.openhab.habdroid.util.AsyncHttpClient
import org.openhab.habdroid.util.AsyncServiceResolver
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.Util

import java.nio.charset.Charset
import java.util.Locale

import javax.jmdns.ServiceInfo

class MainActivity : AbstractBaseActivity(), AsyncServiceResolver.Listener, ConnectionFactory.UpdateListener {
    private lateinit var prefs: SharedPreferences
    private var serviceResolver: AsyncServiceResolver? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var drawerMenu: Menu
    private var drawerIconTintList: ColorStateList? = null
    lateinit var viewPool: RecyclerView.RecycledViewPool
        private set
    private lateinit var progressBar: ProgressBar
    private var sitemapSelectionDialog: Dialog? = null
    private var lastSnackbar: Snackbar? = null
    var connection: Connection? = null
        private set

    private var pendingOpenSitemapUrl: String? = null
    private var pendingOpenedNotificationId: String? = null
    private var shouldOpenHabpanel: Boolean = false
    private var shouldLaunchVoiceRecognition: Boolean = false
    private var selectedSitemap: Sitemap? = null
    private lateinit var controller: ContentController
    var serverProperties: ServerProperties? = null
        private set
    private var propsUpdateHandle: ServerProperties.Companion.UpdateHandle? = null
    var isStarted: Boolean = false
        private set
    private lateinit var shortcutManager: ShortcutManager

    /**
     * Daydreaming gets us into a funk when in fullscreen, this allows us to
     * reset ourselves to fullscreen.
     * @author Dan Cunningham
     */
    private val dreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i("INTENTFILTER", "Recieved intent: $intent")
            checkFullscreen()
        }
    }

    /**
     * This method is called when activity receives a new intent while running
     */
    override fun onNewIntent(intent: Intent) {
        Log.d(TAG, "onNewIntent()")
        processIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")

        // Set default values, false means do it one time during the very first launch
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Disable screen timeout if set in preferences
        if (prefs.getBoolean(Constants.PREFERENCE_SCREENTIMEROFF, false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        super.onCreate(savedInstanceState)

        val controllerClassName = resources.getString(R.string.controller_class)
        try {
            val controllerClass = Class.forName(controllerClassName)
            val constructor = controllerClass.getConstructor(MainActivity::class.java)
            controller = constructor.newInstance(this) as ContentController
        } catch (e: Exception) {
            Log.wtf(TAG, "Could not instantiate activity controller class '"
                    + controllerClassName + "'")
            throw RuntimeException(e)
        }

        setContentView(R.layout.activity_main)
        // inflate the controller dependent content view
        controller.inflateViews(findViewById(R.id.content_stub))

        setupToolbar()
        setupDrawer()

        viewPool = RecyclerView.RecycledViewPool()

        // Check if we have openHAB page url in saved instance state?
        if (savedInstanceState != null) {
            serverProperties = savedInstanceState.getParcelable("serverProperties")
            selectedSitemap = savedInstanceState.getParcelable("sitemap")
            val lastConnectionHash = savedInstanceState.getInt("connectionHash")
            if (lastConnectionHash != -1) {
                try {
                    val c = ConnectionFactory.usableConnection
                    if (c != null && c.hashCode() == lastConnectionHash) {
                        connection = c
                    }
                } catch (e: ConnectionException) {
                    // ignored
                }

            }

            controller.onRestoreInstanceState(savedInstanceState)
            val lastControllerClass = savedInstanceState.getString("controller")
            if (controller.javaClass.canonicalName != lastControllerClass) {
                // Our controller type changed, so we need to make the new controller aware of the
                // page hierarchy. If the controller didn't change, the hierarchy will be restored
                // via the fragment state restoration.
                controller.recreateFragmentState()
            }
            if (savedInstanceState.getBoolean("isSitemapSelectionDialogShown")) {
                showSitemapSelectionDialog()
            }
        }

        processIntent(intent)

        if (isFullscreenEnabled) {
            val filter = IntentFilter(Intent.ACTION_DREAMING_STARTED)
            filter.addAction(Intent.ACTION_DREAMING_STOPPED)
            registerReceiver(dreamReceiver, filter)
        }

        //  Create a new boolean and preference and set it to true
        val isFirstStart = prefs.getBoolean("firstStart", true)

        val prefsEditor = prefs.edit()
        //  If the activity has never started before...
        if (isFirstStart) {
            //  Launch app intro
            val i = Intent(this@MainActivity, IntroActivity::class.java)
            startActivityForResult(i, INTRO_REQUEST_CODE)

            prefsEditor.putBoolean("firstStart", false)
        }
        OnUpdateBroadcastReceiver.updateComparableVersion(prefsEditor)
        prefsEditor.apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            shortcutManager = getSystemService(ShortcutManager::class.java)
        }

        val isSpeechRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(this)
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                manageVoiceRecognitionShortcut(isSpeechRecognizerAvailable)
                setVoiceWidgetComponentEnabledSetting(VoiceWidget::class.java,
                        isSpeechRecognizerAvailable)
                setVoiceWidgetComponentEnabledSetting(VoiceWidgetWithIcon::class.java,
                        isSpeechRecognizerAvailable)
                return null
            }
        }.execute()
    }

    private fun handleConnectionChange() {
        if (connection is DemoConnection) {
            showDemoModeHintSnackbar()
        } else {
            val hasLocalAndRemote = ConnectionFactory.getConnection(Connection.TYPE_LOCAL) != null
                    && ConnectionFactory.getConnection(Connection.TYPE_REMOTE) != null
            val type = connection?.connectionType
            if (hasLocalAndRemote && type == Connection.TYPE_LOCAL) {
                showSnackbar(R.string.info_conn_url)
            } else if (hasLocalAndRemote && type == Connection.TYPE_REMOTE) {
                showSnackbar(R.string.info_conn_rem_url)
            }
        }
        queryServerProperties()
    }

    fun enableWifiAndIndicateStartup() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.isWifiEnabled = true
        controller.updateConnection(null, getString(R.string.waiting_for_wifi),
                R.drawable.ic_wifi_strength_outline_black_24dp)
    }

    fun retryServerPropertyQuery() {
        controller.clearServerCommunicationFailure()
        queryServerProperties()
    }

    private fun queryServerProperties() {
        propsUpdateHandle?.cancel()
        val successCb: (ServerProperties) -> Unit = { props ->
            serverProperties = props
            updateSitemapAndHabpanelDrawerItems()
            if (props.sitemaps.isEmpty()) {
                Log.e(TAG, "openHAB returned empty sitemap list")
                controller.indicateServerCommunicationFailure(
                        getString(R.string.error_empty_sitemap_list))
            } else {
                val sitemap = selectConfiguredSitemapFromList()
                if (sitemap != null) {
                    openSitemap(sitemap)
                } else {
                    showSitemapSelectionDialog()
                }
            }
            if (connection !is DemoConnection) {
                PreferenceManager.getDefaultSharedPreferences(this)
                        .edit()
                        .putInt(Constants.PREV_SERVER_FLAGS, props.flags)
                        .apply()
            }
            openHabpanelIfNeeded()
            launchVoiceRecognitionIfNeeded()
            openPendingSitemapIfNeeded()
        }
        propsUpdateHandle = ServerProperties.fetch(connection!!,
                successCb, this::handlePropertyFetchFailure)
    }

    override fun onServiceResolved(serviceInfo: ServiceInfo) {
        Log.d(TAG, "Service resolved: "
                + serviceInfo.hostAddresses[0]
                + " port:" + serviceInfo.port)
        val serverUrl = ("https://" + serviceInfo.hostAddresses[0] + ":"
                + serviceInfo.port.toString() + "/")

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString(Constants.PREFERENCE_LOCAL_URL, serverUrl)
                .apply()
        // We'll get a connection update later
        serviceResolver = null
    }

    override fun onServiceResolveFailed() {
        Log.d(TAG, "onServiceResolveFailed()")
        controller.indicateMissingConfiguration(true)
        serviceResolver = null
    }

    private fun processIntent(intent: Intent) {
        Log.d(TAG, "Got intent: $intent")
        val action = if (intent.action != null) intent.action else ""
        when (action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED, Intent.ACTION_VIEW -> {
                val tag = intent.data?.toTagData()
                BackgroundTasksManager.enqueueNfcUpdateIfNeeded(this, tag)

                if (!tag?.sitemap.isNullOrEmpty()) {
                    pendingOpenSitemapUrl = tag?.sitemap
                    openPendingSitemapIfNeeded()
                }
            }
            ACTION_NOTIFICATION_SELECTED -> {
                CloudMessagingHelper.onNotificationSelected(this, intent)
                onNotificationSelected(intent)
            }
            ACTION_HABPANEL_SELECTED -> {
                shouldOpenHabpanel = true
                openHabpanelIfNeeded()
            }
            ACTION_VOICE_RECOGNITION_SELECTED -> {
                shouldLaunchVoiceRecognition = true
                launchVoiceRecognitionIfNeeded()
            }
            ACTION_SITEMAP_SELECTED -> {
                pendingOpenSitemapUrl = intent.getStringExtra(EXTRA_SITEMAP_URL)
                openPendingSitemapIfNeeded()
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onPostCreate()")
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged()")
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onResume() {
        Log.d(TAG, "onResume()")
        super.onResume()

        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) {
            val intent = Intent(this, javaClass)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(this, 0, intent, 0)
            nfcAdapter.enableForegroundDispatch(this, pi, null, null)
        }

        updateTitle()
    }

    override fun onPause() {
        Log.d(TAG, "onPause()")
        super.onPause()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onAvailableConnectionChanged() {
        Log.d(TAG, "onAvailableConnectionChanged()")
        var newConnection: Connection?
        var failureReason: ConnectionException?

        try {
            newConnection = ConnectionFactory.usableConnection
            failureReason = null
        } catch (e: ConnectionException) {
            newConnection = null
            failureReason = e
        }

        updateNotificationDrawerItem()

        if (newConnection != null && newConnection === connection) {
            return
        }

        connection = newConnection
        hideSnackbar()
        serverProperties = null
        selectedSitemap = null

        // Handle pending NFC tag if initial connection determination finished
        openPendingSitemapIfNeeded()
        openHabpanelIfNeeded()
        launchVoiceRecognitionIfNeeded()

        if (newConnection != null) {
            handleConnectionChange()
            controller.updateConnection(newConnection, null, 0)
        } else {
            if (failureReason is NoUrlInformationException) {
                // Attempt resolving only if we're connected locally and
                // no local connection is configured yet
                if (failureReason.wouldHaveUsedLocalConnection() && ConnectionFactory.getConnection(Connection.TYPE_LOCAL) == null) {
                    if (serviceResolver == null) {
                        serviceResolver = AsyncServiceResolver(this, this,
                                getString(R.string.openhab_service_type))
                        serviceResolver!!.start()
                        controller.updateConnection(null,
                                getString(R.string.resolving_openhab),
                                R.drawable.ic_openhab_appicon_340dp /*FIXME?*/)
                    }
                } else {
                    controller.indicateMissingConfiguration(false)
                }
            } else if (failureReason != null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (failureReason is NetworkNotSupportedException) {
                    val info = failureReason.networkInfo
                    controller.indicateNoNetwork(
                            getString(R.string.error_network_type_unsupported, info.typeName),
                            false)
                } else if (failureReason is NetworkNotAvailableException && !wifiManager.isWifiEnabled) {
                    controller.indicateNoNetwork(
                            getString(R.string.error_wifi_not_available), true)
                } else {
                    controller.indicateNoNetwork(getString(R.string.error_network_not_available),
                            false)
                }
            } else {
                controller.updateConnection(null, null, 0)
            }
        }
        viewPool.clear()
        updateSitemapAndHabpanelDrawerItems()
        invalidateOptionsMenu()
        updateTitle()
    }

    override fun onCloudConnectionChanged(connection: CloudConnection?) {
        Log.d(TAG, "onCloudConnectionChanged()")
        updateNotificationDrawerItem()
        openNotificationsPageIfNeeded()
    }

    override fun onStart() {
        Log.d(TAG, "onStart()")
        super.onStart()
        isStarted = true

        ConnectionFactory.addListener(this)

        onAvailableConnectionChanged()
        updateNotificationDrawerItem()

        if (connection != null && serverProperties == null) {
            controller.clearServerCommunicationFailure()
            queryServerProperties()
        }
        openPendingSitemapIfNeeded()
        openNotificationsPageIfNeeded()
        openHabpanelIfNeeded()
        launchVoiceRecognitionIfNeeded()
    }

    public override fun onStop() {
        Log.d(TAG, "onStop()")
        isStarted = false
        super.onStop()
        ConnectionFactory.removeListener(this)
        if (serviceResolver?.isAlive ?: false) {
            serviceResolver?.interrupt()
        }
        serviceResolver = null
        if (sitemapSelectionDialog?.isShowing ?: false) {
            sitemapSelectionDialog?.dismiss()
        }
        propsUpdateHandle?.cancel()
    }

    fun triggerPageUpdate(pageUrl: String, forceReload: Boolean) {
        controller.triggerPageUpdate(pageUrl, forceReload)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.openhab_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        // ProgressBar layout params inside the toolbar have to be done programmatically
        // because it doesn't work through layout file :-(
        progressBar = toolbar.findViewById(R.id.toolbar_progress_bar)
        progressBar.layoutParams = Toolbar.LayoutParams(Gravity.END or Gravity.CENTER_VERTICAL)
        setProgressIndicatorVisible(false)
    }

    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        drawerToggle = ActionBarDrawerToggle(this, drawerLayout,
                R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                if (serverProperties != null && propsUpdateHandle == null) {
                    propsUpdateHandle = ServerProperties.updateSitemaps(serverProperties!!,
                            connection!!,
                            { props ->
                                serverProperties = props
                                openPendingSitemapIfNeeded()
                                updateSitemapAndHabpanelDrawerItems()
                            },
                            this@MainActivity::handlePropertyFetchFailure)
                }
            }
        })
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)

        val drawerView = findViewById<NavigationView>(R.id.left_drawer)
        drawerView.inflateMenu(R.menu.left_drawer)
        drawerMenu = drawerView.menu

        // We only want to tint the menu icons, but not our loaded sitemap icons. NavigationView
        // unfortunately doesn't support this directly, so we tint the icon drawables manually
        // instead of letting NavigationView do it.
        drawerIconTintList = drawerView.itemIconTintList
        drawerView.itemIconTintList = null
        drawerMenu.forEach { item -> item.icon = applyDrawerIconTint(item.icon) }

        drawerView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawers()
            var handled = false
            when (item.itemId) {
                R.id.notifications -> {
                    openNotifications(null)
                    handled = true
                }
                R.id.habpanel -> {
                    openHabpanel()
                    handled = true
                }
                R.id.settings -> {
                    val settingsIntent = Intent(this@MainActivity,
                            PreferencesActivity::class.java)
                    settingsIntent.putExtra(PreferencesActivity.START_EXTRA_SERVER_PROPERTIES,
                            serverProperties)
                    startActivityForResult(settingsIntent, SETTINGS_REQUEST_CODE)
                    handled = true
                }
                R.id.about -> {
                    openAbout()
                    handled = true
                }
            }
            if (item.groupId == GROUP_ID_SITEMAPS) {
                val sitemap = serverProperties!!.sitemaps[item.itemId]
                openSitemap(sitemap)
                handled = true
            }
            handled
        }
    }

    private fun updateNotificationDrawerItem() {
        val notificationsItem = drawerMenu.findItem(R.id.notifications)
        val hasCloudConnection = ConnectionFactory.getConnection(Connection.TYPE_CLOUD) != null
        notificationsItem.isVisible = hasCloudConnection
        if (hasCloudConnection) {
            manageNotificationShortcut(true)
        }
    }

    private fun updateSitemapAndHabpanelDrawerItems() {
        val sitemapItem = drawerMenu.findItem(R.id.sitemaps)
        val habpanelItem = drawerMenu.findItem(R.id.habpanel)
        val props = serverProperties
        if (props == null) {
            sitemapItem.isVisible = false
            habpanelItem.isVisible = false
        } else {
            habpanelItem.isVisible = props.hasHabpanelInstalled()
            manageHabpanelShortcut(props.hasHabpanelInstalled())
            val defaultSitemapName = prefs.getString(Constants.PREFERENCE_SITEMAP_NAME, "") as String
            val sitemaps = props.sitemaps.sortedWithDefaultName(defaultSitemapName)

            if (sitemaps.isEmpty()) {
                sitemapItem.isVisible = false
            } else {
                sitemapItem.isVisible = true
                val menu = sitemapItem.subMenu
                menu.clear()

                sitemaps.forEachIndexed { index, sitemap ->
                    val item = menu.add(GROUP_ID_SITEMAPS, index, index, sitemap.label)
                    loadSitemapIcon(sitemap, item)
                }
            }
        }
    }

    private fun loadSitemapIcon(sitemap: Sitemap, item: MenuItem) {
        val url = if (sitemap.icon != null) Uri.encode(sitemap.iconPath, "/?=") else null
        val defaultIcon = ContextCompat.getDrawable(this, R.drawable.ic_openhab_appicon_24dp)
        item.icon = applyDrawerIconTint(defaultIcon)

        if (url == null || connection == null) {
            return
        }
        connection!!.asyncHttpClient[url, object : AsyncHttpClient.BitmapResponseHandler(defaultIcon!!.intrinsicWidth) {
            override fun onFailure(request: Request, statusCode: Int, error: Throwable) {
                Log.w(TAG, "Could not fetch icon for sitemap " + sitemap.name)
            }

            override fun onSuccess(response: Bitmap, headers: Headers) {
                item.icon = BitmapDrawable(resources, response)
            }
        }]
    }

    private fun applyDrawerIconTint(icon: Drawable?): Drawable? {
        if (icon == null) {
            return null
        }
        val wrapped = DrawableCompat.wrap(icon.mutate())
        DrawableCompat.setTintList(wrapped, drawerIconTintList)
        return wrapped
    }

    private fun openNotificationsPageIfNeeded() {
        if (pendingOpenedNotificationId != null && isStarted
                && ConnectionFactory.getConnection(Connection.TYPE_CLOUD) != null) {
            openNotifications(pendingOpenedNotificationId)
            pendingOpenedNotificationId = null
        }
    }

    private fun openHabpanelIfNeeded() {
        if (isStarted && shouldOpenHabpanel && serverProperties != null
                && serverProperties!!.hasHabpanelInstalled()) {
            openHabpanel()
            shouldOpenHabpanel = false
        }
    }

    private fun launchVoiceRecognitionIfNeeded() {
        if (isStarted && shouldLaunchVoiceRecognition && serverProperties != null) {
            launchVoiceRecognition()
            shouldLaunchVoiceRecognition = false
        }
    }

    private fun openPendingSitemapIfNeeded() {
        val url = pendingOpenSitemapUrl
        if (isStarted && url != null && serverProperties != null) {
            buildUrlAndOpenSitemap(url)
            pendingOpenSitemapUrl = null
        }
    }

    private fun openAbout() {
        val aboutIntent = Intent(this, AboutActivity::class.java)
        aboutIntent.putExtra("serverProperties", serverProperties)

        startActivityForResult(aboutIntent, INFO_REQUEST_CODE)
    }

    private fun selectConfiguredSitemapFromList(): Sitemap? {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val configuredSitemap = settings.getString(Constants.PREFERENCE_SITEMAP_NAME, "") as String
        val sitemaps = serverProperties!!.sitemaps
        val result: Sitemap?

        if (sitemaps.size == 1) {
            // We only have one sitemap, use it
            result = sitemaps[0]
        } else if (!configuredSitemap.isEmpty()) {
            // Select configured sitemap if still present, nothing otherwise
            result = sitemaps.firstOrNull { sitemap -> sitemap.name == configuredSitemap }
        } else {
            // Nothing configured -> can't auto-select anything
            result = null
        }

        Log.d(TAG, "Configured sitemap is '$configuredSitemap', selected $result")
        if (result == null && !configuredSitemap.isEmpty()) {
            // clear old configuration
            settings.edit()
                    .remove(Constants.PREFERENCE_SITEMAP_LABEL)
                    .remove(Constants.PREFERENCE_SITEMAP_NAME)
                    .apply()
        } else if (result != null && (configuredSitemap.isEmpty() || configuredSitemap != result.name)) {
            // update result
            settings.edit()
                    .putString(Constants.PREFERENCE_SITEMAP_NAME, result.name)
                    .putString(Constants.PREFERENCE_SITEMAP_LABEL, result.label)
                    .apply()
        }

        return result
    }

    private fun showSitemapSelectionDialog() {
        Log.d(TAG, "Opening sitemap selection dialog")
        if (sitemapSelectionDialog?.isShowing ?: false) {
            sitemapSelectionDialog?.dismiss()
        }
        if (isFinishing) {
            return
        }

        val sitemaps = serverProperties!!.sitemaps
        val sitemapLabels = sitemaps.map { s -> s.label }.toTypedArray()
        sitemapSelectionDialog = AlertDialog.Builder(this)
                .setTitle(R.string.mainmenu_openhab_selectsitemap)
                .setItems(sitemapLabels) { _, which ->
                    val sitemap = sitemaps[which]
                    Log.d(TAG, "Selected sitemap $sitemap")
                    PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                            .edit()
                            .putString(Constants.PREFERENCE_SITEMAP_NAME, sitemap.name)
                            .putString(Constants.PREFERENCE_SITEMAP_LABEL, sitemap.label)
                            .apply()
                    openSitemap(sitemap)
                }
                .show()
    }

    private fun openNotifications(highlightedId: String?) {
        controller.openNotifications(highlightedId)
        drawerToggle.isDrawerIndicatorEnabled = false
    }

    private fun openHabpanel() {
        controller.showHabpanel()
        drawerToggle.isDrawerIndicatorEnabled = false
    }

    private fun openSitemap(sitemap: Sitemap) {
        Log.i(TAG, "Opening sitemap $sitemap, currently selected $selectedSitemap")
        if (sitemap != selectedSitemap) {
            selectedSitemap = sitemap
            controller.openSitemap(sitemap)
        }
    }


    private fun buildUrlAndOpenSitemap(partUrl: String) {
        val newPageUrl = String.format(Locale.US, "rest/sitemaps%s", partUrl)
        controller.openPage(newPageUrl)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "onCreateOptionsMenu()")
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "onPrepareOptionsMenu()")
        val voiceRecognitionItem = menu.findItem(R.id.mainmenu_voice_recognition)
        @ColorInt val iconColor = ContextCompat.getColor(this, R.color.light)
        voiceRecognitionItem.isVisible = connection != null
        voiceRecognitionItem.icon.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")
        // Handle back navigation arrow
        if (item.itemId == android.R.id.home && controller.canGoBack()) {
            controller.goBack()
            return true
        }

        // Handle hamburger menu
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }

        // Handle menu items
        when (item.itemId) {
            R.id.mainmenu_voice_recognition -> {
                launchVoiceRecognition()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, String.format("onActivityResult() requestCode = %d, resultCode = %d",
                requestCode, resultCode))
        when (requestCode) {
            SETTINGS_REQUEST_CODE -> {
                if (data == null) {
                    return
                }
                if (data.getBooleanExtra(PreferencesActivity.RESULT_EXTRA_SITEMAP_CLEARED, false)
                        && connection != null && serverProperties != null) {
                    val sitemap = selectConfiguredSitemapFromList()
                    if (sitemap != null) {
                        openSitemap(sitemap)
                    } else {
                        showSitemapSelectionDialog()
                    }
                }
                if (data.getBooleanExtra(PreferencesActivity.RESULT_EXTRA_THEME_CHANGED, false)) {
                    recreate()
                }
            }
            WRITE_NFC_TAG_REQUEST_CODE -> Log.d(TAG, "Got back from Write NFC tag")
        }
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        Log.d(TAG, "onSaveInstanceState()")
        isStarted = false
        savedInstanceState.putParcelable("serverProperties", serverProperties)
        savedInstanceState.putParcelable("sitemap", selectedSitemap)
        savedInstanceState.putBoolean("isSitemapSelectionDialogShown",
                sitemapSelectionDialog?.isShowing ?: false)
        savedInstanceState.putString("controller", controller.javaClass.canonicalName)
        savedInstanceState.putInt("connectionHash", connection?.hashCode() ?: -1)
        controller.onSaveInstanceState(savedInstanceState)
        super.onSaveInstanceState(savedInstanceState)
    }

    private fun onNotificationSelected(intent: Intent) {
        Log.d(TAG, "onNotificationSelected()")
        // mPendingOpenedNotificationId being non-null is used as trigger for
        // opening the notifications page, so use a dummy if it's null
        pendingOpenedNotificationId = intent.getStringExtra(EXTRA_PERSISTED_NOTIFICATION_ID) ?: ""
        openNotificationsPageIfNeeded()
    }

    fun onWidgetSelected(linkedPage: LinkedPage, source: WidgetListFragment) {
        Log.d(TAG, "Got widget link = " + linkedPage.link)
        controller.openPage(linkedPage, source)
    }

    fun updateTitle() {
        val title = controller.currentTitle
        setTitle(title ?: getString(R.string.app_name))
        drawerToggle.isDrawerIndicatorEnabled = !controller.canGoBack()
    }

    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed()")
        if (controller.canGoBack()) {
            controller.goBack()
        } else if (!isFullscreenEnabled) {
            // Only handle back action in non-fullscreen mode, as we don't want to exit
            // the app via back button in fullscreen mode
            super.onBackPressed()
        }
    }

    fun setProgressIndicatorVisible(visible: Boolean) {
        progressBar.isInvisible = !visible
    }

    private fun launchVoiceRecognition() {
        val callbackIntent = Intent(this, VoiceService::class.java)
        val openhabPendingIntent = PendingIntent.getService(this, 0, callbackIntent, 0)

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        // Display an hint to the user about what he should say.
        speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.info_voice_input))
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        speechIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, openhabPendingIntent)

        try {
            startActivity(speechIntent)
        } catch (speechRecognizerNotFoundException: ActivityNotFoundException) {
            showSnackbar(R.string.error_no_speech_to_text_app_found, R.string.install, {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                            "market://details?id=com.google.android.googlequicksearchbox".toUri()))
                } catch (appStoreNotFoundException: ActivityNotFoundException) {
                    Util.openInBrowser(this, "http://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox")
                }
            })
        }

    }

    fun showRefreshHintSnackbarIfNeeded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(Constants.PREFERENCE_SWIPE_REFRESH_EXPLAINED, false)) {
            return
        }

        showSnackbar(R.string.swipe_to_refresh_description, R.string.swipe_to_refresh_dismiss, {
            prefs.edit()
                    .putBoolean(Constants.PREFERENCE_SWIPE_REFRESH_EXPLAINED, true)
                    .apply()
        })
    }

    fun showDemoModeHintSnackbar() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        showSnackbar(R.string.info_demo_mode_short, R.string.turn_off, {
            prefs.edit()
                    .putBoolean(Constants.PREFERENCE_DEMOMODE, false)
                    .apply()
        })
    }

    private fun showSnackbar(@StringRes messageResId: Int, @StringRes actionResId: Int = 0,
                             onClickListener: (() -> Unit)? = null) {
        hideSnackbar()
        lastSnackbar = Snackbar.make(findViewById(android.R.id.content), messageResId,
                Snackbar.LENGTH_LONG)
        if (actionResId != 0 && onClickListener != null) {
            lastSnackbar!!.setAction(actionResId, { onClickListener() })
        }
        lastSnackbar!!.show()
    }

    private fun hideSnackbar() {
        lastSnackbar?.dismiss()
        lastSnackbar = null
    }

    private fun handlePropertyFetchFailure(request: Request, statusCode: Int, error: Throwable) {
        Log.e(TAG, "Error: $error", error)
        Log.e(TAG, "HTTP status code: $statusCode")
        var message = Util.getHumanReadableErrorMessage(this,
                request.url().toString(), statusCode, error)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
        if (prefs.getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false)) {
            val builder = SpannableStringBuilder(message)
            val detailsStart = builder.length

            builder.append("\n\nURL: ").append(request.url().toString())

            val authHeader = request.header("Authorization")
            if (authHeader != null && authHeader.startsWith("Basic")) {
                val base64Credentials = authHeader.substring("Basic".length).trim { it <= ' ' }
                val credentials = String(Base64.decode(base64Credentials, Base64.DEFAULT),
                        Charset.forName("UTF-8"))
                builder.append("\nUsername: ")
                        .append(credentials.substring(0, credentials.indexOf(":")))
            }

            builder.append("\nException stack:\n")

            val exceptionStart = builder.length
            var origError: Throwable?
            var cause: Throwable? = error
            do {
                builder.append(cause?.toString()).append('\n')
                origError = cause
                cause = origError?.cause
            } while (cause != null && origError !== cause)

            builder.setSpan(RelativeSizeSpan(0.8f), detailsStart, exceptionStart,
                    SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
            builder.setSpan(RelativeSizeSpan(0.6f), exceptionStart, builder.length,
                    SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
            message = builder
        }

        controller.indicateServerCommunicationFailure(message)
        propsUpdateHandle = null
    }

    private fun manageHabpanelShortcut(visible: Boolean) {
        manageShortcut(visible, "habpanel", ACTION_HABPANEL_SELECTED,
                R.string.mainmenu_openhab_habpanel, R.mipmap.ic_shortcut_habpanel,
                R.string.app_shortcut_diabled_habpanel)
    }

    private fun manageNotificationShortcut(visible: Boolean) {
        manageShortcut(visible, "notification", ACTION_NOTIFICATION_SELECTED,
                R.string.app_notifications, R.mipmap.ic_shortcut_notifications,
                R.string.app_shortcut_diabled_notifications)
    }

    private fun manageVoiceRecognitionShortcut(visible: Boolean) {
        manageShortcut(visible, "voice_recognition", ACTION_VOICE_RECOGNITION_SELECTED,
                R.string.mainmenu_openhab_voice_recognition,
                R.mipmap.ic_shortcut_voice_recognition,
                R.string.app_shortcut_diabled_voice_recognition)
    }

    private fun manageShortcut(visible: Boolean, id: String, action: String,
                               @StringRes shortLabel: Int, @DrawableRes icon: Int, @StringRes disableMessage: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        if (visible) {
            val intent = Intent(this, MainActivity::class.java)
            intent.action = action
            val shortcut = ShortcutInfo.Builder(this, id)
                    .setShortLabel(getString(shortLabel))
                    .setIcon(Icon.createWithResource(this, icon))
                    .setIntent(intent)
                    .build()
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
        } else {
            shortcutManager.disableShortcuts(listOf(id), getString(disableMessage))
        }
    }

    private fun setVoiceWidgetComponentEnabledSetting(component: Class<*>,
                                                      isSpeechRecognizerAvailable: Boolean) {
        val voiceWidget = ComponentName(this, component)
        val pm = packageManager
        val newState = if (isSpeechRecognizerAvailable)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(voiceWidget, newState, PackageManager.DONT_KILL_APP)
    }

    companion object {
        val ACTION_NOTIFICATION_SELECTED = "org.openhab.habdroid.action.NOTIFICATION_SELECTED"
        val ACTION_HABPANEL_SELECTED = "org.openhab.habdroid.action.HABPANEL_SELECTED"
        val ACTION_VOICE_RECOGNITION_SELECTED = "org.openhab.habdroid.action.VOICE_SELECTED"
        val ACTION_SITEMAP_SELECTED = "org.openhab.habdroid.action.SITEMAP_SELECTED"
        val EXTRA_SITEMAP_URL = "sitemapUrl"
        val EXTRA_PERSISTED_NOTIFICATION_ID = "persistedNotificationId"

        private val TAG = MainActivity::class.java.simpleName

        // Activities request codes
        private val INTRO_REQUEST_CODE = 1001
        private val SETTINGS_REQUEST_CODE = 1002
        private val WRITE_NFC_TAG_REQUEST_CODE = 1003
        private val INFO_REQUEST_CODE = 1004
        // Drawer item codes
        private val GROUP_ID_SITEMAPS = 1
    }
}