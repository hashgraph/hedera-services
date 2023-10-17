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
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackpressureObjectCounterTests {

    /**
     * Choose a capacity that is sufficiently high as to never trigger. Validate that the counting part of this
     * implementation works as expected.
     */
    @Test
    void countWithHighCapacityTest() throws InterruptedException {
        final Random random = getRandomPrintSeed();

        final ObjectCounter counter = new BackpressureObjectCounter(1_000_000_000, null);

        int count = 0;
        for (int i = 0; i < 1000; i++) {

            final boolean increment = count == 0 || random.nextBoolean();

            if (increment) {
                count++;

                // All of these methods are logically equivalent with current capacity.
                final int choice = random.nextInt(4);
                switch (choice) {
                    case 0 -> counter.onRamp();
                    case 1 -> counter.interruptableOnRamp();
                    case 2 -> counter.attemptOnRamp();
                    case 3 -> counter.forceOnRamp();
                    default -> throw new IllegalStateException("Unexpected value: " + choice);
                }

            } else {
                count--;
                counter.offRamp();
            }

            assertEquals(count, counter.getCount());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void onRampTest(final int sleepMillis) throws InterruptedException {
        final Duration sleepDuration;
        if (sleepMillis < 0) {
            sleepDuration = null;
        } else {
            sleepDuration = Duration.ofMillis(sleepMillis);
        }

        final ObjectCounter counter = new BackpressureObjectCounter(10, sleepDuration);

        // Fill up the counter to capacity
        for (int i = 0; i < 10; i++) {
            counter.onRamp();
        }

        assertEquals(10, counter.getCount());

        // Attempt to add one more, should block.
        final AtomicBoolean added = new AtomicBoolean(false);
        final AtomicReference<Boolean> interrupted = new AtomicReference<>();
        final Thread thread = new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    counter.onRamp();
                    added.set(true);

                    interrupted.set(Thread.currentThread().isInterrupted());
                })
                .build(true);

        assertEquals(10, counter.getCount());

        // Sleep for a little while. Thread should be unable to on ramp another element.
        MILLISECONDS.sleep(50);
        assertEquals(10, counter.getCount());

        // Interrupting the thread should not unblock us.
        thread.interrupt();
        MILLISECONDS.sleep(50);
        assertEquals(10, counter.getCount());

        // Off ramp one element. Thread should become unblocked.
        counter.offRamp();

        assertEventuallyTrue(added::get, Duration.ofSeconds(1), "Thread should have been unblocked");

        // even though the interrupt did not unblock the thread, the interrupt should not have been squelched.
        assertEventuallyEquals(true, interrupted::get, Duration.ofSeconds(1), "Thread should have been interrupted");

        assertEquals(10, counter.getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void interruptableOnRampTest(final int sleepMillis) throws InterruptedException {
        final Duration sleepDuration;
        if (sleepMillis < 0) {
            sleepDuration = null;
        } else {
            sleepDuration = Duration.ofMillis(sleepMillis);
        }

        final ObjectCounter counter = new BackpressureObjectCounter(10, sleepDuration);

        // Fill up the counter to capacity
        for (int i = 0; i < 10; i++) {
            counter.onRamp();
        }

        assertEquals(10, counter.getCount());

        // Attempt to another, should block.
        final AtomicBoolean added1 = new AtomicBoolean(false);
        final AtomicReference<Boolean> interrupted1 = new AtomicReference<>();
        final Thread thread1 = new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    try {
                        counter.interruptableOnRamp();
                        added1.set(true);
                    } catch (InterruptedException e) {
                        interrupted1.set(true);
                        return;
                    }

                    interrupted1.set(false);
                })
                .build(true);

        // Attempt to add one more, should block.
        final AtomicBoolean added2 = new AtomicBoolean(false);
        final AtomicReference<Boolean> interrupted2 = new AtomicReference<>();
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    try {
                        counter.interruptableOnRamp();
                        added2.set(true);
                    } catch (InterruptedException e) {
                        interrupted2.set(true);
                        return;
                    }
                    interrupted2.set(false);
                })
                .build(true);

        assertEquals(10, counter.getCount());

        // Sleep for a little while. Threads should be unable to on ramp another element.
        MILLISECONDS.sleep(50);
        assertEquals(10, counter.getCount());

        // Interrupt thread 1.
        thread1.interrupt();
        assertEventuallyEquals(true, interrupted1::get, Duration.ofSeconds(1), "Thread should have been interrupted");
        assertFalse(added1.get());

        // The second thread should still be blocked.
        MILLISECONDS.sleep(50);
        assertEquals(10, counter.getCount());

        // Off ramp one element. Thread 2 should become unblocked.
        counter.offRamp();

        assertEventuallyTrue(added2::get, Duration.ofSeconds(1), "Thread should have been unblocked");
        assertEventuallyEquals(false, interrupted2::get, Duration.ofSeconds(1), "Thread should have been interrupted");

        assertEquals(10, counter.getCount());
    }

    @Test
    void attemptOnRampTest() {
        final ObjectCounter counter = new BackpressureObjectCounter(10, null);

        // Fill up the counter to capacity
        for (int i = 0; i < 10; i++) {
            assertTrue(counter.attemptOnRamp());
        }

        assertEquals(10, counter.getCount());

        // Attempt to add one more, should block immediately fail and return false.
        assertFalse(counter.attemptOnRamp());

        assertEquals(10, counter.getCount());
    }

    @Test
    void forceOnRampTest() {
        final ObjectCounter counter = new BackpressureObjectCounter(10, null);

        // Fill up the counter to capacity
        for (int i = 0; i < 10; i++) {
            counter.forceOnRamp();
        }

        assertEquals(10, counter.getCount());

        // Attempt to add one more, should work even though it violates capacity restrictions
        counter.forceOnRamp();

        assertEquals(11, counter.getCount());
    }
}
