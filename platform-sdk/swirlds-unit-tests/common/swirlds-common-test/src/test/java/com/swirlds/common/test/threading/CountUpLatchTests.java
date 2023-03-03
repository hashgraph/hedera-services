package com.swirlds.common.test.threading;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.threading.CountUpLatch;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CountUpLatch Tests")
class CountUpLatchTests {

    @Test
    @DisplayName("Track Count Test")
    void trackCountTest() {
        final Random random = getRandomPrintSeed();

        final int start = random.nextInt();
        final CountUpLatch latch = new CountUpLatch(start);

        long sum = start;
        for (int i = 0; i < 100; i++) {
            latch.increment();
            sum++;

            final int amount = random.nextInt(100);
            latch.add(amount);
            sum += amount;

            assertEquals(sum, latch.getCount());
        }
    }

    @Test
    @DisplayName("Set Test")
    void setTest() {
        final Random random = getRandomPrintSeed();

        long count = Long.MIN_VALUE;
        final CountUpLatch latch = new CountUpLatch(count);

        for (int i = 0; i < 100; i++) {
            final long proposedCount = random.nextLong();

            if (proposedCount >= count) {
                latch.set(proposedCount);
                assertEquals(proposedCount, latch.getCount());
                count = proposedCount;
            } else {
                assertThrows(IllegalArgumentException.class, () -> latch.set(proposedCount));
            }
        }
    }

    // TODO test that is more likely to increment over the finish line

    private record MinMaxCount(long minCount, long maxCount) {

    }

    protected static Stream<Arguments> buildArguments() {
        final List<Arguments> arguments = new ArrayList<>();
        arguments.add(Arguments.of(new MinMaxCount(Long.MIN_VALUE / 4, Long.MAX_VALUE / 4)));
        arguments.add(Arguments.of(new MinMaxCount(0, Long.MAX_VALUE)));
        arguments.add(Arguments.of(new MinMaxCount(Long.MIN_VALUE + 1, 0)));
        arguments.add(Arguments.of(new MinMaxCount(-10_000, 10_000)));
        arguments.add(Arguments.of(new MinMaxCount(-10_000, 0)));
        arguments.add(Arguments.of(new MinMaxCount(0, 10_000)));
        return arguments.stream();
    }


    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Increment On Creating Thread Test")
    void incrementOnCreatingThreadTest(final MinMaxCount minMaxCount) throws InterruptedException {
        final Random random = getRandomPrintSeed();

        final long minCount = minMaxCount.minCount();
        final long maxCount = minMaxCount.maxCount();

        final int threadCount = 10;
        final int waitsPerThread = 100;
        final long maxIncrement = (maxCount - minCount) / 1000;

        final AtomicBoolean error = new AtomicBoolean(false);
        final CountDownLatch finishedLatch = new CountDownLatch(threadCount);

        final CountUpLatch latch = new CountUpLatch(minCount);

        // Create a bunch of threads that will wait for random counts
        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            final Random threadRandom = new Random(random.nextLong());
            new ThreadConfiguration(getStaticThreadManager())
                    .setThreadName("testThread-" + threadIndex)
                    .setInterruptableRunnable(() -> {
                        long desiredCount = minCount;
                        for (int iteration = 0; iteration < waitsPerThread; iteration++) {
                            desiredCount = threadRandom.nextLong(desiredCount, maxCount);

                            latch.await(desiredCount);
                            if (latch.getCount() < desiredCount) {
                                error.set(true);
                                break;
                            }

                        }
                        finishedLatch.countDown();
                    })
                    .build(true);
        }

        // Increment the count a little at a time
        while (latch.getCount() < maxCount - maxIncrement) {
            if (random.nextBoolean()) {
                latch.increment();
            } else {
                latch.add(random.nextLong(maxIncrement));
            }
        }

        latch.set(maxCount);

