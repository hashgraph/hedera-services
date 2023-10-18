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

package com.swirlds.common.wiring.internal;

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

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.wiring.Wire;
import com.swirlds.common.wiring.WireBuilder;
import com.swirlds.common.wiring.WireChannel;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SequentialWireTests {

    @Test
    void illegalNamesTest() {
        assertThrows(NullPointerException.class, () -> Wire.builder(null));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder(""));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder(" "));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo bar"));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo?bar"));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo:bar"));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo*bar"));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo/bar"));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo\\bar"));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo-bar"));

        // legal names that should not throw
        Wire.builder("x");
        Wire.builder("fooBar");
        Wire.builder("foo_bar");
        Wire.builder("foo_bar123");
        Wire.builder("123");
    }

    /**
     * Add values to the wire, ensure that each value was processed in the correct order.
     */
    @Test
    void orderOfOperationsTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final Consumer<Integer> handler = x -> wireValue.set(hash32(wireValue.get(), x));

        final Wire wire = Wire.builder("test").withConcurrency(false).build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            channel.put(i);
            value = hash32(value, i);
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Add values to the wire, ensure that each value was processed in the correct order. Add a delay to the handler.
     * The delay should not effect the final value if things are happening as we expect. If the wire is allowing things
     * to happen with parallelism, then the delay is likely to result in a reordering of operations (which will fail the
     * test).
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

        final Wire wire = Wire.builder("test").withConcurrency(false).build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            channel.put(i);
            value = hash32(value, i);
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Multiple threads adding work to the wire shouldn't cause problems. Also, work should always be handled
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

        final Wire wire = Wire.builder("test").withConcurrency(false).build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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
     * Multiple threads adding work to the wire shouldn't cause problems. Also, work should always be handled
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

        final Wire wire = Wire.builder("test").withConcurrency(false).build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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
     * Ensure that the work happening on the wire is not happening on the callers thread.
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

        final Wire wire = Wire.builder("test").withConcurrency(false).build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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

        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withMetricsBuilder(Wire.metricsBuilder(new NoOpMetrics(), Time.getCurrent())
                        .withScheduledTaskCountMetricEnabled(true))
                .build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            channel.put(i);
            value = hash32(value, i);
        }

        assertEventuallyEquals(
                99L,
                wire::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value, count = " + wire.getUnprocessedTaskCount());

        latch0.countDown();

        assertEventuallyEquals(
                49L,
                wire::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");

        latch50.countDown();

        assertEventuallyEquals(
                1L,
                wire::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");

        latch98.countDown();

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");

        assertEquals(0, wire.getUnprocessedTaskCount());
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

        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .withSleepDuration(Duration.ofMillis(1))
                .build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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
        assertEquals(10, wire.getUnprocessedTaskCount());

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
        assertEquals(10, wire.getUnprocessedTaskCount());

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
                wire::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");
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

        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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
        assertEquals(10, wire.getUnprocessedTaskCount());

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
        assertEquals(10, wire.getUnprocessedTaskCount());

        // Release the latch, all work should now be added
        latch.countDown();

        assertEventuallyTrue(allWorkAdded::get, Duration.ofSeconds(1), "unable to add all work");
        assertEventuallyEquals(
                0L,
                wire::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");
        assertEventuallyEquals(
                value.get(), wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Test interrupts with accept() when backpressure is being applied.
     */
    @Test
    void interruptableTest() throws InterruptedException {
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

        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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
        assertEquals(10, wire.getUnprocessedTaskCount());

        // Try to enqueue work on another thread. It should get stuck and be
        // unable to add anything until we release the latch.
        final AtomicBoolean allWorkAdded = new AtomicBoolean(false);
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        final Thread thread = new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 11; i < 100; i++) {
                        try {
                            channel.interruptablePut(i);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            interrupted.set(true);
                            return;
                        }
                        value.set(hash32(value.get(), i));
                    }
                    allWorkAdded.set(true);
                })
                .build(true);

        // Interrupting the thread should cause it to quickly terminate.
        thread.interrupt();

        assertEventuallyTrue(interrupted::get, Duration.ofSeconds(1), "thread was not interrupted");
        assertFalse(allWorkAdded.get());
        assertEventuallyTrue(() -> !thread.isAlive(), Duration.ofSeconds(1), "thread did not terminate");
    }

    /**
     * Offering tasks is equivalent to calling accept() if there is no backpressure.
     */
    @Test
    void offerNoBackpressureTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final Consumer<Integer> handler = x -> wireValue.set(hash32(wireValue.get(), x));

        final Wire wire = Wire.builder("test").withConcurrency(false).build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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

        final Wire wireToA = Wire.builder("wireToA").build();
        final Wire wireToB = Wire.builder("wireToB").build();
        final Wire wireToC = Wire.builder("wireToC").build();
        final Wire wireToD = Wire.builder("wireToD").build();

        final WireChannel<Integer> channelToA = wireToA.createChannel(Integer.class);
        final WireChannel<Integer> channelToB = wireToB.createChannel(Integer.class);
        final WireChannel<Integer> channelToC = wireToC.createChannel(Integer.class);
        final WireChannel<Integer> channelToD = wireToD.createChannel(Integer.class);

        final Consumer<Integer> handlerA = x -> {
            if (x > 0) {
                countA.set(hash32(x, countA.get()));
                channelToB.put(x);
            } else {
                negativeCountA.set(hash32(x, negativeCountA.get()));
                // negative values are values that have been passed around the loop
                // Don't pass them on again or else we will get an infinite loop
            }
        };

        final Consumer<Integer> handlerB = x -> {
            countB.set(hash32(x, countB.get()));
            channelToC.put(x);
        };

        final Consumer<Integer> handlerC = x -> {
            countC.set(hash32(x, countC.get()));
            channelToD.put(x);
        };

        final Consumer<Integer> handlerD = x -> {
            countD.set(hash32(x, countD.get()));
            if (x % 7 == 0) {
                channelToA.put(-x);
            }
        };

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

        final Wire wire = Wire.builder("test").withConcurrency(false).build();

        final WireChannel<Integer> integerChannel =
                wire.createChannel(Integer.class).bind(integerHandler);
        final WireChannel<Boolean> booleanChannel =
                wire.createChannel(Boolean.class).bind(booleanHandler);
        final WireChannel<String> stringChannel =
                wire.createChannel(String.class).bind(stringHandler);

        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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

        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .build();

        final WireChannel<Integer> channel1 = wire.createChannel(Integer.class).bind(handler1);
        final WireChannel<Integer> channel2 = wire.createChannel(Integer.class).bind(handler2);

        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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
        assertEquals(10, wire.getUnprocessedTaskCount());

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
        assertEquals(10, wire.getUnprocessedTaskCount());

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
                wire::getUnprocessedTaskCount,
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

        final ObjectCounter backpressure = new BackpressureObjectCounter(10, null);

        final Wire wireA = Wire.builder("testA")
                .withConcurrency(false)
                .withOnRamp(backpressure)
                .build();

        final Wire wireB = Wire.builder("testB")
                .withConcurrency(false)
                .withOffRamp(backpressure)
                .build();

        final WireChannel<Integer> channelA = wireA.createChannel();
        final WireChannel<Integer> channelB = wireB.createChannel();

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
        assertEquals("testA", wireA.getName());
        assertEquals("testB", wireB.getName());

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
        assertEquals(10, backpressure.getCount());

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
        assertEquals(10, backpressure.getCount());

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

        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .withFlushingEnabled(true)
                .build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        final AtomicInteger value = new AtomicInteger();

        // Flushing a wire with nothing in it should return quickly.
        completeBeforeTimeout(wire::flush, Duration.ofSeconds(1), "unable to flush wire");

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
        assertEquals(10, wire.getUnprocessedTaskCount());

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
                    wire.flush();
                    flushed.set(true);
                })
                .build(true);

        // Adding work to an unblocked wire should be very fast. If we sleep for a while, we'd expect that an unblocked
        // wire would have processed all of the work that was added to it.
        MILLISECONDS.sleep(50);
        assertFalse(allWorkAdded.get());
        assertFalse(flushed.get());
        // The flush operation puts a task on the wire, which bumps the number up to 11 from 10
        assertEquals(11, wire.getUnprocessedTaskCount());

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
                wire::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");
        assertEventuallyEquals(
                value.get(), wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Validate the behavior of the interruptableFlush() method.
     */
    @Test
    void interruptableFlushTest() throws InterruptedException {
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

        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .withFlushingEnabled(true)
                .build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        final AtomicInteger value = new AtomicInteger();

        // Flushing a wire with nothing in it should return quickly.
        completeBeforeTimeout(wire::flush, Duration.ofSeconds(1), "unable to flush wire");

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
        assertEquals(10, wire.getUnprocessedTaskCount());

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
                    try {
                        wire.interruptableFlush();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    flushed.set(true);
                })
                .build(true);

        // Flush the wire on a thread that we are going to intentionally interrupt.
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        final AtomicBoolean flushedBeforeInterrupt = new AtomicBoolean(false);
        final Thread threadToInterrupt = new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    try {
                        wire.interruptableFlush();
                        flushedBeforeInterrupt.set(true);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        throw new RuntimeException(e);
                    }
                })
                .build(true);

        // Wait a little time. Threads should become blocked.
        MILLISECONDS.sleep(50);

        // Interrupt the thread. This should happen fairly quickly.
        threadToInterrupt.interrupt();
        assertEventuallyTrue(interrupted::get, Duration.ofSeconds(1), "thread was not interrupted");
        assertFalse(flushedBeforeInterrupt.get());

        // Adding work to an unblocked wire should be very fast. If we sleep for a while, we'd expect that an unblocked
        // wire would have processed all of the work that was added to it.
        MILLISECONDS.sleep(50);
        assertFalse(allWorkAdded.get());
        assertFalse(flushed.get());
        // The flush operation puts a task on the wire and we flush twice, bumping the number from 10 to 12
        assertEquals(12, wire.getUnprocessedTaskCount());

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
                wire::getUnprocessedTaskCount,
                Duration.ofSeconds(1),
                "Wire unprocessed task count did not match expected value");
        assertEventuallyEquals(
                value.get(), wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    @Test
    void flushDisabledTest() {
        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .build();

        assertThrows(UnsupportedOperationException.class, wire::flush, "flush() should not be supported");
        assertThrows(UnsupportedOperationException.class, wire::interruptableFlush, "flush() should not be supported");
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

        final Wire wire = Wire.builder("test")
                .withConcurrency(false)
                .withUncaughtExceptionHandler((t, e) -> exceptionCount.incrementAndGet())
                .build();
        final WireChannel<Integer> channel = wire.createChannel(Integer.class).bind(handler);
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

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
     * A test that shows that backpressure can cause a deadlock if we have more wires than threads.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void deadlockTest(final int parallelism) throws InterruptedException {
        final ForkJoinPool pool = new ForkJoinPool(parallelism);

        // create 3 wires with the following bindings:
        // a -> b -> c -> latch
        final Wire a = Wire.builder("a")
                .withConcurrency(false)
                .withScheduledTaskCapacity(1)
                .withBackpressureSleepDuration(Duration.ofMillis(1))
                .withPool(pool)
                .build();
        final Wire b = Wire.builder("b")
                .withConcurrency(false)
                .withScheduledTaskCapacity(1)
                .withBackpressureSleepDuration(Duration.ofMillis(1))
                .withPool(pool)
                .build();
        final Wire c = Wire.builder("c")
                .withConcurrency(false)
                .withScheduledTaskCapacity(1)
                .withBackpressureSleepDuration(Duration.ofMillis(1))
                .withPool(pool)
                .build();

        final WireChannel<Object> channelA = a.createChannel();
        final WireChannel<Object> channelB = b.createChannel();
        final WireChannel<Object> channelC = c.createChannel();

        final CountDownLatch latch = new CountDownLatch(1);

        channelA.bind(channelB::put);
        channelB.bind(channelC::put);
        channelC.bind(o-> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // each wire has a capacity of 1, so we can have 1 task waiting on each wire
        // insert an task into C, which will start executing and waiting on the latch
        channelC.put(Object.class);
        // fill up the queues for each wire
        channelC.put(Object.class);
        channelA.put(Object.class);
        channelB.put(Object.class);

        completeBeforeTimeout(
                ()->{
                    // release the latch, that should allow all tasks to complete
                    latch.countDown();
                    // if tasks are completing, none of the wires should block
                    channelA.put(Object.class);
                    channelB.put(Object.class);
                    channelC.put(Object.class);
                },
                Duration.ofSeconds(1),
                "deadlock"
        );
    }
}
