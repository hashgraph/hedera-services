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

import static com.swirlds.platform.state.signed.SignedStateFileUtils.SIGNED_STATE_FILE_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.dispatch.triggers.control.ShutdownRequestedTrigger;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.reconnect.emergency.EmergencySignedStateValidator;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileWriter;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

public class SavedStateLoaderTests {

    private static boolean requireStateLoad;
    private static boolean checkSignedStateFromDisk;

    @TempDir
    Path tmpDir;

    private final SoftwareVersion version = new BasicSoftwareVersion(1);
    private final AddressBook addressBook = mock(AddressBook.class);
    private AtomicInteger shutdownCount;
    private ShutdownRequestedTrigger shutdownTrigger;
    private EmergencySignedStateValidator emergencyValidator;
    private EmergencyRecoveryManager emergencyRecoveryManager;
    private SavedStateLoader savedStateLoader;

    @BeforeEach
    void beforeEach() throws IOException {
        TemporaryFileBuilder.overrideTemporaryFileLocation(tmpDir.resolve("tmp"));

        shutdownCount = new AtomicInteger(0);
        emergencyValidator = mock(EmergencySignedStateValidator.class);

        shutdownTrigger = mock(ShutdownRequestedTrigger.class);
        doAnswer(invocation -> shutdownCount.getAndIncrement())
                .when(shutdownTrigger)
                .dispatch(any(), any());
    }

