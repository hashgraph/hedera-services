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

package com.swirlds.common.wiring.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.base.time.Time;
import com.swirlds.common.wiring.InputWire;
import com.swirlds.common.wiring.ModelGroup;
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelTests {

    /**
     * For debugging with a human in the loop.
     */
    private static final boolean printMermaidDiagram = false;

    /**
     * Validate the model.
     *
     * @param model         the model to validate
     * @param cycleExpected true if a cycle is expected, false otherwise
     */
    private static void validateModel(@NonNull final WiringModel model, boolean cycleExpected) {
        final boolean cycleDetected = model.checkForCyclicalBackpressure();
        assertEquals(cycleExpected, cycleDetected);

        final Set<ModelGroup> groups = new HashSet<>();

        // Should not throw.
        final String diagram = model.generateWiringDiagram(groups);
        if (printMermaidDiagram) {
            System.out.println(diagram);
        }
    }

    @Test
    void emptyModelTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());
        validateModel(model, false);
    }

    @Test
    void singleVertexTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A

        */

        final TaskScheduler<Void> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();

        validateModel(model, false);
    }

    @Test
    void shortChainTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -> B -> C

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Void> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);

        validateModel(model, false);
    }

    @Test
    void loopSizeOneTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A --|
        ^   |
        |---|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        taskSchedulerA.getOutputWire().solderTo(inputA);

        validateModel(model, true);
    }

    @Test
    void loopSizeOneBrokenByInjectionTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A --|
        ^   |
        |---|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        taskSchedulerA.getOutputWire().solderTo(inputA, true);

        validateModel(model, false);
    }

    @Test
    void loopSizeTwoTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -> B
        ^    |
        |----|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputA);

        validateModel(model, true);
    }

    @Test
    void loopSizeTwoBrokenByInjectionTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -> B
        ^    |
        |----|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputA, true);

        validateModel(model, false);
    }

    @Test
    void loopSizeTwoBrokenByMissingBoundTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -> B
        ^    |
        |----|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputA);

        validateModel(model, false);
    }

    @Test
    void loopSizeThreeTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -> B -> C
        ^         |
        |---------|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputA);

        validateModel(model, true);
    }

    @Test
    void loopSizeThreeBrokenByInjectionTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -> B -> C
        ^         |
        |---------|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputA, true);

        validateModel(model, false);
    }

    @Test
    void loopSizeThreeBrokenByMissingBoundTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -> B -> C
        ^         |
        |---------|

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputA);

        validateModel(model, false);
    }

    @Test
    void loopSizeFourTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -----> B
        ^        |
        |        v
        D <----- C

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputA);

        validateModel(model, true);
    }

    @Test
    void loopSizeFourBrokenByInjectionTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -----> B
        ^        |
        |        v
        D <----- C

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputA, true);

        validateModel(model, false);
    }

    @Test
    void loopSizeFourBrokenByMissingBoundTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -----> B
        ^        |
        |        v
        D <----- C

        */

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputA);

        validateModel(model, true);
    }

    @Test
    void loopSizeFourWithChainTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

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

        validateModel(model, true);
    }

    @Test
    void loopSizeFourWithChainBrokenByInjectionTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD, true);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, false);
    }

    @Test
    void loopSizeFourWithChainBrokenByMissingBoundTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

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

        validateModel(model, false);
    }

    @Test
    void multiLoopTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

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

        validateModel(model, true);
    }

    @Test
    void multiLoopBrokenByInjectionTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD, true);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        taskSchedulerJ.getOutputWire().solderTo(inputA, true);

        taskSchedulerI.getOutputWire().solderTo(inputE, true);

        validateModel(model, false);
    }

    @Test
    void multiLoopBrokenByMissingBoundTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

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

        validateModel(model, false);
    }

    @Test
    void filterInCycleTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().buildFilter("onlyEven", x -> x % 2 == 0).solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, true);
    }

    @Test
    void transformerInCycleTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        taskSchedulerD.getOutputWire().buildTransformer("inverter", x -> -x).solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, true);
    }

    @Test
    void splitterInCycleTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<List<Integer>> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, List<Integer>> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.getOutputWire().solderTo(inputB);
        taskSchedulerB.getOutputWire().solderTo(inputC);
        taskSchedulerC.getOutputWire().solderTo(inputD);
        final OutputWire<Integer> splitter = taskSchedulerD.getOutputWire().buildSplitter();
        splitter.solderTo(inputE);
        taskSchedulerE.getOutputWire().solderTo(inputF);
        taskSchedulerF.getOutputWire().solderTo(inputG);
        taskSchedulerG.getOutputWire().solderTo(inputD);

        taskSchedulerF.getOutputWire().solderTo(inputH);
        taskSchedulerH.getOutputWire().solderTo(inputI);
        taskSchedulerI.getOutputWire().solderTo(inputJ);

        validateModel(model, true);
    }

    @Test
    void multipleOutputCycleTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

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
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.schedulerBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.schedulerBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.schedulerBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.schedulerBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.schedulerBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.schedulerBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.schedulerBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.schedulerBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final OutputWire<Integer> secondaryOutputI = taskSchedulerI.buildSecondaryOutputWire();
        final OutputWire<Integer> tertiaryOutputI = taskSchedulerI.buildSecondaryOutputWire();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.schedulerBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");
        final InputWire<Integer, Integer> inputJ2 = taskSchedulerJ.buildInputWire("inputJ2");

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

        validateModel(model, true);
    }
}
