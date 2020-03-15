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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.R
import org.openhab.habdroid.util.getLocalUrl
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getRemoteUrl
import java.io.BufferedReader
import java.io.InputStreamReader

class LogActivity : AbstractBaseActivity(), SwipeRefreshLayout.OnRefreshListener {
    private lateinit var logTextView: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var scrollView: ScrollView
    private lateinit var swipeLayout: SwipeRefreshLayout
    private var showErrorsOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_log)

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fab = findViewById(R.id.shareFab)
        logTextView = findViewById(R.id.log)
        scrollView = findViewById(R.id.scrollview)
        swipeLayout = findViewById(R.id.activity_content)
        swipeLayout.setOnRefreshListener(this)
        swipeLayout.applyColors(R.attr.colorPrimary, R.attr.colorAccent)

        fab.setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, logTextView.text)
            }
            startActivity(sendIntent)
        }

        setUiState(true)
    }

    override fun onResume() {
        super.onResume()
        onRefresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_ERRORS_ONLY, showErrorsOnly)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        showErrorsOnly = savedInstanceState.getBoolean(KEY_ERRORS_ONLY)
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "onCreateOptionsMenu()")
        menuInflater.inflate(R.menu.log_menu, menu)
        updateErrorsOnlyButtonState(menu.findItem(R.id.show_errors))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")
        return when (item.itemId) {
            R.id.delete_log -> {
                setUiState(false)
                fetchLog(true)
                true
            }
            R.id.show_errors -> {
                showErrorsOnly = !showErrorsOnly
                onRefresh()
                updateErrorsOnlyButtonState(item)
                true
            }
            R.id.refresh -> {
                onRefresh()
                true
            }
            android.R.id.home -> {
                finish()
                super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateErrorsOnlyButtonState(item: MenuItem) {
        if (showErrorsOnly) {
            item.setIcon(R.drawable.ic_error_white_24dp)
            item.setTitle(R.string.log_activity_action_show_all)
        } else {
            item.setIcon(R.drawable.ic_error_outline_white_24dp)
            item.setTitle(R.string.log_activity_action_show_errors)
        }
    }

    override fun onRefresh() {
        setUiState(true)
        fetchLog(false)
    }

    private fun setUiState(isLoading: Boolean) {
        swipeLayout.isRefreshing = isLoading
        logTextView.isVisible = !isLoading
        if (isLoading) fab.hide() else fab.show()
    }

    private fun fetchLog(clear: Boolean) = launch {
        logTextView.text = collectLog(clear)
        setUiState(false)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private suspend fun collectLog(clear: Boolean): String = withContext(Dispatchers.Default) {
        val logBuilder = StringBuilder()
        val separator = System.getProperty("line.separator")
        val process = try {
            var args = if (clear) "-c" else "-v threadtime -d"
            if (showErrorsOnly) {
                args += " *:E"
            }
            Runtime.getRuntime().exec("logcat -b all $args")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading process", e)
            return@withContext Log.getStackTraceString(e)
        }

        logBuilder.append("-----------------------\n")
        logBuilder.append("Device information\n")
        logBuilder.append(getDeviceInfo())
        logBuilder.append("-----------------------\n\n")

        if (clear) {
            logBuilder.append(getString(R.string.empty_log))
            return@withContext logBuilder.toString()
        }

        try {
            InputStreamReader(process.inputStream).use { reader ->
                BufferedReader(reader).use { bufferedReader ->
                    for (line in bufferedReader.readLines()) {
                        logBuilder.append(line)
                        logBuilder.append(separator)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading log", e)
            return@withContext Log.getStackTraceString(e)
        }

        var log = logBuilder.toString()
        log = redactHost(log, getPrefs().getLocalUrl(), "<openhab-local-address>")
        log = redactHost(log, getPrefs().getRemoteUrl(), "<openhab-remote-address>")
        log
    }

    private fun getDeviceInfo(): String {
        return "Model: ${Build.MODEL}\n" +
            "Manufacturer: ${Build.MANUFACTURER}\n" +
            "Brand: ${Build.BRAND}\n" +
            "Device: ${Build.DEVICE}\n" +
            "Product: ${Build.PRODUCT}\n" +
            "OS: ${Build.VERSION.RELEASE}\n"
    }

    private fun redactHost(text: String, url: String?, replacement: String): String {
        val host = url?.toHttpUrlOrNull()?.host
        return if (!host.isNullOrEmpty()) text.replace(host, replacement) else text
    }

    companion object {
        private const val KEY_ERRORS_ONLY = "errorsOnly"

        private val TAG = LogActivity::class.java.simpleName
    }
}
