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
import org.openhab.habdroid.util.updateDefaultCarSitemap

class CarSession(
    sitemapsFlow: Flow<Result<List<Sitemap>>?>,
    private val onPageListChanged: () -> Unit,
    private val onSendWidgetCommand: (widget: Widget, command: String, sourceId: String) -> Unit
) : Session() {
    private var latestSitemapResult: Result<List<Sitemap>>? = null
    private val pageStack = mutableListOf<WidgetListScreen>()
    val pageUrls get() = pageStack.map { it.url }

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sitemapsFlow.collect {
                    Log.d(TAG, "Got new sitemap result $it")
                    latestSitemapResult = it
                    pageStack.clear()
                    onPageListChanged()

                    val screenManager = carContext.getCarService(ScreenManager::class.java)
                    screenManager.popToRoot()
                    // At this point only the root screen is left, which we want to replace with the screen
                    // we're going to create. As there's no direct way to do that, we push the new screen to the top
                    // and replace the old root afterwards.
                    val oldRoot = screenManager.top
                    screenManager.push(createScreenForCurrentSitemap(it))
                    screenManager.remove(oldRoot)
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
        screenManager.popToRoot()
        screenManager.push(createErrorScreen(null, reason))
    }

    override fun onCreateScreen(intent: Intent) = createScreenForCurrentSitemap(latestSitemapResult)

    private fun createScreenForCurrentSitemap(result: Result<List<Sitemap>>?): Screen = when {
        result == null -> LoadingScreen(carContext)

        result.isSuccess -> {
            val sitemaps = result.getOrThrow()
            val lastSitemapName = carContext.getPrefs().getDefaultCarSitemapName()
            val selectedSitemap = sitemaps.firstOrNull { it.name == lastSitemapName }
            when {
                selectedSitemap != null ->
                    createWidgetListScreen(
                        selectedSitemap.homepageLink,
                        selectedSitemap.name,
                        selectedSitemap.label,
                        false
                    )

                sitemaps.isEmpty() ->
                    createErrorScreen(carContext.getString(R.string.error_empty_sitemap_list), null)

                else ->
                    SitemapSelectionScreen(carContext, sitemaps) { sitemap ->
                        carContext.getPrefs().updateDefaultCarSitemap(sitemap)
                        val screenManager = carContext.getCarService(ScreenManager::class.java)
                        screenManager.popToRoot()
                        screenManager.push(
                            createWidgetListScreen(sitemap.homepageLink, sitemap.name, sitemap.label, false)
                        )
                    }
            }
        }

        else -> createErrorScreen(null, result.exceptionOrNull())
    }

    private fun createErrorScreen(message: CharSequence?, reason: Throwable?) =
        ErrorScreen(carContext, message, reason) {
            carContext.getConnectionFactory().restartNetworkCheck()
        }

    private fun createWidgetListScreen(url: String, id: String, title: String, canGoBack: Boolean): WidgetListScreen {
        val screen = WidgetListScreen(
            carContext,
            url,
            id,
            canGoBack,
            // Omit state portion of the label, as we can't update it anyway without it counting against the step limit
            title.substringBefore("[").trim(),
            onPageSelected = { page -> openWidgetListScreen(page) },
            onWidgetCommand = { widget, command -> onSendWidgetCommand(widget, command, buildSourceId(id)) }
        )
        pageStack += screen
        onPageListChanged()
        return screen
    }

    private fun buildSourceId(id: String): String {
        val sitemapName = pageStack.getOrNull(0)?.id ?: id
        val pageId = if (sitemapName == id) null else id
        return carContext.buildSitemapSourceId(sitemapName, pageId, "org.openhab.android.car")
    }

    private fun openWidgetListScreen(page: LinkedPage) {
        Log.d(TAG, "Open widget list for page $page")
        val screen = createWidgetListScreen(page.link, page.id, page.title, true)
        screen.screenManager.push(screen)
    }

    companion object {
        const val TAG = "CarSession"
    }
}
