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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.model.NfcTag
import org.openhab.habdroid.util.ToastType
import org.openhab.habdroid.util.showToast
import java.io.IOException

class WriteTagActivity : AbstractBaseActivity(), CoroutineScope {
    private var nfcAdapter: NfcAdapter? = null
    private var nfcStateChangeReceiver: NfcStateChangeReceiver = NfcStateChangeReceiver()
    private var longUri: Uri? = null
    private var shortUri: Uri? = null

    private val fragment get() = when {
        nfcAdapter == null -> NfcUnsupportedFragment()
        nfcAdapter?.isEnabled == false -> NfcDisabledFragment()
        else -> NfcWriteTagFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_writetag)

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val manager = getSystemService(Context.NFC_SERVICE) as NfcManager
        nfcAdapter = manager.defaultAdapter

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.activity_content, fragment)
            }
        }

        setResult(RESULT_OK)

        longUri = intent.getParcelableExtra(EXTRA_LONG_URI)
        shortUri = intent.getParcelableExtra(EXTRA_SHORT_URI)
        Log.d(TAG, "Got URL $longUri (short URI $shortUri)")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        Log.d(TAG, "onResume()")
        super.onResume()

        val adapter = nfcAdapter
        if (adapter != null) {
            val intent = Intent(this, javaClass)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
            adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        }

        replaceFragment()

        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        registerReceiver(nfcStateChangeReceiver, filter)
    }

    private fun replaceFragment() {
        supportFragmentManager.commit {
            replace(R.id.activity_content, fragment)
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause()")
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        unregisterReceiver(nfcStateChangeReceiver)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launch {
            val writeTagMessage = findViewById<TextView>(R.id.write_tag_message)
            writeTagMessage.setText(R.string.info_write_tag_progress)

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null && writeTag(tag)) {
                showToast(R.string.info_write_tag_finished, ToastType.SUCCESS)
                finish()
            } else {
                writeTagMessage.setText(R.string.info_write_failed)
            }
        }
    }

    private suspend fun writeTag(tag: Tag): Boolean = withContext(Dispatchers.Default) {
        Log.d(TAG, "NFC TAG = $tag")
        Log.d(TAG, "Writing URL $longUri to tag")

        var success = false
        val longMessage = longUri.toNdefMessage()
        val shortMessage = shortUri.toNdefMessage()
        val ndefFormatable = NdefFormatable.get(tag)

        if (ndefFormatable != null) {
            Log.d(TAG, "Tag is uninitialized, formatting")
            try {
                ndefFormatable.connect()
                try {
                    ndefFormatable.format(longMessage)
                } catch (e: IOException) {
                    if (shortMessage != null) {
                        Log.d(TAG, "Try with short uri")
                        ndefFormatable.format(shortMessage)
                    }
                }
                success = true
            } catch (e: IOException) {
                Log.e(TAG, "Writing to unformatted tag failed: $e")
            } catch (e: FormatException) {
                Log.e(TAG, "Formatting tag failed: $e")
            } finally {
                try {
                    ndefFormatable.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Closing ndefFormatable failed", e)
                }
            }
        } else {
            Log.d(TAG, "Tag is initialized, writing")
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                try {
                    Log.d(TAG, "Connecting")
                    ndef.connect()
                    Log.d(TAG, "Writing")
                    if (ndef.isWritable) {
                        try {
                            ndef.writeNdefMessage(longMessage)
                        } catch (e: IOException) {
                            if (shortMessage != null) {
                                Log.d(TAG, "Try with short uri")
                                ndef.writeNdefMessage(shortMessage)
                            }
                        }
                    }
                    success = true
                } catch (e: IOException) {
                    Log.e(TAG, "Writing to formatted tag failed", e)
                } catch (e: FormatException) {
                    Log.e(TAG, "Formatting formatted tag failed", e)
                } finally {
                    try {
                        ndef.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "Closing ndef failed", e)
                    }
                }
            } else {
                Log.e(TAG, "Ndef == null")
            }
        }
        success
    }

    abstract class AbstractNfcFragment : Fragment() {
        @get:DrawableRes
        protected abstract val watermarkIcon: Int

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.fragment_writenfc, container, false)
            val watermark = view.findViewById<ImageView>(R.id.nfc_watermark)

            val nfcIcon = ContextCompat.getDrawable(view.context, watermarkIcon)
            nfcIcon?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(view.context, R.color.empty_list_text_color),
                PorterDuff.Mode.SRC_IN)
            watermark.setImageDrawable(nfcIcon)

            return view
        }
    }

    class NfcUnsupportedFragment : AbstractNfcFragment() {
        override val watermarkIcon: Int
            @DrawableRes
            get() = R.drawable.ic_nfc_off_black_180dp

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            val message = view?.findViewById<TextView>(R.id.write_tag_message)
            message?.setText(R.string.info_write_tag_unsupported)
            return view
        }
    }

    class NfcDisabledFragment : AbstractNfcFragment() {
        override val watermarkIcon: Int
            @DrawableRes
            get() = R.drawable.ic_nfc_off_black_180dp

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            val message = view?.findViewById<TextView>(R.id.write_tag_message)
            message?.setText(R.string.info_write_tag_disabled)

            val nfcActivate = view?.findViewById<Button>(R.id.nfc_activate)
            nfcActivate?.isVisible = true

            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_NFC
            } else {
                Settings.ACTION_NFC_SETTINGS
            }
            nfcActivate?.setOnClickListener {
                startActivity(Intent(action))
            }

            return view
        }
    }

    class NfcWriteTagFragment : AbstractNfcFragment() {
        override val watermarkIcon: Int
            @DrawableRes
            get() = R.drawable.ic_nfc_search_black_180dp

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            view?.findViewById<View>(R.id.nfc_wait_progress)?.isVisible = true
            return view
        }
    }

    companion object {
        private val TAG = WriteTagActivity::class.java.simpleName
        private const val EXTRA_LONG_URI = "longUri"
        private const val EXTRA_SHORT_URI = "shortUri"

        fun createItemUpdateIntent(
            context: Context,
            itemName: String,
            state: String,
            mappedState: String,
            label: String?
        ): Intent {
            require(itemName.isNotEmpty()) { "Item name is empty" }
            val labelOrItemName = if (label.isNullOrEmpty()) itemName else label

            val uriBuilder = Uri.Builder()
                .scheme(NfcTag.SCHEME)
                .authority("")
                .appendQueryParameter(NfcTag.QUERY_PARAMETER_ITEM_NAME, itemName)
                .appendQueryParameter(NfcTag.QUERY_PARAMETER_STATE, state)

            val shortUri = uriBuilder.build()
            val longUri = uriBuilder
                .appendQueryParameter(NfcTag.QUERY_PARAMETER_MAPPED_STATE, mappedState)
                .appendQueryParameter(NfcTag.QUERY_PARAMETER_ITEM_LABEL, labelOrItemName)
                .build()

            return Intent(context, WriteTagActivity::class.java).apply {
                putExtra(EXTRA_SHORT_URI, shortUri)
                putExtra(EXTRA_LONG_URI, longUri)
            }
        }

        fun createSitemapNavigationIntent(context: Context, sitemapUrl: String): Intent {
            val sitemapUri = sitemapUrl.toUri()
            val path = sitemapUri.path.orEmpty()
            if (!path.startsWith("/rest/sitemaps")) {
                throw IllegalArgumentException("Expected a sitemap URL")
            }
            val longUri = Uri.Builder()
                .scheme(NfcTag.SCHEME)
                .authority("")
                .appendEncodedPath(path.substring(15))
                .build()
            return Intent(context, WriteTagActivity::class.java)
                .putExtra(EXTRA_LONG_URI, longUri)
        }
    }

    inner class NfcStateChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                val state = intent?.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                if (state == NfcAdapter.STATE_ON || state == NfcAdapter.STATE_OFF) {
                    replaceFragment()
                }
            }
        }
    }
}

private fun Uri?.toNdefMessage(): NdefMessage? {
    if (this == null) {
        return null
    }
    return NdefMessage(arrayOf(NdefRecord.createUri(this)))
}
