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

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.sitemap.SitemapPageRepository
import org.openhab.habdroid.ui.WidgetListFragment

/**
 * Fragment that manages connections for active instances of
 * [WidgetListFragment]
 *
 * It retains the connections over activity recreations, and takes care of stopping
 * and restarting connections if needed.
 */
class PageConnectionHolderFragment :
    Fragment(),
    CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job
    private val repository = SitemapPageRepository(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION") // TODO: Replace deprecated function
        retainInstance = true
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart(), started ${repository.isStarted}")
        repository.start()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop()")
        // If the activity is only changing configuration (e.g. orientation or locale)
        // we know it'll be immediately recreated, thus there's no point in shutting down
        // the connections in that case
        if (activity?.isChangingConfigurations != true) {
            repository.stop()
        }
    }

    override fun toString() =
        "${super.toString()} [${repository.connectionCount} connections, started=${repository.isStarted}]"

    /**
     * Assign parent callback
     *
     *
     * To be called by the parent as early as possible,
     * as it's expected to be non-null at all times
     *
     * @param callback Callback for parent
     */
    fun setCallback(callback: SitemapPageRepository.Callback) = repository.setCallback(callback)

    /**
     * Update list of page URLs to track
     *
     * @param urls New list of URLs to track
     * @param connection Connection to use, or null if none is available
     */
    fun updateActiveConnections(urls: List<String>, connection: Connection?) =
        repository.updateActiveConnections(urls, connection)

    /**
     * Ask for new data to be delivered for a given page
     *
     * @param pageUrl URL of page to trigger update for
     * @param forceReload true if existing data should be discarded and new data be loaded,
     * false if only existing data should be delivered, if it exists
     */
    fun triggerUpdate(pageUrl: String, forceReload: Boolean) = repository.triggerUpdate(pageUrl, forceReload)

    companion object {
        private val TAG = PageConnectionHolderFragment::class.java.simpleName
    }
}
