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
import com.swirlds.common.wiring.InputChannel;
import com.swirlds.common.wiring.Wire;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
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

        // Should not throw.
        final String diagram = model.generateWiringDiagram();
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

        final Wire<Void> wireA = model.wireBuilder("A").build().cast();

        validateModel(model, false);
    }

    @Test
    void shortChainTest() {
        final WiringModel model =
                WiringModel.create(TestPlatformContextBuilder.create().build(), Time.getCurrent());

        /*

        A -> B -> C

        */

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Void> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Void> inputC = wireC.buildInputChannel("inputC");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        wireA.solderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        wireA.injectionSolderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        wireA.solderTo(inputB);
        wireB.solderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        wireA.solderTo(inputB);
        wireB.injectionSolderTo(inputA);

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

        final Wire<Integer> wireA = model.wireBuilder("A").build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        wireA.solderTo(inputB);
        wireB.solderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.injectionSolderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC = model.wireBuilder("C").build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.solderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD = model.wireBuilder("D").build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.injectionSolderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.solderTo(inputA);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        final Wire<Integer> wireE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputE = wireE.buildInputChannel("inputE");

        final Wire<Integer> wireF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputF = wireF.buildInputChannel("inputF");

        final Wire<Integer> wireG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputG = wireG.buildInputChannel("inputG");

        final Wire<Integer> wireH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputH = wireH.buildInputChannel("inputH");

        final Wire<Integer> wireI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputI = wireI.buildInputChannel("inputI");

        final Wire<Integer> wireJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputJ = wireJ.buildInputChannel("inputJ");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.solderTo(inputE);
        wireE.solderTo(inputF);
        wireF.solderTo(inputG);
        wireG.solderTo(inputD);

        wireF.solderTo(inputH);
        wireH.solderTo(inputI);
        wireI.solderTo(inputJ);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        final Wire<Integer> wireE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputE = wireE.buildInputChannel("inputE");

        final Wire<Integer> wireF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputF = wireF.buildInputChannel("inputF");

        final Wire<Integer> wireG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputG = wireG.buildInputChannel("inputG");

        final Wire<Integer> wireH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputH = wireH.buildInputChannel("inputH");

        final Wire<Integer> wireI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputI = wireI.buildInputChannel("inputI");

        final Wire<Integer> wireJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputJ = wireJ.buildInputChannel("");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.solderTo(inputE);
        wireE.solderTo(inputF);
        wireF.solderTo(inputG);
        wireG.injectionSolderTo(inputD);

        wireF.solderTo(inputH);
        wireH.solderTo(inputI);
        wireI.solderTo(inputJ);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD = model.wireBuilder("D").build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        final Wire<Integer> wireE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputE = wireE.buildInputChannel("inputE");

        final Wire<Integer> wireF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputF = wireF.buildInputChannel("inputF");

        final Wire<Integer> wireG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputG = wireG.buildInputChannel("inputG");

        final Wire<Integer> wireH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputH = wireH.buildInputChannel("inputH");

        final Wire<Integer> wireI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputI = wireI.buildInputChannel("inputI");

        final Wire<Integer> wireJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputJ = wireJ.buildInputChannel("inputJ");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.solderTo(inputE);
        wireE.solderTo(inputF);
        wireF.solderTo(inputG);
        wireG.solderTo(inputD);

        wireF.solderTo(inputH);
        wireH.solderTo(inputI);
        wireI.solderTo(inputJ);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        final Wire<Integer> wireE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputE = wireE.buildInputChannel("inputE");

        final Wire<Integer> wireF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputF = wireF.buildInputChannel("inputF");

        final Wire<Integer> wireG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputG = wireG.buildInputChannel("inputG");

        final Wire<Integer> wireH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputH = wireH.buildInputChannel("inputH");

        final Wire<Integer> wireI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputI = wireI.buildInputChannel("inputI");

        final Wire<Integer> wireJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputJ = wireJ.buildInputChannel("inputJ");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.solderTo(inputE);
        wireE.solderTo(inputF);
        wireF.solderTo(inputG);
        wireG.solderTo(inputD);

        wireF.solderTo(inputH);
        wireH.solderTo(inputI);
        wireI.solderTo(inputJ);

        wireJ.solderTo(inputA);

        wireI.solderTo(inputE);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        final Wire<Integer> wireE =
                model.wireBuilder("E").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputE = wireE.buildInputChannel("inputE");

        final Wire<Integer> wireF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputF = wireF.buildInputChannel("inputF");

        final Wire<Integer> wireG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputG = wireG.buildInputChannel("inputG");

        final Wire<Integer> wireH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputH = wireH.buildInputChannel("inputH");

        final Wire<Integer> wireI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputI = wireI.buildInputChannel("inputI");

        final Wire<Integer> wireJ =
                model.wireBuilder("J").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputJ = wireJ.buildInputChannel("inputJ");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.solderTo(inputE);
        wireE.solderTo(inputF);
        wireF.solderTo(inputG);
        wireG.injectionSolderTo(inputD);

        wireF.solderTo(inputH);
        wireH.solderTo(inputI);
        wireI.solderTo(inputJ);

        wireJ.injectionSolderTo(inputA);

        wireI.injectionSolderTo(inputE);

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

        final Wire<Integer> wireA =
                model.wireBuilder("A").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputA = wireA.buildInputChannel("inputA");

        final Wire<Integer> wireB =
                model.wireBuilder("B").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputB = wireB.buildInputChannel("inputB");

        final Wire<Integer> wireC =
                model.wireBuilder("C").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputC = wireC.buildInputChannel("inputC");

        final Wire<Integer> wireD =
                model.wireBuilder("D").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputD = wireD.buildInputChannel("inputD");

        final Wire<Integer> wireE = model.wireBuilder("E").build().cast();
        final InputChannel<Integer, Integer> inputE = wireE.buildInputChannel("inputE");

        final Wire<Integer> wireF =
                model.wireBuilder("F").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputF = wireF.buildInputChannel("inputF");

        final Wire<Integer> wireG =
                model.wireBuilder("G").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputG = wireG.buildInputChannel("inputG");

        final Wire<Integer> wireH =
                model.wireBuilder("H").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputH = wireH.buildInputChannel("inputH");

        final Wire<Integer> wireI =
                model.wireBuilder("I").withUnhandledTaskCapacity(1).build().cast();
        final InputChannel<Integer, Integer> inputI = wireI.buildInputChannel("inputI");

        final Wire<Integer> wireJ = model.wireBuilder("J").build().cast();
        final InputChannel<Integer, Integer> inputJ = wireJ.buildInputChannel("inputJ");

        wireA.solderTo(inputB);
        wireB.solderTo(inputC);
        wireC.solderTo(inputD);
        wireD.solderTo(inputE);
        wireE.solderTo(inputF);
        wireF.solderTo(inputG);
        wireG.solderTo(inputD);

        wireF.solderTo(inputH);
        wireH.solderTo(inputI);
        wireI.solderTo(inputJ);

        wireJ.solderTo(inputA);

        wireI.solderTo(inputE);

        validateModel(model, false);
    }

    // TODO test with transformers
}
