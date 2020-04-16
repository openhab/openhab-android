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

package org.openhab.habdroid.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isDataSaverActive
import org.openhab.habdroid.util.orDefaultIfEmpty
import java.util.Random

class ChartActivity : AbstractBaseActivity(), SwipeRefreshLayout.OnRefreshListener {
    private lateinit var swipeLayout: SwipeRefreshLayout
    private lateinit var chart: WidgetImageView
    private lateinit var period: String
    private lateinit var widget: Widget
    private lateinit var chartTheme: CharSequence
    private var density: Int = 0
    private val random = Random()
    private var showLegend: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chart)

        widget = intent.getParcelableExtra(WIDGET)!!
        period = widget.period
        // If Widget#legend is null, show legend only for groups
        showLegend = widget.legend ?: widget.item?.type === Item.Type.Group

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = widget.label.orDefaultIfEmpty(getString(R.string.chart_activity_title))

        chart = findViewById(R.id.chart)
        swipeLayout = findViewById(R.id.activity_content)
        swipeLayout.setOnRefreshListener(this)
        swipeLayout.applyColors(R.attr.colorPrimary, R.attr.colorAccent)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        density = metrics.densityDpi

        updateChartTheme()
    }

    override fun onResume() {
        super.onResume()
        onRefresh()
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
        val connection = ConnectionFactory.usableConnectionOrNull
        if (connection == null) {
            finish()
            return
        }

        if (chart.height == 0 || chart.width == 0) {
            Log.d(TAG, "Height or width is 0")
            return
        }

        val chartUrl = widget.toChartUrl(
            getPrefs(),
            random,
            chart.width,
            chart.height,
            chartTheme,
            density,
            period,
            showLegend
        ) ?: return

        Log.d(TAG, "Load chart with url $chartUrl")
        chart.setImageUrl(connection, chartUrl, chart.width, forceLoad = true)
        if (widget.refresh > 0 && !isDataSaverActive()) {
            chart.startRefreshing(widget.refresh)
        }
        swipeLayout.isRefreshing = false
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

    override fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean {
        return mode == ScreenLockMode.Enabled
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateChartTheme()
        onRefresh()
    }

    private fun updateChartTheme() {
        val tv = TypedValue()
        theme.resolveAttribute(R.attr.chartTheme, tv, true)
        chartTheme = tv.string
    }

    companion object {
        private val TAG = ChartActivity::class.java.simpleName

        private const val SHOW_LEGEND = "show_legend"
        private const val PERIOD = "period"
        const val WIDGET = "widget"
    }
}
