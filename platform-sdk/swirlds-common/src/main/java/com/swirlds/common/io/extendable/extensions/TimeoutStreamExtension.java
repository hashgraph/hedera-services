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
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.io.extendable.InputStreamExtension;
import com.swirlds.common.io.extendable.OutputStreamExtension;
import com.swirlds.common.io.extendable.extensions.internal.StreamTimeoutManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * This extension will trigger an exception on a timeout if a single operation takes too long on a stream.
 * If a timeout is triggered then the stream is closed.
 * </p>
 *
 * <p>
 * This extension will not trigger an exception if no data is passing through the stream, and no data has
 * been requested to pass through the stream.
 * </p>
 *
 * <p>
 * Timing is approximate in the positive direction. That is, things make take longer than the configured duration
 * to time out, but will never time out in less than the configured duration.
 * </p>
 */
public class TimeoutStreamExtension implements InputStreamExtension, OutputStreamExtension {

    private static final Logger logger = LogManager.getLogger(TimeoutStreamExtension.class);

    private final Duration timeoutPeriod;

    private final AtomicLong operationStartNumber = new AtomicLong();
    private final AtomicLong operationFinishNumber = new AtomicLong();
    private long lastTimeoutCheck = -1;
    private Instant watchStart;

    private InputStream inputStream;
    private OutputStream outputStream;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final InputStream baseStream) {
        inputStream = baseStream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final OutputStream baseStream) {
        outputStream = baseStream;
    }

    /**
     * Construct and register a timeout stream extension. This is handled by a static method due
     * to registration requirements with the timeout stream manager.
     *
     * @param timeoutPeriod
     * 		the time required
     * @return a new timeout extension
     */
    public static TimeoutStreamExtension buildTimeoutStreamExtension(final Duration timeoutPeriod) {
        final TimeoutStreamExtension extension = new TimeoutStreamExtension(timeoutPeriod);
        StreamTimeoutManager.register(extension);
        return extension;
    }

    /**
     * Create a new timeout stream extension.
     *
     * @param timeoutPeriod
     * 		the maximum period of time permitted for a single operation
     */
    private TimeoutStreamExtension(final Duration timeoutPeriod) {
        this.timeoutPeriod = timeoutPeriod;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        closed.getAndSet(true);
    }

    /**
     * Called periodically to check if the timeout has expired. Closes the stream once the timeout has occurred.
     *
     * @return true if the stream is still open and valid, false if the stream has been closed
     */
    public boolean checkTimeout() {
        if (closed.get()) {
            return false;
        }

        final long finishNumber = operationFinishNumber.get();
        final long startNumber = operationStartNumber.get();

        if (lastTimeoutCheck == startNumber && finishNumber < startNumber) {
            // Since the last time the timeout was checked, no new operation has started, and
            // the finish number indicates that the started operation was never completed.

            if (watchStart == null) {
                // We not yet started a watch on this operation.

                watchStart = Instant.now();
            } else {
                // We have already started a watch on this operation. Check if the watch
                // has been open for longer than the timeout duration.

                final Duration elapsedTime = Duration.between(watchStart, Instant.now());
                if (isGreaterThan(elapsedTime, timeoutPeriod)) {
                    triggerTimeout();
                }
            }
        } else {
            lastTimeoutCheck = operationStartNumber.get();
            watchStart = null;
        }

        return true;
    }

    /**
     * Called on a stream after it has timed out.
     */
    private void triggerTimeout() {
        logger.error(EXCEPTION.getMarker(), "operation timed out on stream");
        close();
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "exception while attempting to close timed out stream", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        final long op = operationStartNumber.incrementAndGet();
        final int aByte = inputStream.read();
        operationFinishNumber.set(op);
        return aByte;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final byte[] bytes, final int offset, final int length) throws IOException {
        final long op = operationStartNumber.incrementAndGet();
        final int count = inputStream.read(bytes, offset, length);
        operationFinishNumber.set(op);
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] readNBytes(final int length) throws IOException {
        final long op = operationStartNumber.incrementAndGet();
        final byte[] data = inputStream.readNBytes(length);
        operationFinishNumber.set(op);
        return data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readNBytes(final byte[] bytes, final int offset, final int length) throws IOException {
        final long op = operationStartNumber.incrementAndGet();
        final int count = inputStream.readNBytes(bytes, offset, length);
        operationFinishNumber.set(op);
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final int b) throws IOException {
        final long op = operationStartNumber.incrementAndGet();
        outputStream.write(b);
        operationFinishNumber.set(op);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final byte[] bytes, final int offset, final int length) throws IOException {
        final long op = operationStartNumber.incrementAndGet();
        outputStream.write(bytes, offset, length);
        operationFinishNumber.set(op);
    }
}
