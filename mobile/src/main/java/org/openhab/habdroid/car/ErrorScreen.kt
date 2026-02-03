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
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.NetworkNotAvailableException
import org.openhab.habdroid.core.connection.NoUrlInformationException
import org.openhab.habdroid.core.connection.WrongWifiException

class ErrorScreen(
    carContext: CarContext,
    private val message: CharSequence?,
    private val reason: Throwable?,
    private val retryHandler: () -> Unit
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(R.string.app_name))
            .build()

        val actualMessage = if (message != null) {
            message
        } else {
            val errorMessageResId = when (reason) {
                is NetworkNotAvailableException -> R.string.car_error_no_network
                is WrongWifiException -> R.string.car_error_server_unreachable
                is NoUrlInformationException -> R.string.car_error_server_unreachable
                else -> R.string.car_error_sitemap_load_failure
            }
            carContext.getString((errorMessageResId))
        }

        val retryAction = Action.Builder()
            .setTitle(carContext.getString(R.string.car_error_retry_button))
            .setOnClickListener(retryHandler)
            .build()

        val messageBuilder = MessageTemplate.Builder(actualMessage)
            .setHeader(header)
            .addAction(retryAction)

        reason?.let {
            messageBuilder
                .setDebugMessage(carContext.getString(R.string.car_error_debug_header))
                .setDebugMessage(it)
        }

        return messageBuilder.build()
    }
}
