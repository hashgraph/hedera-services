package com.swirlds.common.test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.threading.manager.internal.ManagedExecutorService;
import com.swirlds.common.threading.manager.internal.ManagedScheduledExecutorService;
import com.swirlds.common.utility.LifecycleException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ManagedExecutorService Tests")
class ManagedExecutorServiceTests {

    @Test
    @DisplayName("Incorrect Startup Test")
    void incorrectLifecycleTest() {
        final ManagedScheduledExecutorService executor = new ManagedScheduledExecutorService(
                mock(ScheduledExecutorService.class));

        assertThrows(LifecycleException.class, executor::stop);

        // These methods are not permitted prior to executor service startup
        assertThrows(LifecycleException.class, () -> executor.invokeAny(List.of()));
        assertThrows(LifecycleException.class, () -> executor.invokeAny(List.of(), 100, MILLISECONDS));
        assertThrows(LifecycleException.class, () -> executor.invokeAll(List.of()));
        assertThrows(LifecycleException.class, () -> executor.invokeAll(List.of(), 100, MILLISECONDS));

        executor.start();
        executor.stop();

        assertThrows(LifecycleException.class, executor::start);
        assertThrows(LifecycleException.class, executor::stop);

        assertThrows(LifecycleException.class, () -> executor.submit(() -> 0));
        assertThrows(LifecycleException.class, () -> executor.submit(() -> {
        }));
        assertThrows(LifecycleException.class, () -> executor.submit(() -> {
        }, 0));
        assertThrows(LifecycleException.class, () -> executor.invokeAll(List.of()));
        assertThrows(LifecycleException.class, () -> executor.invokeAll(List.of(), 100, MILLISECONDS));
        assertThrows(LifecycleException.class, () -> executor.invokeAny(List.of()));
        assertThrows(LifecycleException.class, () -> executor.invokeAny(List.of(), 100, MILLISECONDS));
        assertThrows(LifecycleException.class, () -> executor.execute(() -> {
        }));
    }

    @Test
    @DisplayName("submit(Callable<>) Test")
    void submitCallableTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedExecutorService executorService = new ManagedExecutorService(Executors.newSingleThreadExecutor());

        // submit work prior to startup
        final Future<Integer> future1 = executorService.submit(count::getAndIncrement);
        final Future<Integer> future2 = executorService.submit(count::getAndIncrement);
        final Future<Integer> future3 = executorService.submit(count::getAndIncrement);

        // No work should happen until started. Sleep a little while to give background work the opportunity to happen
        // if it is going to happen.
        MILLISECONDS.sleep(10);

        assertEquals(0, count.get());

        executorService.start();

        // submit some work after startup
        final Future<Integer> future4 = executorService.submit(count::getAndIncrement);
        final Future<Integer> future5 = executorService.submit(count::getAndIncrement);
        final Future<Integer> future6 = executorService.submit(count::getAndIncrement);

