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

package com.swirlds.platform.state.signed;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStateDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileWriter.writeSignedStateToDisk;
import static com.swirlds.platform.state.signed.StartupStateUtils.doRecoveryCleanup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.scratchpad.Scratchpad;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.fixtures.TestRecycleBin;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamConfig;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.recovery.RecoveryScratchpad;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.recovery.emergencyfile.Recovery;
import com.swirlds.platform.recovery.emergencyfile.State;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("StartupStateUtilities Tests")
class StartupStateUtilsTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private final NodeId selfId = new NodeId(0);
    private final String mainClassName = "mainClassName";
    private final String swirldName = "swirldName";

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @NonNull
    private PlatformContext buildContext(final boolean deleteInvalidStateFiles) {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.savedStateDirectory", testDirectory.toString())
                .withValue("state.deleteInvalidStateFiles", deleteInvalidStateFiles)
                .getOrCreateConfig();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        return platformContext;
    }

    /**
     * Write a state to disk in a location that will be discovered by {@link StartupStateUtils}.
     *
     * @return the signed state that was written to disk
     */
    @NonNull
    private SignedState writeState(
            @NonNull final Random random,
            @NonNull final PlatformContext platformContext,
            final long round,
            @Nullable final Hash epoch,
            final boolean corrupted)
            throws IOException {

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRound(round)
                .setEpoch(epoch)
                .build();

        final Path savedStateDirectory = getSignedStateDirectory(mainClassName, selfId, swirldName, round);

        writeSignedStateToDisk(
                selfId,
                savedStateDirectory,
                signedState,
                StateToDiskReason.PERIODIC_SNAPSHOT,
                platformContext.getConfiguration());

        if (corrupted) {
            final Path stateFilePath = savedStateDirectory.resolve("SignedState.swh");
            Files.delete(stateFilePath);
            final BufferedWriter writer = Files.newBufferedWriter(stateFilePath);
            writer.write("this is not a real state file");
            writer.close();
        }

        return signedState;
    }

    @Test
    @DisplayName("Genesis Test")
    void genesisTest() throws SignedStateLoadingException {
        final PlatformContext platformContext = buildContext(false);

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        mock(EmergencyRecoveryManager.class))
                .getNullable();

        assertNull(loadedState);
    }

    @Test
    @DisplayName("Normal Restart Test")
    void normalRestartTest() throws IOException, SignedStateLoadingException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(false);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        SignedState latestState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            latestState = writeState(random, platformContext, latestRound, null, false);
        }

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        mock(EmergencyRecoveryManager.class))
                .get();

        loadedState.getState().throwIfImmutable();
        loadedState.getState().throwIfDestroyed();

        assertEquals(latestState.getRound(), loadedState.getRound());
        assertEquals(latestState.getState().getHash(), loadedState.getState().getHash());
    }

    @Test
    @DisplayName("Corrupted State No Recycling Test")
    void corruptedStateNoRecyclingTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(false);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            final boolean corrupted = i == stateCount - 1;
            writeState(random, platformContext, latestRound, null, corrupted);
        }

        assertThrows(SignedStateLoadingException.class, () -> StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        mock(EmergencyRecoveryManager.class))
                .get());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("Corrupted State Recycling Permitted Test")
    void corruptedStateRecyclingPermittedTest(final int invalidStateCount)
            throws IOException, SignedStateLoadingException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(true);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        SignedState latestUncorruptedState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            final boolean corrupted = (stateCount - i) <= invalidStateCount;
            final SignedState state = writeState(random, platformContext, latestRound, null, corrupted);
            if (!corrupted) {
                latestUncorruptedState = state;
            }
        }

        final AtomicInteger recycleCount = new AtomicInteger(0);
        final RecycleBin recycleBin = spy(TestRecycleBin.getInstance());
        // increment recycle count every time recycleBin.recycle() is called
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    recycleCount.incrementAndGet();
                    return null;
                })
                .when(recycleBin)
                .recycle(any());

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        recycleBin,
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        mock(EmergencyRecoveryManager.class))
                .getNullable();

        if (latestUncorruptedState != null) {
            loadedState.getState().throwIfImmutable();
            loadedState.getState().throwIfDestroyed();

            assertEquals(latestUncorruptedState.getRound(), loadedState.getRound());
            assertEquals(
                    latestUncorruptedState.getState().getHash(),
                    loadedState.getState().getHash());
        } else {
            assertNull(loadedState);
        }

        final Path savedStateDirectory = getSignedStateDirectory(mainClassName, selfId, swirldName, latestRound)
                .getParent();

        assertEquals(5 - invalidStateCount, Files.list(savedStateDirectory).count());
        assertEquals(invalidStateCount, recycleCount.get());
    }

    @Test
    @DisplayName("Latest State Exact Epoch Hash Test")
    void latestStateHasExactEpochHashTest() throws IOException, SignedStateLoadingException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(false);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        SignedState latestState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            latestState = writeState(random, platformContext, latestRound, null, false);
        }

        final Hash epoch = latestState.getState().getHash();
        final long epochRound = latestState.getRound();

        final AtomicBoolean emergencyStateLoaded = new AtomicBoolean(false);

        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
        doAnswer(invocation -> {
                    emergencyStateLoaded.set(true);
                    return null;
                })
                .when(emergencyRecoveryManager)
                .emergencyStateLoaded();

        final State state = new State(epochRound, epoch, null);
        final Recovery recovery = new Recovery(state, null, null, null);
        final EmergencyRecoveryFile emergencyRecoveryFile = new EmergencyRecoveryFile(recovery);
        when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(emergencyRecoveryFile);

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        emergencyRecoveryManager)
                .get();

        loadedState.getState().throwIfImmutable();
        loadedState.getState().throwIfDestroyed();

        assertEquals(latestState.getRound(), loadedState.getRound());
        assertEquals(latestState.getState().getHash(), loadedState.getState().getHash());

        assertTrue(emergencyStateLoaded.get());
    }

    @Test
    @DisplayName("Previous State Has Epoch Hash Test")
    void previousStateHasExactEpochHashTest() throws IOException, SignedStateLoadingException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(false);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        SignedState targetState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            final SignedState state = writeState(random, platformContext, latestRound, null, false);
            if (i == 2) {
                targetState = state;
            }
        }

        final Hash epoch = targetState.getState().getHash();
        final long epochRound = targetState.getRound();

        final AtomicBoolean emergencyStateLoaded = new AtomicBoolean(false);

        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
        doAnswer(invocation -> {
                    emergencyStateLoaded.set(true);
                    return null;
                })
                .when(emergencyRecoveryManager)
                .emergencyStateLoaded();

        final State state = new State(epochRound, epoch, null);
        final Recovery recovery = new Recovery(state, null, null, null);
        final EmergencyRecoveryFile emergencyRecoveryFile = new EmergencyRecoveryFile(recovery);
        when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(emergencyRecoveryFile);

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        emergencyRecoveryManager)
                .get();

        loadedState.getState().throwIfImmutable();
        loadedState.getState().throwIfDestroyed();

        assertEquals(targetState.getRound(), loadedState.getRound());
        assertEquals(targetState.getState().getHash(), loadedState.getState().getHash());

        assertTrue(emergencyStateLoaded.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    @DisplayName("Previous State Has Epoch Hash")
    void noStateHasEpochHashPreviousRoundExistsTest(final int startingStateIndex)
            throws IOException, SignedStateLoadingException {

        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(false);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        SignedState targetState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            final SignedState state = writeState(random, platformContext, latestRound, null, false);
            if (i == startingStateIndex) {
                targetState = state;
            }
        }

        final Hash epoch = randomHash(random);
        final long epochRound = targetState.getRound() + 1;

        final AtomicBoolean emergencyStateLoaded = new AtomicBoolean(false);

        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
        doAnswer(invocation -> {
                    emergencyStateLoaded.set(true);
                    return null;
                })
                .when(emergencyRecoveryManager)
                .emergencyStateLoaded();

        final State state = new State(epochRound, epoch, null);
        final Recovery recovery = new Recovery(state, null, null, null);
        final EmergencyRecoveryFile emergencyRecoveryFile = new EmergencyRecoveryFile(recovery);
        when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(emergencyRecoveryFile);

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        emergencyRecoveryManager)
                .get();

        loadedState.getState().throwIfImmutable();
        loadedState.getState().throwIfDestroyed();

        assertEquals(targetState.getRound(), loadedState.getRound());
        assertEquals(targetState.getState().getHash(), loadedState.getState().getHash());
        assertNull(loadedState.getState().getPlatformState().getPlatformData().getEvents());

        // As a sanity check, make sure the consensus timestamp is the same. This is generated randomly, so if this
        // matches then it's a good signal that the correct state was loaded.
        assertEquals(
                targetState.getState().getPlatformState().getPlatformData().getConsensusTimestamp(),
                loadedState.getState().getPlatformState().getPlatformData().getConsensusTimestamp());

        assertFalse(emergencyStateLoaded.get());
    }

    @Test
    @DisplayName("Recover From Genesis Test")
    void recoverFromGenesisTest() throws IOException, SignedStateLoadingException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(false);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        SignedState firstState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            final SignedState state = writeState(random, platformContext, latestRound, null, false);
            if (i == 0) {
                firstState = state;
            }
        }

        final Hash epoch = randomHash(random);
        final long epochRound = firstState.getRound() - 1;

        final AtomicBoolean emergencyStateLoaded = new AtomicBoolean(false);

        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
        doAnswer(invocation -> {
                    emergencyStateLoaded.set(true);
                    return null;
                })
                .when(emergencyRecoveryManager)
                .emergencyStateLoaded();

        final State state = new State(epochRound, epoch, null);
        final Recovery recovery = new Recovery(state, null, null, null);
        final EmergencyRecoveryFile emergencyRecoveryFile = new EmergencyRecoveryFile(recovery);
        when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(emergencyRecoveryFile);

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        emergencyRecoveryManager)
                .getNullable();

        assertNull(loadedState);
        assertFalse(emergencyStateLoaded.get());
    }

    @Test
    @DisplayName("State After Epoch State Is Present Test")
    void stateAfterEpochStateIsPresentTest() throws IOException, SignedStateLoadingException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(false);

        int stateCount = 5;

        final Hash epoch = randomHash(random);

        int latestRound = random.nextInt(1_000, 10_000);
        SignedState targetState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);

            final Hash epochHash = i == (stateCount - 1) ? epoch : null;

            final SignedState state = writeState(random, platformContext, latestRound, epochHash, false);

            if (i == (stateCount - 1)) {
                targetState = state;
            }
        }

        final long epochRound = targetState.getRound() - 1;

        final AtomicBoolean emergencyStateLoaded = new AtomicBoolean(false);

        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
        doAnswer(invocation -> {
                    emergencyStateLoaded.set(true);
                    return null;
                })
                .when(emergencyRecoveryManager)
                .emergencyStateLoaded();

        final State state = new State(epochRound, epoch, null);
        final Recovery recovery = new Recovery(state, null, null, null);
        final EmergencyRecoveryFile emergencyRecoveryFile = new EmergencyRecoveryFile(recovery);
        when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(emergencyRecoveryFile);

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        emergencyRecoveryManager)
                .get();

        loadedState.getState().throwIfImmutable();
        loadedState.getState().throwIfDestroyed();

        assertEquals(targetState.getRound(), loadedState.getRound());
        assertEquals(targetState.getState().getHash(), loadedState.getState().getHash());

        assertTrue(emergencyStateLoaded.get());
    }

    @Test
    @DisplayName("Recovery Corrupted State No Recycling Test")
    void recoveryCorruptedStateNoRecyclingTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(false);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            final boolean corrupted = i == stateCount - 1;
            writeState(random, platformContext, latestRound, null, corrupted);
        }

        final Hash epoch = randomHash(random);
        final long epochRound = latestRound + 1;

        final AtomicBoolean emergencyStateLoaded = new AtomicBoolean(false);

        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
        doAnswer(invocation -> {
                    emergencyStateLoaded.set(true);
                    return null;
                })
                .when(emergencyRecoveryManager)
                .emergencyStateLoaded();

        final State state = new State(epochRound, epoch, null);
        final Recovery recovery = new Recovery(state, null, null, null);
        final EmergencyRecoveryFile emergencyRecoveryFile = new EmergencyRecoveryFile(recovery);
        when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(emergencyRecoveryFile);

        assertThrows(
                SignedStateLoadingException.class,
                () -> StartupStateUtils.loadStateFile(
                        platformContext,
                        TestRecycleBin.getInstance(),
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        emergencyRecoveryManager));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("Recovery Corrupted State Recycling Permitted Test")
    void recoveryCorruptedStateRecyclingPermittedTest(final int invalidStateCount)
            throws IOException, SignedStateLoadingException {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext = buildContext(true);

        int stateCount = 5;

        int latestRound = random.nextInt(1_000, 10_000);
        SignedState latestUncorruptedState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            final boolean corrupted = (stateCount - i) <= invalidStateCount;
            final SignedState state = writeState(random, platformContext, latestRound, null, corrupted);
            if (!corrupted) {
                latestUncorruptedState = state;
            }
        }

        final AtomicInteger recycleCount = new AtomicInteger(0);
        final RecycleBin recycleBin = spy(TestRecycleBin.getInstance());
        // increment recycle count every time recycleBin.recycle() is called
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    recycleCount.incrementAndGet();
                    return null;
                })
                .when(recycleBin)
                .recycle(any());

        final Hash epoch = randomHash(random);
        final long epochRound = latestRound + 1;

        final AtomicBoolean emergencyStateLoaded = new AtomicBoolean(false);

        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
        doAnswer(invocation -> {
                    emergencyStateLoaded.set(true);
                    return null;
                })
                .when(emergencyRecoveryManager)
                .emergencyStateLoaded();

        final State state = new State(epochRound, epoch, null);
        final Recovery recovery = new Recovery(state, null, null, null);
        final EmergencyRecoveryFile emergencyRecoveryFile = new EmergencyRecoveryFile(recovery);
        when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(emergencyRecoveryFile);

        final SignedState loadedState = StartupStateUtils.loadStateFile(
                        platformContext,
                        recycleBin,
                        selfId,
                        mainClassName,
                        swirldName,
                        new BasicSoftwareVersion(1),
                        emergencyRecoveryManager)
                .getNullable();

        if (latestUncorruptedState != null) {
            loadedState.getState().throwIfImmutable();
            loadedState.getState().throwIfDestroyed();

            assertEquals(latestUncorruptedState.getRound(), loadedState.getRound());

            assertEquals(
                    latestUncorruptedState.getState().getHash(),
                    loadedState.getState().getHash());

            // As a sanity check, make sure the consensus timestamp is the same. This is generated randomly, so if this
            // matches then it's a good signal that the correct state was loaded.
            assertEquals(
                    latestUncorruptedState
                            .getState()
                            .getPlatformState()
                            .getPlatformData()
                            .getConsensusTimestamp(),
                    loadedState.getState().getPlatformState().getPlatformData().getConsensusTimestamp());
        } else {
            assertNull(loadedState);
        }

        final Path savedStateDirectory = getSignedStateDirectory(mainClassName, selfId, swirldName, latestRound)
                .getParent();

        assertEquals(5 - invalidStateCount, Files.list(savedStateDirectory).count());
        assertEquals(invalidStateCount, recycleCount.get());
    }

    @Test
    @DisplayName("doRecoveryCleanup() Initial Epoch Test")
    void doRecoveryCleanupInitialEpochTest() throws IOException {

        final PlatformContext platformContext = buildContext(false);

        final AtomicInteger recycleCount = new AtomicInteger(0);
        final RecycleBin recycleBin = spy(TestRecycleBin.getInstance());
        // increment recycle count every time recycleBin.recycle() is called
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    recycleCount.incrementAndGet();
                    return null;
                })
                .when(recycleBin)
                .recycle(any());

        final Random random = getRandomPrintSeed();

        int stateCount = 5;
        int latestRound = random.nextInt(1_000, 10_000);
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            writeState(random, platformContext, latestRound, null, false);
        }

        // Write a file into the PCES directory. This file will be deleted if the PCES is cleared.
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);
        final Path savedStateDirectory = stateConfig.savedStateDirectory();
        final Path pcesDirectory = savedStateDirectory
                .resolve(preconsensusEventStreamConfig.databaseDirectory())
                .resolve("0");
        Files.createDirectories(pcesDirectory);

        final Path markerFile = pcesDirectory.resolve("markerFile");
        final BufferedWriter writer = Files.newBufferedWriter(markerFile);
        writer.write("this is a marker file");
        writer.close();

        doRecoveryCleanup(platformContext, recycleBin, selfId, swirldName, mainClassName, null, latestRound);

        final Path signedStateDirectory = getSignedStateDirectory(mainClassName, selfId, swirldName, latestRound)
                .getParent();

        assertEquals(0, recycleCount.get());
        assertEquals(stateCount, Files.list(signedStateDirectory).count());

        assertTrue(Files.exists(markerFile));
    }

    @Test
    @DisplayName("doRecoveryCleanup() Already Cleaned Up Test")
    void doRecoveryCleanupAlreadyCleanedUpTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final Hash epoch = randomHash(random);

        final PlatformContext platformContext = buildContext(false);

        final Scratchpad<RecoveryScratchpad> scratchpad =
                new Scratchpad<>(platformContext, selfId, RecoveryScratchpad.class, RecoveryScratchpad.SCRATCHPAD_ID);
        scratchpad.set(RecoveryScratchpad.EPOCH_HASH, epoch);

        final AtomicInteger recycleCount = new AtomicInteger(0);
        final RecycleBin recycleBin = spy(TestRecycleBin.getInstance());
        // increment recycle count every time recycleBin.recycle() is called
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    recycleCount.incrementAndGet();
                    return null;
                })
                .when(recycleBin)
                .recycle(any());

        int stateCount = 5;
        int latestRound = random.nextInt(1_000, 10_000);
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            writeState(random, platformContext, latestRound, null, false);
        }

        // Write a file into the PCES directory. This file will be deleted if the PCES is cleared.
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);
        final Path savedStateDirectory = stateConfig.savedStateDirectory();
        final Path pcesDirectory = savedStateDirectory
                .resolve(preconsensusEventStreamConfig.databaseDirectory())
                .resolve("0");
        Files.createDirectories(pcesDirectory);

        final Path markerFile = pcesDirectory.resolve("markerFile");
        final BufferedWriter writer = Files.newBufferedWriter(markerFile);
        writer.write("this is a marker file");
        writer.close();

        doRecoveryCleanup(platformContext, recycleBin, selfId, swirldName, mainClassName, epoch, latestRound);

        final Path signedStateDirectory = getSignedStateDirectory(mainClassName, selfId, swirldName, latestRound)
                .getParent();

        assertEquals(0, recycleCount.get());
        assertEquals(stateCount, Files.list(signedStateDirectory).count());

        assertTrue(Files.exists(markerFile));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("doRecoveryCleanup() Work Required Test")
    void doRecoveryCleanupWorkRequiredTest(final int statesToDelete) throws IOException {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext = buildContext(false);

        final AtomicInteger recycleCount = new AtomicInteger(0);
        final RecycleBin recycleBin = spy(TestRecycleBin.getInstance());
        // increment recycle count every time recycleBin.recycle() is called
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    recycleCount.incrementAndGet();
                    return null;
                })
                .when(recycleBin)
                .recycle(any());

        int stateCount = 5;
        int latestRound = random.nextInt(1_000, 10_000);
        SignedState targetState = null;
        for (int i = 0; i < stateCount; i++) {
            latestRound += random.nextInt(100, 200);
            final SignedState signedState = writeState(random, platformContext, latestRound, null, false);
            if (i == (stateCount - statesToDelete - 1)) {
                targetState = signedState;
            }
        }

        final Hash epoch = randomHash(random);
        final long epochRound;
        if (statesToDelete == stateCount) {
            // lower round than what all states have
            epochRound = 999;
        } else {
            epochRound = targetState.getRound();
        }

        // Write a file into the PCES directory. This file will should be deleted
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);
        final Path savedStateDirectory = stateConfig.savedStateDirectory();
        final Path pcesDirectory = savedStateDirectory
                .resolve(preconsensusEventStreamConfig.databaseDirectory())
                .resolve("0");
        Files.createDirectories(pcesDirectory);

        final Path markerFile = pcesDirectory.resolve("markerFile");
        final BufferedWriter writer = Files.newBufferedWriter(markerFile);
        writer.write("this is a marker file");
        writer.close();

        doRecoveryCleanup(platformContext, recycleBin, selfId, swirldName, mainClassName, epoch, epochRound);

        final Scratchpad<RecoveryScratchpad> scratchpad =
                new Scratchpad<>(platformContext, selfId, RecoveryScratchpad.class, RecoveryScratchpad.SCRATCHPAD_ID);

        assertEquals(epoch, scratchpad.get(RecoveryScratchpad.EPOCH_HASH));

        final Path signedStateDirectory = getSignedStateDirectory(mainClassName, selfId, swirldName, latestRound)
                .getParent();

        assertEquals(statesToDelete, recycleCount.get());

        assertEquals(
                stateCount - statesToDelete, Files.list(signedStateDirectory).count());

        assertTrue(Files.exists(markerFile));
    }
}
