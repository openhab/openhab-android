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
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import org.openhab.habdroid.model.LabeledValue

class ActionListScreen(
    carContext: CarContext,
    private val title: String,
    private val actions: List<ActionListItem>,
    private val onItemSelected: (item: ActionListItem) -> Unit
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val itemsBuilder = ItemList.Builder()

        actions.forEach { action ->
            val rowBuilder = Row.Builder()
                .setTitle(action.label(carContext))
                .setOnClickListener { onItemSelected(action) }

            action.icon(carContext)
                ?.let { icon -> CarIcon.Builder(icon).setTint(CarColor.PRIMARY).build() }
                ?.let { icon -> rowBuilder.setImage(icon) }

            itemsBuilder.addItem(rowBuilder.build())
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

interface ActionListItem {
    fun label(carContext: CarContext): String
    fun icon(carContext: CarContext): IconCompat?
    val command: String
}

data class MappingActionListItem(val mapping: LabeledValue) : ActionListItem {
    override fun label(carContext: CarContext) = mapping.label
    override fun icon(carContext: CarContext) = null
    override val command get() = mapping.value
}

data class InternalActionListItem(val labelResId: Int, val iconResId: Int, override val command: String) :
    ActionListItem {
    override fun label(carContext: CarContext) = carContext.getString(labelResId)
    override fun icon(carContext: CarContext) = IconCompat.createWithResource(carContext, iconResId)
}
