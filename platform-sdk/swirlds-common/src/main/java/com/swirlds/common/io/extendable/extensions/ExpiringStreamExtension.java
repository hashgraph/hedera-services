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

package com.swirlds.common.io.extendable.extensions;

import static com.swirlds.common.utility.CompareTo.isGreaterThan;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * This stream extension causes the stream to expire after a certain duration. If bytes are
 * read from the stream after the expiration period then the stream throws an IOException.
 * Note that this extension will not interrupt extremely long read/write operations if the
 * operation starts before the stream expires and ends after the stream expires.
 */
public class ExpiringStreamExtension extends AbstractStreamExtension {

    private static final int DEFAULT_BYTES_PER_SAMPLE = 1024 * 1024;

    private final Duration period;
    private final int bytesPerSample;

    private Instant start;
    private long bytesInCurrentSample;

    /**
     * Create an extension that causes a stream to expire.
     *
     * @param period
     * 		the length of time until the stream expires
     */
    public ExpiringStreamExtension(final Duration period) {
        this(period, DEFAULT_BYTES_PER_SAMPLE);
    }

    /**
     * Create an extension that causes a stream to expire.
     *
     * @param period
     * 		the length of time until the stream expires
     * @param bytesPerSample
     * 		check the time after reading this many bytes. A large number may cause
     * 		the stream to be usable beyond its expiration period, while a small number
     * 		will have higher overhead.
     */
    public ExpiringStreamExtension(final Duration period, final int bytesPerSample) {
        this.period = period;
        this.bytesPerSample = bytesPerSample;

        this.start = Instant.now();
    }

    /**
     * Reset the expiration timer.
     */
    public void reset() {
        bytesInCurrentSample = 0;
        start = Instant.now();
    }

    /**
     * Check if the stream has expired.
     *
     * @param length
     * 		the number of bytes passing through the stream
     * @throws IOException
     * 		if the stream has expired
     */
    private void checkIfExpired(final int length) throws IOException {
        bytesInCurrentSample += length;
        if (bytesInCurrentSample > bytesPerSample) {

            final Instant now = Instant.now();
            final Duration elapsed = Duration.between(start, now);
            if (isGreaterThan(elapsed, period)) {
                throw new IOException("stream has expired");
            }

            bytesInCurrentSample = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newByte(final int aByte) throws IOException {
        checkIfExpired(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newBytes(final byte[] bytes, final int offset, final int length) throws IOException {
        checkIfExpired(length);
    }
}
