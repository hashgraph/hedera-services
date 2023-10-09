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

package com.swirlds.platform;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.throwIfFileExists;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.CURRENT_ADDRESS_BOOK_FILE_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.HASH_INFO_FILE_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.SIGNED_STATE_FILE_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStateDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesBaseDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesDirectoryForApp;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesDirectoryForNode;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesDirectoryForSwirld;
import static com.swirlds.platform.state.signed.SignedStateFileWriter.writeHashInfoFile;
import static com.swirlds.platform.state.signed.SignedStateFileWriter.writeSignedStateToDisk;
import static com.swirlds.platform.state.signed.SignedStateFileWriter.writeStateFile;
import static java.nio.file.Files.exists;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.system.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.DeserializedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileUtils;
import com.swirlds.platform.state.signed.StateToDiskReason;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SignedState Read/Write Test")
class SignedStateFileReadWriteTest {

    private static final NodeId SELF_ID = new NodeId(1234);
    private static final String MAIN_CLASS_NAME = "com.swirlds.foobar";
    private static final String SWIRLD_NAME = "mySwirld";
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");
    }

    @BeforeEach
    void beforeEach() throws IOException {
        TemporaryFileBuilder.overrideTemporaryFileLocation(testDirectory.resolve("tmp"));
    }

    @Test
    @DisplayName("writeHashInfoFile() Test")
    void writeHashInfoFileTest() throws IOException {

        final State state = new RandomSignedStateGenerator().build().getState();
        writeHashInfoFile(testDirectory, state);
        final StateConfig stateConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(StateConfig.class);

        final Path hashInfoFile = testDirectory.resolve(SignedStateFileUtils.HASH_INFO_FILE_NAME);
        assertTrue(exists(hashInfoFile), "file should exist");

        final String hashInfoString = new MerkleTreeVisualizer(state)
                .setDepth(stateConfig.debugHashDepth())
                .render();

        final StringBuilder sb = new StringBuilder();
        try (final BufferedReader br = new BufferedReader(new FileReader(hashInfoFile.toFile()))) {
            br.lines().forEach(line -> sb.append(line).append("\n"));
        }

        final String fileString = sb.toString();
        assertTrue(fileString.contains(hashInfoString), "hash info string not found");
    }

    @Test
    @DisplayName("Write Then Read State File Test")
    void writeThenReadStateFileTest() throws IOException {
        final SignedState signedState = new RandomSignedStateGenerator().build();
        final Path stateFile = testDirectory.resolve(SIGNED_STATE_FILE_NAME);

        assertFalse(exists(stateFile), "signed state file should not yet exist");
        writeStateFile(testDirectory, signedState);
        assertTrue(exists(stateFile), "signed state file should be present");

        final DeserializedSignedState deserializedSignedState =
                readStateFile(TestPlatformContextBuilder.create().build(), stateFile);
        MerkleCryptoFactory.getInstance()
                .digestTreeSync(
                        deserializedSignedState.reservedSignedState().get().getState());

        assertNotNull(deserializedSignedState.originalHash(), "hash should not be null");
        assertEquals(signedState.getState().getHash(), deserializedSignedState.originalHash(), "hash should match");
        assertEquals(
                signedState.getState().getHash(),
                deserializedSignedState.reservedSignedState().get().getState().getHash(),
                "hash should match");
        assertNotSame(signedState, deserializedSignedState.reservedSignedState(), "state should be a different object");
    }

    @Test
    @DisplayName("writeSavedStateToDisk() Test")
    void writeSavedStateToDiskTest() throws IOException {
        final SignedState signedState = new RandomSignedStateGenerator().build();
        final Path directory = testDirectory.resolve("state");

        final Path stateFile = directory.resolve(SIGNED_STATE_FILE_NAME);
        final Path hashInfoFile = directory.resolve(HASH_INFO_FILE_NAME);
        final Path settingsUsedFile = directory.resolve("settingsUsed.txt");
        final Path addressBookFile = directory.resolve(CURRENT_ADDRESS_BOOK_FILE_NAME);

        throwIfFileExists(stateFile, hashInfoFile, settingsUsedFile, directory);
        final Configuration configuration = changeConfigAndConfigHolder("data/saved");
        writeSignedStateToDisk(
                new NodeId(0), directory, signedState, StateToDiskReason.PERIODIC_SNAPSHOT, configuration);

        assertTrue(exists(stateFile), "state file should exist");
        assertTrue(exists(hashInfoFile), "hash info file should exist");
        assertTrue(exists(settingsUsedFile), "settings used file should exist");
        assertTrue(exists(addressBookFile), "address book file should exist");
    }

    @Test
    @DisplayName("getSignedStateBaseDirectory() Test")
    void getSignedStateBaseDirectoryTest() {
        changeConfigAndConfigHolder("data/saved");

        assertEquals(getAbsolutePath("./data/saved"), getSignedStatesBaseDirectory(), "unexpected saved state file");

        changeConfigAndConfigHolder("foo/bar/baz");

        assertEquals(getAbsolutePath("./foo/bar/baz"), getSignedStatesBaseDirectory(), "unexpected saved state file");

        changeConfigAndConfigHolder("data/saved");
    }

    @Test
    @DisplayName("getSignedStatesDirectoryForApp() Test")
    void getSignedStatesDirectoryForAppTest() {
        changeConfigAndConfigHolder("data/saved");

        assertEquals(
                getAbsolutePath("./data/saved/com.swirlds.foobar"),
                getSignedStatesDirectoryForApp("com.swirlds.foobar"),
                "unexpected saved state file");

        changeConfigAndConfigHolder("foo/bar/baz");

        assertEquals(
                getAbsolutePath("./foo/bar/baz/com.swirlds.barfoo"),
                getSignedStatesDirectoryForApp("com.swirlds.barfoo"),
                "unexpected saved state file");

        changeConfigAndConfigHolder("data/saved");
    }

    @Test
    @DisplayName("getSignedStatesDirectoryForNode() Test")
    void getSignedStatesDirectoryForNodeTest() {
        changeConfigAndConfigHolder("data/saved");

        assertEquals(
                getAbsolutePath("./data/saved/com.swirlds.foobar/1234"),
                getSignedStatesDirectoryForNode("com.swirlds.foobar", new NodeId(1234)),
                "unexpected saved state file");

        changeConfigAndConfigHolder("foo/bar/baz");

        assertEquals(
                getAbsolutePath("./foo/bar/baz/com.swirlds.barfoo/4321"),
                getSignedStatesDirectoryForNode("com.swirlds.barfoo", new NodeId(4321)),
                "unexpected saved state file");

        changeConfigAndConfigHolder("data/saved");
    }

    @Test
    @DisplayName("getSignedStatesDirectoryForSwirld() Test")
    void getSignedStatesDirectoryForSwirldTest() {
        changeConfigAndConfigHolder("data/saved");

        assertEquals(
                getAbsolutePath("./data/saved/com.swirlds.foobar/1234/mySwirld"),
                getSignedStatesDirectoryForSwirld("com.swirlds.foobar", new NodeId(1234), "mySwirld"),
                "unexpected saved state file");

        changeConfigAndConfigHolder("foo/bar/baz");

        assertEquals(
                getAbsolutePath("./foo/bar/baz/com.swirlds.barfoo/4321/myOtherSwirld"),
                getSignedStatesDirectoryForSwirld("com.swirlds.barfoo", new NodeId(4321), "myOtherSwirld"),
                "unexpected saved state file");

        changeConfigAndConfigHolder("data/saved");
    }

    @Test
    @DisplayName("getSignedStateDirectory() Test")
    void getSignedStateDirectoryTest() {
        changeConfigAndConfigHolder("data/saved");

        assertEquals(
                getAbsolutePath("./data/saved/com.swirlds.foobar/1234/mySwirld/1337"),
                getSignedStateDirectory("com.swirlds.foobar", new NodeId(1234), "mySwirld", 1337),
                "unexpected saved state file");

        changeConfigAndConfigHolder("foo/bar/baz");

        assertEquals(
                getAbsolutePath("./foo/bar/baz/com.swirlds.barfoo/4321/myOtherSwirld/42"),
                getSignedStateDirectory("com.swirlds.barfoo", new NodeId(4321), "myOtherSwirld", 42),
                "unexpected saved state file");

        changeConfigAndConfigHolder("data/saved");
    }

    private Configuration changeConfigAndConfigHolder(String directory) {
        return new TestConfigBuilder()
                .withValue("state.savedStateDirectory", directory)
                .getOrCreateConfig();
    }
}
