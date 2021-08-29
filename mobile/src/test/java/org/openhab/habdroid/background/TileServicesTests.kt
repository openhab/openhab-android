/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

package org.openhab.habdroid.background

import com.google.common.reflect.ClassPath
import java.util.stream.Collectors
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.background.tiles.AbstractTileService

class TileServicesTests {
    private lateinit var tileServices: Set<Class<*>>

    @Suppress("UnstableApiUsage")
    @Before
    fun setup() {
        tileServices = ClassPath.from(ClassLoader.getSystemClassLoader())
            .allClasses
            .stream()
            .filter { it.name.startsWith("org.openhab.habdroid.background.tiles.TileService")}
            .map { it.load() }
            .collect(Collectors.toSet())
    }

    @Test
    fun checkTileServiceNumber() {
        Assert.assertEquals(tileServices.size, AbstractTileService.TILE_COUNT)
    }

    @Test
    fun checkTileServicesImplementCorrectId() {
        for (tileService in tileServices) {
            val id = (tileService.newInstance() as AbstractTileService).ID
            Assert.assertEquals(
                "Name of the tile service doesn't match its id",
                id,
                tileService.name.substringAfter("TileService").toInt()
            )
        }
    }

    @Test
    fun checkClassName() {
        for (tileService in tileServices) {
            val id = (tileService.newInstance() as AbstractTileService).ID
            Assert.assertEquals(
                AbstractTileService.getClassNameForId(id),
                tileService.canonicalName
            )

            Assert.assertEquals(
                AbstractTileService.getIdFromClassName(tileService.canonicalName!!),
                id
            )
        }
    }
}
