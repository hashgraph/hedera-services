/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.benchmark.reconnect.lag;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.io.IOException;
import java.time.Duration;

/**
 * This variant of the async output stream introduces an extra delay for every single
 * message, which emulates I/O-related performance issues (slow disk when the message
 * was read from disk originally, and then slow network I/O).
 */
public class BenchmarkSlowAsyncOutputStream<T extends SelfSerializable> extends AsyncOutputStream<T> {

    private final long delayStorageMicroseconds;
    private final long delayNetworkMicroseconds;

    public BenchmarkSlowAsyncOutputStream(
            final SerializableDataOutputStream out,
            final StandardWorkGroup workGroup,
            final long delayStorageMicroseconds,
            final long delayNetworkMicroseconds,
            final ReconnectConfig reconnectConfig) {
        super(out, workGroup, reconnectConfig);
        this.delayStorageMicroseconds = delayStorageMicroseconds;
        this.delayNetworkMicroseconds = delayNetworkMicroseconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendAsync(final T message) throws InterruptedException {
        if (!isAlive()) {
            throw new MerkleSynchronizationException("Messages can not be sent after close has been called.");
        }
        sleepMicros(delayStorageMicroseconds);
        getOutgoingMessages().put(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void serializeMessage(final T message) throws IOException {
        sleepMicros(delayNetworkMicroseconds);
        message.serialize(getOutputStream());
    }

    /**
     * Sleep for a given number of microseconds.
     * @param micros time to sleep, in microseconds
     */
    private static void sleepMicros(final long micros) {
        try {
            Thread.sleep(Duration.ofNanos(micros * 1000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
