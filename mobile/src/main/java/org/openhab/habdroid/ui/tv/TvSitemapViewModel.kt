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
package org.openhab.habdroid.ui.tv

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.sitemap.SitemapPageRepository
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.sortedWithDefaultName
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.buildBaseSourceId
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getDefaultSitemap
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isDebugModeEnabled
import org.openhab.habdroid.util.loadActiveServerConfig

/**
 * Loads the default sitemap of the active server and keeps track of the pages the user navigated to.
 */
class TvSitemapViewModel(application: Application) :
    AndroidViewModel(application),
    SitemapPageRepository.Callback {
    sealed interface State {
        data object Loading : State

        data class Error(val message: String) : State

        data class NoSitemaps(val serverUrl: String?) : State

        data class Page(
            val url: String,
            val title: String,
            val widgets: List<Widget>?,
            val connection: Connection,
            val serverFlags: Int,
            val canGoBack: Boolean
        ) : State
    }

    private data class PageData(val title: String?, val widgets: List<Widget>)

    private val context get() = getApplication<Application>()
    private val repository = SitemapPageRepository(viewModelScope)
    private var connection: Connection? = null
    private var sitemap: Sitemap? = null
    private val pageStack = mutableListOf<String>()
    private val pages = mutableMapOf<String, PageData>()

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    override var serverProperties: ServerProperties? = null
        private set

    override val isDetailedLoggingEnabled get() = context.getPrefs().isDebugModeEnabled()

    init {
        repository.setCallback(this)
        viewModelScope.launch {
            context.getConnectionFactory().activeFlow.collectLatest { info -> onConnectionChanged(info.conn) }
        }
    }

    fun start() {
        repository.start()
        // The default sitemap might have been changed in the settings
        val conn = connection ?: return
        val selectedSitemap = selectSitemap(conn) ?: return
        if (selectedSitemap.name != sitemap?.name) {
            sitemap = selectedSitemap
            pageStack.clear()
            pages.clear()
            pageStack.add(selectedSitemap.homepageLink)
            updateTrackedPage()
        }
    }

    fun stop() = repository.stop()

    fun retry() {
        val conn = connection
        when {
            // The connection factory publishes a new connection if it's available now
            conn == null -> context.getConnectionFactory().restartNetworkCheck()

            serverProperties == null -> viewModelScope.launch {
                onConnectionChanged(ConnectionFactory.ConnectionResult(conn, null))
            }

            else -> {
                pageStack.lastOrNull()?.let { url -> repository.triggerUpdate(url, true) }
                publishPage()
            }
        }
    }

    fun openPage(url: String) {
        pageStack.add(url)
        updateTrackedPage()
    }

    /**
     * @return false if the root page of the sitemap is shown, true otherwise
     */
    fun goBack(): Boolean {
        if (pageStack.size <= 1) {
            return false
        }
        pages.remove(pageStack.removeAt(pageStack.lastIndex))
        updateTrackedPage()
        return true
    }

    fun sendCommand(item: Item?, command: String) {
        Log.d(TAG, "Send command $command to ${item?.name}")
        connection?.httpClient?.sendItemCommand(item, command, buildSourceId())
    }

    private suspend fun onConnectionChanged(result: ConnectionFactory.ConnectionResult?) {
        Log.d(TAG, "onConnectionChanged($result)")
        val conn = result?.connection
        // Forget everything loaded from the previous connection
        connection = conn
        serverProperties = null
        sitemap = null
        pageStack.clear()
        pages.clear()
        repository.updateActiveConnections(emptyList(), conn)

        if (conn == null) {
            val url = context.loadActiveServerConfig()?.localPath?.url
            _state.value = State.Error(context.getString(R.string.tv_error_connection_failed, url.orEmpty()))
            return
        }

        _state.value = State.Loading
        when (val propsResult = ServerProperties.fetch(conn)) {
            is ServerProperties.Companion.PropsSuccess -> serverProperties = propsResult.props

            is ServerProperties.Companion.PropsFailure -> {
                _state.value = State.Error(
                    context.getHumanReadableErrorMessage(
                        propsResult.request.url.toString(),
                        propsResult.httpStatusCode,
                        propsResult.error,
                        false
                    ).toString()
                )
                return
            }
        }

        val selectedSitemap = selectSitemap(conn)
        sitemap = selectedSitemap
        if (selectedSitemap == null) {
            _state.value = State.NoSitemaps(context.loadActiveServerConfig()?.localPath?.url)
            return
        }
        pageStack.add(selectedSitemap.homepageLink)
        updateTrackedPage()
    }

    /**
     * Use the configured default sitemap, or the first one in alphabetical order if none is configured
     */
    private fun selectSitemap(conn: Connection): Sitemap? {
        val defaultSitemapName = context.getPrefs().getDefaultSitemap(conn)?.name.orEmpty()
        return serverProperties?.sitemaps?.sortedWithDefaultName(defaultSitemapName)?.firstOrNull()
    }

    private fun updateTrackedPage() {
        // Only the visible page is kept up to date. Pages further up in the stack are reloaded when navigating back.
        repository.updateActiveConnections(listOfNotNull(pageStack.lastOrNull()), connection)
        pageStack.lastOrNull()?.let { url -> repository.triggerUpdate(url, false) }
        publishPage()
    }

    private fun publishPage() {
        val url = pageStack.lastOrNull() ?: return
        val conn = connection ?: return
        val data = pages[url]
        _state.value = State.Page(
            url = url,
            title = data?.title ?: sitemap?.label.orEmpty(),
            widgets = data?.widgets,
            connection = conn,
            serverFlags = serverProperties?.flags ?: 0,
            canGoBack = pageStack.size > 1
        )
    }

    private fun buildSourceId(): String {
        val pageId = pageStack.lastOrNull()?.substringAfterLast('/')
        return "org.openhab.ui.basic$${sitemap?.name}:$pageId=>${context.buildBaseSourceId()}"
    }

    override fun onPageUpdated(pageUrl: String, pageTitle: String?, widgets: List<Widget>) {
        pages[pageUrl] = PageData(pageTitle, widgets)
        if (pageUrl == pageStack.lastOrNull()) {
            publishPage()
        }
    }

    override fun onWidgetUpdated(pageUrl: String, widget: Widget) {
        val data = pages[pageUrl] ?: return
        pages[pageUrl] = data.copy(widgets = data.widgets.map { w -> if (w.id == widget.id) widget else w })
        if (pageUrl == pageStack.lastOrNull()) {
            publishPage()
        }
    }

    override fun onPageTitleUpdated(pageUrl: String, title: String) {
        val data = pages[pageUrl] ?: return
        pages[pageUrl] = data.copy(title = title)
        if (pageUrl == pageStack.lastOrNull()) {
            publishPage()
        }
    }

    override fun onLoadFailure(error: HttpClient.HttpException) {
        _state.value = State.Error(
            context.getHumanReadableErrorMessage(error.originalUrl, error.statusCode, error, false).toString()
        )
    }

    override fun onSseFailure() {
        // The repository falls back to long polling
    }

    companion object {
        private val TAG = TvSitemapViewModel::class.java.simpleName
    }
}
