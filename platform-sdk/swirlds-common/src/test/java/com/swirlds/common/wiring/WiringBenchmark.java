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

import com.swirlds.common.wiring.components.Event;
import com.swirlds.common.wiring.components.EventPool;
import com.swirlds.common.wiring.components.EventVerifier;
import com.swirlds.common.wiring.components.Gossip;
import com.swirlds.common.wiring.components.TopologicalEventSorter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;

class WiringBenchmark {

    @Test
    void basicBenchmark() throws InterruptedException {

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
        final ObjectCounter backpressure = new BackpressureObjectCounter(10_000, null);

        final Wire<Event> toVerifier = Wire.builder("verification", Event.class)
                .withConcurrency(true)
                .withOnRamp(backpressure)
                .build();

        final Wire<Event> toOrphanBuffer = Wire.builder("orphanBuffer", Event.class)
                .withConcurrency(false)
                .withOffRamp(backpressure)
                .build();
        final EventPool eventPool = new EventPool();

        // Step 2: construct components

        final TopologicalEventSorter orphanBuffer = new TopologicalEventSorter(eventPool);
        final EventVerifier verifier = new EventVerifier(toOrphanBuffer::put);
        final Gossip gossip = new Gossip(executor, eventPool, toVerifier::put);

        // Step 3: hook wires into components

        toOrphanBuffer.setConsumer(orphanBuffer);
        toVerifier.setConsumer(verifier);

        // Step 4: run

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

    public static void main(String[] args) throws Exception {
        new WiringBenchmark().basicBenchmark();
    }
}
