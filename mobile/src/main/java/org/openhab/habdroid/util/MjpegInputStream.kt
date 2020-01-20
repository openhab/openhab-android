/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

package org.openhab.habdroid.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties

class MjpegInputStream(stream: InputStream) : DataInputStream(BufferedInputStream(stream, FRAME_MAX_LENGTH)) {
    @Throws(IOException::class)
    private fun getEndOfSequence(stream: DataInputStream, sequence: ByteArray): Int {
        var seqIndex = 0
        for (i in 0 until FRAME_MAX_LENGTH) {
            val c = stream.readUnsignedByte().toByte()
            if (c == sequence[seqIndex]) {
                seqIndex++
                if (seqIndex == sequence.size) {
                    return i + 1
                }
            } else {
                seqIndex = 0
            }
        }
        return -1
    }

    @Throws(IOException::class)
    private fun getStartOfSequence(stream: DataInputStream, sequence: ByteArray): Int {
        val end = getEndOfSequence(stream, sequence)
        return if (end < 0) -1 else end - sequence.size
    }

    @Throws(IOException::class, NumberFormatException::class)
    private fun parseContentLength(headerBytes: ByteArray): Int {
        val headerIn = ByteArrayInputStream(headerBytes)
        val props = Properties()
        try {
            props.load(headerIn)
        } catch (e: IllegalArgumentException) {
            throw IOException("Error loading props", e)
        }
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH))
    }

    @Throws(IOException::class)
    fun readMjpegFrame(): Bitmap? {
        mark(FRAME_MAX_LENGTH)
        val headerLen = getStartOfSequence(this, SOI_MARKER)
        reset()

        if (headerLen < 0) {
            return null
        }

        val header = ByteArray(headerLen)
        readFully(header)

        val contentLength = try {
            parseContentLength(header)
        } catch (nfe: NumberFormatException) {
            getEndOfSequence(this, EOF_MARKER)
        }

        reset()
        skipBytes(headerLen)

        if (contentLength < 0) {
            return null
        }

        val frameData = ByteArray(contentLength)
        readFully(frameData)

        return BitmapFactory.decodeStream(ByteArrayInputStream(frameData))
    }

    companion object {
        private val SOI_MARKER = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        private val EOF_MARKER = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        private const val HEADER_MAX_LENGTH = 100
        private const val FRAME_MAX_LENGTH = 400000 + HEADER_MAX_LENGTH
        private const val CONTENT_LENGTH = "Content-Length"
    }
}
