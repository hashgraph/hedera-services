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

package com.swirlds.common.wiring.transformers;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;

import com.swirlds.common.wiring.InputWire;
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.test.framework.TestWiringModel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskSchedulerTransformersTests {

    private static final WiringModel model = TestWiringModel.getInstance();

    @Test
    void wireListSplitterTest() {

        // Component A produces lists of integers. It passes data to B, C, and D.
        // Components B and C want individual integers. Component D wants the full list of integers.

        final TaskScheduler<List<Integer>> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final InputWire<Integer, List<Integer>> wireAIn = taskSchedulerA.buildInputWire("A in");

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final InputWire<Integer, Void> wireBIn = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final InputWire<Integer, Void> wireCIn = taskSchedulerC.buildInputWire("C in");

        final TaskScheduler<Void> taskSchedulerD =
                model.schedulerBuilder("D").build().cast();
        final InputWire<List<Integer>, Void> wireDIn = taskSchedulerD.buildInputWire("D in");

        final OutputWire<Integer> splitter = taskSchedulerA.getOutputWire().buildSplitter();
        splitter.solderTo(wireBIn);
        splitter.solderTo(wireCIn);
        taskSchedulerA.getOutputWire().solderTo(wireDIn);

        wireAIn.bind(x -> {
            return List.of(x, x, x);
        });

        final AtomicInteger countB = new AtomicInteger(0);
        wireBIn.bind(x -> {
            countB.set(hash32(countB.get(), x));
        });

        final AtomicInteger countC = new AtomicInteger(0);
        wireCIn.bind(x -> {
            countC.set(hash32(countC.get(), -x));
        });

        final AtomicInteger countD = new AtomicInteger(0);
        wireDIn.bind(x -> {
            int product = 1;
            for (final int i : x) {
                product *= i;
            }
            countD.set(hash32(countD.get(), product));
        });

        int expectedCountB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 0; i < 100; i++) {
            wireAIn.put(i);

            for (int j = 0; j < 3; j++) {
                expectedCountB = hash32(expectedCountB, i);
                expectedCountC = hash32(expectedCountC, -i);
            }

            expectedCountD = hash32(expectedCountD, i * i * i);
        }

        assertEventuallyEquals(expectedCountB, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(expectedCountC, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(expectedCountD, countD::get, Duration.ofSeconds(1), "D did not receive all data");
    }

    @Test
    void wireFilterTest() {

        // Wire A passes data to B, C, and a lambda.
        // B wants all of A's data, but C and the lambda only want even values.

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final InputWire<Integer, Integer> inA = taskSchedulerA.buildInputWire("A in");

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final InputWire<Integer, Void> inB = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final InputWire<Integer, Void> inC = taskSchedulerC.buildInputWire("C in");

        final AtomicInteger countA = new AtomicInteger(0);
        final AtomicInteger countB = new AtomicInteger(0);
        final AtomicInteger countC = new AtomicInteger(0);
        final AtomicInteger countLambda = new AtomicInteger(0);

        taskSchedulerA.getOutputWire().solderTo(inB);
        final OutputWire<Integer> filter = taskSchedulerA.getOutputWire().buildFilter("onlyEven", x -> x % 2 == 0);
        filter.solderTo(inC);
        filter.solderTo("lambda", x -> countLambda.set(hash32(countLambda.get(), x)));

        inA.bind(x -> {
            countA.set(hash32(countA.get(), x));
            return x;
        });

        inB.bind(x -> {
            countB.set(hash32(countB.get(), x));
        });

        inC.bind(x -> {
            countC.set(hash32(countC.get(), x));
        });

        int expectedCount = 0;
        int expectedEvenCount = 0;

        for (int i = 0; i < 100; i++) {
            inA.put(i);
            expectedCount = hash32(expectedCount, i);
            if (i % 2 == 0) {
                expectedEvenCount = hash32(expectedEvenCount, i);
            }
        }

        assertEventuallyEquals(expectedCount, countA::get, Duration.ofSeconds(1), "A did not receive all data");
        assertEventuallyEquals(expectedCount, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(expectedEvenCount, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(
                expectedEvenCount, countLambda::get, Duration.ofSeconds(1), "Lambda did not receive all data");
    }

    private record TestData(int value, boolean invert) {}

    @Test
    void wireTransformerTest() {

        // A produces data of type TestData.
        // B wants all of A's data, C wants the integer values, and D wants the boolean values.

        final TaskScheduler<TestData> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final InputWire<TestData, TestData> inA = taskSchedulerA.buildInputWire("A in");

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final InputWire<TestData, Void> inB = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final InputWire<Integer, Void> inC = taskSchedulerC.buildInputWire("C in");

        final TaskScheduler<Void> taskSchedulerD =
                model.schedulerBuilder("D").build().cast();
        final InputWire<Boolean, Void> inD = taskSchedulerD.buildInputWire("D in");

        taskSchedulerA.getOutputWire().solderTo(inB);
        taskSchedulerA
                .getOutputWire()
                .buildTransformer("getValue", TestData::value)
                .solderTo(inC);
        taskSchedulerA
                .getOutputWire()
                .buildTransformer("getInvert", TestData::invert)
                .solderTo(inD);

        final AtomicInteger countA = new AtomicInteger(0);
        inA.bind(x -> {
            final int invert = x.invert() ? -1 : 1;
            countA.set(hash32(countA.get(), x.value() * invert));
            return x;
        });

        final AtomicInteger countB = new AtomicInteger(0);
        inB.bind(x -> {
            final int invert = x.invert() ? -1 : 1;
            countB.set(hash32(countB.get(), x.value() * invert));
        });

        final AtomicInteger countC = new AtomicInteger(0);
        inC.bind(x -> {
            countC.set(hash32(countC.get(), x));
        });

        final AtomicInteger countD = new AtomicInteger(0);
        inD.bind(x -> {
            countD.set(hash32(countD.get(), x ? 1 : 0));
        });

        int expectedCountAB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 0; i < 100; i++) {
            final boolean invert = i % 3 == 0;
            inA.put(new TestData(i, invert));

            expectedCountAB = hash32(expectedCountAB, i * (invert ? -1 : 1));
            expectedCountC = hash32(expectedCountC, i);
            expectedCountD = hash32(expectedCountD, invert ? 1 : 0);
        }

        assertEventuallyEquals(expectedCountAB, countA::get, Duration.ofSeconds(1), "A did not receive all data");
        assertEventuallyEquals(expectedCountAB, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(expectedCountC, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(expectedCountD, countD::get, Duration.ofSeconds(1), "D did not receive all data");
    }
}
