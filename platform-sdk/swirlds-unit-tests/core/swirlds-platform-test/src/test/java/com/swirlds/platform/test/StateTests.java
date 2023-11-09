/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import static com.swirlds.common.test.merkle.util.MerkleTestUtils.areTreesEqual;
import static com.swirlds.platform.test.PlatformStateUtils.randomPlatformState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.State;
import com.swirlds.platform.test.fixtures.state.DummySwirldState;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("State Tests")
class StateTests {

    private static State state;
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        new TestConfigBuilder().getOrCreateConfig();

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        state = new State();
        state.setPlatformState(randomPlatformState());
        state.setSwirldState(new DummySwirldState());
        state.setDualState(new DualStateImpl());

        state.invalidateHash();
        MerkleCryptoFactory.getInstance().digestTreeSync(state);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.IO)
    @DisplayName("State Serialization Test")
    void stateSerializationTest() throws IOException {
        InputOutputStream io = new InputOutputStream();

        io.getOutput().writeMerkleTree(testDirectory, state);

        io.startReading();

        final State decodedState = io.getInput().readMerkleTree(testDirectory, Integer.MAX_VALUE);
        MerkleCryptoFactory.getInstance().digestTreeSync(decodedState);

        assertEquals(state.getHash(), decodedState.getHash(), "expected trees to be equal");
        assertTrue(areTreesEqual(state, decodedState), "expected trees to be equal");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("State Copy Test")
    void stateCopyTest() {
        final State copiedState = state.copy();
        MerkleCryptoFactory.getInstance().digestTreeSync(copiedState);

        assertEquals(state.getHash(), copiedState.getHash(), "expected trees to be equal");
        assertTrue(areTreesEqual(state, copiedState), "expected trees to be equal");
    }
}