        assertTrue(finishedLatch.await(1, TimeUnit.SECONDS));
        assertFalse(error.get());
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Increment On Another Thread Test")
    void incrementOnAnotherThreadTest(final MinMaxCount minMaxCount) throws InterruptedException {
        final Random random = getRandomPrintSeed();

        final long minCount = minMaxCount.minCount();
        final long maxCount = minMaxCount.maxCount();

        final int threadCount = 10;
        final int waitsPerThread = 100;
        final long maxIncrement = (maxCount - minCount) / 1000;

        final AtomicBoolean error = new AtomicBoolean(false);
        final CountDownLatch finishedLatch = new CountDownLatch(threadCount);

        final CountUpLatch latch = new CountUpLatch(minCount);

        // Create a bunch of threads that will wait for random counts
        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            final Random threadRandom = new Random(random.nextLong());
            new ThreadConfiguration(getStaticThreadManager())
                    .setThreadName("testThread-" + threadIndex)
                    .setInterruptableRunnable(() -> {
                        long desiredCount = minCount;
                        for (int iteration = 0; iteration < waitsPerThread; iteration++) {
                            desiredCount = threadRandom.nextLong(desiredCount, maxCount);

                            if (threadRandom.nextBoolean()) {
                                latch.await(desiredCount);
                            } else {
                                // Wait for a very long time, much longer than should be needed for this test
                                if (!latch.await(desiredCount, Duration.ofMinutes(1))) {
                                    error.set(true);
                                    break;
                                }
                            }

                            if (latch.getCount() < desiredCount) {
                                error.set(true);
                                break;
                            }

                        }
                        finishedLatch.countDown();
                    })
                    .build(true);
        }

        final CountDownLatch incrementLatch = new CountDownLatch(1);

        new ThreadConfiguration(getStaticThreadManager())
                .setThreadName("incrementThread")
                .setRunnable(() -> {
                    // Increment the count a little at a time
                    while (latch.getCount() < maxCount - maxIncrement) {
                        if (random.nextBoolean()) {
                            latch.increment();
                        } else {
                            latch.add(random.nextLong(maxIncrement));
                        }
                    }

                    latch.set(maxCount);
                    incrementLatch.countDown();
                })
                .build(true);

        incrementLatch.await();

        assertTrue(finishedLatch.await(1, TimeUnit.SECONDS));
        assertFalse(error.get());
    }

    // TODO multiple incrementers

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Successful Await With Timeout Test")
    void successfulAwaitWithTimeoutTest(final boolean artificialPauses) throws InterruptedException {

        final FakeTime fakeTime = new FakeTime();
        final CountUpLatch latch = new CountUpLatch(0, fakeTime);

        final CountDownLatch finishedLatch = new CountDownLatch(1);
        final AtomicBoolean error = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setThreadName("test")
                .setInterruptableRunnable(() -> {
                    final boolean success = latch.await(100, Duration.ofMinutes(1));

                    if (latch.getCount() != 100 || !success) {
                        error.set(true);
                    }

                    finishedLatch.countDown();
                })
                .build(true);

        // Simulate the count being increased once per second for 50 seconds
        for (int i = 0; i < 50; i++) {
            latch.increment();
            fakeTime.tick(Duration.ofSeconds(1));

            if (artificialPauses) {
                // Sleep some real world time to allow the background thread to do bad stuff it wants to.
                // Not required for the test to pass.
                MILLISECONDS.sleep(1);
            }
        }

        assertEquals(1, finishedLatch.getCount());

        latch.set(100);

        assertTrue(finishedLatch.await(1, TimeUnit.SECONDS));
        assertFalse(error.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Unsuccessful Await With Timeout Test")
    void unsuccessfulAwaitWithTimeoutTest(final boolean artificialPauses) throws InterruptedException {

        final FakeTime fakeTime = new FakeTime();
        final CountUpLatch latch = new CountUpLatch(0, fakeTime);

        final CountDownLatch finishedLatch = new CountDownLatch(1);
        final AtomicBoolean error = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setThreadName("test")
                .setInterruptableRunnable(() -> {
                    final boolean success = latch.await(100, Duration.ofMinutes(1));

                    if (success) {
                        error.set(true);
                    }

                    finishedLatch.countDown();
                })
                .build(true);

        // Simulate the count being increased once per second for 70 seconds
        for (int i = 0; i < 70; i++) {
            latch.increment();
            fakeTime.tick(Duration.ofSeconds(1));

            if (artificialPauses) {
                // Sleep some real world time to allow the background thread to do bad stuff it wants to.
                // Not required for the test to pass.
                MILLISECONDS.sleep(1);
            }
        }

        assertTrue(finishedLatch.await(1, TimeUnit.SECONDS));
        assertFalse(error.get());
    }
}
