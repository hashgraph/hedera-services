/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.benchmark;

import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;

class WiringBenchmark {

    /* Data flow for this benchmark:

    gossip -> event verifier -> orphan buffer
      ^                               |
      |                               |
      ---------------------------------

     */

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

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final WiringModel model = WiringModel.create(platformContext, Time.getCurrent(), ForkJoinPool.commonPool());

        // Ensures that we have no more than 10,000 events in the pipeline at any given time
        final ObjectCounter backpressure = new BackpressureObjectCounter("backpressure", 10_000, Duration.ZERO);

        final TaskScheduler<WiringBenchmarkEvent> verificationTaskScheduler = model.schedulerBuilder("verification")
                .withPool(executor)
                .withType(TaskSchedulerType.CONCURRENT)
                .withOnRamp(backpressure)
                .withExternalBackPressure(true)
                .build()
                .cast();

        final TaskScheduler<WiringBenchmarkEvent> orphanBufferTaskScheduler = model.schedulerBuilder("orphanBuffer")
                .withPool(executor)
                .withType(TaskSchedulerType.SEQUENTIAL)
                .withExternalBackPressure(true)
                .build()
                .cast();

        final TaskScheduler<Void> eventPoolTaskScheduler = model.schedulerBuilder("eventPool")
                .withPool(executor)
                .withType(TaskSchedulerType.SEQUENTIAL)
                .withOffRamp(backpressure)
                .withExternalBackPressure(true)
                .build()
                .cast();

        final BindableInputWire<WiringBenchmarkEvent, WiringBenchmarkEvent> eventsToOrphanBuffer =
                orphanBufferTaskScheduler.buildInputWire("unordered events");

        final BindableInputWire<WiringBenchmarkEvent, WiringBenchmarkEvent> eventsToBeVerified =
                verificationTaskScheduler.buildInputWire("unverified events");

        final BindableInputWire<WiringBenchmarkEvent, Void> eventsToInsertBackIntoEventPool =
                eventPoolTaskScheduler.buildInputWire("verified events");

        verificationTaskScheduler.getOutputWire().solderTo(eventsToOrphanBuffer);
        orphanBufferTaskScheduler.getOutputWire().solderTo(eventsToInsertBackIntoEventPool);

        final WiringBenchmarkEventPool eventPool = new WiringBenchmarkEventPool();
        final WiringBenchmarkTopologicalEventSorter orphanBuffer = new WiringBenchmarkTopologicalEventSorter();
        final WiringBenchmarkEventVerifier verifier = new WiringBenchmarkEventVerifier();
        final WiringBenchmarkGossip gossip = new WiringBenchmarkGossip(executor, eventPool, eventsToBeVerified::put);

        eventsToOrphanBuffer.bind(orphanBuffer);
        eventsToBeVerified.bind(verifier);
        eventsToInsertBackIntoEventPool.bindConsumer(eventPool::checkin);

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
