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

package com.swirlds.common.threading.utility;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Used to wrap an {@link InterruptableConsumer} in order to track when the consumer started executing and when it
 * finished.
 * This object can be used to detect when a {@link com.swirlds.common.threading.framework.QueueThread QueueThread} is
 * currently handling any object, and to wait until that object has been fully handled.
 *
 * @param <T>
 * 		the type being consumed
 */
public class SequenceCycle<T> implements InterruptableConsumer<T> {
    private static final Duration DEFAULT_SLEEP = Duration.ofMillis(1);

    private final InterruptableConsumer<T> consumer;
    private final ThreadSleep sleep;
    private final long sleepMillis;

    private final AtomicLong startedSequence = new AtomicLong(0);
    private final AtomicLong endedSequence = new AtomicLong(0);

    public SequenceCycle(final InterruptableConsumer<T> consumer) {
        this(consumer, Thread::sleep);
    }

    public SequenceCycle(final InterruptableConsumer<T> consumer, final ThreadSleep sleep) {
        this(consumer, sleep, DEFAULT_SLEEP);
    }

    /**
     * @param consumer
     * 		the consumer to wrap
     * @param sleep
     * 		the method that executed the sleep
     * @param sleepTime
     * 		the amount of time to sleep while waiting for a cycle to end
     */
    public SequenceCycle(final InterruptableConsumer<T> consumer, final ThreadSleep sleep, final Duration sleepTime) {
        this.consumer = consumer;
        this.sleep = sleep;
        this.sleepMillis = sleepTime.toMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(final T item) throws InterruptedException {
        startedSequence.incrementAndGet();
        try {
            consumer.accept(item);
        } finally {
            endedSequence.incrementAndGet();
        }
    }

    private long getSequenceStarted() {
        return startedSequence.get();
    }

    private void waitForSequenceEnd(final long startSequence) throws InterruptedException {
        while (endedSequence.get() < startSequence) {
            sleep.sleep(sleepMillis);
        }
    }

    /**
     * Waits for the current cycle to end. If there is no cycle ongoing, it returns immediately
     *
     * @throws InterruptedException
     * 		if interrupted while waiting
     */
    public void waitForCurrentSequenceEnd() throws InterruptedException {
        waitForSequenceEnd(getSequenceStarted());
    }
}
