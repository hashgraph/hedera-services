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

package com.swirlds.common.wiring.counters;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
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
    void countWithHighCapacityTest() {
        final Random random = getRandomPrintSeed();

        final ObjectCounter counter = new BackpressureObjectCounter("test", 1_000_000_000, Duration.ofMillis(1));

        int count = 0;
        for (int i = 0; i < 1000; i++) {

            final boolean increment = count == 0 || random.nextBoolean();

            if (increment) {
                count++;

                // All of these methods are logically equivalent with current capacity.
                final int choice = random.nextInt(3);
                switch (choice) {
                    case 0 -> counter.onRamp();
                    case 1 -> counter.attemptOnRamp();
                    case 2 -> counter.forceOnRamp();
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
    @ValueSource(ints = {0, 1})
    void onRampTest(final int sleepMillis) throws InterruptedException {
        final Duration sleepDuration = Duration.ofMillis(sleepMillis);

        final ObjectCounter counter = new BackpressureObjectCounter("test", 10, sleepDuration);

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

    @Test
    void attemptOnRampTest() {
        final ObjectCounter counter = new BackpressureObjectCounter("test", 10, Duration.ofMillis(1));

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
        final ObjectCounter counter = new BackpressureObjectCounter("test", 10, Duration.ofMillis(1));

        // Fill up the counter to capacity
        for (int i = 0; i < 10; i++) {
            counter.forceOnRamp();
        }

        assertEquals(10, counter.getCount());

        // Attempt to add one more, should work even though it violates capacity restrictions
        counter.forceOnRamp();

        assertEquals(11, counter.getCount());
    }

    @Test
    void waitUntilEmptyTest() throws InterruptedException {
        final ObjectCounter counter = new BackpressureObjectCounter("test", 1000, Duration.ofMillis(1));

        for (int i = 0; i < 100; i++) {
            counter.onRamp();
        }

        final AtomicBoolean empty = new AtomicBoolean(false);
        final Thread thread = new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    counter.waitUntilEmpty();
                    empty.set(true);
                })
                .build(true);

        // Should be blocked.
        MILLISECONDS.sleep(50);
        assertFalse(empty.get());

        // Draining most of the things from the counter should still block.
        for (int i = 0; i < 90; i++) {
            counter.offRamp();
        }
        MILLISECONDS.sleep(50);
        assertFalse(empty.get());

        // Interrupting the thread should have no effect.
        thread.interrupt();
        MILLISECONDS.sleep(50);
        assertFalse(empty.get());

        // Removing remaining things from the counter should unblock.
        for (int i = 0; i < 10; i++) {
            counter.offRamp();
        }

        assertEventuallyTrue(empty::get, Duration.ofSeconds(1), "Counter did not empty in time.");
    }
}
