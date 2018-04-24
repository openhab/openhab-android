/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MjpegInputStream extends DataInputStream {
    private static final byte[] SOI_MARKER = { (byte) 0xFF, (byte) 0xD8 };
    private static final byte[] EOF_MARKER = { (byte) 0xFF, (byte) 0xD9 };
    private static final int HEADER_MAX_LENGTH = 100;
    private static final int FRAME_MAX_LENGTH = 400000 + HEADER_MAX_LENGTH;
    private static final String CONTENT_LENGTH = "Content-Length";

    public MjpegInputStream(InputStream in) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }

    private int getEndOfSeqeunce(DataInputStream in, byte[] sequence) throws IOException {
        int seqIndex = 0;
        for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
            byte c = (byte) in.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                seqIndex++;
                if (seqIndex == sequence.length) {
                    return i + 1;
                }
            } else {
                seqIndex = 0;
            }
        }
        return -1;
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        int end = getEndOfSeqeunce(in, sequence);
        return end < 0 ? -1 : end - sequence.length;
    }

    private int parseContentLength(byte[] headerBytes) throws IOException, NumberFormatException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
    }

    public Bitmap readMjpegFrame() throws IOException {
        mark(FRAME_MAX_LENGTH);
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();

        byte[] header = new byte[headerLen];
        readFully(header);

        int contentLength;
        try {
            contentLength = parseContentLength(header);
        } catch (NumberFormatException nfe) {
            contentLength = getEndOfSeqeunce(this, EOF_MARKER);
        }

        reset();
        skipBytes(headerLen);

        byte[] frameData = new byte[contentLength];
        readFully(frameData);

        return BitmapFactory.decodeStream(new ByteArrayInputStream(frameData));
    }
}