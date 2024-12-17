/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.threading.framework.internal.AbstractQueueThreadConfiguration.UNLIMITED_CAPACITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.SolderType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTests {

    /**
     * For debugging with a human in the loop.
     */
    private static final boolean printMermaidDiagram = false;

    /**
     * Validate the model.
     *
     * @param model                             the model to validate
     * @param cycleExpected                     true if a cycle is expected, false otherwise
     * @param illegalDirectSchedulerUseExpected true if illegal direct scheduler use is expected, false otherwise
     */
    private static void validateModel(
            @NonNull final WiringModel model,
            final boolean cycleExpected,
            final boolean illegalDirectSchedulerUseExpected) {

        final boolean cycleDetected = model.checkForCyclicalBackpressure();
        assertEquals(cycleExpected, cycleDetected);

        final boolean illegalDirectSchedulerUseDetected = model.checkForIllegalDirectSchedulerUsage();
        assertEquals(illegalDirectSchedulerUseExpected, illegalDirectSchedulerUseDetected);

        // Should not throw.
        final String diagram = model.generateWiringDiagram(List.of(), List.of(), List.of(), false);
        if (printMermaidDiagram) {
            System.out.println(diagram);
        }
    }

    @Test
    void emptyModelTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();
        validateModel(model, false, false);
    }

    @Test
    void singleVertexTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A

        */

        final TaskScheduler<Void> taskSchedulerA = model.schedulerBuilder("A")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();

        validateModel(model, false, false);
    }

    @Test
    void shortChainTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -> B -> C

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);

        validateModel(model, false, false);
    }

    @Test
    void loopSizeOneTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A --|
        ^   |
        |---|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        taskSchedulerA.getOutputWire().solderTo(inputA);

        validateModel(model, true, false);
    }

    @Test
    void loopSizeOneBrokenByInjectionTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A --|
        ^   |
        |---|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        taskSchedulerA.getOutputWire().solderTo(inputA, SolderType.INJECT);

        validateModel(model, false, false);
    }

    @Test
    void loopSizeTwoTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -> B
        ^    |
        |----|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputA);

        validateModel(model, true, false);
    }

    @Test
    void loopSizeTwoBrokenByInjectionTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -> B
        ^    |
        |----|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputA, SolderType.INJECT);

        validateModel(model, false, false);
    }

    @Test
    void loopSizeTwoBrokenByMissingBoundTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -> B
        ^    |
        |----|

        */

        final TaskScheduler<Integer> taskSchedulerA = model.schedulerBuilder("A")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputA);

        validateModel(model, false, false);
    }

    @Test
    void loopSizeThreeTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -> B -> C
        ^         |
        |---------|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputA);

        validateModel(model, true, false);
    }

    @Test
    void loopSizeThreeBrokenByInjectionTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -> B -> C
        ^         |
        |---------|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputA, SolderType.INJECT);

        validateModel(model, false, false);
    }

    @Test
    void loopSizeThreeBrokenByMissingBoundTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -> B -> C
        ^         |
        |---------|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC = model.schedulerBuilder("C")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputA);

        validateModel(model, false, false);
    }

    @Test
    void loopSizeFourTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -----> B
        ^        |
        |        v
        D <----- C

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputA);

        validateModel(model, true, false);
    }

    @Test
    void loopSizeFourBrokenByInjectionTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -----> B
        ^        |
        |        v
        D <----- C

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD = model.schedulerBuilder("D")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputA, SolderType.INJECT);

        validateModel(model, false, false);
    }

    @Test
    void loopSizeFourBrokenByMissingBoundTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A -----> B
        ^        |
        |        v
        D <----- C

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputA);

        validateModel(model, true, false);
    }

    @Test
    void loopSizeFourWithChainTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, true, false);
    }

    @Test
    void loopSizeFourWithChainBrokenByInjectionTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD, SolderType.INJECT);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, false, false);
    }

    @Test
    void loopSizeFourWithChainBrokenByMissingBoundTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD = model.schedulerBuilder("D")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, false, false);
    }

    @Test
    void multiLoopTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A <---------------------------------|
        |                                   |
        v                                   |
        B                                   |
        |                                   |
        v                                   |
        C                                   |
        |                                   |
        v                                   |
        D -----> E <---------------|        |
        ^        |                 |        |
        |        v                 |        |
        G <----- F -----> H -----> I -----> J

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        taskSchedulerJ.getOutputWire().solderTo(inputA);

        taskSchedulerI.getOutputWire().solderTo(inputE);

        validateModel(model, true, false);
    }

    @Test
    void multiLoopBrokenByInjectionTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A <---------------------------------|
        |                                   |
        v                                   |
        B                                   |
        |                                   |
        v                                   |
        C                                   |
        |                                   |
        v                                   |
        D -----> E <---------------|        |
        ^        |                 |        |
        |        v                 |        |
        G <----- F -----> H -----> I -----> J

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD, SolderType.INJECT);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        taskSchedulerJ.getOutputWire().solderTo(inputA, SolderType.INJECT);

        taskSchedulerI.getOutputWire().solderTo(inputE, SolderType.INJECT);

        validateModel(model, false, false);
    }

    @Test
    void multiLoopBrokenByMissingBoundTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A <---------------------------------|
        |                                   |
        v                                   |
        B                                   |
        |                                   |
        v                                   |
        C                                   |
        |                                   |
        v                                   |
        D -----> E <---------------|        |
        ^        |                 |        |
        |        v                 |        |
        G <----- F -----> H -----> I -----> J

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE = model.schedulerBuilder("E")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ = model.schedulerBuilder("J")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        taskSchedulerJ.getOutputWire().solderTo(inputA);

        taskSchedulerI.getOutputWire().solderTo(inputE);

        validateModel(model, false, false);
    }

    @Test
    void filterInCycleTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        Connection D -> E uses a filter

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD
                .getOutputWire()
                .buildFilter("onlyEven", "onlyEvenInput", x -> x % 2 == 0)
                .solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, true, false);
    }

    @Test
    void transformerInCycleTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        Connection D -> E uses a transformer

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD
                .getOutputWire()
                .buildTransformer("inverter", "inverterInput", x -> -x)
                .solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, true, false);
    }

    @Test
    void splitterInCycleTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        Connection D -> E uses a splitter

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<List<Integer>> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        final OutputWire<Integer> splitter = taskSchedulerD.getOutputWire().buildSplitter("splitter", "splitterInput");
        splitter.solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, true, false);
    }

    @Test
    void multipleOutputCycleTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A <---------------------------------|
        |                                   |
        v                                   |
        B                                   |
        |                                   |
        v                                   |
        C                                   |
        |                                   |
        v                                   |
        D -----> E <---------------|        |
        ^        |                 |        |
        |        v                 |        |
        G <----- F -----> H -----> I -----> J
                                   |        ^
                                   |        |
                                   |--------|

        I has secondary output channels

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final OutputWire<Integer> secondaryOutputI = taskSchedulerI.buildSecondaryOutputWire();
        final OutputWire<Integer> tertiaryOutputI = taskSchedulerI.buildSecondaryOutputWire();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");
        final InputWire<Integer> inputJ2 = taskSchedulerJ.buildInputWire("inputJ2");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        taskSchedulerJ.getOutputWire().solderTo(inputA);

        secondaryOutputI.solderTo(inputE);
        tertiaryOutputI.solderTo(inputJ2);

        validateModel(model, true, false);
    }

    /**
     * We should detect when a concurrent scheduler access a direct scheduler.
     */
    @Test
    void concurrentAccessingDirectTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        D = CONCURRENT
        E = DIRECT

        */

        final TaskScheduler<Integer> taskSchedulerA = model.schedulerBuilder("A")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB = model.schedulerBuilder("B")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC = model.schedulerBuilder("C")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD = model.schedulerBuilder("D")
                .withType(TaskSchedulerType.CONCURRENT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE = model.schedulerBuilder("E")
                .withType(TaskSchedulerType.DIRECT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF = model.schedulerBuilder("F")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG = model.schedulerBuilder("G")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH = model.schedulerBuilder("H")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI = model.schedulerBuilder("I")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ = model.schedulerBuilder("J")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, false, true);
    }

    /**
     * We should detect when a concurrent scheduler access a direct scheduler.
     */
    @Test
    void concurrentAccessingMultipleDirectTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        D = CONCURRENT
        E = DIRECT
        F = DIRECT

        */

        final TaskScheduler<Integer> taskSchedulerA = model.schedulerBuilder("A")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB = model.schedulerBuilder("B")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC = model.schedulerBuilder("C")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD = model.schedulerBuilder("D")
                .withType(TaskSchedulerType.CONCURRENT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE = model.schedulerBuilder("E")
                .withType(TaskSchedulerType.DIRECT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF = model.schedulerBuilder("F")
                .withType(TaskSchedulerType.DIRECT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG = model.schedulerBuilder("G")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH = model.schedulerBuilder("H")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI = model.schedulerBuilder("I")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ = model.schedulerBuilder("J")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, false, true);
    }

    /**
     * We should detect when a concurrent scheduler access a direct scheduler through proxies (i.e. the concurrent
     * scheduler calls into a DIRECT_THREADSAFE scheduler which calls into a DIRECT scheduler).
     */
    @Test
    void concurrentAccessingDirectThroughProxyTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        D = CONCURRENT
        E = DIRECT_THREADSAFE
        F = DIRECT_THREADSAFE
        G = DIRECT

        */

        final TaskScheduler<Integer> taskSchedulerA = model.schedulerBuilder("A")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB = model.schedulerBuilder("B")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD = model.schedulerBuilder("D")
                .withType(TaskSchedulerType.CONCURRENT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE = model.schedulerBuilder("E")
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF = model.schedulerBuilder("F")
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG = model.schedulerBuilder("G")
                .withType(TaskSchedulerType.DIRECT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH = model.schedulerBuilder("H")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI = model.schedulerBuilder("I")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ = model.schedulerBuilder("J")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, false, true);
    }

    /**
     * We should detect when multiple sequential schedulers call into a scheduler.
     */
    @Test
    void multipleSequentialSchedulerTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        /*

        A
        |
        v
        B
        |
        v
        C
        |
        v
        D -----> E
        ^        |
        |        v
        G <----- F -----> H -----> I -----> J

        B = SEQUENTIAL_THREAD
        C = DIRECT_THREADSAFE
        D = DIRECT

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final InputWire<Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB = model.schedulerBuilder("B")
                .withType(TaskSchedulerType.SEQUENTIAL_THREAD)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC = model.schedulerBuilder("C")
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD = model.schedulerBuilder("D")
                .withType(TaskSchedulerType.DIRECT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE = model.schedulerBuilder("E")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF = model.schedulerBuilder("F")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG = model.schedulerBuilder("G")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH = model.schedulerBuilder("H")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI = model.schedulerBuilder("I")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ = model.schedulerBuilder("J")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final InputWire<Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, false, true);
    }

    @Test
    void unboundInputWireTest() {
        final WiringModel model = WiringModelBuilder.create(
                        TestPlatformContextBuilder.create().build())
                .build();

        final TaskScheduler<Integer> taskSchedulerA = model.schedulerBuilder("A")
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final BindableInputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        assertTrue(model.checkForUnboundInputWires());

        inputA.bindConsumer(x -> {});

        model.start();
        assertFalse(model.checkForUnboundInputWires());
        model.stop();
    }
}
