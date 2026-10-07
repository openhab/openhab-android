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
package org.openhab.habdroid.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import de.duenndns.ssl.MTMDecision
import de.duenndns.ssl.MemorizingTrustManager
import org.openhab.habdroid.R

/**
 * Asks whether to trust an unknown certificate. Replaces the dialog of [de.duenndns.ssl.MemorizingActivity] on TV,
 * which shows the full certificate chain in small text and can't be scrolled with a D-pad.
 */
class TvCertificateActivity : ComponentActivity() {
    private var decisionSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val decisionId = intent.getIntExtra(MemorizingTrustManager.DECISION_INTENT_ID, MTMDecision.DECISION_INVALID)
        val titleId = intent.getIntExtra(MemorizingTrustManager.DECISION_TITLE_ID, R.string.mtm_accept_cert)
        val summary = intent.getStringExtra(MemorizingTrustManager.DECISION_INTENT_SUMMARY).orEmpty()
        val fingerprint = intent.getStringExtra(MemorizingTrustManager.DECISION_INTENT_FINGERPRINT).orEmpty()

        setContent {
            TvTheme {
                CertificateDecision(
                    title = stringResource(titleId),
                    summary = summary,
                    fingerprint = fingerprint,
                    onDecision = { decision -> sendDecision(decisionId, decision) }
                )
            }
        }
    }

    override fun onDestroy() {
        // Don't leave the connection waiting for a decision forever
        if (isFinishing && !decisionSent) {
            val decisionId = intent.getIntExtra(MemorizingTrustManager.DECISION_INTENT_ID, MTMDecision.DECISION_INVALID)
            MemorizingTrustManager.interactResult(decisionId, MTMDecision.DECISION_ABORT)
        }
        super.onDestroy()
    }

    private fun sendDecision(decisionId: Int, decision: Int) {
        if (!decisionSent) {
            decisionSent = true
            MemorizingTrustManager.interactResult(decisionId, decision)
        }
        finish()
    }
}

@Composable
private fun CertificateDecision(title: String, summary: String, fingerprint: String, onDecision: (Int) -> Unit) {
    val abortFocus = remember { FocusRequester() }
    BackHandler { onDecision(MTMDecision.DECISION_ABORT) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        // Surface provides the content color for the texts
        Surface(
            modifier = Modifier.widthIn(max = 720.dp),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(modifier = Modifier.padding(32.dp)) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = summary, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "SHA-256", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = fingerprint.toFingerprintLines(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.mtm_connect_anyway), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Focus the safe option, so accidentally pressing OK doesn't trust the certificate
                    Button(
                        onClick = { onDecision(MTMDecision.DECISION_ABORT) },
                        modifier = Modifier.focusRequester(abortFocus)
                    ) {
                        Text(stringResource(R.string.mtm_decision_abort))
                    }
                    OutlinedButton(onClick = { onDecision(MTMDecision.DECISION_ALWAYS) }) {
                        Text(stringResource(R.string.mtm_decision_always))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        abortFocus.requestFocusWhenAttached()
    }
}

/**
 * Split the 32 bytes of a SHA-256 fingerprint into two lines, so it fits the dialog
 */
private fun String.toFingerprintLines() = split(':')
    .chunked(16)
    .joinToString("\n") { bytes -> bytes.joinToString(":") }
