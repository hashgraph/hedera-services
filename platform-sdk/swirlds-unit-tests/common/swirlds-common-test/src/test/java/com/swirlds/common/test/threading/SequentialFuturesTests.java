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

package com.swirlds.common.test.threading;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.AssertionUtils.completeBeforeTimeout;
import static com.swirlds.common.test.AssertionUtils.throwBeforeTimeout;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.futures.SequentialFutures;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.test.framework.TestQualifierTags;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("SequentialFutures Tests")
class SequentialFuturesTests {

    /**
     * Verify behavior when a value from a stale index is accessed.
     */
    private void verifyStaleIndex(final SequentialFutures<Long> sequence, final long indexToCheck)
            throws InterruptedException {

        throwBeforeTimeout(
                IllegalStateException.class,
                () -> sequence.get(indexToCheck),
                Duration.ofSeconds(1),
                "expected value to be stale (index=" + indexToCheck + ")");

        assertNull(
                completeBeforeTimeout(
                        () -> sequence.getIfAvailable(indexToCheck),
                        Duration.ofSeconds(1),
                        "expected operation to complete by now (index=" + indexToCheck + ")"),
                "expected value to be stale (index=" + indexToCheck + ")");

        throwBeforeTimeout(
                IllegalStateException.class,
                () -> {
                    try {
                        sequence.getValue(indexToCheck);
                    } catch (final ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofSeconds(1),
                "expected value to be stale (index=" + indexToCheck + ")");

        assertNull(
                completeBeforeTimeout(
                        () -> {
                            try {
                                return sequence.getValueIfAvailable(indexToCheck);
                            } catch (final ExecutionException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        Duration.ofSeconds(1),
                        "expected operation to complete by now (index=" + indexToCheck + ")"),
                "expected value to be stale (index=" + indexToCheck + ")");
    }

    /**
     * Verify behavior when a value from a cancelled index is accessed.
     */
    private void verifyCancelledIndex(final SequentialFutures<Long> sequence, final long indexToCheck)
            throws InterruptedException {

        throwBeforeTimeout(
                CancellationException.class,
                () -> {
                    try {
                        sequence.get(indexToCheck).get();
                    } catch (final ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofSeconds(1),
                "expected value to be cancelled (index=" + indexToCheck + ")");

        throwBeforeTimeout(
                CancellationException.class,
                () -> {
                    try {
                        sequence.getIfAvailable(indexToCheck).get();
                    } catch (final ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofSeconds(1),
                "expected value to be cancelled (index=" + indexToCheck + ")");

        throwBeforeTimeout(
                CancellationException.class,
                () -> {
                    try {
                        sequence.getValue(indexToCheck);
                    } catch (final ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofSeconds(1),
                "expected value to be cancelled (index=" + indexToCheck + ")");

        assertNull(
                completeBeforeTimeout(
                        () -> {
                            try {
                                return sequence.getValueIfAvailable(indexToCheck);
                            } catch (final ExecutionException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        Duration.ofSeconds(1),
                        "expected operation to complete by now (index=" + indexToCheck + ")"),
                "expected value to be cancelled (index=" + indexToCheck + ")");
    }

    /**
     * Verify behavior when a value from a completed index is accessed.
     */
    private void verifyCompletedIndex(
            final SequentialFutures<Long> sequence, final long indexToCheck, final long expectedValue)
            throws InterruptedException {

        assertEquals(
                expectedValue,
                completeBeforeTimeout(
                        () -> {
                            try {
                                return sequence.get(indexToCheck).get();
                            } catch (final ExecutionException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        Duration.ofSeconds(1),
                        "expected future to be ready (index=" + indexToCheck + ")"),
                "unexpected value (index=" + indexToCheck + ")");

        assertEquals(
                expectedValue,
                completeBeforeTimeout(
                        () -> {
                            try {
                                return sequence.getIfAvailable(indexToCheck).get();
                            } catch (final ExecutionException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        Duration.ofSeconds(1),
                        "expected future to be ready (index=" + indexToCheck + ")"),
                "unexpected value (index=" + indexToCheck + ")");

        assertEquals(
                expectedValue,
                completeBeforeTimeout(
                        () -> {
                            try {
                                return sequence.getValue(indexToCheck);
                            } catch (final ExecutionException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        Duration.ofSeconds(1),
                        "expected future to be ready (index=" + indexToCheck + ")"),
                "unexpected value (index=" + indexToCheck + ")");

        assertEquals(
                expectedValue,
                completeBeforeTimeout(
                        () -> {
                            try {
                                return sequence.getValueIfAvailable(indexToCheck);
                            } catch (final ExecutionException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        Duration.ofSeconds(1),
                        "expected future to be ready"),
                "unexpected value");
    }

    /**
     * Verify that a value eventually becomes completed, but only at the expected time.
     */
    private void verifyFutureCompletion(
            final SequentialFutures<Long> sequence,
            final long indexToCheck,
            final long expectedValue,
            final AtomicLong currentIndex,
            final AtomicInteger expectedOperations,
            final AtomicInteger operationsCompleted)
            throws InterruptedException {

        expectedOperations.getAndIncrement();

        final CountDownLatch latch = new CountDownLatch(1);

        new ThreadConfiguration(getStaticThreadManager())
                .setComponent("sequential-futures-test")
                .setThreadName("verify-eventual-completion-get()-" + indexToCheck)
                .setExceptionHandler((final Thread thread, final Throwable exception) -> {
                    System.out.println("problem while attempting to verify future completion of index " + indexToCheck);
                    exception.printStackTrace();
                })
                .setRunnable(() -> {
                    final Future<Long> future = sequence.get(indexToCheck);
                    latch.countDown();
                    final Long value;
                    try {
                        value = future.get();
                    } catch (final ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    assertEquals(value, expectedValue, "values do not match (index=" + indexToCheck + ")");
                    assertTrue(
                            currentIndex.get() >= indexToCheck,
                            "future completed too soon (index=" + indexToCheck + ")");
                    operationsCompleted.getAndIncrement();
                })
                .build(true);

        latch.await();
    }

    /**
     * Verify that a value eventually becomes cancelled, but only at the expected time.
     */
    private void verifyFutureCancellation(
            final SequentialFutures<Long> sequence,
            final long indexToCheck,
            final AtomicLong currentIndex,
            final AtomicInteger expectedOperations,
            final AtomicInteger operationsCompleted)
            throws InterruptedException {

        expectedOperations.getAndAdd(1);

        final CountDownLatch latch = new CountDownLatch(1);

        new ThreadConfiguration(getStaticThreadManager())
                .setComponent("sequential-futures-test")
                .setThreadName("verify-eventual-cancellation-get()-" + indexToCheck)
                .setExceptionHandler((final Thread thread, final Throwable exception) -> {
                    System.out.println("problem while attempting to verify future completion of index " + indexToCheck);
                    exception.printStackTrace();
                })
                .setRunnable(() -> {
                    final Future<Long> future = sequence.get(indexToCheck);
                    latch.countDown();
                    assertThrows(
                            CancellationException.class,
                            future::get,
                            "expected future to be cancelled (index=" + indexToCheck + ")");

                    assertTrue(
                            currentIndex.get() >= indexToCheck,
                            "future completed too soon (index=" + indexToCheck + ")");
                    operationsCompleted.getAndIncrement();
                })
                .build(true);

        latch.await();
    }

    @Test
    @DisplayName("Random Operations Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void randomOperationsTest() throws InterruptedException {

        final Random random = getRandomPrintSeed();

        final int numberOfValuesToTest = 1_000;
        final int numberOfValuesToStore = 10;
        final double gapProbability = 0.1;
        final double cancelProbability = 0.1;
        final int initialIndex = random.nextInt();

        final long lastIndexToComplete = initialIndex + numberOfValuesToTest - 1;

        // "Fast forward" over some indices in the middle. This is kind of similar to what
        // happens during a reconnect when a bunch of rounds get completely skipped.
        final long fastForwardStart = numberOfValuesToTest / 2;
        final long fastForwardEnd = fastForwardStart + numberOfValuesToTest / 20;

        // Due to the way this test is structured, if a value is skipped we must wait until a later
        // value is completed in order to validate gap handling for older values. If we skip the last value
        // or the value before the fast forward, we may never do gap handling like the background
        // validation threads expect us to do.
        final Set<Long> valuesRequiredToComplete = Set.of(fastForwardStart - 1, lastIndexToComplete);

        // Determine which indices will be gaps and which ones will be cancelled.
        final Set<Long> gaps = new HashSet<>();
        final Set<Long> cancelled = new HashSet<>();
        for (long nextIndex = initialIndex; nextIndex <= lastIndexToComplete; nextIndex++) {

            if (valuesRequiredToComplete.contains(nextIndex)) {
                continue;
            }

            if (nextIndex >= fastForwardStart && nextIndex < fastForwardEnd - numberOfValuesToStore) {
                // Special case: indices that got cancelled due to fast forward
                cancelled.add(nextIndex);
                continue;

            } else if (nextIndex >= fastForwardEnd - numberOfValuesToStore && nextIndex < fastForwardEnd) {
                // Special case: indices that got regenerated after the fast forward. These indices
                // should never be cancelled or skipped.
                continue;
            }

            // For all remaining cases, randomly skip and cancel a small fraction
            if (random.nextDouble() < gapProbability) {
                gaps.add(nextIndex);
            } else if (random.nextDouble() < cancelProbability) {
                cancelled.add(nextIndex);
            }
        }

        // Pre-compute values for each index.
        final Map<Long, Long> values = new HashMap<>();
        final long firstPrecomputedIndex = initialIndex - numberOfValuesToStore;
        for (long i = firstPrecomputedIndex; i <= lastIndexToComplete; i++) {
            values.put(i, random.nextLong());
        }

        // This tracks the index that was most recently inserted. Used to detect premature future completion.
        final AtomicLong currentIndex = new AtomicLong(initialIndex - 1);

        // This test kicks off a large number of background threads that wait on futures in the background.
        // If all of those background threads complete successfully then these two values will be equal at the
        // end of the test.
        final AtomicInteger expectedOperations = new AtomicInteger();
        final AtomicInteger operationsCompleted = new AtomicInteger();

        final SequentialFutures<Long> sequence = new SequentialFutures<>(
                initialIndex,
                numberOfValuesToStore,
                values::get,
                (final long index, final StandardFuture<Long> future, final Long previousValue) -> {

                    // Find the first non-cancelled value that came previously
                    boolean foundPreviousValue = false;
                    for (long previousIndex = index - 1; previousIndex >= firstPrecomputedIndex; previousIndex--) {
                        if (!cancelled.contains(previousIndex)) {
                            assertEquals(
                                    values.get(previousIndex),
                                    previousValue,
                                    "previous value does not match expected (index=" + index + ")");
                            foundPreviousValue = true;
                            break;
                        }
                    }
                    assertTrue(foundPreviousValue, "could not find previous index for " + index);

                    future.complete(values.get(index));
                });

        // Iterate from first to last index, completing/skipping/cancelling each index along the way
        for (long nextIndex = initialIndex; nextIndex <= lastIndexToComplete; nextIndex++) {

            if (gaps.contains(nextIndex)) {
                continue;
            }

            if (nextIndex == fastForwardStart) {
                // Simulate a fast-forwarding of indices.
                nextIndex = fastForwardEnd;
                currentIndex.set(fastForwardEnd);
                sequence.fastForwardIndex(fastForwardEnd, values::get);
            } else {
                currentIndex.set(nextIndex);
            }

            if (cancelled.contains(nextIndex)) {
                sequence.cancel(nextIndex);
            } else {
                final long value = values.get(nextIndex);
                sequence.complete(nextIndex, value);
            }

            final long firstStaleIndexToCheck = nextIndex - numberOfValuesToStore * 2;
            final long firstCompletedIndexToCheck = nextIndex - numberOfValuesToStore + 1;
            final long firstUncompletedIndex = nextIndex + 1;

            // Check values so far in the past that they have expired.
            for (long i = firstStaleIndexToCheck; i < firstCompletedIndexToCheck; i++) {
                verifyStaleIndex(sequence, i);
            }

            // Check old values that have not yet expired
            for (long i = firstCompletedIndexToCheck; i < firstUncompletedIndex; i++) {
                if (cancelled.contains(i)) {
                    verifyCancelledIndex(sequence, i);
                } else {
                    verifyCompletedIndex(sequence, i, values.get(i));
                }
            }

            if (firstUncompletedIndex < lastIndexToComplete) {
                // Spawn a thread that will verify a random future (as long as this is not the last index).
                // May create a small number of duplicate verifiers. I'm too lazy to fix it, so let's go with
                // "it's a feature not a bug", as it will test what happens when multiple thread wait on the
                // same value... and that's actually an important use case.
                final long indexToVerify = random.nextLong(
                        firstUncompletedIndex,
                        Math.min(initialIndex + numberOfValuesToTest, nextIndex + numberOfValuesToTest / 10));

                if (cancelled.contains(indexToVerify)) {
                    verifyFutureCancellation(
                            sequence, indexToVerify, currentIndex, expectedOperations, operationsCompleted);
                } else {
                    verifyFutureCompletion(
                            sequence,
                            indexToVerify,
                            values.get(indexToVerify),
                            currentIndex,
                            expectedOperations,
                            operationsCompleted);
                }
            }
        }

        assertEventuallyEquals(
                expectedOperations.get(),
                operationsCompleted::get,
                Duration.ofSeconds(1),
                "all futures did not complete on time");
    }

    @Test
    @DisplayName("Initial Value Test")
    void initialValueTest() throws InterruptedException {
        final SequentialFutures<Long> sequence = new SequentialFutures<>(0, 10, i -> i, null);

        verifyStaleIndex(sequence, -11);

        for (long i = -10; i < 0; i++) {
            verifyCompletedIndex(sequence, i, i);
        }
    }

    @Test
    @DisplayName("Fast Forward Test")
    @Tag(TIME_CONSUMING)
    void fastForwardTest() throws InterruptedException {
        final SequentialFutures<Long> sequence = new SequentialFutures<>(0, 10, i -> i, null);

        // Add some initial values
        for (long i = 0; i < 100; i++) {
            sequence.complete(i, i);
        }

        sequence.fastForwardIndex(200, i -> i);

        // Make sure old values are not around any more
        for (long i = -11; i < 189; i++) {
            verifyStaleIndex(sequence, i);
        }

        // Check initial values after the fast forward
        for (long i = 190; i < 199; i++) {
            verifyCompletedIndex(sequence, i, i);
        }

        // Get futures on the next 10 values and verify as they become completed
        final AtomicLong currentIndex = new AtomicLong(199);
        final AtomicInteger expectedOperations = new AtomicInteger();
        final AtomicInteger operationsCompleted = new AtomicInteger();
        for (long i = 200; i < 210; i++) {
            verifyFutureCompletion(sequence, i, i, currentIndex, expectedOperations, operationsCompleted);
        }
        for (long i = 200; i < 210; i++) {
            currentIndex.set(i);
            sequence.complete(i, i);
        }

        assertEventuallyEquals(
                expectedOperations.get(),
                operationsCompleted::get,
                Duration.ofSeconds(1),
                "all futures did not complete on time");
    }

    @Test
    @DisplayName("Expiration Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void expirationTest() throws InterruptedException {
        final SequentialFutures<Long> sequence = new SequentialFutures<>(0, 10, i -> i, null);

        for (long i = 0; i < 100; i++) {
            sequence.complete(i, i);
            long youngestExpired = i - 10;

            verifyStaleIndex(sequence, youngestExpired);
            for (long j = youngestExpired + 1; j < i; j++) {
                verifyCompletedIndex(sequence, i, i);
            }
        }
    }

    @Test
    @DisplayName("Gap Test")
    void gapTest() throws InterruptedException {

        final ValueReference<Long> nextExpectedGap = new ValueReference<>(1L);

        final SequentialFutures<Long> sequence = new SequentialFutures<>(
                0, 10, i -> i, (final long index, final StandardFuture<Long> future, final Long previousValue) -> {
                    assertEquals(nextExpectedGap.getValue(), index, "unexpected gap");
                    future.complete(index);
                    assertEquals(index - 1, previousValue, "previous value does not match expected");
                    nextExpectedGap.setValue(index + 2);
                });

        final AtomicLong currentIndex = new AtomicLong();
        final AtomicInteger expectedOperations = new AtomicInteger();
        final AtomicInteger operationsCompleted = new AtomicInteger();

        for (long i = 0; i < 101; i++) {
            if (i % 2 == 0) {
                currentIndex.set(i);
                sequence.complete(i, i);
                verifyCompletedIndex(sequence, i, i);
            } else {
                verifyFutureCompletion(sequence, i, i, currentIndex, expectedOperations, operationsCompleted);
            }
        }

        assertEventuallyEquals(
                expectedOperations.get(),
                operationsCompleted::get,
                Duration.ofSeconds(1),
                "all futures did not complete on time");
    }
}
