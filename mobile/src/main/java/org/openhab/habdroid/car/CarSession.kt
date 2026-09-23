/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

package org.openhab.habdroid.car

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.util.buildSitemapSourceId
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getDefaultCarSitemapName
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.onDestroy

class CarSession(
    sitemapsFlow: Flow<Result<List<Sitemap>>?>,
    private val onPageListChanged: () -> Unit,
    private val onSendWidgetCommand: (widget: Widget, command: String, sourceId: String) -> Unit
) : Session() {
    private var latestSitemapResult: Result<SitemapLookupResult>? = null
    private val pageStack = mutableListOf<WidgetGridScreen>()
    val pageUrls get() = pageStack.map { it.url }

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sitemapsFlow.collect { sitemapsResult ->
                    val selectedSitemap = carContext.getPrefs().getDefaultCarSitemapName()
                    val sitemapResult = sitemapsResult
                        ?.map { SitemapLookupResult(it, selectedSitemap) }

                    if (sitemapResult != latestSitemapResult) {
                        Log.d(TAG, "Got new sitemap result $sitemapResult")
                        latestSitemapResult = sitemapResult
                        pageStack.clear()
                        onPageListChanged()

                        val screenManager = carContext.getCarService(ScreenManager::class.java)
                        screenManager.replaceRoot(createScreenForCurrentSitemap(sitemapResult))
                    }
                }
            }
        }
    }

    fun handlePageUpdate(pageUrl: String, widgets: List<Widget>) {
        pageStack.firstOrNull { it.url == pageUrl }
            ?.updateWidgets(widgets)
    }

    fun handleWidgetUpdate(pageUrl: String, widget: Widget) {
        pageStack.firstOrNull { it.url == pageUrl }
            ?.updateWidget(widget)
    }

    fun handleLoadFailure(reason: Throwable?) {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        screenManager.replaceRoot(createErrorScreen(null, reason))
    }

    override fun onCreateScreen(intent: Intent) = createScreenForCurrentSitemap(latestSitemapResult)

    private fun createScreenForCurrentSitemap(result: Result<SitemapLookupResult>?): Screen = when {
        result == null -> LoadingScreen(carContext)

        result.isSuccess -> {
            val (sitemaps, selectedSitemapName) = result.getOrThrow()
            val selectedSitemap = sitemaps.firstOrNull { it.name == selectedSitemapName }
            if (selectedSitemap != null) {
                createWidgetListScreen(
                    selectedSitemap.homepageLink,
                    selectedSitemap.name,
                    selectedSitemap.label,
                    0
                )
            } else {
                createErrorScreen(carContext.getString(R.string.car_error_sitemap_not_found), null)
            }
        }

        else -> createErrorScreen(null, result.exceptionOrNull())
    }

    private fun createErrorScreen(message: CharSequence?, reason: Throwable?) =
        ErrorScreen(carContext, message, reason) {
            carContext.getConnectionFactory().restartNetworkCheck()
        }

    private fun createWidgetListScreen(url: String, id: String, title: String, nestingDepth: Int): WidgetGridScreen {
        val screen = WidgetGridScreen(
            carContext,
            url,
            id,
            nestingDepth,
            // Omit state portion of the label, as we can't update it anyway without it counting against the step limit
            title.substringBefore("[").trim(),
            onPageSelected = { page -> openWidgetListScreen(page, nestingDepth + 1) },
            onWidgetCommand = { widget, command -> onSendWidgetCommand(widget, command, buildSourceId(id)) }
        )
        screen.lifecycle.onDestroy {
            if (pageStack.remove(screen)) {
                onPageListChanged()
            }
        }
        pageStack += screen
        onPageListChanged()
        return screen
    }

    private fun buildSourceId(id: String): String {
        val sitemapName = pageStack.getOrNull(0)?.id ?: id
        val pageId = if (sitemapName == id) null else id
        return carContext.buildSitemapSourceId(sitemapName, pageId, "org.openhab.android.car")
    }

    private fun openWidgetListScreen(page: LinkedPage, nestingDepth: Int) {
        Log.d(TAG, "Open widget list for page $page")
        val screen = createWidgetListScreen(page.link, page.id, page.title, nestingDepth)
        screen.screenManager.push(screen)
    }

    private fun ScreenManager.replaceRoot(screen: Screen) {
        popToRoot()
        // At this point only the root screen is left, which we want to replace with the screen
        // we're going to create. As there's no direct way to do that, we push the new screen to the top
        // and replace the old root afterwards.
        val oldRoot = top
        push(screen)
        remove(oldRoot)
    }

    private data class SitemapLookupResult(val sitemaps: List<Sitemap>, val selectedSitemapName: String?)

    companion object {
        const val TAG = "CarSession"
    }
}
