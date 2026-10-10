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

package org.openhab.habdroid.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun ParsedState.NumberState?.toItemCommand(item: Item?): String? {
    if (this == null || item == null) {
        return null
    }
    return if (item.isOfTypeOrGroupType(Item.Type.NumberWithDimension)) {
        // For number items, include unit (if present) in command
        toString(Locale.US, false)
    } else {
        // For all other items, send the plain value
        formatValue()
    }
}

fun LocalDateTime?.toItemCommand(item: Item?) =
    if (this != null && item != null && item.isOfTypeOrGroupType(Item.Type.DateTime)) {
        format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } else {
        null
    }
