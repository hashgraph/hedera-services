/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.internal.NoOpWiringHealthMonitor;
import com.swirlds.common.wiring.model.internal.StandardWiringHealthMonitor;
import com.swirlds.common.wiring.model.internal.WiringHealthMonitor;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

class WiringHealthMonitorTests {

    @Test
    void noOpTest() {
        final WiringHealthMonitor monitor = new NoOpWiringHealthMonitor();
        final TaskScheduler<Void> scheduler = mock(TaskScheduler.class);
        monitor.registerScheduler(scheduler, 100);
        monitor.checkHealth(Instant.now());
        assertFalse(monitor.isStressed());
        assertNull(monitor.getStressedDuration());
    }

    @Test
    void singleSchedulerIsStressed() {

        final FakeTime fakeTime = new FakeTime();
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().withTime(fakeTime).build();

        // We are intentionally not going ask the wiring model to monitor health, we are going to do it manually.
        // We have to do this since the model's heartbeat is currently nontrivial to mock.
        final WiringModel model = WiringModel.builder(platformContext).build();

        final int runningAverageSize = 10;
        final int stressedCapacity = 5;

        final WiringHealthMonitor monitor = new StandardWiringHealthMonitor(platformContext, runningAverageSize);

        final TaskScheduler<Void> scheduler = model.schedulerBuilder("test")
                .withType(TaskSchedulerType.SEQUENTIAL)
                .withFlushingEnabled(true)
                .build()
                .cast();
        monitor.registerScheduler(scheduler, stressedCapacity);

        final BindableInputWire<Integer, Void> input = scheduler.buildInputWire("input");

        final Lock lock = new ReentrantLock();
        final AtomicLong sum = new AtomicLong(0);
        input.bind(x -> {
            lock.lock();
            lock.unlock();
            sum.addAndGet(x);
        });

        model.start();

        // Feed a bunch of data through the scheduler without it being blocked.
        int expectedSum = 0;
        for (int i = 0; i < 100; i++) {
            expectedSum += i;
            input.put(i);
        }

        // Make sure the scheduler has handled the work.
        scheduler.flush();
        assertEquals(expectedSum, sum.get());

        // Take a handful of samples with the health monitor. Should not be stressed.
        for (int i = 0; i < 2 * runningAverageSize; i++) {
            monitor.checkHealth(fakeTime.now());
            fakeTime.tick(Duration.ofSeconds(1));
            assertFalse(monitor.isStressed());
            assertNull(monitor.getStressedDuration());
        }

        // Cause the scheduler to be blocked. Fill it up with just under the stressed threshold
        lock.lock();
        for (int i = 0; i < stressedCapacity - 1; i++) {
            expectedSum += i;
            input.put(i);
        }

        // Should not become stressed after many samples.
        for (int i = 0; i < 2 * runningAverageSize; i++) {
            monitor.checkHealth(fakeTime.now());
            fakeTime.tick(Duration.ofSeconds(1));
            assertFalse(monitor.isStressed());
            assertNull(monitor.getStressedDuration());
        }

        // Adding one more element should cause the scheduler to be stressed.
        input.put(1);
        expectedSum += 1;

        // We've not yet sampled, should not report stress.
        assertFalse(monitor.isStressed());

        // After first sampling, running average will still be below threshold.
        monitor.checkHealth(fakeTime.now());
        fakeTime.tick(Duration.ofSeconds(1));
        assertFalse(monitor.isStressed());

        // Take a bunch more samples, will tip the running average over the threshold.
        for (int i = 0; i < runningAverageSize; i++) {
            monitor.checkHealth(fakeTime.now());
            fakeTime.tick(Duration.ofSeconds(1));
        }

        assertTrue(monitor.isStressed());
        final Duration stressedDuration = monitor.getStressedDuration();
        assertNotNull(stressedDuration);

        // The stressed duration should increase as time goes forward.
        fakeTime.tick(Duration.ofSeconds(1234));
        monitor.checkHealth(fakeTime.now());

        assertEquals(stressedDuration.plus(Duration.ofSeconds(1234 + 1)), monitor.getStressedDuration());

        // Unblock the scheduler.
        lock.unlock();
        scheduler.flush();
        assertEquals(expectedSum, sum.get());

        // Should become un-stressed after a single sample.
        monitor.checkHealth(fakeTime.now());
        assertFalse(monitor.isStressed());
        assertNull(monitor.getStressedDuration());

        model.stop();
    }
}
