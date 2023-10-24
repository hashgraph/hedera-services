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
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelTests {

    /**
     * For debugging with a human in the loop.
     */
    private static final boolean printMermaidDiagram = false;

    //    @BeforeAll
    //    public static void beforeAll() throws InterruptedException {
    //        // TODO remove
    //        Log4jSetup.startLoggingFramework(
    //
    // Path.of("/Users/codylittley/ws/hedera-services/platform-sdk/swirlds-cli/log4j2-stdout.xml"))
    //                .await();
    //    }

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

        // TODO create PCLI tool for drawing this type of diagram for the platform
        //        groups.add(new ModelGroup("foo", Set.of("A", "B", "C"), false));
        //        groups.add(new ModelGroup("bar", Set.of("D", "E", "F", "G"), false));
        //        groups.add(new ModelGroup("baz", Set.of("H", "I", "J"), false));

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
                model.wireBuilder("A").build().cast();

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Void> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Void> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        taskSchedulerA.solderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        taskSchedulerA.injectionSolderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.injectionSolderTo(inputA);

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
                model.wireBuilder("A").build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.injectionSolderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.solderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.injectionSolderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.solderTo(inputA);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.solderTo(inputE);
        taskSchedulerE.solderTo(inputF);
        taskSchedulerF.solderTo(inputG);
        taskSchedulerG.solderTo(inputD);

        taskSchedulerF.solderTo(inputH);
        taskSchedulerH.solderTo(inputI);
        taskSchedulerI.solderTo(inputJ);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.solderTo(inputE);
        taskSchedulerE.solderTo(inputF);
        taskSchedulerF.solderTo(inputG);
        taskSchedulerG.injectionSolderTo(inputD);

        taskSchedulerF.solderTo(inputH);
        taskSchedulerH.solderTo(inputI);
        taskSchedulerI.solderTo(inputJ);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.solderTo(inputE);
        taskSchedulerE.solderTo(inputF);
        taskSchedulerF.solderTo(inputG);
        taskSchedulerG.solderTo(inputD);

        taskSchedulerF.solderTo(inputH);
        taskSchedulerH.solderTo(inputI);
        taskSchedulerI.solderTo(inputJ);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.solderTo(inputE);
        taskSchedulerE.solderTo(inputF);
        taskSchedulerF.solderTo(inputG);
        taskSchedulerG.solderTo(inputD);

        taskSchedulerF.solderTo(inputH);
        taskSchedulerH.solderTo(inputI);
        taskSchedulerI.solderTo(inputJ);

        taskSchedulerJ.solderTo(inputA);

        taskSchedulerI.solderTo(inputE);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.solderTo(inputE);
        taskSchedulerE.solderTo(inputF);
        taskSchedulerF.solderTo(inputG);
        taskSchedulerG.injectionSolderTo(inputD);

        taskSchedulerF.solderTo(inputH);
        taskSchedulerH.solderTo(inputI);
        taskSchedulerI.solderTo(inputJ);

        taskSchedulerJ.injectionSolderTo(inputA);

        taskSchedulerI.injectionSolderTo(inputE);

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
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputA = taskSchedulerA.buildInputWire("inputA");

        final TaskScheduler<Integer> taskSchedulerB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputB = taskSchedulerB.buildInputWire("inputB");

        final TaskScheduler<Integer> taskSchedulerC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputC = taskSchedulerC.buildInputWire("inputC");

        final TaskScheduler<Integer> taskSchedulerD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputD = taskSchedulerD.buildInputWire("inputD");

        final TaskScheduler<Integer> taskSchedulerE =
                model.wireBuilder("E").build().cast();
        final InputWire<Integer, Integer> inputE = taskSchedulerE.buildInputWire("inputE");

        final TaskScheduler<Integer> taskSchedulerF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputF = taskSchedulerF.buildInputWire("inputF");

        final TaskScheduler<Integer> taskSchedulerG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputG = taskSchedulerG.buildInputWire("inputG");

        final TaskScheduler<Integer> taskSchedulerH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputH = taskSchedulerH.buildInputWire("inputH");

        final TaskScheduler<Integer> taskSchedulerI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputWire<Integer, Integer> inputI = taskSchedulerI.buildInputWire("inputI");

        final TaskScheduler<Integer> taskSchedulerJ =
                model.wireBuilder("J").build().cast();
        final InputWire<Integer, Integer> inputJ = taskSchedulerJ.buildInputWire("inputJ");

        taskSchedulerA.solderTo(inputB);
        taskSchedulerB.solderTo(inputC);
        taskSchedulerC.solderTo(inputD);
        taskSchedulerD.solderTo(inputE);
        taskSchedulerE.solderTo(inputF);
        taskSchedulerF.solderTo(inputG);
        taskSchedulerG.solderTo(inputD);

        taskSchedulerF.solderTo(inputH);
        taskSchedulerH.solderTo(inputI);
        taskSchedulerI.solderTo(inputJ);

        taskSchedulerJ.solderTo(inputA);

        taskSchedulerI.solderTo(inputE);

        validateModel(model, false);
    }

    // TODO test with transformers
}
