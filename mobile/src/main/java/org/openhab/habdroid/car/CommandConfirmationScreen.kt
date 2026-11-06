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

package org.openhab.habdroid.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/**
 * Asks the user to confirm a command (`confirmCmd` in the sitemap). Sets `true` as result if the user confirmed it.
 */
class CommandConfirmationScreen(carContext: CarContext, private val title: String, private val message: String) :
    Screen(carContext) {
    override fun onGetTemplate(): Template {
        val header = Header.Builder()
            .setTitle(title)
            .setStartHeaderAction(Action.BACK)
            .build()

        val cancelAction = Action.Builder()
            .setTitle(carContext.getString(android.R.string.cancel))
            .setOnClickListener { screenManager.pop() }
            .build()

        val confirmAction = Action.Builder()
            .setTitle(carContext.getString(android.R.string.ok))
            .setOnClickListener {
                setResult(true)
                screenManager.pop()
            }
            .build()

        return MessageTemplate.Builder(message)
            .setHeader(header)
            .addAction(cancelAction)
            .addAction(confirmAction)
            .build()
    }
}
