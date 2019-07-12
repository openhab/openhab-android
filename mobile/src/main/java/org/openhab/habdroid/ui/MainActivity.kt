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

package org.openhab.habdroid.ui

import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.util.Base64
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.text.inSpans
import androidx.core.view.GravityCompat
import androidx.core.view.forEach
import androidx.core.view.isInvisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import org.openhab.habdroid.core.connection.exception.*
import org.openhab.habdroid.model.*
import org.openhab.habdroid.ui.activity.ContentController
import org.openhab.habdroid.ui.homescreenwidget.VoiceWidget
import org.openhab.habdroid.ui.homescreenwidget.VoiceWidgetWithIcon
import org.openhab.habdroid.util.*
import java.nio.charset.Charset
import javax.jmdns.ServiceInfo

class MainActivity : AbstractBaseActivity(), ConnectionFactory.UpdateListener {
    private lateinit var prefs: SharedPreferences
    private var serviceResolveJob: Job? = null
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

    private var pendingAction: PendingAction? = null
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
            Log.i("INTENTFILTER", "Received intent: $intent")
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

        prefs = getPrefs()

        // Disable screen timeout if set in preferences
        if (prefs.isScreenTimerDisabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        super.onCreate(savedInstanceState)

        val controllerClassName = resources.getString(R.string.controller_class)
        try {
            val controllerClass = Class.forName(controllerClassName)
            val constructor = controllerClass.getConstructor(MainActivity::class.java)
            controller = constructor.newInstance(this) as ContentController
        } catch (e: Exception) {
            Log.wtf(TAG, "Could not instantiate activity controller class '$controllerClassName'")
            throw RuntimeException(e)
        }

        setContentView(R.layout.activity_main)
        // inflate the controller dependent content view
        controller.inflateViews(findViewById(R.id.content_stub))

        setupToolbar()
        setupDrawer()

        viewPool = RecyclerView.RecycledViewPool()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            shortcutManager = getSystemService(ShortcutManager::class.java)
        }

        // Check if we have openHAB page url in saved instance state?
        if (savedInstanceState != null) {
            serverProperties = savedInstanceState.getParcelable(STATE_KEY_SERVER_PROPERTIES)
            val lastConnectionHash = savedInstanceState.getInt(STATE_KEY_CONNECTION_HASH)
            if (lastConnectionHash != -1) {
                val c = ConnectionFactory.usableConnectionOrNull
                if (c != null && c.hashCode() == lastConnectionHash) {
                    connection = c
                }
            }

            controller.onRestoreInstanceState(savedInstanceState)
            val lastControllerClass = savedInstanceState.getString(STATE_KEY_CONTROLLER_NAME)
            if (controller.javaClass.canonicalName != lastControllerClass) {
                // Our controller type changed, so we need to make the new controller aware of the
                // page hierarchy. If the controller didn't change, the hierarchy will be restored
                // via the fragment state restoration.
                controller.recreateFragmentState()
            }
            if (savedInstanceState.getBoolean(STATE_KEY_SITEMAP_SELECTION_SHOWN)) {
                showSitemapSelectionDialog()
            }

            updateSitemapAndHabPanelDrawerItems()
            updateNotificationDrawerItem()
        }

        processIntent(intent)

        if (isFullscreenEnabled) {
            val filter = IntentFilter(Intent.ACTION_DREAMING_STARTED)
            filter.addAction(Intent.ACTION_DREAMING_STOPPED)
            registerReceiver(dreamReceiver, filter)
        }

        //  Create a new boolean and preference and set it to true
        val isFirstStart = prefs.getBoolean(Constants.PREFERENCE_FIRST_START, true)

        val prefsEditor = prefs.edit()
        //  If the activity has never started before...
        if (isFirstStart) {
            //  Launch app intro
            val i = Intent(this@MainActivity, IntroActivity::class.java)
            startActivityForResult(i, INTRO_REQUEST_CODE)

            prefsEditor.putBoolean(Constants.PREFERENCE_FIRST_START, false)
        }
        OnUpdateBroadcastReceiver.updateComparableVersion(prefsEditor)
        prefsEditor.apply()

