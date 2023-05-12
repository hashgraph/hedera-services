/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.io.extendable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("CountingStreamExtension Tests")
class CountingStreamExtensionTests {

    @Test
    @DisplayName("Input Stream Sanity Test")
    void inputStreamSanityTest() throws IOException {
        StreamSanityChecks.inputStreamSanityCheck(
                (final InputStream base) -> new ExtendableInputStream(base, new CountingStreamExtension()));

        StreamSanityChecks.inputStreamSanityCheck(
                (final InputStream base) -> new ExtendableInputStream(base, new CountingStreamExtension(false)));
    }

    @Test
    @DisplayName("Output Stream Sanity Test")
    void outputStreamSanityTest() throws IOException {
        StreamSanityChecks.outputStreamSanityCheck(
                (final OutputStream base) -> new ExtendableOutputStream(base, new CountingStreamExtension()));

        StreamSanityChecks.outputStreamSanityCheck(
                (final OutputStream base) -> new ExtendableOutputStream(base, new CountingStreamExtension(false)));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Counting Test")
    void countingTest() throws IOException {
        final PipedInputStream pipeIn = new PipedInputStream();
        final PipedOutputStream pipeOut = new PipedOutputStream(pipeIn);

        final CountingStreamExtension extensionIn = new CountingStreamExtension();
        final ExtendableInputStream countIn = new ExtendableInputStream(pipeIn, extensionIn);

        final CountingStreamExtension extensionOut = new CountingStreamExtension();
        final ExtendableOutputStream countOut = new ExtendableOutputStream(pipeOut, extensionOut);

        assertEquals(0, extensionIn.getCount(), "no bytes have been read");
        assertEquals(0, extensionOut.getCount(), "no bytes have been written");

        final int writeBytes = 10;
        final int readBytes = writeBytes / 2;

        countOut.write(new byte[writeBytes]);

        assertEquals(0, extensionIn.getCount(), "no bytes have been read");
        assertEquals(writeBytes, extensionOut.getCount(), "incorrect count");

        countIn.read(new byte[readBytes]);

        assertEquals(readBytes, extensionIn.getCount(), "incorrect count");
        assertEquals(writeBytes, extensionOut.getCount(), "incorrect count");

        countIn.read(new byte[readBytes * 2]);
        assertEquals(writeBytes, extensionIn.getCount(), "incorrect count");
        assertEquals(writeBytes, extensionOut.getCount(), "incorrect count");

        extensionIn.resetCount();
        assertEquals(0, extensionIn.getCount(), "no additional bytes written");

        countIn.close();
        countOut.close();
    }

    @Test
    @DisplayName("Reading Closed Stream Test")
    void readingClosedStreamTest() throws IOException {
        final byte[] bytes = new byte[1024];
        final ByteArrayInputStream byteIn = new ByteArrayInputStream(bytes);
        final CountingStreamExtension extension = new CountingStreamExtension();
        final InputStream in = new ExtendableInputStream(byteIn, extension);

        in.readNBytes(bytes.length);

        assertEquals(bytes.length, extension.getCount(), "count should have all bytes counted");

        // Read some bytes from the now closed stream
        in.read();
        in.readNBytes(new byte[10], 0, 10);
        in.readNBytes(10);
        in.readNBytes(new byte[10], 0, 10);

        assertEquals(bytes.length, extension.getCount(), "count should not have changed");
    }
}
