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

package com.swirlds.platform.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.io.InputOutputStream;
import com.swirlds.common.test.state.DummySwirldState1;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("State Registry Tests")
class StateRegistryTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructable(new ClassConstructorPair(State.class, State::new));
        registry.registerConstructable(new ClassConstructorPair(PlatformState.class, PlatformState::new));
        registry.registerConstructable(new ClassConstructorPair(DummySwirldState1.class, DummySwirldState1::new));
    }

    @AfterAll
    static void tearDown() {
        // Don't leave the registry with a strange configuration after these tests
        RuntimeObjectRegistry.reset();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Active State Count Test")
    void activeStateCountTest() throws IOException {

        // Restore the registry to its original condition at boot time
        RuntimeObjectRegistry.reset();

        assertEquals(0, RuntimeObjectRegistry.getActiveObjectsCount(State.class), "no states have been created yet");

        final List<State> states = new LinkedList<>();
        // Create a bunch of states
        for (int i = 0; i < 100; i++) {
            states.add(new State());
            assertEquals(
                    states.size(),
                    RuntimeObjectRegistry.getActiveObjectsCount(State.class),
                    "actual count should match expected count");
        }

        // Fast copy a state
        final State stateToCopy = new State();
        states.add(stateToCopy);
        final State copyOfStateToCopy = stateToCopy.copy();
        states.add(copyOfStateToCopy);
        assertEquals(
                states.size(),
                RuntimeObjectRegistry.getActiveObjectsCount(State.class),
                "actual count should match expected count");

        final Path dir = testDirectory;

        // Deserialize a state
        final State stateToSerialize = new State();
        stateToSerialize.setPlatformState(new PlatformState());
        stateToSerialize.setSwirldState(new DummySwirldState1());
        states.add(stateToSerialize);
        final InputOutputStream io = new InputOutputStream();
        io.getOutput().writeMerkleTree(dir, stateToSerialize);
        io.startReading();
        final State deserializedState = io.getInput().readMerkleTree(dir, 5);
        states.add(deserializedState);
        assertEquals(
                states.size(),
                RuntimeObjectRegistry.getActiveObjectsCount(State.class),
                "actual count should match expected count");

        // Deleting states in a random order should cause the number of states to decrease
        final Random random = new Random();
        while (states.size() > 0) {
            states.remove(random.nextInt(states.size())).release();
            assertEquals(
                    states.size(),
                    RuntimeObjectRegistry.getActiveObjectsCount(State.class),
                    "actual count should match expected count");
        }
    }
}