        val isSpeechRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(this)
        GlobalScope.launch {
            manageVoiceRecognitionShortcut(isSpeechRecognizerAvailable)
            setVoiceWidgetComponentEnabledSetting(VoiceWidget::class.java, isSpeechRecognizerAvailable)
            setVoiceWidgetComponentEnabledSetting(VoiceWidgetWithIcon::class.java, isSpeechRecognizerAvailable)
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
        handlePendingAction()
    }

    public override fun onStop() {
        Log.d(TAG, "onStop()")
        isStarted = false
        super.onStop()
        ConnectionFactory.removeListener(this)
        serviceResolveJob?.cancel()
        serviceResolveJob = null
        if (sitemapSelectionDialog?.isShowing == true) {
            sitemapSelectionDialog?.dismiss()
        }
        propsUpdateHandle?.cancel()
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
        return when (item.itemId) {
            R.id.mainmenu_voice_recognition -> {
                launchVoiceRecognition()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult() requestCode = $requestCode, resultCode = $resultCode")
        when (requestCode) {
            SETTINGS_REQUEST_CODE -> {
                if (data == null) {
                    return
                }
                if (data.getBooleanExtra(PreferencesActivity.RESULT_EXTRA_SITEMAP_CLEARED, false)) {
                    executeOrStoreAction(PendingAction.ChooseSitemap())
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
        with(savedInstanceState) {
            putParcelable(STATE_KEY_SERVER_PROPERTIES, serverProperties)
            putBoolean(STATE_KEY_SITEMAP_SELECTION_SHOWN, sitemapSelectionDialog?.isShowing == true)
            putString(STATE_KEY_CONTROLLER_NAME, controller.javaClass.canonicalName)
            putInt(STATE_KEY_CONNECTION_HASH, connection?.hashCode() ?: -1)
            controller.onSaveInstanceState(this)
        }
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed()")
        when {
            controller.canGoBack() -> controller.goBack()
            isFullscreenEnabled -> when {
                lastSnackbar?.isShown != true ->
                    showSnackbar(R.string.press_back_to_exit, tag = TAG_SNACKBAR_PRESS_AGAIN_EXIT)
                lastSnackbar?.view?.tag?.toString() == TAG_SNACKBAR_PRESS_AGAIN_EXIT -> super.onBackPressed()
                else -> showSnackbar(R.string.press_back_to_exit, tag = TAG_SNACKBAR_PRESS_AGAIN_EXIT)
            }
            else -> super.onBackPressed()
        }
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
        handlePendingAction()

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        when {
            newConnection != null -> {
                handleConnectionChange()
                controller.updateConnection(newConnection, null, 0)
            }
            failureReason is NoUrlInformationException -> {
                // Attempt resolving only if we're connected locally and
                // no local connection is configured yet
                if (failureReason.wouldHaveUsedLocalConnection() && ConnectionFactory.localConnection == null) {
                    if (serviceResolveJob == null) {
                        val resolver = AsyncServiceResolver(this,
                            getString(R.string.openhab_service_type), this)
                        serviceResolveJob = launch {
                            handleServiceResolveResult(resolver.resolve())
                            serviceResolveJob = null
                        }
                        controller.updateConnection(null,
                            getString(R.string.resolving_openhab),
                            R.drawable.ic_openhab_appicon_340dp /*FIXME?*/)
                    }
                } else {
                    controller.indicateMissingConfiguration(false)
                }
            }
            failureReason is NetworkNotSupportedException -> {
                controller.indicateNoNetwork(getString(R.string.error_network_type_unsupported), false)
            }
            failureReason is NetworkNotAvailableException && !wifiManager.isWifiEnabled -> {
                controller.indicateNoNetwork(
                    getString(R.string.error_wifi_not_available), true)
            }
            failureReason is ConnectionNotInitializedException -> {
                controller.updateConnection(null, null, 0)
            }
            else -> {
                controller.indicateNoNetwork(getString(R.string.error_network_not_available), false)
            }
        }

        viewPool.clear()
        updateSitemapAndHabPanelDrawerItems()
        invalidateOptionsMenu()
        updateTitle()
    }

    override fun onCloudConnectionChanged(connection: CloudConnection?) {
        Log.d(TAG, "onCloudConnectionChanged()")
        updateNotificationDrawerItem()
        handlePendingAction()
    }

    private fun handleConnectionChange() {
        if (connection is DemoConnection) {
            showDemoModeHintSnackbar()
        } else {
            val hasLocalAndRemote =
                ConnectionFactory.localConnection != null && ConnectionFactory.remoteConnection != null
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

    override fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean {
        return mode == ScreenLockMode.Enabled
    }

    private fun queryServerProperties() {
        propsUpdateHandle?.cancel()
        val successCb: (ServerProperties) -> Unit = { props ->
            serverProperties = props
            updateSitemapAndHabPanelDrawerItems()
            if (props.sitemaps.isEmpty()) {
                Log.e(TAG, "openHAB returned empty sitemap list")
                controller.indicateServerCommunicationFailure(getString(R.string.error_empty_sitemap_list))
            } else {
                chooseSitemap()
            }
            if (connection !is DemoConnection) {
                prefs.edit {
                    putInt(Constants.PREV_SERVER_FLAGS, props.flags)
                }
            }
            handlePendingAction()
        }
        propsUpdateHandle = ServerProperties.fetch(this, connection!!,
            successCb, this::handlePropertyFetchFailure)
    }

    private fun chooseSitemap() {
        val sitemap = selectConfiguredSitemapFromList()
        if (sitemap != null) {
            controller.openSitemap(sitemap)
        } else {
            showSitemapSelectionDialog()
        }
    }

    private fun handleServiceResolveResult(info: ServiceInfo?) {
        if (info != null) {
            val address = info.hostAddresses[0]
            val port = info.port.toString()
            Log.d(TAG, "Service resolved: $address port: $port")

            prefs.edit {
                putString(Constants.PREFERENCE_LOCAL_URL, "https://$address:$port")
            }
        } else {
            Log.d(TAG, "onServiceResolveFailed()")
            controller.indicateMissingConfiguration(true)
        }
    }

    private fun processIntent(intent: Intent) {
        Log.d(TAG, "Got intent: $intent")
        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED, Intent.ACTION_VIEW -> {
                val tag = intent.data?.toTagData()
                BackgroundTasksManager.enqueueNfcUpdateIfNeeded(this, tag)

                val sitemapUrl = tag?.sitemap
                if (!sitemapUrl.isNullOrEmpty()) {
                    executeOrStoreAction(PendingAction.OpenSitemapUrl(sitemapUrl))
                }
            }
            ACTION_NOTIFICATION_SELECTED -> {
                CloudMessagingHelper.onNotificationSelected(this, intent)
                val id = intent.getStringExtra(EXTRA_PERSISTED_NOTIFICATION_ID).orEmpty()
                executeActionIfPossible(PendingAction.OpenNotification(id))
            }
            ACTION_HABPANEL_SELECTED -> executeOrStoreAction(PendingAction.OpenHabPanel())
            ACTION_VOICE_RECOGNITION_SELECTED -> executeOrStoreAction(PendingAction.LaunchVoiceRecognition())
            ACTION_SITEMAP_SELECTED -> {
                val sitemapUrl = intent.getStringExtra(EXTRA_SITEMAP_URL)
                executeOrStoreAction(PendingAction.OpenSitemapUrl(sitemapUrl))
            }
        }
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
                    propsUpdateHandle = ServerProperties.updateSitemaps(this@MainActivity,
                        serverProperties!!, connection!!,
                        { props ->
                            serverProperties = props
                            updateSitemapAndHabPanelDrawerItems()
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
                    openHabPanel()
                    handled = true
                }
                R.id.settings -> {
                    val settingsIntent = Intent(this@MainActivity, PreferencesActivity::class.java)
                    settingsIntent.putExtra(PreferencesActivity.START_EXTRA_SERVER_PROPERTIES, serverProperties)
                    startActivityForResult(settingsIntent, SETTINGS_REQUEST_CODE)
                    handled = true
                }
                R.id.about -> {
                    openAbout()
                    handled = true
                }
            }
            if (item.groupId == GROUP_ID_SITEMAPS) {
                val sitemap = serverProperties?.sitemaps?.firstOrNull { s -> s.name.hashCode() == item.itemId }
                if (sitemap != null) {
                    controller.openSitemap(sitemap)
                    handled = true
                }
            }
            handled
        }
    }

    private fun updateNotificationDrawerItem() {
        val notificationsItem = drawerMenu.findItem(R.id.notifications)
        val hasCloudConnection = ConnectionFactory.cloudConnection != null
        notificationsItem.isVisible = hasCloudConnection
        if (hasCloudConnection) {
            manageNotificationShortcut(true)
        }
    }

    private fun updateSitemapAndHabPanelDrawerItems() {
        val sitemapItem = drawerMenu.findItem(R.id.sitemaps)
        val habPanelItem = drawerMenu.findItem(R.id.habpanel)
        val props = serverProperties
        if (props == null) {
            sitemapItem.isVisible = false
            habPanelItem.isVisible = false
        } else {
            habPanelItem.isVisible = props.hasHabPanelInstalled()
            manageHabPanelShortcut(props.hasHabPanelInstalled())
            val sitemaps = props.sitemaps.sortedWithDefaultName(prefs.getDefaultSitemap())

            if (sitemaps.isEmpty()) {
                sitemapItem.isVisible = false
            } else {
                sitemapItem.isVisible = true
                val menu = sitemapItem.subMenu
                menu.clear()

                sitemaps.forEachIndexed { index, sitemap ->
                    val item = menu.add(GROUP_ID_SITEMAPS, sitemap.name.hashCode(), index, sitemap.label)
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
        launch {
            try {
                item.icon = connection!!.httpClient.get(url)
                    .asBitmap(defaultIcon!!.intrinsicWidth, true)
                    .response
                    .toDrawable(resources)
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Could not fetch icon for sitemap ${sitemap.name}")
            }
        }
    }

    private fun applyDrawerIconTint(icon: Drawable?): Drawable? {
        if (icon == null) {
            return null
        }
        val wrapped = DrawableCompat.wrap(icon.mutate())
        DrawableCompat.setTintList(wrapped, drawerIconTintList)
        return wrapped
    }

    private fun executeOrStoreAction(action: PendingAction) {
        if (!executeActionIfPossible(action)) {
            pendingAction = action
        }
    }

    private fun handlePendingAction() {
        val action = pendingAction
        if (action != null && executeActionIfPossible(action)) {
            pendingAction = null
        }
    }

    private fun executeActionIfPossible(action: PendingAction): Boolean = when {
        action is PendingAction.ChooseSitemap && isStarted -> {
            chooseSitemap()
            true
        }
        action is PendingAction.OpenSitemapUrl && isStarted && serverProperties != null -> {
            buildUrlAndOpenSitemap(action.url)
            true
        }
        action is PendingAction.OpenHabPanel && isStarted && serverProperties?.hasHabPanelInstalled() == true -> {
            openHabPanel()
            true
        }
        action is PendingAction.LaunchVoiceRecognition && serverProperties != null -> {
            launchVoiceRecognition()
            true
        }
        action is PendingAction.OpenNotification && isStarted && ConnectionFactory.cloudConnection != null -> {
            openNotifications(action.notificationId)
            true
        }
        else -> false
    }

    private fun openAbout() {
        val aboutIntent = Intent(this, AboutActivity::class.java)
        aboutIntent.putExtra("serverProperties", serverProperties)

        startActivityForResult(aboutIntent, INFO_REQUEST_CODE)
    }

    private fun selectConfiguredSitemapFromList(): Sitemap? {
        val configuredSitemap = prefs.getDefaultSitemap()
        val sitemaps = serverProperties?.sitemaps
        val result = when {
            sitemaps == null -> null
            // We only have one sitemap, use it
            sitemaps.size == 1 -> sitemaps[0]
            // Select configured sitemap if still present, nothing otherwise
            configuredSitemap.isNotEmpty() -> sitemaps.firstOrNull { sitemap -> sitemap.name == configuredSitemap }
            // Nothing configured -> can't auto-select anything
            else -> null
        }

        Log.d(TAG, "Configured sitemap is '$configuredSitemap', selected $result")
        prefs.edit {
            if (result == null && configuredSitemap.isNotEmpty()) {
                // clear old configuration
                updateDefaultSitemap(null)
            } else if (result != null && (configuredSitemap.isEmpty() || configuredSitemap != result.name)) {
                // update result
                updateDefaultSitemap(result)
            }
        }

        return result
    }

    private fun showSitemapSelectionDialog() {
        Log.d(TAG, "Opening sitemap selection dialog")
        if (sitemapSelectionDialog?.isShowing == true) {
            sitemapSelectionDialog?.dismiss()
        }
        val sitemaps = serverProperties?.sitemaps
        if (isFinishing || sitemaps == null) {
            return
        }
        val sitemapLabels = sitemaps.map { s -> s.label }.toTypedArray()
        sitemapSelectionDialog = AlertDialog.Builder(this)
            .setTitle(R.string.mainmenu_openhab_selectsitemap)
            .setItems(sitemapLabels) { _, which ->
                val sitemap = sitemaps[which]
                Log.d(TAG, "Selected sitemap $sitemap")
                prefs.edit {
                    updateDefaultSitemap(sitemap)
                }
                controller.openSitemap(sitemap)
            }
            .show()
    }

    private fun openNotifications(highlightedId: String?) {
        controller.openNotifications(highlightedId)
        drawerToggle.isDrawerIndicatorEnabled = false
    }

    private fun openHabPanel() {
        controller.showHabPanel()
        drawerToggle.isDrawerIndicatorEnabled = false
    }

    private fun buildUrlAndOpenSitemap(partUrl: String) {
        controller.openPage("rest/sitemaps$partUrl")
    }

    fun onWidgetSelected(linkedPage: LinkedPage, source: WidgetListFragment) {
        Log.d(TAG, "Got widget link = ${linkedPage.link}")
        controller.openPage(linkedPage, source)
    }

    fun updateTitle() {
        val title = controller.currentTitle
        setTitle(title ?: getString(R.string.app_name))
        drawerToggle.isDrawerIndicatorEnabled = !controller.canGoBack()
    }

    fun setProgressIndicatorVisible(visible: Boolean) {
        progressBar.isInvisible = !visible
    }

    private fun launchVoiceRecognition() {
        val callbackIntent = Intent(this, VoiceService::class.java)
        val openhabPendingIntent = PendingIntent.getService(this, 0, callbackIntent, 0)

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Display an hint to the user about what he should say.
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.info_voice_input))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, openhabPendingIntent)
        }

        try {
            startActivity(speechIntent)
        } catch (speechRecognizerNotFoundException: ActivityNotFoundException) {
            showSnackbar(R.string.error_no_speech_to_text_app_found, R.string.install) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        "market://details?id=com.google.android.googlequicksearchbox".toUri()))
                } catch (appStoreNotFoundException: ActivityNotFoundException) {
                    "http://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox"
                        .toUri()
                        .openInBrowser(this)
                }
            }
        }
    }

