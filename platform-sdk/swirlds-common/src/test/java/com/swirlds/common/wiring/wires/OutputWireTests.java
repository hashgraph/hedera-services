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

package com.swirlds.common.wiring.wires;

import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIMING_SENSITIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the functionality of output wires
 */
@Tag(TIMING_SENSITIVE)
public class OutputWireTests {

    /**
     * Test that the ordered solder to method forwards data in the proper order.
     *
     * @param count the number of data to send through the wires
     */
    @ParameterizedTest()
    @ValueSource(ints = {10_000})
    void orderedSolderToTest(final int count) {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final WiringModel model = WiringModel.create(platformContext, Time.getCurrent(), ForkJoinPool.commonPool());

        final TaskScheduler<Integer> intForwarder = model.schedulerBuilder("intForwarder")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();
        final TaskScheduler<Void> firstComponent = model.schedulerBuilder("firstComponent")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();
        final TaskScheduler<Void> secondComponent = model.schedulerBuilder("secondComponent")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();

        final BindableInputWire<Integer, Integer> intInput = intForwarder.buildInputWire("intInput");
        final BindableInputWire<Integer, Void> firstComponentInput = firstComponent.buildInputWire("ints");
        final BindableInputWire<Integer, Void> secondComponentInput = secondComponent.buildInputWire("ints");

        // Send integers to the first component before the second component
        final List<InputWire<Integer>> inputList = List.of(firstComponentInput, secondComponentInput);
        intForwarder.getOutputWire().orderedSolderTo(inputList);

        intInput.bind((i -> i));

        final AtomicInteger firstCompRecNum = new AtomicInteger();
        final AtomicInteger secondCompRecNum = new AtomicInteger();
        final AtomicInteger firstCompErrorCount = new AtomicInteger();
        final AtomicInteger secondCompErrorCount = new AtomicInteger();

        firstComponentInput.bindConsumer(i -> {
            if (firstCompRecNum.incrementAndGet() <= secondCompRecNum.get()) {
                firstCompErrorCount.incrementAndGet();
            }
        });
        secondComponentInput.bindConsumer(i -> {
            if (firstCompRecNum.get() != secondCompRecNum.incrementAndGet()) {
                secondCompErrorCount.incrementAndGet();
            }
        });

        for (int i = 0; i < count; i++) {
            intInput.put(i);
        }

        assertEquals(0, firstCompErrorCount.get(), "The first component should always receive data first");
        assertEquals(0, secondCompErrorCount.get(), "The second component should always receive data second");
    }

    /**
     * Test that the expected exceptions are thrown
     */
    @Test
    void orderedSolderToThrows() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final WiringModel model = WiringModel.create(platformContext, Time.getCurrent(), ForkJoinPool.commonPool());

        final TaskScheduler<Integer> schedulerA = model.schedulerBuilder("schedulerA")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();
        final TaskScheduler<Integer> schedulerB = model.schedulerBuilder("schedulerB")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();

        InputWire<Integer> inputWire = schedulerB.buildInputWire("inputWire");
        assertThrows(
                IllegalArgumentException.class,
                () -> schedulerA.getOutputWire().orderedSolderTo(List.of(inputWire)),
                "Method should throw when provided less than two input wires.");
    }
}
