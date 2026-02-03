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
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template

class LoadingScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = ListTemplate.Builder()
        .setLoading(true)
        .build()
}
