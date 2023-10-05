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

import com.swirlds.common.wiring.components.EventPool;
import com.swirlds.common.wiring.components.EventVerifier;
import com.swirlds.common.wiring.components.Gossip;
import com.swirlds.common.wiring.components.TopologicalEventSorter;
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

        final EventPool eventPool = new EventPool();

        final TopologicalEventSorter orphanBuffer = new TopologicalEventSorter(eventPool);
        final EventVerifier verifier = new EventVerifier(
                Wire.builder(orphanBuffer).withConcurrency(false).build());
        final Gossip gossip = new Gossip(
                executor,
                eventPool,
                Wire.builder(verifier).withConcurrency(true).build());

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
