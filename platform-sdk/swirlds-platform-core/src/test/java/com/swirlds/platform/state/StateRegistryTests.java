/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.registerMerkleStateRootClassIds;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.test.fixtures.state.BlockingSwirldState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("State Registry Tests")
class StateRegistryTests {

    private static ConstructableRegistry registry;
    private static SemanticVersion version;
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private static final Function<SemanticVersion, SoftwareVersion> softwareVersionSupplier =
            version -> new BasicSoftwareVersion(version.major());

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        registry = ConstructableRegistry.getInstance();
        version = SemanticVersion.newBuilder().major(nextInt(1, 100)).build();
        registry.registerConstructable(new ClassConstructorPair(BlockingSwirldState.class, BlockingSwirldState::new));
        registerMerkleStateRootClassIds();
    }

    @AfterAll
    static void tearDown() {
        // Don't leave the registry with a strange configuration after these tests
        RuntimeObjectRegistry.reset();
    }

    @Test
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Active State Count Test")
    void activeStateCountTest() throws IOException {

        // Restore the registry to its original condition at boot time
        RuntimeObjectRegistry.reset();

        assertEquals(
                0,
                RuntimeObjectRegistry.getActiveObjectsCount(MerkleStateRoot.class),
                "no states have been created yet");

        final List<MerkleRoot> states = new LinkedList<>();
        // Create a bunch of states
        for (int i = 0; i < 100; i++) {
            states.add(new PlatformMerkleStateRoot(FAKE_MERKLE_STATE_LIFECYCLES, softwareVersionSupplier));
            assertEquals(
                    states.size(),
                    RuntimeObjectRegistry.getActiveObjectsCount(PlatformMerkleStateRoot.class),
                    "actual count should match expected count");
        }

        // Fast copy a state
        final MerkleRoot stateToCopy =
                new PlatformMerkleStateRoot(FAKE_MERKLE_STATE_LIFECYCLES, softwareVersionSupplier);
        states.add(stateToCopy);
        final MerkleRoot copyOfStateToCopy = stateToCopy.copy();
        states.add(copyOfStateToCopy);
        assertEquals(
                states.size(),
                RuntimeObjectRegistry.getActiveObjectsCount(PlatformMerkleStateRoot.class),
                "actual count should match expected count");

        final Path dir = testDirectory;

        // Deserialize a state
        final PlatformMerkleStateRoot stateToSerialize =
                new PlatformMerkleStateRoot(FAKE_MERKLE_STATE_LIFECYCLES, softwareVersionSupplier);
        FAKE_MERKLE_STATE_LIFECYCLES.initPlatformState(stateToSerialize);
        final var platformState = stateToSerialize.getWritablePlatformState();
        platformState.bulkUpdate(v -> {
            v.setCreationSoftwareVersion(new BasicSoftwareVersion(version.minor()));
            v.setLegacyRunningEventHash(new Hash());
        });

        states.add(stateToSerialize);
        final InputOutputStream io = new InputOutputStream();
        io.getOutput().writeMerkleTree(dir, stateToSerialize);
        io.startReading();
        final MerkleRoot deserializedState = io.getInput().readMerkleTree(dir, 5);
        states.add(deserializedState);
        assertEquals(
                states.size(),
                RuntimeObjectRegistry.getActiveObjectsCount(PlatformMerkleStateRoot.class),
                "actual count should match expected count");

        // Deleting states in a random order should cause the number of states to decrease
        final Random random = new Random();
        while (!states.isEmpty()) {
            states.remove(random.nextInt(states.size())).release();
            assertEquals(
                    states.size(),
                    RuntimeObjectRegistry.getActiveObjectsCount(PlatformMerkleStateRoot.class),
                    "actual count should match expected count");
        }
    }
}
