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

import static com.swirlds.common.io.extendable.ExtendableInputStream.extendInputStream;
import static com.swirlds.common.io.extendable.ExtendableOutputStream.extendOutputStream;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.extendable.extensions.ThrottleStreamExtension;
import com.swirlds.test.framework.TestQualifierTags;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ThrottleStreamExtension Tests")
class ThrottleStreamExtensionTests {

    @Test
    @DisplayName("Input Stream Sanity Test")
    void inputStreamSanityTest() throws IOException {
        StreamSanityChecks.inputStreamSanityCheck(
                (final InputStream base) -> new ExtendableInputStream(base, new ThrottleStreamExtension(1024 * 1024)));
    }

    @Test
    @DisplayName("Output Stream Sanity Test")
    void outputStreamSanityTest() throws IOException {
        StreamSanityChecks.outputStreamSanityCheck((final OutputStream base) ->
                new ExtendableOutputStream(base, new ThrottleStreamExtension(1024 * 1024)));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 50, 100})
    @DisplayName("Input Stream Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void inputStreamTest(final int timeIncrementMs) throws IOException {
        final Random random = getRandomPrintSeed();

        final int bytesPerSecond = 1024 * 1024;
        final CountingStreamExtension counter = new CountingStreamExtension();

        final InputStream byteIn = new ByteArrayInputStream(new byte[1024 * 1024 * 10]);
        final InputStream in = extendInputStream(
                byteIn, new ThrottleStreamExtension(bytesPerSecond, Duration.ofMillis(timeIncrementMs)), counter);

        final Instant start = Instant.now();
        final Duration duration = Duration.ofSeconds(1);

        while (true) {
            final Instant now = Instant.now();
            final Duration elapsed = Duration.between(start, now);
            if (isGreaterThan(elapsed, duration)) {
                break;
            }

            if (random.nextBoolean()) {
                for (int i = 0; i < 1024; i++) {
                    in.read();
                }
            }

            if (random.nextBoolean()) {
                in.read(new byte[1024], 0, 1024);
            }

            if (random.nextBoolean()) {
                in.readNBytes(1024);
            }

            if (random.nextBoolean()) {
                in.readNBytes(new byte[1024], 0, 1024);
            }
        }

        final long count = counter.getCount();
        final double ratio = count / (double) bytesPerSecond;

        assertTrue(ratio > 0.8 && ratio < 1.2, "read at " + ratio + " times the expected rate");
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 50, 100})
    @DisplayName("Output Stream Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void outputStreamTest(final int timeIncrementMs) throws IOException {
        final Random random = getRandomPrintSeed();

        final int bytesPerSecond = 1024 * 1024;
        final CountingStreamExtension counter = new CountingStreamExtension();

        final OutputStream byteOut = new ByteArrayOutputStream();
        final OutputStream out = extendOutputStream(
                byteOut, new ThrottleStreamExtension(bytesPerSecond, Duration.ofMillis(timeIncrementMs)), counter);

        final Instant start = Instant.now();
        final Duration duration = Duration.ofSeconds(1);

        while (true) {
            final Instant now = Instant.now();
            final Duration elapsed = Duration.between(start, now);
            if (isGreaterThan(elapsed, duration)) {
                break;
            }

            if (random.nextBoolean()) {
                for (int i = 0; i < 1024; i++) {
                    out.write(i);
                }
            }

            if (random.nextBoolean()) {
                out.write(new byte[1024], 0, 1024);
            }
        }

        final long count = counter.getCount();
        final double ratio = count / (double) bytesPerSecond;

        assertTrue(ratio > 0.8 && ratio < 1.2, "read at " + ratio + " times the expected rate");
    }
}
