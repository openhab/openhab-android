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

package org.openhab.habdroid.wear.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.sitemap.SitemapPageRepository
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.wear.connection.WearConnectionManager
import org.openhab.habdroid.wear.connection.WearConnectionManager.ConnectionError
import org.openhab.habdroid.wear.tile.SitemapTileService
import org.openhab.habdroid.wear.util.buildSourceId
import org.openhab.habdroid.wear.util.findWearSitemap
import org.openhab.habdroid.wear.wearApplication

/**
 * Loads the sitemap selected for the watch and keeps the visible page up to date.
 */
class SitemapViewModel(application: Application) :
    AndroidViewModel(application),
    SitemapPageRepository.Callback {
    sealed interface State {
        data object Loading : State

        data object NotConfigured : State

        data class Error(val error: ConnectionError, val url: String?) : State

        data object NoSitemaps : State

        data class Ready(val sitemap: Sitemap, val connection: Connection, val serverFlags: Int, val isDemo: Boolean) :
            State
    }

    data class Page(val title: String?, val widgets: List<Widget>?)

    private val connectionManager = application.wearApplication.connectionManager
    private val repository = SitemapPageRepository(viewModelScope)
    private var connection: Connection? = null
    private var visiblePage: String? = null

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _pages = MutableStateFlow<Map<String, Page>>(emptyMap())
    val pages: StateFlow<Map<String, Page>> = _pages.asStateFlow()

    override var serverProperties: ServerProperties? = null
        private set

    override val isDetailedLoggingEnabled = false

    init {
        repository.setCallback(this)
        viewModelScope.launch {
            connectionManager.state.collect { state -> onConnectionStateChanged(state) }
        }
    }

    fun start() = repository.start()

    fun stop() = repository.stop()

    fun retry() {
        if (state.value is State.Error) {
            connectionManager.reconnect()
        } else {
            visiblePage?.let { url -> repository.triggerUpdate(url, true) }
        }
    }

    /**
     * Only the visible page is kept up to date. Other pages are reloaded when they become visible again.
     */
    fun onPageVisible(url: String) {
        visiblePage = url
        repository.updateActiveConnections(listOf(url), connection)
        repository.triggerUpdate(url, false)
    }

    fun sendCommand(item: Item?, command: String) {
        Log.d(TAG, "Send command $command to ${item?.name}")
        val sitemap = (state.value as? State.Ready)?.sitemap
        connection?.httpClient?.sendItemCommand(item, command, buildSourceId(sitemap, visiblePage))
        // The tile might show the item
        SitemapTileService.requestUpdate(getApplication())
    }

    private fun onConnectionStateChanged(connectionState: WearConnectionManager.State) {
        Log.d(TAG, "onConnectionStateChanged($connectionState)")
        val conn = (connectionState as? WearConnectionManager.State.Connected)?.connection
        if (conn != connection) {
            // Forget everything loaded from the previous connection
            connection = conn
            _pages.value = emptyMap()
            repository.updateActiveConnections(emptyList(), conn)
        }
        serverProperties = (connectionState as? WearConnectionManager.State.Connected)?.properties

        _state.value = when (connectionState) {
            is WearConnectionManager.State.NotConfigured -> State.NotConfigured

            is WearConnectionManager.State.Connecting -> State.Loading

            is WearConnectionManager.State.Failed -> State.Error(connectionState.error, connectionState.url)

            is WearConnectionManager.State.Connected -> {
                val sitemap = connectionState.properties.findWearSitemap(connectionState.config.sitemapName)
                if (sitemap == null) {
                    State.NoSitemaps
                } else {
                    State.Ready(
                        sitemap,
                        connectionState.connection,
                        connectionState.properties.flags,
                        connectionState.isDemo
                    )
                }
            }
        }
    }

    override fun onPageUpdated(pageUrl: String, pageTitle: String?, widgets: List<Widget>) {
        _pages.update { pages -> pages + (pageUrl to Page(pageTitle, widgets)) }
    }

    override fun onWidgetUpdated(pageUrl: String, widget: Widget) {
        _pages.update { pages ->
            val page = pages[pageUrl] ?: return@update pages
            val widgets = page.widgets?.map { w -> if (w.id == widget.id) widget else w }
            pages + (pageUrl to page.copy(widgets = widgets))
        }
    }

    override fun onPageTitleUpdated(pageUrl: String, title: String) {
        _pages.update { pages ->
            val page = pages[pageUrl] ?: return@update pages
            pages + (pageUrl to page.copy(title = title))
        }
    }

    override fun onLoadFailure(error: HttpClient.HttpException) {
        Log.w(TAG, "Loading ${error.originalUrl} failed", error)
        _state.value = State.Error(ConnectionError.ConnectionFailed, error.originalUrl)
    }

    override fun onSseFailure() {
        // The repository falls back to long polling
    }

    companion object {
        private val TAG = SitemapViewModel::class.java.simpleName
    }
}
