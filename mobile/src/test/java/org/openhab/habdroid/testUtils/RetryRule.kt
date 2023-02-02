/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

package org.openhab.habdroid.testUtils

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class RetryRule : TestRule {
    private var currentAttempt = 0
    private var lastError: Throwable? = null

    override fun apply(base: Statement, description: Description): Statement {
        description.getAnnotation(Retry::class.java) ?: return base

        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                while (currentAttempt < 3) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        lastError = t
                        currentAttempt++
                        runBlocking {
                            delay(1000)
                        }
                    }
                }
                throw Exception(lastError)
            }
        }
    }
}

@Target(AnnotationTarget.FUNCTION)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
annotation class Retry()
