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
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class SelectionScreen(
    carContext: CarContext,
    private val title: String,
    private val options: List<SelectionListItem>,
    private val selectedPosition: Int,
    private val onItemSelected: (item: SelectionListItem) -> Unit
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val itemsBuilder = ItemList.Builder()
            .setOnSelectedListener { index -> onItemSelected(options[index]) }

        if (selectedPosition < 0) {
            val noValueRow = Row.Builder()
                .setTitle("-----")
                .setEnabled(false)
                .build()
            itemsBuilder.addItem(noValueRow)
        } else {
            itemsBuilder.setSelectedIndex(selectedPosition)
        }

        options.forEach {
            val row = Row.Builder().setTitle(it.label).build()
            itemsBuilder.addItem(row)
        }

        val header = Header.Builder()
            .setTitle(title)
            .setStartHeaderAction(Action.BACK)
            .build()

        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(itemsBuilder.build())
            .build()
    }
}

data class SelectionListItem(val label: String, val command: String)
