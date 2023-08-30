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

package com.swirlds.platform.scratchpad;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.system.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Scratchpad Tests")
class ScratchpadTests {

    // TODO
    //  no file exists
    //  one file exists
    //  many files exist
    //  file is empty
    //  file is corrupted
    //  null value
    //  non-null value
    //  multiple updates

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private PlatformContext platformContext;

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.savedStateDirectory", testDirectory.toString())
                .getOrCreateConfig();
        platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @Test
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest() {
        final Random random = getRandomPrintSeed();

        final Scratchpad scratchpad = new Scratchpad(platformContext, new NodeId(0));
        final Path scratchpadDirectory = testDirectory.resolve("scratchpad").resolve("0");

        // No scratchpad file will exist until we write the first value
        assertFalse(scratchpadDirectory.toFile().exists());

        // Values are null by default
        assertNull(scratchpad.get(ScratchpadField.EPOCH_HASH));

        final Hash hash1 = randomHash(random);
        scratchpad.set(ScratchpadField.EPOCH_HASH, hash1);

        // After a write, there should always be exactly one scratchpad file (absent a crashing node)
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        assertEquals(hash1, scratchpad.get(ScratchpadField.EPOCH_HASH));

        final Hash hash2 = randomHash(random);
        scratchpad.set(ScratchpadField.EPOCH_HASH, hash2);

        // After a write, there should always be exactly one scratchpad file (absent a crashing node)
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        // Simulate a restart
        final Scratchpad scratchpad2 = new Scratchpad(platformContext, new NodeId(0));

        assertEquals(hash2, scratchpad2.get(ScratchpadField.EPOCH_HASH));
    }
}
