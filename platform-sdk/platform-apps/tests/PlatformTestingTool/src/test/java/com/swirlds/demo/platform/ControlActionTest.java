/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.io.InputOutputStream;
import com.swirlds.demo.platform.actions.QuorumResult;
import com.swirlds.demo.platform.fs.stresstest.proto.ControlType;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ControlActionTest {
    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @Test
    void SerializeControlActionTest() throws IOException {
        ControlAction controlAction = new ControlAction(Instant.now(), ControlType.ENTER_VALIDATION);

        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeSerializable(controlAction, true);
        io.startReading();
        ControlAction fromStream = io.getInput().readSerializable();

        assertEquals(controlAction, fromStream);
    }

    @Test
    void SerializeQuorumResultTest() throws IOException {
        ControlAction controlAction = new ControlAction(Instant.now(), ControlType.ENTER_VALIDATION);

        ControlAction controlActionExit = new ControlAction(Instant.now().minusSeconds(5), ControlType.EXIT_SYNC);

        AtomicReferenceArray<ControlAction> lastResultValues = new AtomicReferenceArray<>(4);
        lastResultValues.set(0, controlAction);
        lastResultValues.set(1, controlAction);
        lastResultValues.set(2, controlActionExit);

        QuorumResult<ControlAction> quorumResult =
                new QuorumResult<ControlAction>(false, controlAction, lastResultValues);

        InputOutputStream streamInput = new InputOutputStream();
        streamInput.getOutput().writeSerializable(quorumResult, true);
        streamInput.startReading();
        QuorumResult<ControlAction> quorumResultFromStream =
                streamInput.getInput().readSerializable();
        assertEquals(quorumResult, quorumResultFromStream);
    }
}
