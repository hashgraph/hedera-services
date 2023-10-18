/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring;

import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.wiring.components.WiringBenchmarkEvent;
import com.swirlds.common.wiring.components.WiringBenchmarkEventPool;
import com.swirlds.common.wiring.components.WiringBenchmarkEventVerifier;
import com.swirlds.common.wiring.components.WiringBenchmarkGossip;
import com.swirlds.common.wiring.components.WiringBenchmarkTopologicalEventSorter;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;

class WiringBenchmark {

    static void basicBenchmark() throws InterruptedException {

        // We will use this executor for starting all threads. Maybe we should only use it for temporary threads?
        final ForkJoinPool executor = new ForkJoinPool(
                Runtime.getRuntime().availableProcessors(),
                defaultForkJoinWorkerThreadFactory,
                (t, e) -> {
                    System.out.println("Uncaught exception in thread " + t.getName());
                    e.printStackTrace();
                },
                true);

        // Step 1: construct wires

        // Ensures that we have no more than 10,000 events in the pipeline at any given time
        final ObjectCounter backpressure = new BackpressureObjectCounter(10_000, Duration.ZERO);

        final Wire toVerifier = Wire.builder("verification")
                .withPool(executor)
                .withConcurrency(true)
                .withOnRamp(backpressure)
                .build();

        final Wire toOrphanBuffer = Wire.builder("orphanBuffer")
                .withPool(executor)
                .withConcurrency(false)
                .withOffRamp(backpressure)
                .build();
        final WiringBenchmarkEventPool eventPool = new WiringBenchmarkEventPool();

        // Step 2: create channels

        final WireChannel<WiringBenchmarkEvent> orphanBufferChannel = toOrphanBuffer.createChannel();
        final WireChannel<WiringBenchmarkEvent> verifierChannel = toVerifier.createChannel();

        // Step 3: construct components

        final WiringBenchmarkTopologicalEventSorter orphanBuffer = new WiringBenchmarkTopologicalEventSorter(eventPool);
        final WiringBenchmarkEventVerifier verifier = new WiringBenchmarkEventVerifier(orphanBufferChannel::put);
        final WiringBenchmarkGossip gossip = new WiringBenchmarkGossip(executor, eventPool, verifierChannel::put);

        // Step 4: bind wires to components

        orphanBufferChannel.bind(orphanBuffer);
        verifierChannel.bind(verifier);

        // Step 5: run

        // Create a user thread for running "gossip". It will continue to generate events until explicitly stopped.
        System.out.println("Starting gossip");
        gossip.start();
        SECONDS.sleep(120);
        gossip.stop();

        // Validate that all events have been seen by orphanBuffer
        final long timeout = System.currentTimeMillis() + 1000;
        boolean success = false;
        while (System.currentTimeMillis() < timeout) {
            if (orphanBuffer.getCheckSum() == gossip.getCheckSum()) {
                success = true;
                break;
            }
        }
        assertTrue(success);
    }

    public static void main(String[] args) {
        try {
            basicBenchmark();
        } catch (final Throwable t) {
            t.printStackTrace();
        }
    }
}
