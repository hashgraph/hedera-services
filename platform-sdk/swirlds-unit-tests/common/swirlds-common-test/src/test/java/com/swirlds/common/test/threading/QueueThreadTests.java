/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyFalse;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultIntegerAccumulator;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.framework.ThreadSeed;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Queue Thread Tests")
class QueueThreadTests {

    static final String THREAD_NAME = "myThread";
    static final String MAX_SIZE_METRIC_NAME = THREAD_NAME + "-queueMaxSize";
    static final String MIN_SIZE_METRIC_NAME = THREAD_NAME + "-queueMinSize";

    private static Stream<Arguments> queueTypes() {
        return Stream.of(
                Arguments.of(new PriorityBlockingQueue<Integer>()),
                Arguments.of(new LinkedBlockingQueue<Integer>()),
                Arguments.of(new LinkedBlockingDeque<Integer>()),
                Arguments.of(new LinkedTransferQueue<Integer>()));
    }

    private Metrics metrics;

    @BeforeEach
    void setUp() {
        final MetricKeyRegistry registry = new MetricKeyRegistry();
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final MetricsFactory factory = new DefaultMetricsFactory();
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        metrics = new DefaultMetrics(null, registry, executor, factory, metricsConfig);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Queue Capacity Test")
    void queueCapacityTest() throws InterruptedException {

        final int capacity = 10;

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setCapacity(capacity)
                .setHandler((item) -> {})
                .build();

        // Should be able to fill to capacity
        for (int index = 0; index < capacity; index++) {
            assertTrue(qt.offer(index), "should have been able to add");
        }

        // Should not be able to add one more
        assertFalse(qt.offer(1234), "queue should be full");
        assertThrows(IllegalStateException.class, () -> qt.add(1234), "expected add operation to fail");

        // Starting the queue thread should allow more items to be added
        qt.start();
        assertTrue(qt.offer(1234, 100, MILLISECONDS), "expected item to eventually be added");

        qt.stop();
        qt.join(100);
        assertFalse(qt.isAlive(), "expected thread to be dead");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Items Are Handled Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void itemsAreHandledTest() throws InterruptedException {

        final AtomicInteger nextExpectedValue = new AtomicInteger(0);
        final AtomicBoolean exception = new AtomicBoolean(false);

        final Thread.UncaughtExceptionHandler exceptionHandler = (t, e) -> {
            e.printStackTrace();
            exception.set(true);
        };

        final InterruptableConsumer<Integer> handler = (Integer value) -> {
            assertEquals(nextExpectedValue.get(), value, "actual value does not match expected value");
            nextExpectedValue.getAndIncrement();
            // Force the handling thread to be slower than the thread adding things to the queue
            Thread.sleep(1);
        };

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setExceptionHandler(exceptionHandler)
                .setCapacity(10)
                .setHandler(handler)
                .build();

        qt.start();

        for (int i = 0; i < 1000; i++) {
            qt.put(i);
        }

        qt.stop();
        assertFalse(qt.isAlive(), "thread should be dead");
        assertFalse(exception.get(), "there should have been no exceptions");
        assertEquals(1000, nextExpectedValue.get(), "expected value is not correct");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Close Handles All Remaining Items Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void closeHandlesAllRemainingItemsTest() throws InterruptedException {
        final AtomicInteger nextExpectedValue = new AtomicInteger(0);
        final AtomicBoolean exception = new AtomicBoolean(false);
        final Semaphore lock = new Semaphore(1, true);

        final Thread.UncaughtExceptionHandler exceptionHandler = (t, e) -> {
            e.printStackTrace();
            exception.set(true);
        };

        final InterruptableConsumer<Integer> handler = (Integer value) -> {
            lock.acquire();
            assertEquals(nextExpectedValue.get(), value, "actual value does not match expected value");
            nextExpectedValue.getAndIncrement();
            lock.release();
            // Force the handling thread to be slower than the thread adding things to the queue
            Thread.sleep(1);
        };

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setExceptionHandler(exceptionHandler)
                .setCapacity(1000)
                .setHandler(handler)
                .build();

        qt.start();

        for (int i = 0; i < 500; i++) {
            qt.put(i);
        }

        // Cause the handle thread to be blocked
        lock.acquire();

        for (int i = 500; i < 1000; i++) {
            qt.put(i);
        }

        // Close the thread before it finishes handling everything
        final Thread reaperThread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(qt::stop)
                .build();
        reaperThread.start();

        qt.join(100);
        reaperThread.join(100);
        assertTrue(qt.isAlive(), "thread should still be alive");
        assertTrue(reaperThread.isAlive(), "thread should still be alive");

        // Unblock the queue thread
        lock.release();

        qt.join(1000);
        reaperThread.join(2000);
        assertFalse(qt.isAlive(), "thread should be dead");
        assertFalse(reaperThread.isAlive(), "thread should be dead");

        assertEquals(1000, nextExpectedValue.get(), "expected value is not correct");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @Tag(TIME_CONSUMING)
    @DisplayName("Clear After Interrupt Test")
    void clearTestAfterInterrupt() throws InterruptedException {
        final InterruptableConsumer<Integer> handler = (final Integer item) -> {
            MILLISECONDS.sleep(10_000);
        };
        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setHandler(handler)
                .setMaxBufferSize(1)
                .setStopBehavior(Stoppable.StopBehavior.INTERRUPTABLE)
                .build();

        qt.put(1);
        qt.put(2);
        qt.start();

        MILLISECONDS.sleep(100);

        qt.stop();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Void> future = executorService.submit(() -> {
            qt.clear();
            return null;
        });

        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            fail("clear() hung on stopped thread queue.");
        }
        assertEquals(0, qt.size());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Clear Test")
    void clearTest() throws InterruptedException {

        final AtomicInteger handledValue = new AtomicInteger(-1);

        final InterruptableConsumer<Integer> handler = (final Integer item) -> {
            Thread.sleep(5);
            handledValue.set(item);
        };

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setMaxBufferSize(10)
                .setCapacity(1000)
                .setHandler(handler)
                .build();

        // fill up the queue
        for (int i = 0; i < 1000; i++) {
            qt.put(i);
        }

        // start handling
        qt.start();
        assertEventuallyTrue(
                () -> handledValue.get() > -1, Duration.ofSeconds(1), "expected values to have been handled");

        // clear the queue, high probability that there significant work in the buffer,
        // and much more than 100ms still in the queue.
        // After clear, there should be no work still being done.
        qt.clear();
        assertEquals(0, qt.size(), "queue should be empty");

        final int secondReading = handledValue.get();
        Thread.sleep(10);
        final int thirdReading = handledValue.get();

        assertEquals(secondReading, thirdReading, "there should be no work still being handled");
        assertTrue(thirdReading < 999, "there shouldn't have been enough time to handle all items");

        // Adding more work to the queue should cause it to be handled
        for (int i = 0; i < 5; i++) {
            qt.put(i * 1000);
        }

        assertEventuallyTrue(() -> handledValue.get() > 999, Duration.ofSeconds(1), "new items should be handled");

        qt.stop();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("UnlimitedCapacityTest Test")
    void unlimitedCapacityTest() throws InterruptedException {

        final QueueThread<Integer> queueThread = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setUnlimitedCapacity()
                .setHandler((i) -> {})
                .build();

        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(() -> {
                    for (int i = 0; i < 1_000; i++) {
                        queueThread.add(i);
                    }
                })
                .build();
        thread.start();

        thread.join(1000);
        assertFalse(thread.isAlive(), "thread should have finished by now");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("InterruptableTest")
    void interruptableTest() throws InterruptedException {
        final Semaphore lock = new Semaphore(1);
        final InterruptableConsumer<Integer> handler = (value) -> lock.acquire();

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setHandler(handler)
                .setStopBehavior(Stoppable.StopBehavior.INTERRUPTABLE)
                .build();

        // force the queue thread to wait until this is released
        lock.acquire();

        // thread will wait trying to acquire the lock
        qt.start();
        qt.add(1);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Void> future = executorService.submit(() -> {
            qt.stop();
            return null;
        });

        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            fail("QueueThread was configured to be interruptable but could not be interrupted.");
        }

        assertFalse(qt.isAlive(), "The queue thread should not be alive after being stopped.");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("UninterruptableTest")
    void uninterruptableTest() throws InterruptedException {
        final Semaphore lock = new Semaphore(1);
        final InterruptableConsumer<Integer> handler = (value) -> lock.acquire();

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setHandler(handler)
                .build();

        // force the queue thread to wait until this is released
        lock.acquire();

        // thread will wait trying to acquire the lock
        qt.start();
        qt.add(1);

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Void> future = executorService.submit(() -> {
            qt.stop();
            return null;
        });

        assertThrows(TimeoutException.class, () -> future.get(100, MILLISECONDS), "Thread shouldn't be interruptable");

        // Thread finally closes with manual interrupt
        qt.interrupt();

        assertEventuallyFalse(
                qt::isAlive, Duration.ofSeconds(1), "The queue thread should not be alive after being stopped.");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Blocking Stop Override Test")
    void blockingStopOverrideTest() throws InterruptedException {
        final Semaphore lock = new Semaphore(1);
        final InterruptableConsumer<Integer> handler = (value) -> lock.acquire();

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setHandler(handler)
                .setStopBehavior(Stoppable.StopBehavior.INTERRUPTABLE)
                .build();

        // force the queue thread to wait until this is released
        lock.acquire();

        // thread will wait trying to acquire the lock
        qt.start();
        qt.add(1);

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Void> future = executorService.submit(() -> {
            // Stop with blocking behavior instead of default interruptable behavior
            qt.stop(Stoppable.StopBehavior.BLOCKING);
            return null;
        });

        assertThrows(TimeoutException.class, () -> future.get(100, MILLISECONDS), "Thread should be blocking");

        // Thread finally closes with manual interrupt
        qt.interrupt();

        assertEventuallyFalse(
                qt::isAlive, Duration.ofSeconds(1), "The queue thread should not be alive after being stopped.");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Interruptable Stop Override Test")
    void interruptableStopOverrideTest() throws InterruptedException, ExecutionException, TimeoutException {
        final Semaphore lock = new Semaphore(1);
        final InterruptableConsumer<Integer> handler = (value) -> lock.acquire();

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setHandler(handler)
                .build();

        // force the queue thread to wait until this is released
        lock.acquire();

        // thread will wait trying to acquire the lock
        qt.start();
        qt.add(1);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Void> future = executorService.submit(() -> {
            // Stop with interruptable behavior instead of default blocking behavior
            qt.stop(Stoppable.StopBehavior.INTERRUPTABLE);
            return null;
        });

        future.get(100, MILLISECONDS);

        assertFalse(qt.isAlive(), "The queue thread should not be alive after being stopped.");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("WaitForItemRunnableTest")
    void waitForItemRunnableTest() throws InterruptedException {
        final AtomicInteger waitForItemCount = new AtomicInteger(0);

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setUnlimitedCapacity()
                .setHandler((value) -> {})
                .setWaitForItemRunnable(waitForItemCount::incrementAndGet)
                .build();

        qt.start();

        MILLISECONDS.sleep(100);

        qt.stop();

        assertTrue(waitForItemCount.get() > 0, "The waitForItemRunnable should have been invoked at least once.");
    }

    @ParameterizedTest
    @MethodSource("queueTypes")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("QueueTest")
    void queueTest(final BlockingQueue<Integer> queue) throws InterruptedException {

        Queue<Integer> handledInts = new LinkedList<>();

        final QueueThread<Integer> qt = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setQueue(queue)
                .setHandler(handledInts::add)
                .build();

        qt.start();

        IntStream.range(0, 100).boxed().forEach(qt::add);
        MILLISECONDS.sleep(50);

        qt.stop();

        assertEquals(100, handledInts.size());
    }

    @Test
    @DisplayName("Seed Test")
    void seedTest() throws InterruptedException {
        final AtomicLong count = new AtomicLong();
        final AtomicBoolean enableLongSleep = new AtomicBoolean();
        final CountDownLatch longSleepStarted = new CountDownLatch(1);

        final QueueThread<Integer> queueThread = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName("queue-thread")
                .setUnlimitedCapacity()
                .setStopBehavior(Stoppable.StopBehavior.INTERRUPTABLE)
                .setHandler((final Integer next) -> {
                    count.set(next);
                    if (enableLongSleep.get()) {
                        longSleepStarted.countDown();
                        SECONDS.sleep(999999999);
                    }
                })
                .build();

        final ThreadSeed seed = queueThread.buildSeed();

        final AtomicBoolean seedHasYieldedControl = new AtomicBoolean();
        final CountDownLatch exitLatch = new CountDownLatch(1);

        // This thread will have the seed injected into it.
        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setThreadName("inject-into-this-thread")
                .setInterruptableRunnable(() -> {
                    // The seed will take over this thread for a while

                    seed.inject();

                    seedHasYieldedControl.set(true);
                    exitLatch.await();
                })
                .build(true);

        assertEventuallyTrue(
                () -> thread.getName().equals("<queue-thread>"),
                Duration.ofSeconds(1),
                "queue thread should eventually take over");

        for (int i = 0; i < 1001; i++) {
            queueThread.add(i);
        }

        assertEventuallyTrue(
                () -> count.get() >= 1_000, Duration.ofSeconds(1), "count should have increased more by now");

        enableLongSleep.set(true);
        for (int i = 1001; i < 2001; i++) {
            queueThread.add(i);
        }
        longSleepStarted.await();

        queueThread.stop();

        assertEventuallyTrue(seedHasYieldedControl::get, Duration.ofSeconds(1), "seed should have yielded");
        assertEquals("<inject-into-this-thread>", thread.getName(), "original settings should have been restored");

        exitLatch.countDown();
    }

    @Test
    @DisplayName("Configuration Mutability Test")
    void configurationMutabilityTest() {
        // Build should make the configuration immutable
        final QueueThreadConfiguration<Integer> configuration = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setHandler((final Integer element) -> {});

        assertTrue(configuration.isMutable(), "configuration should be mutable");

        configuration.build();
        assertTrue(configuration.isImmutable(), "configuration should be immutable");

        assertThrows(
                MutabilityException.class, () -> configuration.setCapacity(1234), "configuration should be immutable");
        assertThrows(
                MutabilityException.class, configuration::setUnlimitedCapacity, "configuration should be immutable");
        assertThrows(
                MutabilityException.class, () -> configuration.setHandler(null), "configuration should be immutable");
        assertThrows(
                MutabilityException.class,
                () -> configuration.setWaitForItemRunnable(null),
                "configuration should be immutable");
        assertThrows(
                MutabilityException.class, () -> configuration.setQueue(null), "configuration should be immutable");
    }

    @Test
    @DisplayName("Single Use Per Config Test")
    void singleUsePerConfigTest() {

        // build() should cause future calls to build() to fail, and start() should cause buildSeed() to fail.
        final QueueThreadConfiguration<?> configuration0 = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setHandler((final Integer i) -> {
                    MILLISECONDS.sleep(1);
                });

        final QueueThread<?> queueThread0 = configuration0.build();

        assertThrows(MutabilityException.class, configuration0::build, "configuration has already been used");

        queueThread0.start();

        assertThrows(IllegalStateException.class, queueThread0::buildSeed, "configuration has already been used");

        queueThread0.stop();

        // buildSeed() should cause future calls to buildSeed() and start() to fail.
        final QueueThreadConfiguration<?> configuration1 = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setHandler((final Integer i) -> {
                    MILLISECONDS.sleep(1);
                });

        final QueueThread<?> queueThread1 = configuration1.build();
        queueThread1.buildSeed();

        assertThrows(IllegalStateException.class, queueThread1::buildSeed, "configuration has already been used");
        assertThrows(IllegalStateException.class, queueThread1::start, "configuration has already been used");
    }

    @Test
    @DisplayName("Copy Test")
    void copyTest() {
        final InterruptableConsumer<Integer> handler = (final Integer x) -> {};

        final InterruptableRunnable waitForItem = () -> {};

        final QueueThreadConfiguration<?> configuration = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setCapacity(1234)
                .setMaxBufferSize(1234)
                .setHandler(handler)
                .setWaitForItemRunnable(waitForItem)
                .setQueue(new LinkedBlockingDeque<>());

        final QueueThreadConfiguration<?> copy1 = configuration.copy();

        assertEquals(configuration.getCapacity(), copy1.getCapacity(), "copy configuration should match");
        assertEquals(configuration.getMaxBufferSize(), copy1.getMaxBufferSize(), "copy configuration should match");
        assertSame(configuration.getHandler(), copy1.getHandler(), "copy configuration should match");
        assertSame(
                configuration.getWaitForItemRunnable(),
                copy1.getWaitForItemRunnable(),
                "copy configuration should match");
        assertSame(configuration.getQueue(), copy1.getQueue(), "copy configuration should match");

        // It shouldn't matter if the original is immutable.
        configuration.build();

        final QueueThreadConfiguration<?> copy2 = configuration.copy();
        assertTrue(copy2.isMutable(), "copy should be mutable");

        assertEquals(configuration.getCapacity(), copy2.getCapacity(), "copy configuration should match");
        assertEquals(configuration.getMaxBufferSize(), copy2.getMaxBufferSize(), "copy configuration should match");
        assertSame(configuration.getHandler(), copy2.getHandler(), "copy configuration should match");
        assertSame(
                configuration.getWaitForItemRunnable(),
                copy2.getWaitForItemRunnable(),
                "copy configuration should match");
        assertSame(configuration.getQueue(), copy2.getQueue(), "copy configuration should match");
    }

    @Test
    @DisplayName("Queue Max/Min Size Metrics Test - With Thread Start")
    void testQueueMaxMinSizeMetricsWithThreadStart() throws InterruptedException {
        // given
        final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(List.of(0, 1, 2, 3, 4));
        final Queue<Integer> handler = new LinkedList<>();

        // when
        final QueueThread<Integer> queueThread = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setQueue(queue)
                .setHandler(handler::add)
                .enableMaxSizeMetric(metrics)
                .enableMinSizeMetric(metrics)
                .build();

        final DefaultIntegerAccumulator maxSizeMetric =
                (DefaultIntegerAccumulator) metrics.getMetric(INTERNAL_CATEGORY, MAX_SIZE_METRIC_NAME);
        final DefaultIntegerAccumulator minSizeMetric =
                (DefaultIntegerAccumulator) metrics.getMetric(INTERNAL_CATEGORY, MIN_SIZE_METRIC_NAME);

        // then
        assertThat(queueThread).hasSize(5);
        assertThat(maxSizeMetric).isNotNull();
        assertThat(maxSizeMetric.get()).isEqualTo(5);
        assertThat(minSizeMetric).isNotNull();
        assertThat(minSizeMetric.get()).isEqualTo(5);

        // when
        queueThread.start();
        IntStream.range(0, 100).boxed().forEach(queueThread::add);
        MILLISECONDS.sleep(50);

        // then
        assertThat(maxSizeMetric.get()).isPositive().isLessThanOrEqualTo(105);
        assertThat(minSizeMetric.get()).isZero();
        assertThat(handler).hasSize(105);

        // when
        IntStream.range(0, 100).boxed().forEach(queueThread::add);
        MILLISECONDS.sleep(50);
        queueThread.stop();

        // then
        assertThat(maxSizeMetric.get()).isPositive().isLessThanOrEqualTo(100);
        assertThat(minSizeMetric.get()).isZero();
        assertThat(handler).hasSize(205);
    }

    @Test
    @DisplayName("Queue Max/Min Size Metrics Test - Without Thread Start")
    void testQueueMaxMinSizeMetricsWithoutThreadStart() {
        // given
        final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(List.of(0, 1, 2, 3, 4));
        final Queue<Integer> handler = new LinkedList<>();

        // when
        final QueueThread<Integer> queueThread = getStaticThreadManager()
                .newQueueThreadConfiguration(Integer.class)
                .setThreadName(THREAD_NAME)
                .setQueue(queue)
                .setHandler(handler::add)
                .enableMaxSizeMetric(metrics)
                .enableMinSizeMetric(metrics)
                .build();

        final DefaultIntegerAccumulator maxSizeMetric =
                (DefaultIntegerAccumulator) metrics.getMetric(INTERNAL_CATEGORY, MAX_SIZE_METRIC_NAME);
        final DefaultIntegerAccumulator minSizeMetric =
                (DefaultIntegerAccumulator) metrics.getMetric(INTERNAL_CATEGORY, MIN_SIZE_METRIC_NAME);

        // then
        assertThat(queueThread).hasSize(5);
        assertThat(maxSizeMetric).isNotNull();
        assertThat(maxSizeMetric.get()).isEqualTo(5);
        assertThat(minSizeMetric).isNotNull();
        assertThat(maxSizeMetric.get()).isEqualTo(5);

        // when - add
        IntStream.range(5, 100).boxed().forEach(queueThread::add);

        // then
        assertThat(queueThread).hasSize(100);
        assertThat(maxSizeMetric.get()).isEqualTo(100);
        assertThat(minSizeMetric.get()).isEqualTo(5);

        // when - remove
        IntStream.range(0, 50).boxed().forEach(queueThread::remove);

        // then
        assertThat(queueThread).hasSize(50);
        assertThat(maxSizeMetric.get()).isEqualTo(100);
        assertThat(minSizeMetric.get()).isEqualTo(5);

        // when - addAll
        queueThread.addAll(List.of(0, 1, 2, 3, 4));

        // then
        assertThat(queueThread).hasSize(55);
        assertThat(maxSizeMetric.get()).isEqualTo(100);
        assertThat(minSizeMetric.get()).isEqualTo(5);

        // when - removeAll
        queueThread.removeAll(List.of(0, 1, 2, 3, 4));

        // then
        assertThat(queueThread).hasSize(50);
        assertThat(maxSizeMetric.get()).isEqualTo(100);
        assertThat(minSizeMetric.get()).isEqualTo(5);

        // when - snapshot
        maxSizeMetric.takeSnapshot();
        minSizeMetric.takeSnapshot();

        // then
        assertThat(queueThread).hasSize(50);
        assertThat(maxSizeMetric.get()).isEqualTo(50);
        assertThat(minSizeMetric.get()).isEqualTo(50);

        // when - offer
        IntStream.range(0, 10).boxed().forEach(queueThread::offer);

        // then
        assertThat(queueThread).hasSize(60);
        assertThat(maxSizeMetric.get()).isEqualTo(60);
        assertThat(minSizeMetric.get()).isEqualTo(50);

        // when - poll
        IntStream.range(0, 20).boxed().forEach(x -> queueThread.poll());

        // then
        assertThat(queueThread).hasSize(40);
        assertThat(maxSizeMetric.get()).isEqualTo(60);
        assertThat(minSizeMetric.get()).isEqualTo(40);

        // when - drainTo
        final List<Integer> buffer = new ArrayList<>();
        queueThread.drainTo(buffer, 10);

        // then
        assertThat(queueThread).hasSize(30);
        assertThat(maxSizeMetric.get()).isEqualTo(60);
        assertThat(minSizeMetric.get()).isEqualTo(30);
        assertThat(buffer).hasSize(10);

        // when - drainTo
        queueThread.drainTo(buffer);

        // then
        assertThat(queueThread).isEmpty();
        assertThat(maxSizeMetric.get()).isEqualTo(60);
        assertThat(minSizeMetric.get()).isZero();
        assertThat(buffer).hasSize(40);

        // when - put
        IntStream.range(0, 70).boxed().forEach(x -> {
            try {
                queueThread.put(x);
            } catch (InterruptedException ignored) {
            }
        });
        maxSizeMetric.takeSnapshot();
        minSizeMetric.takeSnapshot();

        // then
        assertThat(queueThread).hasSize(70);
        assertThat(maxSizeMetric.get()).isEqualTo(70);
        assertThat(minSizeMetric.get()).isEqualTo(70);

        // when - take
        IntStream.range(0, 20).boxed().forEach(x -> {
            try {
                queueThread.take();
            } catch (InterruptedException ignored) {
            }
        });

        // then
        assertThat(queueThread).hasSize(50);
        assertThat(maxSizeMetric.get()).isEqualTo(70);
        assertThat(minSizeMetric.get()).isEqualTo(50);

        // when - clear
        queueThread.clear();

        // then
        assertThat(queueThread).isEmpty();
        assertThat(maxSizeMetric.get()).isEqualTo(70);
        assertThat(minSizeMetric.get()).isZero();
    }
}