    fun showRefreshHintSnackbarIfNeeded() {
        if (prefs.getBoolean(Constants.PREFERENCE_SWIPE_REFRESH_EXPLAINED, false)) {
            return
        }

        showSnackbar(R.string.swipe_to_refresh_description, R.string.swipe_to_refresh_dismiss) {
            prefs.edit {
                putBoolean(Constants.PREFERENCE_SWIPE_REFRESH_EXPLAINED, true)
            }
        }
    }

    private fun showDemoModeHintSnackbar() {
        showSnackbar(R.string.info_demo_mode_short, R.string.turn_off) {
            prefs.edit {
                putBoolean(Constants.PREFERENCE_DEMO_MODE, false)
            }
        }
    }

    private fun showSnackbar(
        @StringRes messageResId: Int,
        @StringRes actionResId: Int = 0,
        tag: String? = null,
        onClickListener: (() -> Unit)? = null
    ) {
        hideSnackbar()
        val snackbar = Snackbar.make(findViewById(android.R.id.content), messageResId, Snackbar.LENGTH_LONG)
        if (actionResId != 0 && onClickListener != null) {
            snackbar.setAction(actionResId) { onClickListener() }
        }
        snackbar.view.tag = tag
        snackbar.show()
        lastSnackbar = snackbar
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
        if (prefs.isDebugModeEnabled()) {
            message = SpannableStringBuilder(message).apply {
                inSpans(RelativeSizeSpan(0.8f)) {
                    append("\n\nURL: ").append(request.url().toString())

                    val authHeader = request.header("Authorization")
                    if (authHeader?.startsWith("Basic") == true) {
                        val base64Credentials = authHeader.substring("Basic".length).trim()
                        val credentials = String(Base64.decode(base64Credentials, Base64.DEFAULT),
                            Charset.forName("UTF-8"))
                        append("\nUsername: ")
                        append(credentials.substring(0, credentials.indexOf(":")))
                    }

                    append("\nException stack:\n")
                }

                inSpans(RelativeSizeSpan(0.6f)) {
                    var origError: Throwable?
                    var cause: Throwable? = error
                    do {
                        append(cause?.toString()).append('\n')
                        origError = cause
                        cause = origError?.cause
                    } while (cause != null && origError !== cause)
                }
            }
        }

        controller.indicateServerCommunicationFailure(message)
        propsUpdateHandle = null
    }