        AssertionUtils.completeBeforeTimeout(() -> {
            try {
                assertEquals(0, future1.get());
                assertEquals(1, future2.get());
                assertEquals(2, future3.get());
                assertEquals(3, future4.get());
                assertEquals(4, future5.get());
                assertEquals(5, future6.get());
            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, Duration.ofSeconds(1), "futures not completed on time");

        assertEquals(6, count.get());

        executorService.stop();
    }

    @Test
    @DisplayName("submit(Runnable) Test")
    void submitRunnableTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedExecutorService executorService = new ManagedExecutorService(Executors.newSingleThreadExecutor());

        // submit work prior to startup
        final Future<?> future1 = executorService.submit(() -> {
            count.getAndIncrement();
        });
        final Future<?> future2 = executorService.submit(() -> {
            count.getAndIncrement();
        });
        final Future<?> future3 = executorService.submit(() -> {
            count.getAndIncrement();
        });

        // No work should happen until started. Sleep a little while to give background work the opportunity to happen
        // if it is going to happen.
        MILLISECONDS.sleep(10);

        assertEquals(0, count.get());

        executorService.start();

        // submit some work after startup
        final Future<?> future4 = executorService.submit(() -> {
            count.getAndIncrement();
        });
        final Future<?> future5 = executorService.submit(() -> {
            count.getAndIncrement();
        });
        final Future<?> future6 = executorService.submit(() -> {
            count.getAndIncrement();
        });

        AssertionUtils.completeBeforeTimeout(() -> {
            try {
                future1.get();
                future2.get();
                future3.get();
                future4.get();
                future5.get();
                future6.get();
            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, Duration.ofSeconds(1), "futures not completed on time");

        assertEquals(6, count.get());

        executorService.stop();
    }

    @Test
    @DisplayName("submit(Runnable, result) Test")
    void submitRunnableWithResultTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedExecutorService executorService = new ManagedExecutorService(Executors.newSingleThreadExecutor());

        // submit work prior to startup
        final Future<?> future1 = executorService.submit(count::getAndIncrement, 1);
        final Future<?> future2 = executorService.submit(count::getAndIncrement, 2);
        final Future<?> future3 = executorService.submit(count::getAndIncrement, 3);

        // No work should happen until started. Sleep a little while to give background work the opportunity to happen
        // if it is going to happen.
        MILLISECONDS.sleep(10);

        assertEquals(0, count.get());

        executorService.start();

        // submit some work after startup
        final Future<?> future4 = executorService.submit(count::getAndIncrement, 4);
        final Future<?> future5 = executorService.submit(count::getAndIncrement, 5);
        final Future<?> future6 = executorService.submit(count::getAndIncrement, 6);

        AssertionUtils.completeBeforeTimeout(() -> {
            try {
                assertEquals(1, future1.get());
                assertEquals(2, future2.get());
                assertEquals(3, future3.get());
                assertEquals(4, future4.get());
                assertEquals(5, future5.get());
                assertEquals(6, future6.get());
            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, Duration.ofSeconds(1), "futures not completed on time");

        assertEquals(6, count.get());

        executorService.stop();
    }

    @Test
    @DisplayName("invokeAll() Test")
    void invokeAllTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedExecutorService executorService = new ManagedExecutorService(Executors.newSingleThreadExecutor());

        assertEquals(0, count.get());

        executorService.start();

        final List<Future<Integer>> futures = executorService.invokeAll(
                List.of(count::getAndIncrement, count::getAndIncrement, count::getAndIncrement));
        assertEquals(3, futures.size());

        AssertionUtils.completeBeforeTimeout(() -> {
            try {
                assertEquals(0, futures.get(0).get());
                assertEquals(1, futures.get(1).get());
                assertEquals(2, futures.get(2).get());

            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, Duration.ofSeconds(1), "futures not completed on time");

        assertEquals(3, count.get());

        executorService.stop();
    }

    @Test
    @DisplayName("invokeAll() With Timeout Test")
    void invokeAllWithTimeoutTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedExecutorService executorService = new ManagedExecutorService(Executors.newSingleThreadExecutor());

        assertEquals(0, count.get());

        executorService.start();

        final List<Future<Integer>> futures = executorService.invokeAll(
                List.of(count::getAndIncrement, count::getAndIncrement, () -> {
                    MILLISECONDS.sleep(100);
                    return count.getAndIncrement();
                }), 10, MILLISECONDS);
        assertEquals(3, futures.size());

        AssertionUtils.completeBeforeTimeout(() -> {
            try {
                assertEquals(0, futures.get(0).get());
                assertEquals(1, futures.get(1).get());
                assertThrows(CancellationException.class, () -> futures.get(2).get());

            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, Duration.ofSeconds(1), "futures not completed on time");

        assertEquals(2, count.get());

        executorService.stop();
    }

    @Test
    @DisplayName("invokeAny() Test")
    void invokeAnyTest() throws InterruptedException, ExecutionException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedExecutorService executorService = new ManagedExecutorService(Executors.newSingleThreadExecutor());

        assertEquals(0, count.get());

        executorService.start();

        final int value = executorService.invokeAny(
                List.of(count::getAndIncrement, count::getAndIncrement, count::getAndIncrement));

        assertTrue(value >= 0 && value <= 2);
        assertTrue(count.get() >= 0 && count.get() <= 3);

        executorService.stop();
    }

    @Test
    @DisplayName("invokeAny() With Timeout Test")
    void invokeAnyWithTimeoutTest() throws InterruptedException, ExecutionException, TimeoutException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedExecutorService executorService = new ManagedExecutorService(Executors.newSingleThreadExecutor());

        assertEquals(0, count.get());

        executorService.start();

        final int value = executorService.invokeAny(
                List.of(count::getAndIncrement, count::getAndIncrement, count::getAndIncrement), 100, MILLISECONDS);

        assertTrue(value >= 0 && value <= 2);
        assertTrue(count.get() >= 0 && count.get() <= 3);

        assertThrows(TimeoutException.class, () -> executorService.invokeAny(
                List.of(() -> {
                    MILLISECONDS.sleep(100);
                    return count.getAndIncrement();
                }, count::getAndIncrement, count::getAndIncrement), 10, MILLISECONDS));

        executorService.stop();
    }

    @Test
    @DisplayName("execute() Test")
    void executeTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedExecutorService executorService = new ManagedExecutorService(Executors.newSingleThreadExecutor());

        // submit work prior to startup
        executorService.execute(count::getAndIncrement);
        executorService.execute(count::getAndIncrement);
        executorService.execute(count::getAndIncrement);

        // No work should happen until started. Sleep a little while to give background work the opportunity to happen
        // if it is going to happen.
        MILLISECONDS.sleep(10);

        assertEquals(0, count.get());

        executorService.start();

        // submit some work after startup
        executorService.execute(count::getAndIncrement);
        executorService.execute(count::getAndIncrement);
        executorService.execute(count::getAndIncrement);

        AssertionUtils.assertEventuallyEquals(6, count::get, Duration.ofSeconds(1), "methods not executed in time");

        executorService.stop();
    }

    @Test
    @DisplayName("schedule(Runnable) Test")
    void scheduleRunnableTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedScheduledExecutorService executorService =
                new ManagedScheduledExecutorService(Executors.newSingleThreadScheduledExecutor());

        // submit work prior to startup
        final ScheduledFuture<?> future1 = executorService.schedule(
                () -> {count.getAndIncrement();}, 1, MILLISECONDS);
        final ScheduledFuture<?> future2 = executorService.schedule(
                () -> {count.getAndIncrement();}, 1, MILLISECONDS);
        final ScheduledFuture<?> future3 = executorService.schedule(
                () -> {count.getAndIncrement();}, 1, MILLISECONDS);

        // Something that is scheduled for far in the future should not be observed
        final ScheduledFuture<?> slowFuture1 = executorService.schedule(
                () -> {count.getAndIncrement();}, 10, SECONDS);

        // No work should happen until started. Sleep a little while to give background work the opportunity to happen
        // if it is going to happen.
        MILLISECONDS.sleep(10);

        assertEquals(0, count.get());

        executorService.start();

        // submit some work after startup
        final ScheduledFuture<?> future4 = executorService.schedule(
                () -> {count.getAndIncrement();}, 1, MILLISECONDS);
        final ScheduledFuture<?> future5 = executorService.schedule(
                () -> {count.getAndIncrement();}, 1, MILLISECONDS);
        final ScheduledFuture<?> future6 = executorService.schedule(
                () -> {count.getAndIncrement();}, 1, MILLISECONDS);

        AssertionUtils.completeBeforeTimeout(() -> {
            try {
                future1.get();
                future2.get();
                future3.get();
                future4.get();
                future5.get();
                future6.get();
            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, Duration.ofSeconds(1), "futures not completed on time");

        assertEquals(6, count.get());

        // Something that is scheduled for far in the future should not be observed
        final ScheduledFuture<?> slowFuture2 = executorService.schedule(
                () -> {count.getAndIncrement();}, 10, SECONDS);

        assertThrows(TimeoutException.class, () -> slowFuture1.get(100, MILLISECONDS));
        assertThrows(TimeoutException.class, () -> slowFuture2.get(100, MILLISECONDS));

        executorService.stop();
    }

    @Test
    @DisplayName("schedule(Callable) Test")
    void scheduleCallableTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedScheduledExecutorService executorService =
                new ManagedScheduledExecutorService(Executors.newSingleThreadScheduledExecutor());

        // submit work prior to startup
        final ScheduledFuture<?> future1 = executorService.schedule(
                count::getAndIncrement, 1, MILLISECONDS);
        final ScheduledFuture<?> future2 = executorService.schedule(
                count::getAndIncrement, 1, MILLISECONDS);
        final ScheduledFuture<?> future3 = executorService.schedule(
                count::getAndIncrement, 1, MILLISECONDS);

        // Something that is scheduled for far in the future should not be observed
        final ScheduledFuture<?> slowFuture1 = executorService.schedule(
                count::getAndIncrement, 10, SECONDS);

        // No work should happen until started. Sleep a little while to give background work the opportunity to happen
        // if it is going to happen.
        MILLISECONDS.sleep(10);

        assertEquals(0, count.get());

        executorService.start();

        // submit some work after startup
        final ScheduledFuture<?> future4 = executorService.schedule(
                count::getAndIncrement, 1, MILLISECONDS);
        final ScheduledFuture<?> future5 = executorService.schedule(
                count::getAndIncrement, 1, MILLISECONDS);
        final ScheduledFuture<?> future6 = executorService.schedule(
                count::getAndIncrement, 1, MILLISECONDS);

        AssertionUtils.completeBeforeTimeout(() -> {
            try {
                assertEquals(0, future1.get());
                assertEquals(1, future2.get());
                assertEquals(2, future3.get());
                assertEquals(3, future4.get());
                assertEquals(4, future5.get());
                assertEquals(5, future6.get());
            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, Duration.ofSeconds(1), "futures not completed on time");

        assertEquals(6, count.get());

        // Something that is scheduled for far in the future should not be observed
        final ScheduledFuture<?> slowFuture2 = executorService.schedule(
                () -> {count.getAndIncrement();}, 10, SECONDS);

        assertThrows(TimeoutException.class, () -> slowFuture1.get(100, MILLISECONDS));
        assertThrows(TimeoutException.class, () -> slowFuture2.get(100, MILLISECONDS));

        executorService.stop();
    }

    // TODO this is failing
    @Test
    @DisplayName("scheduleAtFixedRate() Test")
    void scheduleAtFixedRateTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedScheduledExecutorService executorService =
                new ManagedScheduledExecutorService(Executors.newSingleThreadScheduledExecutor());

        // submit work prior to startup
        executorService.scheduleAtFixedRate(() -> {
            count.getAndIncrement();
        }, 1, 1, MILLISECONDS);

        // Something that is scheduled for far in the future should not be observed
        executorService.scheduleAtFixedRate(() -> count.getAndAdd(1000), 1000000, 1, MILLISECONDS);

        // No work should happen until started. Sleep a little while to give background work the opportunity to happen
        // if it is going to happen.
        MILLISECONDS.sleep(10);

        assertEquals(0, count.get());

        executorService.start();

        // submit some work after startup
        executorService.scheduleAtFixedRate(count::getAndDecrement, 1, 2, MILLISECONDS);

        // Something that is scheduled for far in the future should not be observed
        executorService.scheduleAtFixedRate(() -> count.getAndAdd(1000), 1000000, 1, MILLISECONDS);

        // Wait enough time so that we will have incremented ~100 times, and decremented ~50 times.
        // Accept +/- 50% to account for threading unpredictability.
        MILLISECONDS.sleep(100);

        final int value = count.get();
        System.out.println(">>>" + value);
        assertTrue(value > 25);
        assertTrue(value < 75);

        executorService.stop();
    }

    // TODO this is failing
    @Test
    @DisplayName("scheduleWithFixedDelay() Test")
    void scheduleWithFixedDelayTest() throws InterruptedException {

        final AtomicInteger count = new AtomicInteger();

        final ManagedScheduledExecutorService executorService =
                new ManagedScheduledExecutorService(Executors.newSingleThreadScheduledExecutor());

        // submit work prior to startup
        executorService.scheduleWithFixedDelay(count::getAndIncrement, 1, 1, MILLISECONDS);

        // Something that is scheduled for far in the future should not be observed
        executorService.scheduleWithFixedDelay(() -> count.getAndAdd(1000), 1000000, 1, MILLISECONDS);

        // No work should happen until started. Sleep a little while to give background work the opportunity to happen
        // if it is going to happen.
        MILLISECONDS.sleep(10);

        assertEquals(0, count.get());

        executorService.start();

        // submit some work after startup
        executorService.scheduleWithFixedDelay(count::getAndDecrement, 1, 2, MILLISECONDS);

        // Something that is scheduled for far in the future should not be observed
        executorService.scheduleWithFixedDelay(() -> count.getAndAdd(1000), 1000000, 1, MILLISECONDS);

        // Wait enough time so that we will have incremented ~100 times, and decremented ~50 times.
        // Accept +/- 50% to account for threading unpredictability.
        MILLISECONDS.sleep(100);

        final int value = count.get();
        System.out.println(">>>" + value);
        assertTrue(value > 25);
        assertTrue(value < 75);

        executorService.stop();
    }
}
