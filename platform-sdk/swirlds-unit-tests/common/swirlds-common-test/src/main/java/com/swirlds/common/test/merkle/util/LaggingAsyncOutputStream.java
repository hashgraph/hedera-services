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

package com.swirlds.common.test.merkle.util;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This variant of the async output stream introduces extra latency.
 */
public class LaggingAsyncOutputStream<T extends SelfSerializable> extends AsyncOutputStream<T> {

    private final BlockingQueue<Long> messageTimes;

    private final long latencyMilliseconds;

    public LaggingAsyncOutputStream(
            final SerializableDataOutputStream out, final StandardWorkGroup workGroup, final long latencyMilliseconds) {
        super(out, workGroup);
        this.messageTimes = new LinkedBlockingQueue<>();
        this.latencyMilliseconds = latencyMilliseconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendAsync(final T message) throws InterruptedException {
        if (!isAlive()) {
            throw new MerkleSynchronizationException("Messages can not be sent after close has been called.");
        }
        messageTimes.put(System.currentTimeMillis());
        getOutgoingMessages().put(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void serializeMessage(final T message) throws IOException {
        long messageTime = messageTimes.remove();
        long now = System.currentTimeMillis();
        long waitTime = (messageTime + latencyMilliseconds) - now;
        if (waitTime > 0) {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        message.serialize(getOutputStream());
    }
}
