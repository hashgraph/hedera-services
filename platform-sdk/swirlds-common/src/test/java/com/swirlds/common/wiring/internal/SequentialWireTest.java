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

import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.wiring.Wire;
import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SequentialWireTest {

    @Test
    void illegalNamesTest() {
        assertThrows(NullPointerException.class, () -> Wire.builder(null, x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("", x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder(" ", x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo bar", x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo?bar", x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo:bar", x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo*bar", x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo/bar", x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo\\bar", x -> {}));
        assertThrows(IllegalArgumentException.class, () -> Wire.builder("foo-bar", x -> {}));

        // legal names that should not throw
        Wire.builder("x", x -> {});
        Wire.builder("fooBar", x -> {});
        Wire.builder("foo_bar", x -> {});
        Wire.builder("foo_bar123", x -> {});
        Wire.builder("123", x -> {});
    }

    /**
     * Add values to the wire, ensure that each value was processed in the correct order.
     */
    @Test
    void orderOfOperationsTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final Consumer<Integer> handler = x -> wireValue.set(hash32(wireValue.get(), x));

        final Wire<Integer> wire =
                Wire.builder("test", handler).withConcurrency(false).build();
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            wire.put(i);
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

        final Wire<Integer> wire =
                Wire.builder("test", handler).withConcurrency(false).build();
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            wire.put(i);
            value = hash32(value, i);
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }

    /**
     * Multiple threads adding work to the wire shouldn't cause problems. Also, work should always be handled
     * sequentially regardless of the number of threads adding work.
     */
    @Test
    void multipleInsertersTest() {
        final AtomicInteger wireValue = new AtomicInteger();
        final AtomicInteger operationCount = new AtomicInteger();
        final Set<Integer> arguments = ConcurrentHashMap.newKeySet(); // concurrent hash set
        final Consumer<Integer> handler = x -> {
            arguments.add(x);
            // This will result in a deterministic value if there is no parallelism
            wireValue.set(hash32(wireValue.get(), operationCount.getAndIncrement()));
        };

        final Wire<Integer> wire =
                Wire.builder("test", handler).withConcurrency(false).build();
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        final int operationsPerWorker = 1_000;
        final int workers = 10;

        for (int i = 0; i < workers; i++) {
            final int workerNumber = i;
            new ThreadConfiguration(getStaticThreadManager())
                    .setRunnable(() -> {
                        for (int j = 0; j < operationsPerWorker; j++) {
                            wire.put(workerNumber * j);
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
    void multipleInsertersWithDelayTest() {
        final Random random = getRandomPrintSeed();

        final AtomicInteger wireValue = new AtomicInteger();
        final AtomicInteger operationCount = new AtomicInteger();
        final Set<Integer> arguments = ConcurrentHashMap.newKeySet(); // concurrent hash set
        final Consumer<Integer> handler = x -> {
            arguments.add(x);
            // This will result in a deterministic value if there is no parallelism
            wireValue.set(hash32(wireValue.get(), operationCount.getAndIncrement()));
        };

        final Wire<Integer> wire =
                Wire.builder("test", handler).withConcurrency(false).build();
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
                            wire.put(workerNumber * j);
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

        final Wire<Integer> wire =
                Wire.builder("test", handler).withConcurrency(false).build();
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        // The wire will stop processing at 50, but this should not block the calling thread.
        final AtomicInteger value = new AtomicInteger();
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 100; i++) {
                        wire.put(i);
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

        final Wire<Integer> wire = Wire.builder("test", handler)
                .withConcurrency(false)
                .withMetricsBuilder(Wire.metricsBuilder(new NoOpMetrics()).withScheduledTaskCountMetricEnabled(true))
                .build();
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            wire.put(i);
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

        final Wire<Integer> wire = Wire.builder("test", handler)
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .build();
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        final AtomicInteger value = new AtomicInteger();

        // We will be stuck handling 0 and we will have the capacity for 10 more, for a total of 11 tasks in flight
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 11; i++) {
                        wire.put(i);
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
                        wire.put(i);
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

        // Even if the wire has no capacity, offer() should not block.
        completeBeforeTimeout(
                () -> {
                    assertFalse(wire.offer(1234));
                    assertFalse(wire.offer(4321));
                    assertFalse(wire.offer(-1));
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

        final Wire<Integer> wire = Wire.builder("test", handler)
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .build();
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        final AtomicInteger value = new AtomicInteger();

        // We will be stuck handling 0 and we will have the capacity for 10 more, for a total of 11 tasks in flight
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 11; i++) {
                        wire.put(i);
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
                        wire.put(i);
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

        final Wire<Integer> wire = Wire.builder("test", handler)
                .withConcurrency(false)
                .withScheduledTaskCapacity(10)
                .build();
        assertEquals(0, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        final AtomicInteger value = new AtomicInteger();

        // We will be stuck handling 0 and we will have the capacity for 10 more, for a total of 11 tasks in flight
        completeBeforeTimeout(
                () -> {
                    for (int i = 0; i < 11; i++) {
                        wire.put(i);
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
                            wire.interruptablePut(i);
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

        final Wire<Integer> wire =
                Wire.builder("test", handler).withConcurrency(false).build();
        assertEquals(-1, wire.getUnprocessedTaskCount());
        assertEquals("test", wire.getName());

        int value = 0;
        for (int i = 0; i < 100; i++) {
            assertTrue(wire.offer(i));
            value = hash32(value, i);
        }

        assertEventuallyEquals(value, wireValue::get, Duration.ofSeconds(1), "Wire sum did not match expected sum");
    }
}
