package org.openhab.habdroid.background

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle

import org.openhab.habdroid.model.toTagData
import org.openhab.habdroid.ui.MainActivity

class NfcReceiveActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent == null || intent.data == null) {
            finish()
            return
        }

        if (intent.action == Intent.ACTION_VIEW || intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            val tag = intent.data?.toTagData()
            val sitemap = tag?.sitemap
            BackgroundTasksManager.enqueueNfcUpdateIfNeeded(this, tag)
            if (!sitemap.isNullOrEmpty()) {
                val startMainIntent = Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SITEMAP_SELECTED
                    putExtra(MainActivity.EXTRA_SITEMAP_URL, sitemap)
                }
                startActivity(startMainIntent)
            }
        }

        if (Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }
}
