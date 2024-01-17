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

package org.openhab.habdroid.background

import com.google.common.reflect.ClassPath
import java.util.stream.Collectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.background.tiles.AbstractTileService

@ExperimentalCoroutinesApi
class TileServicesTests {
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")
    private lateinit var tileServices: Set<Class<*>>

    @Suppress("UnstableApiUsage")
    @Before
    fun setup() {
        tileServices = ClassPath.from(ClassLoader.getSystemClassLoader())
            .allClasses
            .stream()
            .filter { it.name.startsWith("org.openhab.habdroid.background.tiles.TileService") }
            .map { it.load() }
            .collect(Collectors.toSet())

        Dispatchers.setMain(mainThreadSurrogate)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mainThreadSurrogate.close()
    }

    @Test
    fun checkTileServiceNumber() {
        Assert.assertEquals(tileServices.size, AbstractTileService.TILE_COUNT)
    }

    @Test
    fun checkTileServicesImplementCorrectId() {
        GlobalScope.launch(Dispatchers.Main) {
            for (tileService in tileServices) {
                val id = (tileService.getDeclaredConstructor().newInstance() as AbstractTileService).ID
                Assert.assertEquals(
                    "Name of the tile service doesn't match its id",
                    id,
                    tileService.name.substringAfter("TileService").toInt()
                )
            }
        }
    }

    @Test
    fun checkClassName() {
        GlobalScope.launch(Dispatchers.Main) {
            for (tileService in tileServices) {
                val id = (tileService.getDeclaredConstructor().newInstance() as AbstractTileService).ID
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
}
