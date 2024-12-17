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

package com.swirlds.common.wiring.schedulers;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.TestWiringModelBuilder;
import com.swirlds.common.wiring.counters.StandardObjectCounter;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.SolderType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DirectTaskSchedulerTests {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void basicOperationTest(final boolean threadsafe) {
        final WiringModel model = TestWiringModelBuilder.create();

        final Random random = getRandomPrintSeed();
        final Thread mainThread = Thread.currentThread();

        final TaskSchedulerType type = threadsafe ? TaskSchedulerType.DIRECT_THREADSAFE : TaskSchedulerType.DIRECT;

        final StandardObjectCounter counter = new StandardObjectCounter(Duration.ofMillis(1));

        final TaskScheduler<Integer> schedulerA = model.schedulerBuilder("A")
                .withType(type)
                .withOnRamp(counter)
                .build()
                .cast();
        final BindableInputWire<Integer, Integer> inA = schedulerA.buildInputWire("inA");
        final OutputWire<Integer> outA = schedulerA.getOutputWire();

        final TaskScheduler<Void> schedulerB = model.schedulerBuilder("B")
                .withType(type)
                .withOffRamp(counter)
                .build()
                .cast();
        final BindableInputWire<Integer, Void> inB = schedulerB.buildInputWire("inB");

        final SolderType solderType;
        final double solderChoice = random.nextDouble();
        if (solderChoice < 1.0 / 3.0) {
            solderType = SolderType.PUT;
        } else if (solderChoice < 2.0 / 3.0) {
            solderType = SolderType.OFFER;
        } else {
            solderType = SolderType.INJECT;
        }

        outA.solderTo(inB, solderType);

        final AtomicInteger countA = new AtomicInteger(0);
        inA.bind(x -> {
            assertEquals(Thread.currentThread(), mainThread);
            assertEquals(1, counter.getCount());
            countA.set(hash32(countA.get(), x));
            return -x;
        });

        final AtomicInteger countB = new AtomicInteger(0);
        inB.bindConsumer(x -> {
            assertEquals(Thread.currentThread(), mainThread);
            assertEquals(1, counter.getCount());
            countB.set(hash32(countB.get(), x));
        });

        int expectedCountA = 0;
        int expectedCountB = 0;
        for (int i = 0; i < 100; i++) {

            final double methodChoice = random.nextDouble();
            if (methodChoice < 1.0 / 3.0) {
                inA.put(i);
            } else if (methodChoice < 2.0 / 3.0) {
                inA.offer(i);
            } else {
                inA.inject(i);
            }

            assertEquals(0, counter.getCount());

            expectedCountA = hash32(expectedCountA, i);
            expectedCountB = hash32(expectedCountB, -i);

            assertEquals(expectedCountA, countA.get());
            assertEquals(expectedCountB, countB.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exceptionHandlerTest(final boolean threadsafe) {
        final WiringModel model = TestWiringModelBuilder.create();

        final TaskSchedulerType type = threadsafe ? TaskSchedulerType.DIRECT_THREADSAFE : TaskSchedulerType.DIRECT;
        final Thread mainThread = Thread.currentThread();

        final AtomicInteger exceptionHandlerCount = new AtomicInteger(0);
        final UncaughtExceptionHandler handler = (t, e) -> {
            exceptionHandlerCount.incrementAndGet();
            assertEquals(Thread.currentThread(), mainThread);
            assertEquals(IllegalStateException.class, e.getClass());
            assertEquals("intentional", e.getMessage());
        };

        final TaskScheduler<Void> scheduler = model.schedulerBuilder("test")
                .withType(type)
                .withUncaughtExceptionHandler(handler)
                .build()
                .cast();

        final BindableInputWire<Integer, Void> in = scheduler.buildInputWire("in");

        final AtomicInteger count = new AtomicInteger(0);
        in.bindConsumer(x -> {
            assertEquals(Thread.currentThread(), mainThread);

            if (x == 50) {
                throw new IllegalStateException("intentional");
            }

            count.set(hash32(count.get(), x));
        });

        int expectedCount = 0;
        for (int i = 0; i < 100; i++) {
            in.put(i);

            if (i < 50) {
                assertEquals(0, exceptionHandlerCount.get());
            } else if (i == 50) {
                assertEquals(1, exceptionHandlerCount.get());
                continue;
            } else {
                assertEquals(1, exceptionHandlerCount.get());
            }

            expectedCount = hash32(expectedCount, i);
            assertEquals(expectedCount, count.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void squelching(final boolean threadsafe) {
        final WiringModel model = TestWiringModelBuilder.create();
        final Thread mainThread = Thread.currentThread();
        final TaskSchedulerType type = threadsafe ? TaskSchedulerType.DIRECT_THREADSAFE : TaskSchedulerType.DIRECT;

        final TaskScheduler<Integer> scheduler = model.schedulerBuilder("A")
                .withType(type)
                .withSquelchingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Integer, Integer> inputWire = scheduler.buildInputWire("input");

        final AtomicInteger handleCount = new AtomicInteger(0);
        inputWire.bind(x -> {
            assertEquals(Thread.currentThread(), mainThread);
            handleCount.incrementAndGet();
            return -x;
        });

        for (int i = 0; i < 5; i++) {
            inputWire.put(i);
            inputWire.offer(i);
            inputWire.inject(i);
        }
        assertEquals(15, handleCount.get(), "Tasks added before squelching should be handled");

        scheduler.startSquelching();
        for (int i = 0; i < 5; i++) {
            inputWire.put(i);
            inputWire.offer(i);
            inputWire.inject(i);
        }
        assertEquals(15, handleCount.get(), "Tasks added after starting to squelch should not be handled");

        scheduler.stopSquelching();
        for (int i = 0; i < 5; i++) {
            inputWire.put(i);
            inputWire.offer(i);
            inputWire.inject(i);
        }
        assertEquals(30, handleCount.get(), "Tasks added after stopping squelching should be handled");
    }
}