    private fun manageHabPanelShortcut(visible: Boolean) {
        manageShortcut(visible, "habpanel", ACTION_HABPANEL_SELECTED,
            R.string.mainmenu_openhab_habpanel, R.mipmap.ic_shortcut_habpanel,
            R.string.app_shortcut_disabled_habpanel)
    }

    private fun manageNotificationShortcut(visible: Boolean) {
        manageShortcut(visible, "notification", ACTION_NOTIFICATION_SELECTED,
            R.string.app_notifications, R.mipmap.ic_shortcut_notifications,
            R.string.app_shortcut_disabled_notifications)
    }

    private fun manageVoiceRecognitionShortcut(visible: Boolean) {
        manageShortcut(visible, "voice_recognition", ACTION_VOICE_RECOGNITION_SELECTED,
            R.string.mainmenu_openhab_voice_recognition,
            R.mipmap.ic_shortcut_voice_recognition,
            R.string.app_shortcut_disabled_voice_recognition)
    }

    private fun manageShortcut(
        visible: Boolean,
        id: String,
        action: String,
        @StringRes shortLabel: Int,
        @DrawableRes icon: Int,
        @StringRes disableMessage: Int
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        if (visible) {
            val intent = Intent(this, MainActivity::class.java)
                .setAction(action)
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

    private fun setVoiceWidgetComponentEnabledSetting(component: Class<*>, isSpeechRecognizerAvailable: Boolean) {
        val voiceWidget = ComponentName(this, component)
        val newState = if (isSpeechRecognizerAvailable)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        packageManager.setComponentEnabledSetting(voiceWidget, newState, PackageManager.DONT_KILL_APP)
    }

    private sealed class PendingAction {
        class ChooseSitemap : PendingAction()
        class OpenSitemapUrl constructor(val url: String) : PendingAction()
        class OpenHabPanel : PendingAction()
        class LaunchVoiceRecognition : PendingAction()
        class OpenNotification constructor(val notificationId: String) : PendingAction()
    }

    companion object {
        const val ACTION_NOTIFICATION_SELECTED = "org.openhab.habdroid.action.NOTIFICATION_SELECTED"
        const val ACTION_HABPANEL_SELECTED = "org.openhab.habdroid.action.HABPANEL_SELECTED"
        const val ACTION_VOICE_RECOGNITION_SELECTED = "org.openhab.habdroid.action.VOICE_SELECTED"
        const val ACTION_SITEMAP_SELECTED = "org.openhab.habdroid.action.SITEMAP_SELECTED"
        const val EXTRA_SITEMAP_URL = "sitemapUrl"
        const val EXTRA_PERSISTED_NOTIFICATION_ID = "persistedNotificationId"
        const val TAG_SNACKBAR_PRESS_AGAIN_EXIT = "pressAgainToExit"

        private const val STATE_KEY_SERVER_PROPERTIES = "serverProperties"
        private const val STATE_KEY_SITEMAP_SELECTION_SHOWN = "isSitemapSelectionDialogShown"
        private const val STATE_KEY_CONTROLLER_NAME = "controller"
        private const val STATE_KEY_CONNECTION_HASH = "connectionHash"

        private val TAG = MainActivity::class.java.simpleName

        // Activities request codes
        private const val INTRO_REQUEST_CODE = 1001
        private const val SETTINGS_REQUEST_CODE = 1002
        private const val WRITE_NFC_TAG_REQUEST_CODE = 1003
        private const val INFO_REQUEST_CODE = 1004
        // Drawer item codes
        private const val GROUP_ID_SITEMAPS = 1
    }
}
