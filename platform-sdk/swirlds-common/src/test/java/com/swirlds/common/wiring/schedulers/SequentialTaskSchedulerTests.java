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

package com.swirlds.common.wiring.schedulers;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.test.fixtures.AssertionUtils.completeBeforeTimeout;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.wiring.InputWire;
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.SolderType;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.test.framework.TestWiringModel;
import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SequentialTaskSchedulerTests {

    private static final WiringModel model = TestWiringModel.getInstance();

    @Test
    void illegalNamesTest() {
        assertThrows(NullPointerException.class, () -> model.schedulerBuilder(null));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder(""));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder(" "));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder("foo bar"));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder("foo?bar"));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder("foo:bar"));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder("foo*bar"));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder("foo/bar"));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder("foo\\bar"));
        assertThrows(IllegalArgumentException.class, () -> model.schedulerBuilder("foo-bar"));

        // legal names that should not throw
        model.schedulerBuilder("x");
        model.schedulerBuilder("fooBar");
        model.schedulerBuilder("foo_bar");
        model.schedulerBuilder("foo_bar123");
        model.schedulerBuilder("123");
    }

    /**
     * Add values to the task scheduler, ensure that each value was processed in the correct order.
     */
    @Test
    void orderOfOperationsTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final Consumer<Integer> handler = x -> wireValue.set(hash32(wireValue.get(), x));

        final TaskScheduler<Void> taskScheduler =
                model.schedulerBuilder("test").withConcurrency(false).build().cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(-1, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            channel.put(i);
            value = hash32(value, i);
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Add values to the task scheduler, ensure that each value was processed in the correct order. Add a delay to the
     * handler. The delay should not effect the final value if things are happening as we expect. If the task scheduler
     * is allowing things to happen with parallelism, then the delay is likely to result in a reordering of operations
     * (which will fail the test).
     */
    @Test
    void orderOfOperationsWithDelayTest() {
        final Random random = getRandomPrintSeed();

        final AtomicInteger wireValue = new AtomicInteger();
        final Consumer<Integer> handler = x -> {
            wireValue.set(hash32(wireValue.get(), x));
            try {
                // Sleep for up to a millisecond
                NANOSECONDS.sleep(random.nextInt(1_000_000));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        final TaskScheduler<Void> taskScheduler =
                model.schedulerBuilder("test").withConcurrency(false).build().cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(-1, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            channel.put(i);
            value = hash32(value, i);
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Multiple threads adding work to the task scheduler shouldn't cause problems. Also, work should always be handled
     * sequentially regardless of the number of threads adding work.
     */
    @Test
    void multipleChannelsTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final AtomicInteger operationCount = new AtomicInteger();
        final Set<Integer> arguments = ConcurrentHashMap.newKeySet(); // concurrent hash set
        final Consumer<Integer> handler = x -> {
            arguments.add(x);
            // This will result in a deterministic value if there is no parallelism
            wireValue.set(hash32(wireValue.get(), operationCount.getAndIncrement()));
        };

        final TaskScheduler<Void> taskScheduler =
                model.schedulerBuilder("test").withConcurrency(false).build().cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(-1, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        final int operationsPerWorker = 1_000;
        final int workers = 10;

        for (int i = 0; i < workers; i++) {
            final int workerNumber = i;
            new ThreadConfiguration(getStaticThreadManager())
                    .setRunnable(() -> {
                        for (int j = 0; j < operationsPerWorker; j++) {
                            channel.put(workerNumber * j);
                        }
                    })
                    .build(true);
        }

        // Compute the values we expect to be computed by the wire
        final Set<Integer> expectedArguments = new HashSet<>();
        int expectedValue = 0;
        int count = 0;
        for (int i = 0; i < workers; i++) {
            for (int j = 0; j < operationsPerWorker; j++) {
                expectedArguments.add(i * j);
                expectedValue = hash32(expectedValue, count);
                count++;
            }
        }

        assertEventuallyEquals(
                expectedValue, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
        assertEventuallyEquals(
                expectedArguments.size(),
                arguments::size,
                Duration.ofSeconds(1),
                "Wire arguments did not match expected arguments");
        assertEquals(expectedArguments, arguments);
    }

    /**
     * Multiple threads adding work to the task scheduler shouldn't cause problems. Also, work should always be handled
     * sequentially regardless of the number of threads adding work. Random delay is added to the workers. This should
     * not effect the outcome.
     */
    @Test
    void multipleChannelsWithDelayTest() {
        final Random random = getRandomPrintSeed();

        final AtomicInteger wireValue = new AtomicInteger();
        final AtomicInteger operationCount = new AtomicInteger();
        final Set<Integer> arguments = ConcurrentHashMap.newKeySet(); // concurrent hash set
        final Consumer<Integer> handler = x -> {
            arguments.add(x);
            // This will result in a deterministic value if there is no parallelism
            wireValue.set(hash32(wireValue.get(), operationCount.getAndIncrement()));
        };

        final TaskScheduler<Void> taskScheduler =
                model.schedulerBuilder("test").withConcurrency(false).build().cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(-1, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        final int operationsPerWorker = 1_000;
        final int workers = 10;

        for (int i = 0; i < workers; i++) {
            final int workerNumber = i;
            new ThreadConfiguration(getStaticThreadManager())
                    .setRunnable(() -> {
                        for (int j = 0; j < operationsPerWorker; j++) {
                            if (random.nextDouble() < 0.1) {
                                try {
                                    NANOSECONDS.sleep(random.nextInt(100));
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            channel.put(workerNumber * j);
                        }
                    })
                    .build(true);
        }

        // Compute the values we expect to be computed by the wire
        final Set<Integer> expectedArguments = new HashSet<>();
        int expectedValue = 0;
        int count = 0;
        for (int i = 0; i < workers; i++) {
            for (int j = 0; j < operationsPerWorker; j++) {
                expectedArguments.add(i * j);
                expectedValue = hash32(expectedValue, count);
                count++;
            }
        }

        assertEventuallyEquals(
                expectedValue, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
        assertEventuallyEquals(
                expectedArguments.size(),
                arguments::size,
                Duration.ofSeconds(1),
                "Wire arguments did not match expected arguments");
        assertEquals(expectedArguments, arguments);
    }

    /**
     * Ensure that the work happening on the task scheduler is not happening on the callers thread.
     */
    @Test
    void wireWordDoesNotBlockCallingThreadTest() throws InterruptedException {
        final AtomicInteger wireValue = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        final Consumer<Integer> handler = x -> {
            wireValue.set(hash32(wireValue.get(), x));
            if (x == 50) {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        final TaskScheduler<Void> taskScheduler =
                model.schedulerBuilder("test").withConcurrency(false).build().cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(-1, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        // The wire will stop processing at 50, but this should not block the calling thread.
        final AtomicInteger value = new AtomicInteger();
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 100; i++) {
                        channel.put(i);
                        value.set(hash32(value.get(), i));
                    }
                },
                Duration.ofSeconds(1),
                "calling thread was blocked");

        // Release the latch and allow the wire to finish
        latch.countDown();

        assertEventuallyEquals(
                value.get(), wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Sanity checks on the unprocessed event count.
     */
    @Test
    void unprocessedEventCountTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final CountDownLatch latch0 = new CountDownLatch(1);
        final CountDownLatch latch50 = new CountDownLatch(1);
        final CountDownLatch latch98 = new CountDownLatch(1);
        final Consumer<Integer> handler = x -> {
            try {
                if (x == 0) {
                    latch0.await();
                } else if (x == 50) {
                    latch50.await();
                } else if (x == 98) {
                    latch98.await();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            wireValue.set(hash32(wireValue.get(), x));
        };

        final TaskScheduler<Void> taskScheduler = model.schedulerBuilder("test")
                .withConcurrency(false)
                .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(0, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            channel.put(i);
            value = hash32(value, i);
        }

        assertEventuallyEquals(
                100L,
                taskScheduler::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value, count = "
                        + taskScheduler.getUnprocessedTaskCount());

        latch0.countDown();

        assertEventuallyEquals(
                50L,
                taskScheduler::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");

        latch50.countDown();

        assertEventuallyEquals(
                2L,
                taskScheduler::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");

        latch98.countDown();

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");

        assertEquals(0, taskScheduler.getUnprocessedTaskCount());
    }

    /**
     * Make sure backpressure works.
     */
    @Test
    void backpressureTest() throws InterruptedException {
        final AtomicInteger wireValue = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        final Consumer<Integer> handler = x -> {
            try {
                if (x == 0) {
                    latch.await();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            wireValue.set(hash32(wireValue.get(), x));
        };

        final TaskScheduler<Void> taskScheduler = model.schedulerBuilder("test")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(11)
                .withSleepDuration(Duration.ofMillis(1))
                .build()
                .cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(0, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        final AtomicInteger value = new AtomicInteger();

        // We will be stuck handling 0 and we will have the capacity for 10 more, for a total of 11 tasks in flight
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 11; i++) {
                        channel.put(i);
                        value.set(hash32(value.get(), i));
                    }
                },
                Duration.ofSeconds(1),
                "unable to add tasks");
        assertEquals(11, taskScheduler.getUnprocessedTaskCount());

        // Try to enqueue work on another thread. It should get stuck and be
        // unable to add anything until we release the latch.
        final AtomicBoolean allWorkAdded = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 11; i < 100; i++) {
                        channel.put(i);
                        value.set(hash32(value.get(), i));
                    }
                    allWorkAdded.set(true);
                })
                .build(true);

        // Adding work to an unblocked wire should be very fast. If we sleep for a while, we'd expect that an unblocked
        // wire would have processed all of the work that was added to it.
        MILLISECONDS.sleep(50);
        assertFalse(allWorkAdded.get());
        assertEquals(11, taskScheduler.getUnprocessedTaskCount());

        // Even if the wire has no capacity, neither offer() nor inject() should not block.
        completeBeforeTimeout(
                () -> {
                    assertFalse(channel.offer(1234));
                    assertFalse(channel.offer(4321));
                    assertFalse(channel.offer(-1));
                    channel.inject(42);
                    value.set(hash32(value.get(), 42));
                },
                Duration.ofSeconds(1),
                "unable to offer tasks");

        // Release the latch, all work should now be added
        latch.countDown();

        assertEventuallyTrue(allWorkAdded::get, Duration.ofSeconds(1), "unable to add all work");
        assertEventuallyEquals(
                0L,
                taskScheduler::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value. " + taskScheduler.getUnprocessedTaskCount());
        assertEventuallyEquals(
                value.get(), wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Test interrupts with accept() when backpressure is being applied.
     */
    @Test
    void uninterruptableTest() throws InterruptedException {
        final AtomicInteger wireValue = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        final Consumer<Integer> handler = x -> {
            try {
                if (x == 0) {
                    latch.await();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            wireValue.set(hash32(wireValue.get(), x));
        };

        final TaskScheduler<Void> taskScheduler = model.schedulerBuilder("test")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(11)
                .build()
                .cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(0, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        final AtomicInteger value = new AtomicInteger();

        // We will be stuck handling 0 and we will have the capacity for 10 more, for a total of 11 tasks in flight
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 11; i++) {
                        channel.put(i);
                        value.set(hash32(value.get(), i));
                    }
                },
                Duration.ofSeconds(1),
                "unable to add tasks");
        assertEquals(11, taskScheduler.getUnprocessedTaskCount());

        // Try to enqueue work on another thread. It should get stuck and be
        // unable to add anything until we release the latch.
        final AtomicBoolean allWorkAdded = new AtomicBoolean(false);
        final Thread thread = new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 11; i < 100; i++) {
                        channel.put(i);
                        value.set(hash32(value.get(), i));
                    }
                    allWorkAdded.set(true);
                })
                .build(true);

        // Interrupting the thread should have no effect.
        thread.interrupt();

        // Adding work to an unblocked wire should be very fast. If we sleep for a while, we'd expect that an unblocked
        // wire would have processed all of the work that was added to it.
        MILLISECONDS.sleep(50);
        assertFalse(allWorkAdded.get());
        assertEquals(11, taskScheduler.getUnprocessedTaskCount());

        // Release the latch, all work should now be added
        latch.countDown();

        assertEventuallyTrue(allWorkAdded::get, Duration.ofSeconds(1), "unable to add all work");
        assertEventuallyEquals(
                0L,
                taskScheduler::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");
        assertEventuallyEquals(
                value.get(), wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Offering tasks is equivalent to calling accept() if there is no backpressure.
     */
    @Test
    void offerNoBackpressureTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final Consumer<Integer> handler = x -> wireValue.set(hash32(wireValue.get(), x));

        final TaskScheduler<Void> taskScheduler =
                model.schedulerBuilder("test").withConcurrency(false).build().cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(-1, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            assertTrue(channel.offer(i));
            value = hash32(value, i);
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Test a scenario where there is a circular data flow formed by wires.
     * <p>
     * In this test, all data is passed from A to B to C to D. All data that is a multiple of 7 is passed from D to A as
     * a negative value, but is not passed around the loop again.
     *
     * <pre>
     * A -------> B
     * ^          |
     * |          |
     * |          V
     * D <------- C
     * </pre>
     */
    @Test
    void circularDataFlowTest() throws InterruptedException {
        final Random random = getRandomPrintSeed();

        final AtomicInteger countA = new AtomicInteger();
        final AtomicInteger negativeCountA = new AtomicInteger();
        final AtomicInteger countB = new AtomicInteger();
        final AtomicInteger countC = new AtomicInteger();
        final AtomicInteger countD = new AtomicInteger();

        final TaskScheduler<Integer> taskSchedulerToA =
                model.schedulerBuilder("wireToA").build().cast();
        final TaskScheduler<Integer> taskSchedulerToB =
                model.schedulerBuilder("wireToB").build().cast();
        final TaskScheduler<Integer> taskSchedulerToC =
                model.schedulerBuilder("wireToC").build().cast();
        final TaskScheduler<Integer> taskSchedulerToD =
                model.schedulerBuilder("wireToD").build().cast();

        final InputWire<Integer, Integer> channelToA = taskSchedulerToA.buildInputWire("channelToA");
        final InputWire<Integer, Integer> channelToB = taskSchedulerToB.buildInputWire("channelToB");
        final InputWire<Integer, Integer> channelToC = taskSchedulerToC.buildInputWire("channelToC");
        final InputWire<Integer, Integer> channelToD = taskSchedulerToD.buildInputWire("channelToD");

        final Function<Integer, Integer> handlerA = x -> {
            if (x > 0) {
                countA.set(hash32(x, countA.get()));
                return x;
            } else {
                negativeCountA.set(hash32(x, negativeCountA.get()));
                // negative values are values that have been passed around the loop
                // Don't pass them on again or else we will get an infinite loop
                return null;
            }
        };

        final Function<Integer, Integer> handlerB = x -> {
            countB.set(hash32(x, countB.get()));
            return x;
        };

        final Function<Integer, Integer> handlerC = x -> {
            countC.set(hash32(x, countC.get()));
            return x;
        };

        final Function<Integer, Integer> handlerD = x -> {
            countD.set(hash32(x, countD.get()));
            if (x % 7 == 0) {
                return -x;
            } else {
                return null;
            }
        };

        taskSchedulerToA.getOutputWire().solderTo(channelToB);
        taskSchedulerToB.getOutputWire().solderTo(channelToC);
        taskSchedulerToC.getOutputWire().solderTo(channelToD);
        taskSchedulerToD.getOutputWire().solderTo(channelToA);

        channelToA.bind(handlerA);
        channelToB.bind(handlerB);
        channelToC.bind(handlerC);
        channelToD.bind(handlerD);

        int expectedCountA = 0;
        int expectedNegativeCountA = 0;
        int expectedCountB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 1; i < 1000; i++) {
            channelToA.put(i);

            expectedCountA = hash32(i, expectedCountA);
            expectedCountB = hash32(i, expectedCountB);
            expectedCountC = hash32(i, expectedCountC);
            expectedCountD = hash32(i, expectedCountD);

            if (i % 7 == 0) {
                expectedNegativeCountA = hash32(-i, expectedNegativeCountA);
            }

            // Sleep to give data a chance to flow around the loop
            // (as opposed to adding it so quickly that it is all enqueue prior to any processing)
            if (random.nextDouble() < 0.1) {
                MILLISECONDS.sleep(10);
            }
        }

        assertEventuallyEquals(
                expectedCountA, countA::get, Duration.ofSeconds(1), "Wire A sum did not match expected value");
        assertEventuallyEquals(
                expectedNegativeCountA,
                negativeCountA::get,
                Duration.ofSeconds(1),
                "Wire A negative sum did not match expected value");
        assertEventuallyEquals(
                expectedCountB, countB::get, Duration.ofSeconds(1), "Wire B sum did not match expected value");
        assertEventuallyEquals(
                expectedCountC, countC::get, Duration.ofSeconds(1), "Wire C sum did not match expected value");
        assertEventuallyEquals(
                expectedCountD, countD::get, Duration.ofSeconds(1), "Wire D sum did not match expected value");
    }

    /**
     * Validate the behavior when there are multiple channels.
     */
    @Test
    void multipleChannelTypesTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final Consumer<Integer> integerHandler = x -> wireValue.set(hash32(wireValue.get(), x));
        final Consumer<Boolean> booleanHandler = x -> wireValue.set((x ? -1 : 1) * wireValue.get());
        final Consumer<String> stringHandler = x -> wireValue.set(hash32(wireValue.get(), x.hashCode()));

        final TaskScheduler<Void> taskScheduler =
                model.schedulerBuilder("test").withConcurrency(false).build().cast();

        final InputWire<Integer, Void> integerChannel = taskScheduler
                .buildInputWire("integerChannel")
                .withInputType(Integer.class)
                .bind(integerHandler);
        final InputWire<Boolean, Void> booleanChannel = taskScheduler
                .buildInputWire("booleanChannel")
                .withInputType(Boolean.class)
                .bind(booleanHandler);
        final InputWire<String, Void> stringChannel = taskScheduler
                .buildInputWire("stringChannel")
                .withInputType(String.class)
                .bind(stringHandler);

        assertEquals(-1, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            integerChannel.put(i);
            value = hash32(value, i);

            boolean invert = i % 2 == 0;
            booleanChannel.put(invert);
            value = (invert ? -1 : 1) * value;

            final String string = String.valueOf(i);
            stringChannel.put(string);
            value = hash32(value, string.hashCode());
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire value did not match expected value");
    }

    /**
     * Make sure backpressure works when there are multiple channels.
     */
    @Test
    void multipleChannelBackpressureTest() throws InterruptedException {
        final AtomicInteger wireValue = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);

        final Consumer<Integer> handler1 = x -> {
            try {
                if (x == 0) {
                    latch.await();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            wireValue.set(hash32(wireValue.get(), x));
        };

        final Consumer<Integer> handler2 = x -> wireValue.set(hash32(wireValue.get(), -x));

        final TaskScheduler<Void> taskScheduler = model.schedulerBuilder("test")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(11)
                .build()
                .cast();

        final InputWire<Integer, Void> channel1 = taskScheduler
                .buildInputWire("channel1")
                .withInputType(Integer.class)
                .bind(handler1);
        final InputWire<Integer, Void> channel2 = taskScheduler
                .buildInputWire("channel2")
                .withInputType(Integer.class)
                .bind(handler2);

        assertEquals(0, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        final AtomicInteger value = new AtomicInteger();

        // We will be stuck handling 0 and we will have the capacity for 10 more, for a total of 11 tasks in flight
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 11; i++) {
                        channel1.put(i);
                        value.set(hash32(value.get(), i));
                    }
                },
                Duration.ofSeconds(1),
                "unable to add tasks");
        assertEquals(11, taskScheduler.getUnprocessedTaskCount());

        // Try to enqueue work on another thread. It should get stuck and be
        // unable to add anything until we release the latch.
        final AtomicBoolean allWorkAdded = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 11; i < 100; i++) {
                        channel2.put(i);
                        value.set(hash32(value.get(), -i));
                    }
                    allWorkAdded.set(true);
                })
                .build(true);

        // Adding work to an unblocked wire should be very fast. If we sleep for a while, we'd expect that an unblocked
        // wire would have processed all of the work that was added to it.
        MILLISECONDS.sleep(50);
        assertFalse(allWorkAdded.get());
        assertEquals(11, taskScheduler.getUnprocessedTaskCount());

        // Even if the wire has no capacity, neither offer() nor inject() should not block.
        completeBeforeTimeout(
                () -> {
                    assertFalse(channel1.offer(1234));
                    assertFalse(channel1.offer(4321));
                    assertFalse(channel1.offer(-1));
                    channel1.inject(42);
                    value.set(hash32(value.get(), 42));
                },
                Duration.ofSeconds(1),
                "unable to offer tasks");

        // Release the latch, all work should now be added
        latch.countDown();

        assertEventuallyTrue(allWorkAdded::get, Duration.ofSeconds(1), "unable to add all work");
        assertEventuallyEquals(
                0L,
                taskScheduler::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");
        assertEventuallyEquals(
                value.get(), wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Make sure backpressure works when a single counter spans multiple wires.
     */
    @Test
    void backpressureOverMultipleWiresTest() throws InterruptedException {
        final AtomicInteger wireValueA = new AtomicInteger();
        final AtomicInteger wireValueB = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);

        final ObjectCounter backpressure = new BackpressureObjectCounter("test", 11, Duration.ofMillis(1));

        final TaskScheduler<Void> taskSchedulerA = model.schedulerBuilder("testA")
                .withConcurrency(false)
                .withOnRamp(backpressure)
                .build()
                .cast();

        final TaskScheduler<Void> taskSchedulerB = model.schedulerBuilder("testB")
                .withConcurrency(false)
                .withOffRamp(backpressure)
                .build()
                .cast();

        final InputWire<Integer, Void> channelA = taskSchedulerA.buildInputWire("channelA");
        final InputWire<Integer, Void> channelB = taskSchedulerB.buildInputWire("channelB");

        final Consumer<Integer> handlerA = x -> {
            wireValueA.set(hash32(wireValueA.get(), -x));
            channelB.put(x);
        };

        final Consumer<Integer> handlerB = x -> {
            try {
                if (x == 0) {
                    latch.await();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            wireValueB.set(hash32(wireValueB.get(), x));
        };

        channelA.bind(handlerA);
        channelB.bind(handlerB);

        assertEquals(0, backpressure.getCount());
        assertEquals("testA", taskSchedulerA.getName());
        assertEquals("testB", taskSchedulerB.getName());

        final AtomicInteger valueA = new AtomicInteger();
        final AtomicInteger valueB = new AtomicInteger();

        // We will be stuck handling 0 and we will have the capacity for 10 more, for a total of 11 tasks in flight
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 11; i++) {
                        channelA.put(i);
                        valueA.set(hash32(valueA.get(), -i));
                        valueB.set(hash32(valueB.get(), i));
                    }
                },
                Duration.ofSeconds(1),
                "unable to add tasks");
        assertEquals(11, backpressure.getCount());

        // Try to enqueue work on another thread. It should get stuck and be
        // unable to add anything until we release the latch.
        final AtomicBoolean allWorkAdded = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 11; i < 100; i++) {
                        channelA.put(i);
                        valueA.set(hash32(valueA.get(), -i));
                        valueB.set(hash32(valueB.get(), i));
                    }
                    allWorkAdded.set(true);
                })
                .build(true);

        // Adding work to an unblocked wire should be very fast. If we sleep for a while, we'd expect that an unblocked
        // wire would have processed all of the work that was added to it.
        MILLISECONDS.sleep(50);
        assertFalse(allWorkAdded.get());
        assertEquals(11, backpressure.getCount());

        // Even if the wire has no capacity, neither offer() nor inject() should not block.
        completeBeforeTimeout(
                () -> {
                    assertFalse(channelA.offer(1234));
                    assertFalse(channelA.offer(4321));
                    assertFalse(channelA.offer(-1));
                    channelA.inject(42);
                    valueA.set(hash32(valueA.get(), -42));
                    valueB.set(hash32(valueB.get(), 42));
                },
                Duration.ofSeconds(1),
                "unable to offer tasks");

        // Release the latch, all work should now be added
        latch.countDown();

        assertEventuallyTrue(allWorkAdded::get, Duration.ofSeconds(1), "unable to add all work");
        assertEventuallyEquals(
                0L,
                backpressure::getCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");
        assertEventuallyEquals(
                valueA.get(), wireValueA::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
        assertEventuallyEquals(
                valueB.get(), wireValueB::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Validate the behavior of the flush() method.
     */
    @Test
    void flushTest() throws InterruptedException {
        final AtomicInteger wireValue = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        final Consumer<Integer> handler = x -> {
            try {
                if (x == 0) {
                    latch.await();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            wireValue.set(hash32(wireValue.get(), x));
        };

        final TaskScheduler<Void> taskScheduler = model.schedulerBuilder("test")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(11)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(0, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        final AtomicInteger value = new AtomicInteger();

        // Flushing a wire with nothing in it should return quickly.
        completeBeforeTimeout(taskScheduler::flush, Duration.ofSeconds(1), "unable to flush wire");

        // We will be stuck handling 0 and we will have the capacity for 10 more, for a total of 11 tasks in flight
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 11; i++) {
                        channel.put(i);
                        value.set(hash32(value.get(), i));
                    }
                },
                Duration.ofSeconds(1),
                "unable to add tasks");
        assertEquals(11, taskScheduler.getUnprocessedTaskCount());

        // Try to enqueue work on another thread. It should get stuck and be
        // unable to add anything until we release the latch.
        final AtomicBoolean allWorkAdded = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 11; i < 100; i++) {
                        channel.put(i);
                        value.set(hash32(value.get(), i));
                    }
                    allWorkAdded.set(true);
                })
                .build(true);

        // On another thread, flush the wire. This should also get stuck.
        final AtomicBoolean flushed = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    taskScheduler.flush();
                    flushed.set(true);
                })
                .build(true);

        // Adding work to an unblocked wire should be very fast. If we sleep for a while, we'd expect that an unblocked
        // wire would have processed all of the work that was added to it.
        MILLISECONDS.sleep(50);
        assertFalse(allWorkAdded.get());
        assertFalse(flushed.get());
        // The flush operation puts a task on the wire, which bumps the number up to 12 from 11
        assertEquals(12, taskScheduler.getUnprocessedTaskCount());

        // Even if the wire has no capacity, neither offer() nor inject() should not block.
        completeBeforeTimeout(
                () -> {
                    assertFalse(channel.offer(1234));
                    assertFalse(channel.offer(4321));
                    assertFalse(channel.offer(-1));
                    channel.inject(42);
                    value.set(hash32(value.get(), 42));
                },
                Duration.ofSeconds(1),
                "unable to offer tasks");

        // Release the latch, all work should now be added
        latch.countDown();

        assertEventuallyTrue(allWorkAdded::get, Duration.ofSeconds(1), "unable to add all work");
        assertEventuallyTrue(flushed::get, Duration.ofSeconds(1), "unable to flush wire");
        assertEventuallyEquals(
                0L,
                taskScheduler::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");
        assertEventuallyEquals(
                value.get(), wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    @Test
    void flushDisabledTest() {
        final TaskScheduler<Void> taskScheduler = model.schedulerBuilder("test")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(10)
                .build()
                .cast();

        assertThrows(UnsupportedOperationException.class, taskScheduler::flush, "flush() should not be supported");
    }

    @Test
    void exceptionHandlingTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final Consumer<Integer> handler = x -> {
            if (x == 50) {
                throw new IllegalStateException("intentional");
            }
            wireValue.set(hash32(wireValue.get(), x));
        };

        final AtomicInteger exceptionCount = new AtomicInteger();

        final TaskScheduler<Void> taskScheduler = model.schedulerBuilder("test")
                .withConcurrency(false)
                .withUncaughtExceptionHandler((t, e) -> exceptionCount.incrementAndGet())
                .build()
                .cast();
        final InputWire<Integer, Void> channel = taskScheduler
                .buildInputWire("channel")
                .withInputType(Integer.class)
                .bind(handler);
        assertEquals(-1, taskScheduler.getUnprocessedTaskCount());
        assertEquals("test", taskScheduler.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            channel.put(i);
            if (i != 50) {
                value = hash32(value, i);
            }
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
        assertEquals(1, exceptionCount.get());
    }

    /**
     * An early implementation could deadlock in a scenario with backpressure enabled and a thread count that was less
     * than the number of blocking wires.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void deadlockTest(final int parallelism) throws InterruptedException {
        final ForkJoinPool pool = new ForkJoinPool(parallelism);

        // create 3 wires with the following bindings:
        // a -> b -> c -> latch
        final TaskScheduler<Void> a = model.schedulerBuilder("a")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(2)
                .withSleepDuration(Duration.ofMillis(1))
                .withPool(pool)
                .build()
                .cast();
        final TaskScheduler<Void> b = model.schedulerBuilder("b")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(2)
                .withSleepDuration(Duration.ofMillis(1))
                .withPool(pool)
                .build()
                .cast();
        final TaskScheduler<Void> c = model.schedulerBuilder("c")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(2)
                .withSleepDuration(Duration.ofMillis(1))
                .withPool(pool)
                .build()
                .cast();

        final InputWire<Object, Void> channelA = a.buildInputWire("channelA");
        final InputWire<Object, Void> channelB = b.buildInputWire("channelB");
        final InputWire<Object, Void> channelC = c.buildInputWire("channelC");

        final CountDownLatch latch = new CountDownLatch(1);

        channelA.bind(channelB::put);
        channelB.bind(channelC::put);
        channelC.bind(o -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // each wire has a capacity of 1, so we can have 1 task waiting on each wire
        // insert a task into C, which will start executing and waiting on the latch
        channelC.put(Object.class);
        // fill up the queues for each wire
        channelC.put(Object.class);
        channelA.put(Object.class);
        channelB.put(Object.class);

        completeBeforeTimeout(
                () -> {
                    // release the latch, that should allow all tasks to complete
                    latch.countDown();
                    // if tasks are completing, none of the wires should block
                    channelA.put(Object.class);
                    channelB.put(Object.class);
                    channelC.put(Object.class);
                },
                Duration.ofSeconds(1),
                "deadlock");

        pool.shutdown();
    }

    /**
     * Solder together a simple sequence of wires.
     */
    @Test
    void simpleSolderingTest() {
        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("A").build().cast();
        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("A").build().cast();
        final TaskScheduler<Void> taskSchedulerD =
                model.schedulerBuilder("A").build().cast();

        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");
        final InputWire<Integer, Void> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);

        final AtomicInteger countA = new AtomicInteger();
        final AtomicInteger countB = new AtomicInteger();
        final AtomicInteger countC = new AtomicInteger();
        final AtomicInteger countD = new AtomicInteger();

        inputA.bind(x -> {
            countA.set(hash32(countA.get(), x));
            return x;
        });

        inputB.bind(x -> {
            countB.set(hash32(countB.get(), x));
            return x;
        });

        inputC.bind(x -> {
            countC.set(hash32(countC.get(), x));
            return x;
        });

        inputD.bind(x -> {
            countD.set(hash32(countD.get(), x));
        });

        int expectedCount = 0;

        for (int i = 0; i < 100; i++) {
            inputA.put(i);
            expectedCount = hash32(expectedCount, i);
        }

        assertEventuallyEquals(
                expectedCount, countD::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countB.get());
        assertEquals(expectedCount, countC.get());
    }

    /**
     * Test soldering to a lambda function.
     */
    @Test
    void lambdaSolderingTest() {
        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final TaskScheduler<Void> taskSchedulerD =
                model.schedulerBuilder("D").build().cast();

        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");
        final InputWire<Integer, Void> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);

        final AtomicInteger countA = new AtomicInteger();
        final AtomicInteger countB = new AtomicInteger();
        final AtomicInteger countC = new AtomicInteger();
        final AtomicInteger countD = new AtomicInteger();

        final AtomicInteger lambdaSum = new AtomicInteger();
        taskSchedulerB.getOutputWire().solderTo("lambda", lambdaSum::getAndAdd);

        inputA.bind(x -> {
            countA.set(hash32(countA.get(), x));
            return x;
        });

        inputB.bind(x -> {
            countB.set(hash32(countB.get(), x));
            return x;
        });

        inputC.bind(x -> {
            countC.set(hash32(countC.get(), x));
            return x;
        });

        inputD.bind(x -> {
            countD.set(hash32(countD.get(), x));
        });

        int expectedCount = 0;

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            inputA.put(i);
            expectedCount = hash32(expectedCount, i);
            sum += i;
        }

        assertEventuallyEquals(
                expectedCount, countD::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countB.get());
        assertEquals(sum, lambdaSum.get());
        assertEquals(expectedCount, countC.get());
    }

    /**
     * Solder the output of a wire to the inputs of multiple other wires.
     */
    @Test
    void multiWireSolderingTest() {
        // A passes data to X, Y, and Z
        // X, Y, and Z pass data to B

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final InputWire<Integer, Integer> addNewValueToA = taskSchedulerA.buildInputWire("addNewValueToA");
        final InputWire<Boolean, Integer> setInversionBitInA = taskSchedulerA.buildInputWire("setInversionBitInA");

        final TaskScheduler<Integer> taskSchedulerX =
                model.schedulerBuilder("X").build().cast();
        final InputWire<Integer, Integer> inputX = taskSchedulerX.buildInputWire("inputX");

        final TaskScheduler<Integer> taskSchedulerY =
                model.schedulerBuilder("Y").build().cast();
        final InputWire<Integer, Integer> inputY = taskSchedulerY.buildInputWire("inputY");

        final TaskScheduler<Integer> taskSchedulerZ =
                model.schedulerBuilder("Z").build().cast();
        final InputWire<Integer, Integer> inputZ = taskSchedulerZ.buildInputWire("inputZ");

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final InputWire<Integer, Void> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.getOutputWire().solderTo(inputX);
        taskSchedulerA.getOutputWire().solderTo(inputY);
        taskSchedulerA.getOutputWire().solderTo(inputZ);
        taskSchedulerX.getOutputWire().solderTo(inputB);
        taskSchedulerY.getOutputWire().solderTo(inputB);
        taskSchedulerZ.getOutputWire().solderTo(inputB);

        final AtomicInteger countA = new AtomicInteger();
        final AtomicBoolean invertA = new AtomicBoolean();
        addNewValueToA.bind(x -> {
            final int possiblyInvertedValue = x * (invertA.get() ? -1 : 1);
            countA.set(hash32(countA.get(), possiblyInvertedValue));
            return possiblyInvertedValue;
        });
        setInversionBitInA.bind(x -> {
            invertA.set(x);
            return null;
        });

        final AtomicInteger countX = new AtomicInteger();
        inputX.bind(x -> {
            countX.set(hash32(countX.get(), x));
            return x;
        });

        final AtomicInteger countY = new AtomicInteger();
        inputY.bind(x -> {
            countY.set(hash32(countY.get(), x));
            return x;
        });

        final AtomicInteger countZ = new AtomicInteger();
        inputZ.bind(x -> {
            countZ.set(hash32(countZ.get(), x));
            return x;
        });

        final AtomicInteger sumB = new AtomicInteger();
        inputB.bind(x -> {
            sumB.getAndAdd(x);
        });

        int expectedCount = 0;
        boolean expectedInversionBit = false;
        int expectedSum = 0;

        for (int i = 0; i < 100; i++) {
            if (i % 7 == 0) {
                expectedInversionBit = !expectedInversionBit;
                setInversionBitInA.put(expectedInversionBit);
            }
            addNewValueToA.put(i);

            final int possiblyInvertedValue = i * (expectedInversionBit ? -1 : 1);

            expectedCount = hash32(expectedCount, possiblyInvertedValue);
            expectedSum = expectedSum + 3 * possiblyInvertedValue;
        }

        assertEventuallyEquals(
                expectedSum,
                sumB::get,
                Duration.ofSeconds(1),
                "Wire sum did not match expected sum, " + expectedSum + " vs " + sumB.get());
        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countX.get());
        assertEquals(expectedCount, countY.get());
        assertEquals(expectedCount, countZ.get());
        assertEventuallyEquals(
                expectedInversionBit,
                invertA::get,
                Duration.ofSeconds(1),
                "Wire inversion bit did not match expected value");
    }

    /**
     * Validate that a wire soldered to another using injection ignores backpressure constraints.
     */
    @Test
    void injectionSolderingTest() throws InterruptedException {

        // In this test, wires A and B are connected to the input of wire C, which has a maximum capacity.
        // Wire A respects back pressure, but wire B uses injection and can ignore it.

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final InputWire<Integer, Integer> inA = taskSchedulerA.buildInputWire("inA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final InputWire<Integer, Integer> inB = taskSchedulerB.buildInputWire("inB");

        final TaskScheduler<Void> taskSchedulerC = model.schedulerBuilder("C")
                .withUnhandledTaskCapacity(10)
                .build()
                .cast();
        final InputWire<Integer, Void> inC = taskSchedulerC.buildInputWire("inC");

        taskSchedulerA.getOutputWire().solderTo(inC); // respects capacity
        taskSchedulerB.getOutputWire().solderTo(inC, SolderType.INJECT); // ignores capacity

        final AtomicInteger countA = new AtomicInteger();
        inA.bind(x -> {
            countA.set(hash32(countA.get(), x));
            return x;
        });

        final AtomicInteger countB = new AtomicInteger();
        inB.bind(x -> {
            countB.set(hash32(countB.get(), x));
            return x;
        });

        final AtomicInteger sumC = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        inC.bind(x -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            sumC.getAndAdd(x);
        });

        // Add 5 elements to A and B. This will completely fill C's capacity.
        int expectedCount = 0;
        int expectedSum = 0;
        for (int i = 0; i < 5; i++) {
            inA.put(i);
            inB.put(i);
            expectedCount = hash32(expectedCount, i);
            expectedSum += 2 * i;
        }

        // Eventually, C should have 10 things that have not yet been fully processed.
        assertEventuallyEquals(
                10L,
                taskSchedulerC::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "C should have 10 unprocessed tasks, currently has " + taskSchedulerC.getUnprocessedTaskCount());

        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countB.get());

        // Push some more data into A and B. A will get stuck trying to push it to C.
        inA.put(5);
        inB.put(5);
        expectedCount = hash32(expectedCount, 5);
        expectedSum += 2 * 5;

        assertEventuallyEquals(expectedCount, countA::get, Duration.ofSeconds(1), "A should have processed task");
        assertEventuallyEquals(expectedCount, countB::get, Duration.ofSeconds(1), "B should have processed task");

        // If we wait some time, the task from B should have increased C's count to 11, but the task from A
        // should have been unable to increase C's count.
        MILLISECONDS.sleep(50);
        assertEquals(11, taskSchedulerC.getUnprocessedTaskCount());

        // Push some more data into A and B. A will be unable to process it because it's still
        // stuck pushing the previous value.
        inA.put(6);
        inB.put(6);
        final int expectedCountAfterHandling6 = hash32(expectedCount, 6);
        expectedSum += 2 * 6;

        assertEventuallyEquals(
                expectedCountAfterHandling6, countB::get, Duration.ofSeconds(1), "B should have processed task");

        // Even if we wait, A should not have been able to process the task.
        MILLISECONDS.sleep(50);
        assertEquals(expectedCount, countA.get());
        assertEquals(12, taskSchedulerC.getUnprocessedTaskCount());

        // Releasing the latch should allow data to flow through C.
        latch.countDown();
        assertEventuallyEquals(expectedSum, sumC::get, Duration.ofSeconds(1), "C should have processed all tasks");
        assertEquals(expectedCountAfterHandling6, countA.get());
    }

    /**
     * When a handler returns null, the wire should not forward the null value to the next wire.
     */
    @Test
    void squelchNullValuesInWiresTest() {
        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("A").build().cast();
        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("A").build().cast();
        final TaskScheduler<Void> taskSchedulerD =
                model.schedulerBuilder("A").build().cast();

        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");
        final InputWire<Integer, Void> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);

        final AtomicInteger countA = new AtomicInteger();
        final AtomicInteger countB = new AtomicInteger();
        final AtomicInteger countC = new AtomicInteger();
        final AtomicInteger countD = new AtomicInteger();

        inputA.bind(x -> {
            countA.set(hash32(countA.get(), x));
            if (x % 3 == 0) {
                return null;
            }
            return x;
        });

        inputB.bind(x -> {
            countB.set(hash32(countB.get(), x));
            if (x % 5 == 0) {
                return null;
            }
            return x;
        });

        inputC.bind(x -> {
            countC.set(hash32(countC.get(), x));
            if (x % 7 == 0) {
                return null;
            }
            return x;
        });

        inputD.bind(x -> {
            countD.set(hash32(countD.get(), x));
        });

        int expectedCountA = 0;
        int expectedCountB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 0; i < 100; i++) {
            inputA.put(i);
            expectedCountA = hash32(expectedCountA, i);
            if (i % 3 == 0) {
                continue;
            }
            expectedCountB = hash32(expectedCountB, i);
            if (i % 5 == 0) {
                continue;
            }
            expectedCountC = hash32(expectedCountC, i);
            if (i % 7 == 0) {
                continue;
            }
            expectedCountD = hash32(expectedCountD, i);
        }

        assertEventuallyEquals(
                expectedCountD, countD::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
        assertEquals(expectedCountA, countA.get());
        assertEquals(expectedCountB, countB.get());
        assertEquals(expectedCountC, countC.get());
    }

    /**
     * Make sure we don't crash when metrics are enabled. Might be nice to eventually validate the metrics, but right
     * now the metrics framework makes it complex to do so.
     */
    @Test
    void metricsEnabledTest() {
        final TaskScheduler<Integer> taskSchedulerA = model.schedulerBuilder("A")
                .withMetricsBuilder(model.metricsBuilder()
                        .withBusyFractionMetricsEnabled(true)
                        .withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();
        final TaskScheduler<Integer> taskSchedulerB = model.schedulerBuilder("B")
                .withMetricsBuilder(model.metricsBuilder()
                        .withBusyFractionMetricsEnabled(true)
                        .withUnhandledTaskMetricEnabled(false))
                .build()
                .cast();
        final TaskScheduler<Integer> taskSchedulerC = model.schedulerBuilder("C")
                .withMetricsBuilder(model.metricsBuilder()
                        .withBusyFractionMetricsEnabled(false)
                        .withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();
        final TaskScheduler<Void> taskSchedulerD = model.schedulerBuilder("D")
                .withMetricsBuilder(model.metricsBuilder()
                        .withBusyFractionMetricsEnabled(false)
                        .withUnhandledTaskMetricEnabled(false))
                .build()
                .cast();

        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");
        final InputWire<Integer, Void> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);

        final AtomicInteger countA = new AtomicInteger();
        final AtomicInteger countB = new AtomicInteger();
        final AtomicInteger countC = new AtomicInteger();
        final AtomicInteger countD = new AtomicInteger();

        inputA.bind(x -> {
            countA.set(hash32(countA.get(), x));
            return x;
        });

        inputB.bind(x -> {
            countB.set(hash32(countB.get(), x));
            return x;
        });

        inputC.bind(x -> {
            countC.set(hash32(countC.get(), x));
            return x;
        });

        inputD.bind(x -> {
            countD.set(hash32(countD.get(), x));
        });

        int expectedCount = 0;

        for (int i = 0; i < 100; i++) {
            inputA.put(i);
            expectedCount = hash32(expectedCount, i);
        }

        assertEventuallyEquals(
                expectedCount, countD::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countB.get());
        assertEquals(expectedCount, countC.get());
    }

    @Test
    void multipleOutputChannelsTest() {
        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final InputWire<Integer, Integer> aIn = taskSchedulerA.buildInputWire("aIn");
        final OutputWire<Boolean> aOutBoolean = taskSchedulerA.buildSecondaryOutputWire();
        final OutputWire<String> aOutString = taskSchedulerA.buildSecondaryOutputWire();

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("A").build().cast();
        final InputWire<Integer, Void> bInInteger = taskSchedulerB.buildInputWire("bIn1");
        final InputWire<Boolean, Void> bInBoolean = taskSchedulerB.buildInputWire("bIn2");
        final InputWire<String, Void> bInString = taskSchedulerB.buildInputWire("bIn3");

        taskSchedulerA.getOutputWire().solderTo(bInInteger);
        aOutBoolean.solderTo(bInBoolean);
        aOutString.solderTo(bInString);

        aIn.bind(x -> {
            if (x % 2 == 0) {
                aOutBoolean.forward(x % 3 == 0);
            }

            if (x % 5 == 0) {
                aOutString.forward(Integer.toString(x));
            }

            return x;
        });

        final AtomicInteger count = new AtomicInteger();
        bInBoolean.bind(x -> {
            count.set(hash32(count.get(), x ? 1 : 0));
        });
        bInString.bind(x -> {
            count.set(hash32(count.get(), x.hashCode()));
        });
        bInInteger.bind(x -> {
            count.set(hash32(count.get(), x));
        });

        int expectedCount = 0;
        for (int i = 0; i < 100; i++) {
            aIn.put(i);
            if (i % 2 == 0) {
                expectedCount = hash32(expectedCount, i % 3 == 0 ? 1 : 0);
            }
            if (i % 5 == 0) {
                expectedCount = hash32(expectedCount, Integer.toString(i).hashCode());
            }
            expectedCount = hash32(expectedCount, i);
        }

        assertEventuallyEquals(expectedCount, count::get, Duration.ofSeconds(1), "Wire count did not match expected");
    }

    @Test
    void externalBackPressureTest() throws InterruptedException {

        // There are three components, A, B, and C.
        // We want to control the number of elements in all three, not individually.

        final ObjectCounter counter = new BackpressureObjectCounter("test", 10, Duration.ofMillis(1));

        final TaskScheduler<Integer> taskSchedulerA = model.schedulerBuilder("A")
                .withOnRamp(counter)
                .withExternalBackPressure(true)
                .build()
                .cast();
        final InputWire<Integer, Integer> aIn = taskSchedulerA.buildInputWire("aIn");

        final TaskScheduler<Integer> taskSchedulerB = model.schedulerBuilder("B")
                .withExternalBackPressure(true)
                .build()
                .cast();
        final InputWire<Integer, Integer> bIn = taskSchedulerB.buildInputWire("bIn");

        final TaskScheduler<Void> taskSchedulerC = model.schedulerBuilder("C")
                .withOffRamp(counter)
                .withExternalBackPressure(true)
                .build()
                .cast();
        final InputWire<Integer, Void> cIn = taskSchedulerC.buildInputWire("cIn");

        taskSchedulerA.getOutputWire().solderTo(bIn);
        taskSchedulerB.getOutputWire().solderTo(cIn);

        final AtomicInteger countA = new AtomicInteger();
        final CountDownLatch latchA = new CountDownLatch(1);
        aIn.bind(x -> {
            try {
                latchA.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            countA.set(hash32(countA.get(), x));
            return x;
        });

        final AtomicInteger countB = new AtomicInteger();
        final CountDownLatch latchB = new CountDownLatch(1);
        bIn.bind(x -> {
            try {
                latchB.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            countB.set(hash32(countB.get(), x));
            return x;
        });

        final AtomicInteger countC = new AtomicInteger();
        final CountDownLatch latchC = new CountDownLatch(1);
        cIn.bind(x -> {
            try {
                latchC.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            countC.set(hash32(countC.get(), x));
        });

        // Add enough data to fill all available capacity.
        int expectedCount = 0;
        for (int i = 0; i < 10; i++) {
            aIn.put(i);
            expectedCount = hash32(expectedCount, i);
        }

        final AtomicBoolean moreWorkInserted = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    aIn.put(10);
                    moreWorkInserted.set(true);
                })
                .build(true);
        expectedCount = hash32(expectedCount, 10);

        assertEquals(10, counter.getCount());

        // Work is currently stuck at A. No matter how much time passes, no new work should be added.
        MILLISECONDS.sleep(50);
        assertFalse(moreWorkInserted.get());
        assertEquals(10, counter.getCount());

        // Unblock A. Work will flow forward and get blocked at B. No matter how much time passes, no new work should
        // be added.
        latchA.countDown();
        MILLISECONDS.sleep(50);
        assertFalse(moreWorkInserted.get());
        assertEquals(10, counter.getCount());

        // Unblock B. Work will flow forward and get blocked at C. No matter how much time passes, no new work should
        // be added.
        latchB.countDown();
        MILLISECONDS.sleep(50);
        assertFalse(moreWorkInserted.get());

        // Unblock C. Entire pipeline is now unblocked and new things will be added.
        latchC.countDown();
        assertEventuallyEquals(0L, counter::getCount, Duration.ofSeconds(1), "Counter should be empty");
        assertEventuallyEquals(expectedCount, countA::get, Duration.ofSeconds(1), "A should have processed task");
        assertEventuallyEquals(expectedCount, countB::get, Duration.ofSeconds(1), "B should have processed task");
        assertEventuallyEquals(expectedCount, countC::get, Duration.ofSeconds(1), "C should have processed task");
        assertTrue(moreWorkInserted.get());
    }

    @Test
    void multipleCountersInternalBackpressureTest() throws InterruptedException {

        // There are three components, A, B, and C.
        // The pipeline as a whole has a capacity of 10. Each step individually has a capacity of 5;

        final ObjectCounter counter = new BackpressureObjectCounter("test", 10, Duration.ofMillis(1));

        final TaskScheduler<Integer> taskSchedulerA = model.schedulerBuilder("A")
                .withOnRamp(counter)
                .withExternalBackPressure(true)
                .withUnhandledTaskCapacity(5)
                .build()
                .cast();
        final InputWire<Integer, Integer> aIn = taskSchedulerA.buildInputWire("aIn");

        final TaskScheduler<Integer> taskSchedulerB = model.schedulerBuilder("B")
                .withExternalBackPressure(true)
                .withUnhandledTaskCapacity(5)
                .build()
                .cast();
        final InputWire<Integer, Integer> bIn = taskSchedulerB.buildInputWire("bIn");

        final TaskScheduler<Void> taskSchedulerC = model.schedulerBuilder("C")
                .withOffRamp(counter)
                .withExternalBackPressure(true)
                .withUnhandledTaskCapacity(5)
                .build()
                .cast();
        final InputWire<Integer, Void> cIn = taskSchedulerC.buildInputWire("cIn");

        taskSchedulerA.getOutputWire().solderTo(bIn);
        taskSchedulerB.getOutputWire().solderTo(cIn);

        final AtomicInteger countA = new AtomicInteger();
        final CountDownLatch latchA = new CountDownLatch(1);
        aIn.bind(x -> {
            try {
                latchA.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            countA.set(hash32(countA.get(), x));
            return x;
        });

        final AtomicInteger countB = new AtomicInteger();
        final CountDownLatch latchB = new CountDownLatch(1);
        bIn.bind(x -> {
            try {
                latchB.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            countB.set(hash32(countB.get(), x));
            return x;
        });

        final AtomicInteger countC = new AtomicInteger();
        final CountDownLatch latchC = new CountDownLatch(1);
        cIn.bind(x -> {
            try {
                latchC.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            countC.set(hash32(countC.get(), x));
        });

        int expectedCount = 0;
        for (int i = 0; i < 11; i++) {
            expectedCount = hash32(expectedCount, i);
        }

        // This thread wants to add 11 things to the pipeline.
        final AtomicBoolean allWorkInserted = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 0; i < 11; i++) {
                        aIn.put(i);
                    }
                    allWorkInserted.set(true);
                })
                .build(true);

        // Work is currently stuck at A. No matter how much time passes, we should not be able to exceed A's capacity.
        MILLISECONDS.sleep(50);
        assertFalse(allWorkInserted.get());
        assertEquals(5, counter.getCount());

        // Unblock A. Work will flow forward and get blocked at B. A can fit 5 items, B can fit another 5.
        latchA.countDown();
        MILLISECONDS.sleep(50);
        assertFalse(allWorkInserted.get());
        assertEquals(10, counter.getCount());

        // Unblock B. Work will flow forward and get blocked at C. We shouldn't be able to add additional items
        // since that would violate the global capacity.
        latchB.countDown();
        MILLISECONDS.sleep(50);
        assertFalse(allWorkInserted.get());
        assertEquals(10, counter.getCount());

        // Unblock C. Entire pipeline is now unblocked and new things will be added.
        latchC.countDown();
        assertEventuallyTrue(allWorkInserted::get, Duration.ofSeconds(1), "All work should have been inserted");
        assertEventuallyEquals(0L, counter::getCount, Duration.ofSeconds(1), "Counter should be empty");
        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countB.get());
        assertEquals(expectedCount, countC.get());
    }

    @Test
    void offerSolderingTest() {
        final TaskScheduler<Integer> schedulerA = model.schedulerBuilder("test")
                .withUnhandledTaskCapacity(10)
                .build()
                .cast();
        final InputWire<Integer, Integer> inputA = schedulerA.buildInputWire("inputA");

        final TaskScheduler<Void> schedulerB = model.schedulerBuilder("test")
                .withUnhandledTaskCapacity(10)
                .build()
                .cast();
        final InputWire<Integer, Void> inputB = schedulerB.buildInputWire("inputB");

        schedulerA.getOutputWire().solderTo(inputB, SolderType.OFFER);

        final AtomicInteger countA = new AtomicInteger();
        inputA.bind(x -> {
            countA.set(hash32(countA.get(), x));
            return x;
        });

        final AtomicInteger countB = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        inputB.bind(x -> {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            countB.set(hash32(countB.get(), x));
        });

        // Fill up B's buffer.
        int expectedCountA = 0;
        int expectedCountB = 0;
        for (int i = 0; i < 10; i++) {
            inputA.put(i);
            expectedCountA = hash32(expectedCountA, i);
            expectedCountB = hash32(expectedCountB, i);
        }

        // Add more than B is willing to accept.
        for (int i = 10; i < 20; i++) {
            inputA.put(i);
            expectedCountA = hash32(expectedCountA, i);
        }

        // Wait until A has handled all of its tasks.
        assertEventuallyEquals(expectedCountA, countA::get, Duration.ofSeconds(1), "A should have processed task");

        // B should not have processed any tasks.
        assertEquals(0, countB.get());

        // Release the latch and allow B to process tasks.
        latch.countDown();
        assertEventuallyEquals(expectedCountB, countB::get, Duration.ofSeconds(1), "B should have processed task");

        // Now, add some more data to A. That data should flow to B as well.
        for (int i = 30, j = 0; i < 40; i++, j++) {
            inputA.put(i);
            expectedCountA = hash32(expectedCountA, i);
            expectedCountB = hash32(expectedCountB, i);
        }

        assertEventuallyEquals(expectedCountA, countA::get, Duration.ofSeconds(1), "A should have processed task");
        assertEventuallyEquals(expectedCountB, countB::get, Duration.ofSeconds(1), "B should have processed task");
    }
}
