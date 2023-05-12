/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.test.io;

import com.swirlds.common.io.streams.DebuggableMerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * A convenience class that constructs a pair of streams.
 */
public class InputOutputStream implements AutoCloseable {
    private final ByteArrayOutputStream outByteStream;
    private final MerkleDataOutputStream outStream;
    private MerkleDataInputStream inStream;

    /**
     * Create an input/output stream pair.
     */
    public InputOutputStream() {
        outByteStream = new ByteArrayOutputStream();
        outStream = new MerkleDataOutputStream(outByteStream);
    }

    public MerkleDataOutputStream getOutput() {
        return outStream;
    }

    public void startReading() throws IOException {
        startReading(false, false);
    }

    /**
     * Start reading from the stream. No bytes should be written to the output after this is called.
     *
     * @param printBytes
     * 		if true then print the bytes in the stream
     * @param debug
     * 		if true then enable stream debugging
     */
    public void startReading(final boolean printBytes, final boolean debug) throws IOException {
        outByteStream.flush();
        byte[] bytes = outByteStream.toByteArray();
        if (printBytes) {
            System.out.println(Arrays.toString(bytes));
        }

        if (debug) {
            inStream = new DebuggableMerkleDataInputStream(new ByteArrayInputStream(bytes));
        } else {
            inStream = new MerkleDataInputStream(new ByteArrayInputStream(bytes));
        }

        outByteStream.close();
        outStream.close();
    }

    public void printBytes() {
        System.out.println(Arrays.toString(outByteStream.toByteArray()));
    }

    public MerkleDataInputStream getInput() {
        return inStream;
    }

    public void close() throws IOException {
        if (inStream != null) {
            inStream.close();
        }

        outStream.close();
        outByteStream.close();
    }
}
