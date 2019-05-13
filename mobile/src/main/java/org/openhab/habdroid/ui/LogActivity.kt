package org.openhab.habdroid.ui

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextUtils
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

import org.openhab.habdroid.R
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.Util

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

        fab.setOnClickListener { v ->
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, logTextView.text)
            sendIntent.type = "text/plain"
            startActivity(sendIntent)
        }

        setUiState(true, false)
    }

    override fun onResume() {
        super.onResume()
        setUiState(true, false)
        GetLogFromAdbTask().execute(false)
    }

    private fun setUiState(isLoading: Boolean, isEmpty: Boolean) {
        progressBar.isVisible = isLoading && !isEmpty
        logTextView.isVisible = isLoading && !isEmpty
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
        when (item.itemId) {
            R.id.delete_log -> {
                setUiState(true, false)
                GetLogFromAdbTask().execute(true)
                return true
            }
            android.R.id.home -> {
                finish()
                return super.onOptionsItemSelected(item)
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private inner class GetLogFromAdbTask : AsyncTask<Boolean, Void, String>() {
        override fun doInBackground(vararg clear: Boolean?): String {
            val logBuilder = StringBuilder()
            val separator = System.getProperty("line.separator")
            var process: Process?
            try {
                if (clear[0] ?: false) {
                    Log.d(TAG, "Clear log")
                    Runtime.getRuntime().exec("logcat -b all -c")
                    return ""
                }
                process = Runtime.getRuntime().exec("logcat -b all -v threadtime -d")
            } catch (e: Exception) {
                Log.e(TAG, "Error reading process", e)
                return Log.getStackTraceString(e)
            }

            if (process == null) {
                return "Process is null"
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
                return Log.getStackTraceString(e)
            }

            var log = logBuilder.toString()
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            log = redactHost(log,
                    sharedPreferences.getString(Constants.PREFERENCE_LOCAL_URL, ""),
                    "<openhab-local-address>")
            log = redactHost(log,
                    sharedPreferences.getString(Constants.PREFERENCE_REMOTE_URL, ""),
                    "<openhab-remote-address>")
            return log
        }

        override fun onPostExecute(log: String) {
            logTextView.text = log
            setUiState(false, TextUtils.isEmpty(log))
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun redactHost(text: String, url: String?, replacement: String): String {
        val host = url?.toUri()?.host
        if (host != null && !host.isEmpty()) {
            return text.replace(host, replacement)
        }
        return text
    }

    companion object {
        private val TAG = LogActivity::class.java.simpleName
    }
}
