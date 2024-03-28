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

package com.swirlds.platform;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;
import static com.swirlds.platform.state.signed.StateToDiskReason.FATAL_ERROR;
import static com.swirlds.platform.state.signed.StateToDiskReason.ISS;
import static com.swirlds.platform.state.signed.StateToDiskReason.PERIODIC_SNAPSHOT;
import static java.nio.file.Files.exists;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.config.StateCommonConfig_;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.metrics.api.Counter;
import com.swirlds.platform.components.DefaultSavedStateController;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.config.StateConfig_;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.DeserializedSignedState;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SavedStateMetadata;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateFilePath;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.platform.state.signed.SignedStateFileUtils;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.test.fixtures.state.DummySwirldState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SignedStateFileManager Tests")
class SignedStateFileManagerTests {

    private static final NodeId SELF_ID = new NodeId(1234);
    private static final String MAIN_CLASS_NAME = "com.swirlds.foobar";
    private static final String SWIRLD_NAME = "mySwirld";

    private PlatformContext context;
    private SignedStateFilePath signedStateFilePath;

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
        TemporaryFileBuilder.overrideTemporaryFileLocation(testDirectory);
        final TestConfigBuilder configBuilder = new TestConfigBuilder()
                .withValue(
                        StateCommonConfig_.SAVED_STATE_DIRECTORY,
                        testDirectory.toFile().toString());
        context = TestPlatformContextBuilder.create()
                .withConfiguration(configBuilder.getOrCreateConfig())
                .build();
        signedStateFilePath =
                new SignedStateFilePath(context.getConfiguration().getConfigData(StateCommonConfig.class));
    }

    private SignedStateMetrics buildMockMetrics() {
        final SignedStateMetrics metrics = mock(SignedStateMetrics.class);
        when(metrics.getWriteStateToDiskTimeMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStateToDiskTimeMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getTotalUnsignedDiskStatesMetric()).thenReturn(mock(Counter.class));
        return metrics;
    }

    /**
     * Make sure the signed state was properly saved.
     */
    private void validateSavingOfState(final SignedState originalState) throws IOException {

        final Path stateDirectory = signedStateFilePath.getSignedStateDirectory(
                MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME, originalState.getRound());

        validateSavingOfState(originalState, stateDirectory);
    }

    /**
     * Make sure the signed state was properly saved.
     */
    private void validateSavingOfState(final SignedState originalState, final Path stateDirectory) throws IOException {

        assertEventuallyEquals(
                -1, originalState::getReservationCount, Duration.ofSeconds(1), "invalid reservation count");

        final Path stateFile = stateDirectory.resolve(SignedStateFileUtils.SIGNED_STATE_FILE_NAME);
        final Path hashInfoFile = stateDirectory.resolve(SignedStateFileUtils.HASH_INFO_FILE_NAME);
        final Path settingsUsedFile = stateDirectory.resolve("settingsUsed.txt");

        assertTrue(exists(stateFile), "no state file found");
        assertTrue(exists(hashInfoFile), "no hash info file found");
        assertTrue(exists(settingsUsedFile), "no settings used file found");

        assertEquals(-1, originalState.getReservationCount(), "invalid reservation count");

        final DeserializedSignedState deserializedSignedState =
                readStateFile(TestPlatformContextBuilder.create().build(), stateFile);
        MerkleCryptoFactory.getInstance()
                .digestTreeSync(
                        deserializedSignedState.reservedSignedState().get().getState());

        assertNotNull(deserializedSignedState.originalHash(), "hash should not be null");
        assertNotSame(
                deserializedSignedState.reservedSignedState().get(),
                originalState,
                "deserialized object should not be the same");

        assertEquals(
                originalState.getState().getHash(),
                deserializedSignedState.reservedSignedState().get().getState().getHash(),
                "hash should match");
        assertEquals(originalState.getState().getHash(), deserializedSignedState.originalHash(), "hash should match");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Standard Operation Test")
    void standardOperationTest(final boolean successExpected) throws IOException {
        final SignedState signedState = new RandomSignedStateGenerator().build();

        if (!successExpected) {
            // To make the save fail, create a file with the name of the directory the state will try to be saved to
            final Path savedDir = signedStateFilePath.getSignedStateDirectory(
                    MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME, signedState.getRound());
            Files.createDirectories(savedDir.getParent());
            Files.createFile(savedDir);
        }

        final SignedStateFileManager manager = new SignedStateFileManager(
                context, buildMockMetrics(), new FakeTime(), MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);

        final StateSavingResult stateSavingResult = manager.saveStateTask(signedState.reserve("test"));

        if (successExpected) {
            assertNotNull(stateSavingResult, "If succeeded, should return a StateSavingResult");
            validateSavingOfState(signedState);
        } else {
            assertNull(stateSavingResult, "If unsuccessful, should return null");
        }
    }

    @Test
    @DisplayName("Save Fatal Signed State")
    void saveFatalSignedState() throws InterruptedException, IOException {
        final SignedState signedState = new RandomSignedStateGenerator().build();
        ((DummySwirldState) signedState.getSwirldState()).enableBlockingSerialization();

        final SignedStateFileManager manager = new SignedStateFileManager(
                context, buildMockMetrics(), new FakeTime(), MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);
        signedState.markAsStateToSave(FATAL_ERROR);

        final Thread thread = new ThreadConfiguration(getStaticThreadManager())
                .setInterruptableRunnable(
                        () -> manager.dumpStateTask(StateDumpRequest.create(signedState.reserve("test"))))
                .build(true);

        // State writing should be synchronized. So we shouldn't be able to finish until we unblock.
        MILLISECONDS.sleep(10);
        // shouldn't be finished yet
        assertTrue(thread.isAlive(), "thread should still be blocked");

        ((DummySwirldState) signedState.getSwirldState()).unblockSerialization();
        thread.join(1000);

        final Path stateDirectory = testDirectory.resolve("fatal").resolve("node1234_round" + signedState.getRound());
        validateSavingOfState(signedState, stateDirectory);
    }

    @Test
    @DisplayName("Save ISS Signed State")
    void saveISSignedState() throws IOException {
        final SignedState signedState = new RandomSignedStateGenerator().build();

        final SignedStateFileManager manager = new SignedStateFileManager(
                context, buildMockMetrics(), new FakeTime(), MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);
        signedState.markAsStateToSave(ISS);
        manager.dumpStateTask(StateDumpRequest.create(signedState.reserve("test")));

        final Path stateDirectory = testDirectory.resolve("iss").resolve("node1234_round" + signedState.getRound());
        validateSavingOfState(signedState, stateDirectory);
    }

    /**
     * Simulate a sequence of states where a state is saved periodically. Ensure that the proper states are saved, and
     * ensure that states on disk are deleted when they get too old.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Sequence Of States Test")
    void sequenceOfStatesTest(final boolean startAtGenesis) throws IOException {

        final Random random = getRandomPrintSeed();

        // Save state every 100 (simulated) seconds
        final int stateSavePeriod = 100;
        final int statesOnDisk = 3;
        final TestConfigBuilder configBuilder = new TestConfigBuilder()
                .withValue(StateConfig_.SAVE_STATE_PERIOD, stateSavePeriod)
                .withValue(StateConfig_.SIGNED_STATE_DISK, statesOnDisk)
                .withValue(
                        StateCommonConfig_.SAVED_STATE_DIRECTORY,
                        testDirectory.toFile().toString());
        final PlatformContext context = TestPlatformContextBuilder.create()
                .withConfiguration(configBuilder.getOrCreateConfig())
                .build();

        final int totalStates = 100;
        final int averageTimeBetweenStates = 10;
        final double standardDeviationTimeBetweenStates = 0.5;

        final SignedStateFileManager manager = new SignedStateFileManager(
                context, buildMockMetrics(), new FakeTime(), MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);
        final SavedStateController controller =
                new DefaultSavedStateController(context.getConfiguration().getConfigData(StateConfig.class));

        Instant timestamp;
        final long firstRound;
        Instant nextBoundary;
        final List<SignedState> savedStates = new ArrayList<>();

        if (startAtGenesis) {
            timestamp = Instant.EPOCH;
            firstRound = 1;
            nextBoundary = null;
        } else {
            firstRound = random.nextInt(1000);
            timestamp = Instant.ofEpochSecond(random.nextInt(1000));

            final SignedState initialState = new RandomSignedStateGenerator(random)
                    .setConsensusTimestamp(timestamp)
                    .setRound(firstRound)
                    .build();
            savedStates.add(initialState);
            controller.registerSignedStateFromDisk(initialState);

            nextBoundary = Instant.ofEpochSecond(
                    timestamp.getEpochSecond() / stateSavePeriod * stateSavePeriod + stateSavePeriod);
        }

        for (long round = firstRound; round < totalStates + firstRound; round++) {

            final int secondsDelta = (int)
                    Math.max(1, random.nextGaussian() * standardDeviationTimeBetweenStates + averageTimeBetweenStates);

            timestamp = timestamp.plus(secondsDelta, ChronoUnit.SECONDS);

            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setConsensusTimestamp(timestamp)
                    .setRound(round)
                    .build();
            final ReservedSignedState reservedSignedState = signedState.reserve("initialTestReservation");

            controller.markSavedState(signedState.reserve("markSavedState"));

            if (signedState.isStateToSave()) {
                assertTrue(
                        nextBoundary == null || CompareTo.isGreaterThanOrEqualTo(timestamp, nextBoundary),
                        "timestamp should be after the boundary");
                final StateSavingResult stateSavingResult = manager.saveStateTask(reservedSignedState);

                savedStates.add(signedState);

                validateSavingOfState(signedState);

                final List<SavedStateInfo> currentStatesOnDisk = new SignedStateFilePath(
                                context.getConfiguration().getConfigData(StateCommonConfig.class))
                        .getSavedStateFiles(MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);

                final SavedStateMetadata oldestMetadata =
                        currentStatesOnDisk.getLast().metadata();

                assertNotNull(stateSavingResult, "state should have been saved");
                assertEquals(
                        oldestMetadata.minimumGenerationNonAncient(),
                        stateSavingResult.oldestMinimumGenerationOnDisk());

                assertTrue(
                        currentStatesOnDisk.size() <= statesOnDisk,
                        "unexpected number of states on disk, current number = " + currentStatesOnDisk.size());

                for (int index = 0; index < currentStatesOnDisk.size(); index++) {

                    final SavedStateInfo savedStateInfo = currentStatesOnDisk.get(index);

                    final SignedState stateFromDisk = assertDoesNotThrow(
                            () -> SignedStateFileReader.readStateFile(
                                            TestPlatformContextBuilder.create().build(), savedStateInfo.stateFile())
                                    .reservedSignedState()
                                    .get(),
                            "should be able to read state on disk");

                    final SignedState originalState = savedStates.get(savedStates.size() - index - 1);
                    assertEquals(originalState.getRound(), stateFromDisk.getRound(), "round should match");
                    assertEquals(
                            originalState.getConsensusTimestamp(),
                            stateFromDisk.getConsensusTimestamp(),
                            "timestamp should match");
                }

                // The first state with a timestamp after this boundary should be saved
                nextBoundary = Instant.ofEpochSecond(
                        timestamp.getEpochSecond() / stateSavePeriod * stateSavePeriod + stateSavePeriod);
            } else {
                assertNotNull(nextBoundary, "if the next boundary is null then the state should have been saved");
                assertTrue(
                        CompareTo.isGreaterThan(nextBoundary, timestamp),
                        "next boundary should be after current timestamp");
            }
        }
    }

    @SuppressWarnings("resource")
    @Test
    @DisplayName("State Deletion Test")
    void stateDeletionTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final int statesOnDisk = 3;

        final TestConfigBuilder configBuilder = new TestConfigBuilder()
                .withValue(StateConfig_.SIGNED_STATE_DISK, statesOnDisk)
                .withValue(
                        StateCommonConfig_.SAVED_STATE_DIRECTORY,
                        testDirectory.toFile().toString());
        final PlatformContext context = TestPlatformContextBuilder.create()
                .withConfiguration(configBuilder.getOrCreateConfig())
                .build();

        final int count = 10;

        final SignedStateFileManager manager = new SignedStateFileManager(
                context, buildMockMetrics(), new FakeTime(), MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);

        final Path statesDirectory =
                signedStateFilePath.getSignedStatesDirectoryForSwirld(MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);

        // Simulate the saving of an ISS state
        final int issRound = 666;
        final Path issDirectory = signedStateFilePath
                .getSignedStatesBaseDirectory()
                .resolve("iss")
                .resolve("node" + SELF_ID + "_round" + issRound);
        final SignedState issState =
                new RandomSignedStateGenerator(random).setRound(issRound).build();
        issState.markAsStateToSave(ISS);
        manager.dumpStateTask(StateDumpRequest.create(issState.reserve("test")));
        validateSavingOfState(issState, issDirectory);

        // Simulate the saving of a fatal state
        final int fatalRound = 667;
        final Path fatalDirectory = signedStateFilePath
                .getSignedStatesBaseDirectory()
                .resolve("fatal")
                .resolve("node" + SELF_ID + "_round" + fatalRound);
        final SignedState fatalState =
                new RandomSignedStateGenerator(random).setRound(fatalRound).build();
        fatalState.markAsStateToSave(FATAL_ERROR);
        manager.dumpStateTask(StateDumpRequest.create(fatalState.reserve("test")));
        validateSavingOfState(fatalState, fatalDirectory);

        // Save a bunch of states. After each time, check the states that are still on disk.
        final List<SignedState> states = new ArrayList<>();
        for (int round = 1; round <= count; round++) {
            final SignedState signedState =
                    new RandomSignedStateGenerator(random).setRound(round).build();
            issState.markAsStateToSave(PERIODIC_SNAPSHOT);
            states.add(signedState);
            manager.saveStateTask(signedState.reserve("test"));

            // Verify that the states we want to be on disk are still on disk
            for (int i = 1; i <= statesOnDisk; i++) {
                final int roundToValidate = round - i;
                if (roundToValidate < 0) {
                    continue;
                }
                validateSavingOfState(states.get(roundToValidate));
            }

            // Verify that old states are properly deleted
            assertEquals(
                    Math.min(statesOnDisk, round),
                    (int) Files.list(statesDirectory).count(),
                    "unexpected number of states on disk after saving round " + round);

            // ISS/fatal state should still be in place
            validateSavingOfState(issState, issDirectory);
            validateSavingOfState(fatalState, fatalDirectory);
        }
    }
}
