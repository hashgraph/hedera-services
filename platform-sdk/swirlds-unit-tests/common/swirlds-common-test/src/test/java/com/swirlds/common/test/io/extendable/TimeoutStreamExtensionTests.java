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

import static com.swirlds.common.io.extendable.extensions.TimeoutStreamExtension.buildTimeoutStreamExtension;
import static com.swirlds.common.io.extendable.extensions.internal.StreamTimeoutManager.getMonitoredStreamCount;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.internal.StreamTimeoutManager;
import com.swirlds.common.test.io.BlockingInputStream;
import com.swirlds.common.test.io.BlockingOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("TimeoutStreamExtension Tests")
class TimeoutStreamExtensionTests {

    @Test
    @DisplayName("Input Stream Sanity Test")
    void inputStreamSanityTest() throws IOException {
        StreamSanityChecks.inputStreamSanityCheck((final InputStream base) ->
                new ExtendableInputStream(base, buildTimeoutStreamExtension(Duration.ofMillis(10))));

        assertEventuallyEquals(
                0,
                StreamTimeoutManager::getMonitoredStreamCount,
                Duration.ofSeconds(5),
                "number of monitored streams should eventually drop to zero");
    }

    @Test
    @DisplayName("Output Stream Sanity Test")
    void outputStreamSanityTest() throws IOException {
        StreamSanityChecks.outputStreamSanityCheck((final OutputStream base) ->
                new ExtendableOutputStream(base, buildTimeoutStreamExtension(Duration.ofMillis(10))));

        assertEventuallyEquals(
                0,
                StreamTimeoutManager::getMonitoredStreamCount,
                Duration.ofSeconds(5),
                "number of monitored streams should eventually drop to zero");
    }

    @Test
    @DisplayName("Input Stream Test")
    @Tag(TIME_CONSUMING)
    void inputStreamTest() throws InterruptedException, IOException {
        final InputStream byteIn = new ByteArrayInputStream(new byte[1024 * 2]);
        final BlockingInputStream blockingIn = new BlockingInputStream(byteIn);
        final InputStream in =
                new ExtendableInputStream(blockingIn, buildTimeoutStreamExtension(Duration.ofMillis(50)));

        assertEquals(1, getMonitoredStreamCount(), "there should be one monitored stream");

        // Just some standard reads
        in.read();
        in.read(new byte[100], 0, 100);
        in.readNBytes(100);
        in.readNBytes(new byte[100], 0, 100);

        // No activity will not cause problems for the timeout
        MILLISECONDS.sleep(200);
        assertFalse(blockingIn.isClosed(), "stream should not have been closed");

        blockingIn.blockAllReads();

        in.readNBytes(1024);
        assertTrue(blockingIn.isClosed(), "stream should have been closed");

        assertEventuallyEquals(
                0,
                StreamTimeoutManager::getMonitoredStreamCount,
                Duration.ofSeconds(5),
                "number of monitored streams should eventually drop to zero");
    }

    @Test
    @DisplayName("Output Stream Test")
    void outputStreamTest() throws IOException, InterruptedException {
        final OutputStream byteOut = new ByteArrayOutputStream();
        final BlockingOutputStream blockingOut = new BlockingOutputStream(byteOut);
        final OutputStream out =
                new ExtendableOutputStream(blockingOut, buildTimeoutStreamExtension(Duration.ofMillis(50)));

        assertEquals(1, getMonitoredStreamCount(), "there should be one monitored stream");

        // Just some standard writes
        out.write(1);
        out.write(new byte[100], 0, 100);

        // No activity will not cause problems for the timeout
        MILLISECONDS.sleep(200);
        assertFalse(blockingOut.isClosed(), "stream should not have been closed");

        blockingOut.blockAllWrites();

        out.write(1024);
        assertTrue(blockingOut.isClosed(), "stream should have been closed");

        assertEventuallyEquals(
                0,
                StreamTimeoutManager::getMonitoredStreamCount,
                Duration.ofSeconds(5),
                "number of monitored streams should eventually drop to zero");
    }
}
