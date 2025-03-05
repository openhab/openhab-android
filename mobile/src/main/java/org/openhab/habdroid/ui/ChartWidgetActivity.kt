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

package org.openhab.habdroid.ui

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getChartTheme
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelable

class ChartWidgetActivity :
    AbstractBaseActivity(),
    SwipeRefreshLayout.OnRefreshListener {
    private lateinit var swipeLayout: SwipeRefreshLayout
    private lateinit var chart: WidgetImageView
    private lateinit var period: String
    private lateinit var widget: Widget
    private lateinit var chartTheme: CharSequence
    private var connection: Connection? = null

    private var serverFlags: Int = 0
    private var density: Int = 0
    private var showLegend: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chart)

        widget = intent.parcelable(EXTRA_WIDGET)!!
        period = widget.period
        // If Widget#legend is null, show legend only for groups
        showLegend = widget.legend ?: (widget.item?.type == Item.Type.Group)

        serverFlags = intent.getIntExtra(EXTRA_SERVER_FLAGS, 0)

        supportActionBar?.title = widget.label.orDefaultIfEmpty(getString(R.string.chart_activity_title))

        chart = findViewById(R.id.chart)
        swipeLayout = findViewById(R.id.activity_content)
        swipeLayout.setOnRefreshListener(this)
        swipeLayout.applyColors()

        density = resources.configuration.densityDpi

        updateChartTheme()
    }

    override fun onResume() {
        super.onResume()

        connection = ConnectionFactory.activeUsableConnection?.connection
        if (connection == null) {
            finish()
            return
        }

        loadChartImage(false)
        if (determineDataUsagePolicy(connection).canDoRefreshes) {
            chart.startRefreshingIfNeeded()
        }
    }

    override fun onPause() {
        super.onPause()
        chart.cancelRefresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "onCreateOptionsMenu()")
        menuInflater.inflate(R.menu.chart_menu, menu)
        updateHasLegendButtonState(menu.findItem(R.id.show_legend))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")
        return when {
            item.itemId == R.id.refresh -> {
                onRefresh()
                true
            }
            item.itemId == R.id.show_legend -> {
                showLegend = !showLegend
                updateHasLegendButtonState(item)
                true
            }
            item.itemId == android.R.id.home -> {
                finish()
                super.onOptionsItemSelected(item)
            }
            // The dropdown menu is opened
            item.itemId == R.id.period -> true
            periodForMenuItem(item.itemId) != null -> {
                period = periodForMenuItem(item.itemId)!!
                onRefresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun periodForMenuItem(itemId: Int) = when (itemId) {
        R.id.period_h -> "h"
        R.id.period_4h -> "4h"
        R.id.period_8h -> "8h"
        R.id.period_12h -> "12h"
        R.id.period_d -> "D"
        R.id.period_2d -> "2D"
        R.id.period_3d -> "3D"
        R.id.period_w -> "W"
        R.id.period_2w -> "2W"
        R.id.period_m -> "M"
        R.id.period_2m -> "2M"
        R.id.period_4m -> "4M"
        R.id.period_y -> "Y"
        else -> null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(SHOW_LEGEND, showLegend)
        outState.putString(PERIOD, period)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        showLegend = savedInstanceState.getBoolean(SHOW_LEGEND)
        period = savedInstanceState.getString(PERIOD)!!
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onRefresh() {
        loadChartImage(true)
        swipeLayout.isRefreshing = false
    }

    private fun loadChartImage(force: Boolean) {
        val conn = connection ?: return finish()
        val chartUrl = widget.toChartUrl(
            getPrefs(),
            chart.width,
            chart.height,
            chartTheme,
            density,
            period,
            showLegend
        ) ?: return

        Log.d(TAG, "Load chart with url $chartUrl")
        chart.setImageUrl(conn, chartUrl, refreshDelayInMs = widget.refresh, forceLoad = force)
    }

    private fun updateHasLegendButtonState(item: MenuItem) {
        if (showLegend) {
            item.setIcon(R.drawable.ic_error_white_24dp)
            item.setTitle(R.string.chart_activity_hide_legend)
        } else {
            item.setIcon(R.drawable.ic_error_outline_white_24dp)
            item.setTitle(R.string.chart_activity_show_legend)
        }
        onRefresh()
    }

    override fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean = mode == ScreenLockMode.Enabled

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateChartTheme()
        onRefresh()
    }

    private fun updateChartTheme() {
        chartTheme = getChartTheme(serverFlags)
    }

    companion object {
        private val TAG = ChartWidgetActivity::class.java.simpleName

        private const val SHOW_LEGEND = "show_legend"
        private const val PERIOD = "period"
        const val EXTRA_WIDGET = "widget"
        const val EXTRA_SERVER_FLAGS = "server_flags"
    }
}
