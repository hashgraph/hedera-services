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

import static com.swirlds.common.merkle.utility.MerkleUtils.rehashTree;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.dispatch.triggers.control.ShutdownRequestedTrigger;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.reconnect.emergency.EmergencySignedStateValidator;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.signed.DeserializedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import com.swirlds.platform.system.SystemExitReason;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads the appropriate saved state from disk to be ingested into the system at startup.
 */
public class SavedStateLoader {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SavedStateLoader.class);
    /** Current system settings */
    private final Settings settings = Settings.getInstance();

    /** An array of saved states to consider for loading, ordered from newest to oldest */
    private final SavedStateInfo[] savedStateFiles;

    private final AddressBook addressBook;
    /** Triggers a system shutdown request */
    private final ShutdownRequestedTrigger shutdownRequestedTrigger;
    /** The version of the software currently running */
    private final SoftwareVersion currentSoftwareVersion;
    /** Supplies a validator of emergency recovery states */
    private final Supplier<EmergencySignedStateValidator> emergencyStateValidator;
    /** The status of emergency recovery */
    private final EmergencyRecoveryManager emergencyRecoveryManager;

    /**
     * Creates a new instance.
     *
     * @param shutdownRequestedTrigger
     * 		a trigger capable of dispatching shutdown requests
     * @param addressBook
     * 		the address book used to validate the signed state
     * @param savedStateFiles
     * 		an array of saved state files to consider for loading, ordered from newest to oldest
     * @param currentSoftwareVersion
     * 		the current software version
     * @param emergencyStateValidator
     * 		a supplier of an emergency state validator
     * @param emergencyRecoveryManager
     * 		the emergency recovery manager
     */
    public SavedStateLoader(
            final ShutdownRequestedTrigger shutdownRequestedTrigger,
            final AddressBook addressBook,
            final SavedStateInfo[] savedStateFiles,
            final SoftwareVersion currentSoftwareVersion,
            final Supplier<EmergencySignedStateValidator> emergencyStateValidator,
            final EmergencyRecoveryManager emergencyRecoveryManager) {

        Objects.requireNonNull(shutdownRequestedTrigger, "shutdownRequestedTrigger");
        Objects.requireNonNull(addressBook, "addressBook");
        Objects.requireNonNull(currentSoftwareVersion, "currentSoftwareVersion");
        Objects.requireNonNull(emergencyStateValidator, "emergencyStateValidator");
        Objects.requireNonNull(emergencyStateValidator.get(), "emergencyStateValidator value");
        Objects.requireNonNull(emergencyRecoveryManager, "emergencyRecoveryManager");

        this.shutdownRequestedTrigger = shutdownRequestedTrigger;
        this.addressBook = addressBook;
        this.savedStateFiles = savedStateFiles;
        this.currentSoftwareVersion = currentSoftwareVersion;
        this.emergencyStateValidator = emergencyStateValidator;
        this.emergencyRecoveryManager = emergencyRecoveryManager;

        if (savedStateFiles == null || savedStateFiles.length == 0) {
            logger.info(STARTUP.getMarker(), "No saved states were found on disk");
        } else {
            final StringBuilder sb = new StringBuilder();
            sb.append("The following saved states were found on disk:");
            for (final SavedStateInfo savedStateFile : savedStateFiles) {
                sb.append("\n  - ").append(savedStateFile.stateFile());
            }

            logger.info(STARTUP.getMarker(), sb.toString());
        }
    }

    /**
     * Stores a signed state read from disk along with its original hash and it's recalculated hash. These hashes could
     * be different if a migration was performed.
     */
    private record SignedStateWithHashes(SignedState signedState, Hash oldHash, Hash newHash) {
        /**
         * Returns the version of the software that wrote the signed state to disk
         *
         * @return the software version
         */
        public SoftwareVersion getVersion() {
            return signedState.getState().getPlatformState().getPlatformData().getCreationSoftwareVersion();
        }
    }

    /**
     * Gets a saved state from disk to load, or null if there are no signed states on disk.
     *
     * @return the state to load
     * @throws SignedStateLoadingException
     * 		if the node is configured to require a saved state to load but none are available
     * @throws IOException
     * 		if there was an exception reading a saved state file
     */
    public SignedState getSavedStateToLoad() throws SignedStateLoadingException, IOException {
        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            return getEmergencySavedStateToLoad();
        } else {
            return getRegularSavedStateToLoad();
        }
    }

    /**
     * Returns the most recent, compatible emergency signed state to load into the system at startup, if found.
     *
     * @return a compatible emergency recovery signed state to load, or null if none was found
     * @throws IOException
     * 		if there was an exception reading a saved state file
     */
    private SignedState getEmergencySavedStateToLoad() throws IOException {
        if (savedStateFiles == null) {
            return null;
        }

        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            final SignedStateWithHashes stateWithHashes = readAndRehashState(savedStateFile);
            final Hash oldHash = stateWithHashes.oldHash;
            final Hash newHash = stateWithHashes.newHash;

            if (!oldHash.equals(newHash)) {
                logger.error(EXCEPTION.getMarker(), "Emergency recovery must not be performed during migration.");
                shutdownRequestedTrigger.dispatch(
                        "Migration During Emergency Recovery", SystemExitReason.EMERGENCY_RECOVERY_ERROR.getExitCode());
                return null;
            }

            final SignedState signedState = stateWithHashes.signedState;

            // Don't check any states for rounds earlier than the emergency state round
            if (signedState.getRound()
                    < emergencyRecoveryManager.getEmergencyRecoveryFile().round()) {
                break;
            }

            try {
                emergencyStateValidator.get().validate(signedState, addressBook, null);
                emergencyRecoveryManager.emergencyStateLoaded();
                logger.info(
                        STARTUP.getMarker(),
                        "Found signed state (round {}) on disk that is compatible with the emergency recovery state.",
                        signedState.getRound());
                return signedState;
            } catch (final SignedStateInvalidException e) {
                logger.info(
                        STARTUP.getMarker(),
                        "Signed state from disk for round {} cannot be used "
                                + "for emergency recovery ({}), checking next state.",
                        stateWithHashes.signedState.getRound(),
                        e.getMessage());
            }
        }

        logger.info(
                STARTUP.getMarker(),
                "No states on disk are compatible with the emergency recovery state. Attempting to load a recent "
                        + "state prior to the emergency state round as a starting point for emergency reconnect.");

        // Since no state matched, we must load a state for a round before the emergency recovery state. If we already
        // have a later state loaded, the emergency reconnect state received in emergency reconnect will be rejected
        // because it is older than the already loaded state.
        final long maxStateRound =
                emergencyRecoveryManager.getEmergencyRecoveryFile().round() - 1;
        SignedState latest = null;
        try {
            latest = getRegularSavedStateToLoad(maxStateRound);
        } catch (final SignedStateLoadingException e) {
            // intentionally ignored
        } finally {
            if (latest != null) {
                logger.info(
                        STARTUP.getMarker(),
                        "Loading the latest available [round={}] as a starting point for emergency reconnect.",
                        latest.getRound());
            } else {
                logger.info(
                        STARTUP.getMarker(),
                        "No states on disk could be loaded as a starting point. Starting from a genesis state.");
            }
        }
        return latest;
    }

    /**
     * Returns the most recent signed state to load into the system at startup, if found.
     *
     * @return a signed state to load, or null if none was found
     * @throws IOException
     * 		if there was an exception reading a saved state file
     * @throws SignedStateLoadingException
     * 		if a signed state is required to start the node and none are found
     */
    private SignedState getRegularSavedStateToLoad() throws IOException, SignedStateLoadingException {
        return getRegularSavedStateToLoad(Long.MAX_VALUE);
    }

    /**
     * Returns the most recent signed state to load into the system at startup, if found.
     *
     * @param maxRound
     * 		the maximum round number (inclusive) the returned state is permitted to have
     * @return a signed state to load, or null if none was found
     * @throws IOException
     * 		if there was an exception reading a saved state file
     * @throws SignedStateLoadingException
     * 		if a signed state is required to start the node and none are found
     */
    private SignedState getRegularSavedStateToLoad(final long maxRound)
            throws IOException, SignedStateLoadingException {

        if (savedStateFiles == null || savedStateFiles.length == 0) {
            if (settings.isRequireStateLoad()) {
                throw new SignedStateLoadingException("No saved states found on disk!");
            } else {
                return null;
            }
        }

        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            if (savedStateFile.round() <= maxRound) {
                final SignedStateWithHashes stateWithHashes = readAndRehashState(savedStateFile);

                if (settings.isCheckSignedStateFromDisk()) {
                    evaluateLoadedStateHash(stateWithHashes, currentSoftwareVersion);
                }

                return stateWithHashes.signedState;
            }
        }
        return null;
    }

    private static SignedStateWithHashes readAndRehashState(final SavedStateInfo file) throws IOException {

        logger.info(STARTUP.getMarker(), "Loading signed state from disk: {}", file.stateFile());

        final DeserializedSignedState deserializedSignedState = readStateFile(file.stateFile());
        final Hash oldHash = deserializedSignedState.originalHash();

        // When loading from disk, we should hash the state every time so that the first fast copy will
        // only hash the difference
        final Hash newHash = rehashTree(deserializedSignedState.signedState().getState());
        return new SignedStateWithHashes(deserializedSignedState.signedState(), oldHash, newHash);
    }

    private static void evaluateLoadedStateHash(
            final SignedStateWithHashes stateWithHashes, final SoftwareVersion currentVersion) {
        if (stateWithHashes.newHash.equals(stateWithHashes.oldHash)) {
            logger.info(STARTUP.getMarker(), "Signed state loaded from disk has a valid hash.");
        } else {
            if (currentVersion == null) {
                logger.error(
                        STARTUP.getMarker(),
                        "Unable to determine the validity of the signed state loaded from disk because the current "
                                + "software version is null");
                return;
            }
            if (currentVersion.equals(stateWithHashes.getVersion())) {
                logger.error(
                        STARTUP.getMarker(),
                        "ERROR: Signed state loaded from disk has an invalid hash!\ndisk:{}\ncalc:{}",
                        stateWithHashes.oldHash,
                        stateWithHashes.newHash);
            } else {
                logger.info(
                        STARTUP.getMarker(),
                        """
								Signed state loaded from disk has an invalid hash (expected during an upgrade)
								disk:{}
								calc:{}""",
                        stateWithHashes.oldHash,
                        stateWithHashes.newHash);
            }
        }
    }
}
