package org.openhab.habdroid.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import org.openhab.habdroid.R
import org.openhab.habdroid.util.getLocalUrl
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getRemoteUrl
import java.io.BufferedReader
import java.io.InputStreamReader

class LogActivity : AbstractBaseActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var logTextView: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var scrollView: ScrollView
    private lateinit var emptyView: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_log)

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fab = findViewById(R.id.shareFab)
        logTextView = findViewById(R.id.log)
        progressBar = findViewById(R.id.progressBar)
        scrollView = findViewById(R.id.scrollview)
        emptyView = findViewById(android.R.id.empty)

        fab.setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, logTextView.text)
            }
            startActivity(sendIntent)
        }

        setUiState(isLoading = true, isEmpty = false)
    }

    override fun onResume() {
        super.onResume()
        setUiState(isLoading = true, isEmpty = false)
        fetchLog(false)
    }

    private fun setUiState(isLoading: Boolean, isEmpty: Boolean) {
        progressBar.isVisible = isLoading
        logTextView.isVisible = !isLoading && !isEmpty
        emptyView.isVisible = isEmpty
        if (isLoading || isEmpty) {
            fab.hide()
        } else {
            fab.show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "onCreateOptionsMenu()")
        menuInflater.inflate(R.menu.log_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")
        return when (item.itemId) {
            R.id.delete_log -> {
                setUiState(isLoading = true, isEmpty = false)
                fetchLog(true)
                true
            }
            android.R.id.home -> {
                finish()
                super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchLog(clear: Boolean) = launch {
        val log = collectLog(clear)
        logTextView.text = log
        setUiState(false, log.isEmpty())
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private suspend fun collectLog(clear: Boolean): String = withContext(Dispatchers.Default) {
        val logBuilder = StringBuilder()
        val separator = System.getProperty("line.separator")
        val process = try {
            val args = if (clear) "-c" else "-v threadtime -d"
            Runtime.getRuntime().exec("logcat -b all $args")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading process", e)
            return@withContext Log.getStackTraceString(e)
        }

        if (clear) {
            return@withContext ""
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

    private fun redactHost(text: String, url: String?, replacement: String): String {
        val host = url?.toUri()?.host
        return if (!host.isNullOrEmpty()) text.replace(host, replacement) else text
    }

    companion object {
        private val TAG = LogActivity::class.java.simpleName
    }
}
