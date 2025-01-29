/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.benchmark;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A quick and dirty simulation of gossip :-). It will generate events like crazy.
 */
public class WiringBenchmarkGossip {
    private final Executor executor;
    private final WiringBenchmarkEventPool eventPool;
    private final Consumer<WiringBenchmarkEvent> toEventVerifier;
    private final AtomicLong eventNumber = new AtomicLong();
    private volatile boolean stopped = false;
    private volatile long checkSum;

    public WiringBenchmarkGossip(
            Executor executor, WiringBenchmarkEventPool eventPool, Consumer<WiringBenchmarkEvent> toEventVerifier) {
        this.executor = executor;
        this.toEventVerifier = toEventVerifier;
        this.eventPool = eventPool;
    }

    public void start() {
        eventNumber.set(0);
        checkSum = 0;
        executor.execute(this::generateEvents);
    }

    private void generateEvents() {
        while (!stopped) {
            final var event = eventPool.checkout(eventNumber.getAndIncrement());
            toEventVerifier.accept(event);
        }
        long lastNumber = eventNumber.get();
        checkSum = lastNumber * (lastNumber + 1) / 2;
    }

    public void stop() {
        stopped = true;
    }

    public long getCheckSum() {
        return checkSum;
    }
}
