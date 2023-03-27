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

package com.swirlds.platform.chatter.protocol.processing;

import com.swirlds.base.time.Time;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.MessageHandler;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Sends and receives {@link ProcessingTimeMessage}s, recording the values received from peers locally
 */
public class ProcessingTimeSendReceive implements MessageProvider, MessageHandler<ProcessingTimeMessage> {
    private static final long NO_PROCESSING_TIME = Long.MIN_VALUE;
    private final Duration processingTimeInterval;
    private final Time time;
    private final LongSupplier selfProcessingNanos;

    private long lastSentTime;
    private final AtomicLong peerProcessingTime;

    /**
     * Creates a new instance.
     *
     * @param processingTimeInterval
     * 		the interval at which to send processing time messages
     * @param time
     * 		provides a point in time in nanoseconds, should only be used to measure relative time (from one point to
     * 		another), not absolute time (wall clock time)
     * @param selfProcessingNanos
     * 		provides the current value of this node's event processing time in nanoseconds.
     */
    public ProcessingTimeSendReceive(
            final Time time, final Duration processingTimeInterval, final LongSupplier selfProcessingNanos) {
        this.processingTimeInterval = processingTimeInterval;
        this.time = time;
        this.selfProcessingNanos = selfProcessingNanos;
        this.lastSentTime = time.nanoTime();
        this.peerProcessingTime = new AtomicLong(NO_PROCESSING_TIME);
    }

    @Override
    public void clear() {
        lastSentTime = time.nanoTime();
        peerProcessingTime.set(NO_PROCESSING_TIME);
    }

    @Override
    public void handleMessage(final ProcessingTimeMessage message) {
        peerProcessingTime.set(message.getProcessingTimeInNanos());
    }

    @Override
    public SelfSerializable getMessage() {
        final long now = time.nanoTime();
        if (isTimeToSendMessage(now)) {
            lastSentTime = now;
            return new ProcessingTimeMessage(selfProcessingNanos.getAsLong());
        }
        return null;
    }

    /**
     * @return the processing time received from the peer in nanoseconds, or null if nothing has been received yet
     */
    public Long getPeerProcessingTime() {
        final long t = peerProcessingTime.get();
        return t == NO_PROCESSING_TIME ? null : t;
    }

    private boolean isTimeToSendMessage(final long now) {
        return now - lastSentTime > processingTimeInterval.toNanos();
    }
}
