/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.bridge

/**
 * The page on screen and the pages behind it, as sent in `nav.changed`.
 *
 * @param props What each page in [history] was opened with, as JSON objects. The app never looks inside, it
 * only hands them back in `initialProps`.
 */
data class NavState(val path: String, val history: List<String>, val props: List<String>?, val modal: Boolean)
