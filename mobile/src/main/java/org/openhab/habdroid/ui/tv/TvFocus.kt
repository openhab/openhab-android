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

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus once the target is attached. Items of lazy lists are only composed after the first frame,
 * so requesting focus directly from a LaunchedEffect would fail.
 *
 * @return true if the focus could be moved
 */
suspend fun FocusRequester.requestFocusWhenAttached(maxFrames: Int = 10): Boolean {
    repeat(maxFrames) {
        withFrameNanos { }
        if (requestFocus(FocusDirection.Enter)) {
            return true
        }
    }
    return false
}
