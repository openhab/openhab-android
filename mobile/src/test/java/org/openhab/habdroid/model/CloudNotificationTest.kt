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

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudNotificationTest {
    @Test
    fun testStringToCloudNotificationAction() {
        val commandAction = "Turn on=command:foo:ON".toCloudNotificationAction()!!
        assertEquals("Turn on", commandAction.label)
        assertEquals(commandAction.action, CloudNotificationAction.Action.ItemCommandAction("foo", "ON"))

        val urlAction = "Open website=https://example.com".toCloudNotificationAction()!!
        assertEquals("Open website", urlAction.label)
        assertEquals(CloudNotificationAction.Action.UrlAction("https://example.com"), urlAction.action)

        val uiCommandAction = "Open page=ui:/foo".toCloudNotificationAction()!!
        assertEquals("Open page", uiCommandAction.label)
        assertEquals(CloudNotificationAction.Action.UiCommandAction("navigate:/foo"), uiCommandAction.action)

        val navigateUiCommandAction = "Open page2=ui:navigate:/foo".toCloudNotificationAction()!!
        assertEquals("Open page2", navigateUiCommandAction.label)
        assertEquals(CloudNotificationAction.Action.UiCommandAction("navigate:/foo"), navigateUiCommandAction.action)

        val invalidAction = "Invalid=foo".toCloudNotificationAction()!!
        assertEquals("Invalid", invalidAction.label)
        assertEquals(CloudNotificationAction.Action.NoAction, invalidAction.action)
    }
}
