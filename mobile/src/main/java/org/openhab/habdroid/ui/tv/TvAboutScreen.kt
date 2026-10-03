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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.util.Calendar
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R

/**
 * About screen for TV: version and license
 */
@Composable
fun TvAboutScreen() {
    val year = remember { Calendar.getInstance().get(Calendar.YEAR).toString() }
    val firstItemFocus = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 27.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_openhab_appicon_340dp),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(R.string.about_copyright, year),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = { Text(stringResource(R.string.about_version)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    modifier = Modifier.focusRequester(firstItemFocus)
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = { Text(stringResource(R.string.about_license_title)) },
                    supportingContent = { Text(stringResource(R.string.about_license)) }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        firstItemFocus.requestFocusWhenAttached()
    }
}
