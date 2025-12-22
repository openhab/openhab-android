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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.ActivityWritetagBinding
import org.openhab.habdroid.databinding.FragmentWritenfcBinding
import org.openhab.habdroid.model.NfcTag
import org.openhab.habdroid.util.PendingIntent_Mutable
import org.openhab.habdroid.util.appendQueryParameter
import org.openhab.habdroid.util.parcelable
import org.openhab.habdroid.util.registerExportedReceiver
import org.openhab.habdroid.util.showToast

class WriteTagActivity :
    AbstractBaseActivity(),
    CoroutineScope {
    private lateinit var binding: ActivityWritetagBinding
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

        val manager = getSystemService(Context.NFC_SERVICE) as NfcManager
        nfcAdapter = manager.defaultAdapter

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(binding.activityContent.id, fragment)
            }
        }

        setResult(RESULT_OK)

        longUri = intent.parcelable(EXTRA_LONG_URI)
        shortUri = intent.parcelable(EXTRA_SHORT_URI)
        Log.d(TAG, "Got URL $longUri (short URI $shortUri)")
    }

    override fun inflateBinding(): CommonBinding {
        binding = ActivityWritetagBinding.inflate(layoutInflater)
        return CommonBinding(binding.root, binding.appBar, binding.coordinator, binding.activityContent)
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
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent_Mutable)
            adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        }

        replaceFragment()

        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        registerExportedReceiver(nfcStateChangeReceiver, filter)
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
            val f = supportFragmentManager.findFragmentById(R.id.activity_content)
            if (f is NfcWriteTagFragment) {
                f.updateStatusText(R.string.info_write_tag_progress)

                val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG)
                if (tag != null && writeTag(tag)) {
                    showToast(R.string.info_write_tag_finished)
                    finish()
                } else {
                    f.updateStatusText(R.string.info_write_failed)
                }
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
        protected lateinit var binding: FragmentWritenfcBinding

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = FragmentWritenfcBinding.inflate(inflater, container, false)
            return binding.root
        }
    }

    class NfcUnsupportedFragment : AbstractNfcFragment() {
        override val watermarkIcon: Int
            @DrawableRes
            get() = R.drawable.ic_nfc_off_black_180dp

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            binding.writeTagMessage.setText(R.string.info_write_tag_unsupported)
            return view
        }
    }

    class NfcDisabledFragment : AbstractNfcFragment() {
        override val watermarkIcon: Int
            @DrawableRes
            get() = R.drawable.ic_nfc_off_black_180dp

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            binding.writeTagMessage.setText(R.string.info_write_tag_disabled)

            binding.nfcActivate.apply {
                isVisible = true
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_NFC
                } else {
                    Settings.ACTION_NFC_SETTINGS
                }
                setOnClickListener {
                    startActivity(Intent(action))
                }
            }
            return view
        }
    }

    class NfcWriteTagFragment : AbstractNfcFragment() {
        override val watermarkIcon: Int
            @DrawableRes
            get() = R.drawable.ic_nfc_search_black_180dp

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            binding.nfcWaitProgress.isVisible = true
            return view
        }

        fun updateStatusText(@StringRes statusTextResId: Int) {
            binding.writeTagMessage.setText(statusTextResId)
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
            label: String?,
            deviceId: Boolean
        ): Intent {
            require(itemName.isNotEmpty()) { "Item name is empty" }
            val labelOrItemName = if (label.isNullOrEmpty()) itemName else label
            val stateOrUnsupported = if (deviceId) "UNSUPPORTED" else state

            val uriBuilder = Uri.Builder()
                .scheme(NfcTag.SCHEME)
                .authority("")
                .appendQueryParameter(NfcTag.QUERY_PARAMETER_ITEM_NAME, itemName)
                .appendQueryParameter(NfcTag.QUERY_PARAMETER_STATE, stateOrUnsupported)
            if (deviceId) {
                uriBuilder.appendQueryParameter(NfcTag.QUERY_PARAMETER_DEVICE_ID, deviceId)
            }

            val shortUri = uriBuilder.build()

            uriBuilder.appendQueryParameter(NfcTag.QUERY_PARAMETER_ITEM_LABEL, labelOrItemName)
            if (!deviceId) {
                uriBuilder.appendQueryParameter(NfcTag.QUERY_PARAMETER_MAPPED_STATE, mappedState)
            }
            val longUri = uriBuilder.build()

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