    private void resetCounters() {
        shutdownCount.set(0);
    }

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        requireStateLoad = Settings.getInstance().isRequireStateLoad();
        checkSignedStateFromDisk = Settings.getInstance().isRequireStateLoad();
        ConstructableRegistry.getInstance().registerConstructables("");
    }

    @AfterAll
    static void afterAll() {
        Settings.getInstance().setRequireStateLoad(requireStateLoad);
        Settings.getInstance().setCheckSignedStateFromDisk(checkSignedStateFromDisk);
    }

    @Test
    @DisplayName("Null Constructor Args")
    void testNullConstructorArgs() {
        emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        testNullShutdownTrigger();
        testNullAddressBook();
        testNullSavedStateInfos();
        testNullVersion();
        testNullEmergencyValidatorSupplier();
        testNullEmergencyValidatorValue();
        testNullEmergencyRecoveryManager();
    }

    private void testNullShutdownTrigger() {
        assertThrows(
                NullPointerException.class,
                () -> new SavedStateLoader(
                        null,
                        addressBook,
                        new SavedStateInfo[0],
                        version,
                        () -> emergencyValidator,
                        emergencyRecoveryManager),
                "exception should be thrown for null shutdown trigger");
    }

    private void testNullAddressBook() {
        assertThrows(
                NullPointerException.class,
                () -> new SavedStateLoader(
                        null,
                        addressBook,
                        new SavedStateInfo[0],
                        version,
                        () -> emergencyValidator,
                        emergencyRecoveryManager),
                "exception should be thrown for null shutdown trigger");
    }

    private void testNullSavedStateInfos() {
        assertDoesNotThrow(
                () -> new SavedStateLoader(
                        shutdownTrigger,
                        addressBook,
                        new SavedStateInfo[0],
                        version,
                        () -> emergencyValidator,
                        emergencyRecoveryManager),
                "exception should not be thrown for null saved state info array");
    }

    private void testNullVersion() {
        assertThrows(
                NullPointerException.class,
                () -> new SavedStateLoader(
                        shutdownTrigger,
                        addressBook,
                        new SavedStateInfo[0],
                        null,
                        () -> emergencyValidator,
                        emergencyRecoveryManager),
                "exception should be thrown for null version");
    }

    private void testNullEmergencyValidatorSupplier() {
        assertThrows(
                NullPointerException.class,
                () -> new SavedStateLoader(
                        shutdownTrigger, addressBook, new SavedStateInfo[0], version, null, emergencyRecoveryManager),
                "exception should be thrown for null emergency state validator supplier");
    }

    private void testNullEmergencyValidatorValue() {
        assertThrows(
                NullPointerException.class,
                () -> new SavedStateLoader(
                        shutdownTrigger,
                        addressBook,
                        new SavedStateInfo[0],
                        version,
                        () -> null,
                        emergencyRecoveryManager),
                "exception should be thrown for null emergency state validator supplier value");
    }

    private void testNullEmergencyRecoveryManager() {
        assertThrows(
                NullPointerException.class,
                () -> new SavedStateLoader(
                        shutdownTrigger, addressBook, new SavedStateInfo[0], version, () -> emergencyValidator, null),
                "exception should be thrown for null emergency recovery manager");
    }

    @Test
    @DisplayName("Emergency Saved State Load - Null/Empty Saved State List")
    void testEmergencyLoadNullAndEmptySavedStateFiles() {
        testEmergencySavedStateLoadWithBadValue(null);
        testEmergencySavedStateLoadWithBadValue(new SavedStateInfo[0]);
    }

    private void testEmergencySavedStateLoadWithBadValue(final SavedStateInfo[] savedStateInfos) {
        writeEmergencyFile(5L);
        init(savedStateInfos);
        final SignedState stateToLoad = assertDoesNotThrow(savedStateLoader::getSavedStateToLoad);
        assertNull(stateToLoad, "stateToLoad should be null if the list of saved state files is empty/null");
        assertEquals(0, shutdownCount.get(), "no shutdown request should have been dispatched");
        assertTrue(emergencyRecoveryManager.isEmergencyStateRequired(), "an emergency state should still be required");
    }

    /**
     * Verifies that saved states are checked, in order, for compatibility with an emergency recovery.
     */
    @Test
    @DisplayName("Emergency Saved State Load")
    void testEmergencyLoad() {
        final int numStateToWrite = 4;

        final Random r = RandomUtils.getRandomPrintSeed();

        // Write states to disk, starting with round 5
        final List<SignedState> statesOnDisk = writeStatesToDisk(r, numStateToWrite);
        final SavedStateInfo[] stateInfos = toStateInfos(statesOnDisk);

        writeEmergencyFile(10L);

        init(stateInfos);

        // latest state is valid
        mockSignedStateValid();
        SignedState stateToLoad = assertDoesNotThrow(
                savedStateLoader::getSavedStateToLoad,
                "The first saved state should have been returned for emergency recovery");
        verifyEmergencySignedStateReturned(statesOnDisk.get(0), stateToLoad);

        init(stateInfos);

        // latest state is invalid
        mockSignedStateInvalid(statesOnDisk.get(0));
        stateToLoad = assertDoesNotThrow(
                savedStateLoader::getSavedStateToLoad,
                "The second saved state should have been returned for emergency recovery");
        verifyEmergencySignedStateReturned(statesOnDisk.get(1), stateToLoad);

        init(stateInfos);

        // latest 2 states are invalid
        mockSignedStateInvalid(statesOnDisk.get(0), statesOnDisk.get(1));
        stateToLoad = assertDoesNotThrow(
                savedStateLoader::getSavedStateToLoad,
                "The third saved state should have been returned for emergency recovery");
        verifyEmergencySignedStateReturned(statesOnDisk.get(2), stateToLoad);

        init(stateInfos);

        // all states are invalid
        mockSignedStateInvalid(statesOnDisk.toArray(new SignedState[numStateToWrite]));
        stateToLoad = assertDoesNotThrow(
                savedStateLoader::getSavedStateToLoad,
                "The first saved state with a round smaller than the emergency round should have been returned");
        verifyNonEmergencySignedStateReturned(statesOnDisk.get(3), stateToLoad);
    }

    private void init(final SavedStateInfo[] stateInfos) {
        resetCounters();
        initEmergencyRecoveryManager();
        initSavedStateLoader(stateInfos);
    }

    private void writeEmergencyFile(final long round) {
        try {
            new EmergencyRecoveryFile(round, RandomUtils.randomHash(), Instant.now()).write(tmpDir);
        } catch (final IOException e) {
            fail("Unable to write emergency recovery file to temporary dir " + tmpDir);
        }
    }

    private void initSavedStateLoader(final SavedStateInfo[] stateInfos) {
        savedStateLoader = new SavedStateLoader(
                shutdownTrigger, addressBook, stateInfos, version, () -> emergencyValidator, emergencyRecoveryManager);
    }

    private void initEmergencyRecoveryManager() {
        emergencyRecoveryManager = new EmergencyRecoveryManager(shutdownTrigger, tmpDir);
    }

    @Test
    @DisplayName("Saved State Load - Null/Empty Saved State List")
    void testLoadEmptyAndNullSavedStateFiles() {
        testSavedStateLoadWithBadValue(null);
        testSavedStateLoadWithBadValue(new SavedStateInfo[0]);
    }

    private void testSavedStateLoadWithBadValue(final SavedStateInfo[] savedStateInfos) {
        requireStateLoad(false);
        init(savedStateInfos);
        final SignedState stateToLoad = assertDoesNotThrow(savedStateLoader::getSavedStateToLoad);
        assertNull(stateToLoad, "stateToLoad should be null if the list of saved state files is null");

        requireStateLoad(true);
        assertThrows(
                SignedStateLoadingException.class,
                savedStateLoader::getSavedStateToLoad,
                "If a signed state is required and none is present, an exception should be thrown");
    }

    @Test
    @DisplayName("Saved State Load")
    void testLoadSavedState() {
        final int numStateToWrite = 3;

        final Random r = RandomUtils.getRandomPrintSeed();
        final List<SignedState> statesOnDisk = writeStatesToDisk(r, numStateToWrite);

        final SavedStateInfo[] stateInfos = toStateInfos(statesOnDisk);
        init(stateInfos);

        checkSignedStateFromDisk(true);
        final SignedState stateToLoad =
                assertDoesNotThrow(savedStateLoader::getSavedStateToLoad, "loading state should not throw");
        assertHashesMatch(statesOnDisk.get(0), stateToLoad, "the latest state should be returned");
        assertDoesNotThrow(savedStateLoader::getSavedStateToLoad, "null version should not cause an exception");
    }

    private void checkSignedStateFromDisk(final boolean value) {
        Settings.getInstance().setCheckSignedStateFromDisk(value);
    }

    private void requireStateLoad(final boolean value) {
        Settings.getInstance().setRequireStateLoad(value);
    }

    private void verifyNoSignedStateFound(final SignedState actual) {
        assertNull(actual, "No signed state on disk should have been found as compatible.");
        assertEquals(0, shutdownCount.get(), "no shutdown request should have been dispatched");
        assertTrue(emergencyRecoveryManager.isEmergencyStateRequired(), "an emergency state should still be required");
    }

    private void verifyEmergencySignedStateReturned(final SignedState expected, final SignedState actual) {
        assertHashesMatch(expected, actual, "unexpected signed state returned");
        assertEquals(0, shutdownCount.get(), "no shutdown request should have been dispatched");
        assertFalse(
                emergencyRecoveryManager.isEmergencyStateRequired(), "an emergency state should not still be required");
    }

    private void verifyNonEmergencySignedStateReturned(final SignedState expected, final SignedState actual) {
        assertHashesMatch(expected, actual, "unexpected signed state returned");
        assertEquals(0, shutdownCount.get(), "no shutdown request should have been dispatched");
        assertTrue(emergencyRecoveryManager.isEmergencyStateRequired(), "an emergency state should still be required");
    }

    private void mockSignedStateInvalid(final SignedState... invalidSignedState) {
        emergencyValidator = mock(EmergencySignedStateValidator.class);
        doAnswer(invocation -> {
                    if (invocation.getArgument(0) instanceof SignedState ss) {
                        for (final SignedState invalidState : invalidSignedState) {
                            if (ss.getState()
                                    .getHash()
                                    .equals(invalidState.getState().getHash())) {
                                throw new SignedStateInvalidException("intentionally thrown");
                            }
                        }
                    }
                    return null;
                })
                .when(emergencyValidator)
                .validate(any(), any(), any());
    }

    private void mockSignedStateValid() {
        emergencyValidator = mock(EmergencySignedStateValidator.class);
    }

    private SavedStateInfo[] toStateInfos(final List<SignedState> signedState) {
        return signedState.stream()
                .map(ss -> new SavedStateInfo(ss.getRound(), getStateFile(ss.getRound())))
                .toList()
                .toArray(new SavedStateInfo[0]);
    }

    private void assertHashesMatch(final SignedState expected, final SignedState actual, final String msg) {
        assertEquals(expected.getState().getHash(), actual.getState().getHash(), msg);
    }

    private Path getStateDir(final long round) {
        return tmpDir.resolve(String.valueOf(round));
    }

    private Path getStateFile(final long round) {
        return getStateDir(round).resolve(SIGNED_STATE_FILE_NAME);
    }

    /**
     * Writes states to disk with rounds starting and 5 and progressing by multiples of 5 (i.e. 5, 10, 15, etc)
     *
     * @return a list of signed states on disk ordered from newest to oldest
     */
    private List<SignedState> writeStatesToDisk(final Random r, final int numStates) {
        final LinkedList<SignedState> states = new LinkedList<>();
        final RandomSignedStateGenerator generator = new RandomSignedStateGenerator(r);
        for (int i = 1; i < numStates + 1; i++) {
            states.addFirst(generator.setRound(i * 5L).build());
        }
        states.forEach(ss -> {
            try {
                SignedStateFileWriter.writeSignedStateToDisk(0, getStateDir(ss.getRound()), ss, "test");
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        });

        return states;
    }
}
