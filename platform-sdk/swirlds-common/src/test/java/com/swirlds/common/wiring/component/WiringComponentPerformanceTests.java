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

package com.swirlds.common.wiring.component;

import com.swirlds.base.time.Time;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled // Do not merge with this class enabled
class WiringComponentPerformanceTests {

    private interface SimpleComponent {
        void handleInput(@NonNull Long input);
    }

    private static class SimpleComponentImpl implements SimpleComponent {
        private long runningValue = 0;

        @Override
        public void handleInput(@NonNull final Long input) {
            runningValue += input;
        }

        public long getRunningValue() {
            return runningValue;
        }
    }

    @NonNull
    private InputWire<Long> buildOldStyleComponent(@NonNull final SimpleComponent component) {
        final WiringModel model = WiringModel.create(
                TestPlatformContextBuilder.create().build(), Time.getCurrent(), ForkJoinPool.commonPool());

        final TaskScheduler scheduler = model.schedulerBuilder("test")
                .withType(TaskSchedulerType.DIRECT)
                .build();

        final BindableInputWire<Long, Void> inputWire = scheduler.buildInputWire("input");
        inputWire.bindConsumer(component::handleInput);

        return inputWire;
    }

    @NonNull
    private InputWire<Long> buildAutomaticComponent(@NonNull final SimpleComponent component) {

        final WiringModel model = WiringModel.create(
                TestPlatformContextBuilder.create().build(), Time.getCurrent(), ForkJoinPool.commonPool());

        final TaskScheduler<Void> scheduler = model.schedulerBuilder("test")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();

        final ComponentWiring<SimpleComponent, Void> componentWiring =
                new ComponentWiring<>(model, SimpleComponent.class, scheduler);
        final InputWire<Long> inputWire = componentWiring.getInputWire(SimpleComponent::handleInput);
        componentWiring.bind(component);

        return inputWire;
    }

    // When testing locally on my macbook (m1), the old style component took 0.76s to run 100,000,000 iterations,
    // and the automatic component took 0.79s to run 100,000,000 iterations.

    @Test
    void oldStylePerformanceTest() {
        final long iterations = 100_000_000;

        final SimpleComponentImpl component = new SimpleComponentImpl();
        final InputWire<Long> inputWire = buildOldStyleComponent(component);

        final Instant start = Instant.now();

        for (long i = 0; i < iterations; i++) {
            inputWire.put(i);
        }

        final Instant end = Instant.now();
        final Duration duration = Duration.between(start, end);
        System.out.println("Time required: " + duration.toMillis() + "ms");

        // Just in case the compiler wants to get cheeky and avoid doing computation
        System.out.println("value = " + component.getRunningValue());
    }

    @Test
    void automaticComponentPerformanceTest() {
        final long iterations = 100_000_000;

        final SimpleComponentImpl component = new SimpleComponentImpl();
        final InputWire<Long> inputWire = buildAutomaticComponent(component);

        final Instant start = Instant.now();

        for (long i = 0; i < iterations; i++) {
            inputWire.put(i);
        }

        final Instant end = Instant.now();
        final Duration duration = Duration.between(start, end);
        System.out.println("Time required: " + duration.toMillis() + "ms");

        // Just in case the compiler wants to get cheeky and avoid doing computation
        System.out.println("value = " + component.getRunningValue());
    }
}
