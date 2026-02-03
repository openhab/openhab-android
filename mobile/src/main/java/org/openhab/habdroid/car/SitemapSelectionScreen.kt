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
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Sitemap

class SitemapSelectionScreen(
    carContext: CarContext,
    private val sitemaps: List<Sitemap>,
    private val onSitemapSelected: (sitemap: Sitemap) -> Unit
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(R.string.mainmenu_openhab_selectsitemap))
            .build()

        val itemListBuilder = ItemList.Builder()
        sitemaps
            .map {
                Row.Builder()
                    .setTitle(it.label)
                    .setOnClickListener { onSitemapSelected(it) }
                    .build()
            }
            .forEach { itemListBuilder.addItem(it) }

        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(itemListBuilder.build())
            .build()
    }
}
