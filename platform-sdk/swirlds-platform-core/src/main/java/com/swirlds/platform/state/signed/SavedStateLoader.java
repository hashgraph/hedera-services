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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.LegacySavedStateLoader.readAndRehashState;
import static com.swirlds.platform.state.signed.ReservedSignedState.createNullReservation;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.LegacySavedStateLoader;
import com.swirlds.platform.LegacySavedStateLoader.SignedStateWithHashes;
import com.swirlds.platform.reconnect.emergency.EmergencySignedStateValidator;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A utility for loading saved states from disk.
 */
public final class SavedStateLoader {

    private static final Logger logger = LogManager.getLogger(LegacySavedStateLoader.class);

    // TODO does this need to be a stand alone utility?
    //  perhaps move other state related startup logic to this class or something

    private SavedStateLoader() {}

    /**
     * Log the states that were discovered on disk.
     *
     * @param savedStateFiles the states that were discovered on disk
     */
    private static void logStatesFound(@NonNull final List<SavedStateInfo> savedStateFiles) {
        if (savedStateFiles.isEmpty()) {
            logger.info(STARTUP.getMarker(), "No saved states were found on disk.");
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("The following saved states were found on disk:");
        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            sb.append("\n  - ").append(savedStateFile.stateFile());
        }
        logger.info(STARTUP.getMarker(), sb.toString());
    }

    // TODO fix javadocs

    /**
     * Looks at the states on disk, chooses one to load, and then loads the chosen state..
     *
     * @param platformContext          the platform context
     * @param recycleBin               the recycle bin
     * @param addressBook              the address book used to validate the signed state (if necessary) // TODO
     * @param currentSoftwareVersion   the current software version // TODO
     * @param emergencyStateValidator  an emergency state validator
     * @param emergencyRecoveryManager the emergency recovery manager
     * @return a reserved signed state (wrapped state will be null if no state could be loaded)
     */
    @NonNull
    public ReservedSignedState loadState(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final EmergencySignedStateValidator emergencyStateValidator,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final String actualMainClassName = stateConfig.getMainClassName(mainClassName);

        // Files are sorted from most recent to least recent by round number.
        final List<SavedStateInfo> savedStateFiles = getSavedStateFiles(actualMainClassName, selfId, swirldName);
        logStatesFound(savedStateFiles);

        if (savedStateFiles.isEmpty()) {
            // No states were found on disk.
            return createNullReservation();
        }

        final ReservedSignedState state = emergencyRecoveryManager.isEmergencyStateRequired()
                ? loadEmergencyState()
                : loadLatestState(platformContext, currentSoftwareVersion, savedStateFiles);

        final long loadedRound = state.isNull() ? -1 : state.get().getRound();
        cleanupUnusedStates(recycleBin, savedStateFiles, loadedRound);

        return state;
    }

    @NonNull
    private ReservedSignedState loadEmergencyState() {
        // TODO
        return null;
    }

    /**
     * Load the latest state. If the latest state is invalid, try to load the next latest state. Repeat until a valid
     * state is found or there are no more states to try.
     *
     * @param platformContext        the platform context
     * @param currentSoftwareVersion the current software version
     * @param savedStateFiles        the saved states to try
     * @return the loaded state
     */
    private ReservedSignedState loadLatestState(
            @NonNull final PlatformContext platformContext,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final List<SavedStateInfo> savedStateFiles) {

        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            try {
                return loadState(platformContext, currentSoftwareVersion, savedStateFile);
            } catch (final IOException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Failed to load saved state from file: {}",
                        savedStateFile.stateFile(),
                        e);
            }
        }

        return createNullReservation();
    }

    /**
     * Load the requested state.
     *
     * @param platformContext        the platform context
     * @param currentSoftwareVersion the current software version
     * @param savedStateFile         the state to load
     * @return the loaded state, will be fully hashed
     */
    @NonNull
    private ReservedSignedState loadState(
            @NonNull final PlatformContext platformContext,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final SavedStateInfo savedStateFile)
            throws IOException {

        final SignedStateWithHashes stateWithHashes = readAndRehashState(platformContext, savedStateFile);
        final SoftwareVersion loadedVersion = stateWithHashes.getVersion();

        if (!stateWithHashes.oldHash().equals(stateWithHashes.newHash())) {
            if (loadedVersion.equals(currentSoftwareVersion)) {
                logger.warn(
                        STARTUP.getMarker(),
                        "The saved state file {} was created with the current version of the software, "
                                + "but the state hash has changed. Unless the state was intentionally modified, "
                                + "this good indicator that there may be a bug.",
                        savedStateFile.stateFile());
            } else {
                logger.warn(
                        STARTUP.getMarker(),
                        "The saved state file {} was created with version {}, which is different than the "
                                + "current version {}. The hash of the loaded state is different than the hash of the "
                                + "state when it was first created, which is not abnormal if there have been data "
                                + "migrations.",
                        savedStateFile.stateFile(),
                        loadedVersion,
                        currentSoftwareVersion);
            }
        }

        return stateWithHashes.signedState();
    }

    /**
     * When we load a state from disk, it is illegal to have states with a higher round number on disk. Clean up those
     * states.
     *
     * @param savedStateFiles the states that were found on disk
     * @param loadedRound     the round number of the state that was loaded, or -1 if no state was loaded
     */
    private void cleanupUnusedStates(
            @NonNull final RecycleBin recycleBin,
            @NonNull final List<SavedStateInfo> savedStateFiles,
            final long loadedRound) {

        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            if (savedStateFile.metadata().round() > loadedRound) {
                logger.warn(
                        STARTUP.getMarker(),
                        "Recycling state file {} since it from round {}, "
                                + "which is later than the round of the state being loaded ({}).",
                        savedStateFile.stateFile(),
                        savedStateFile.metadata().round(),
                        loadedRound);

                try {
                    recycleBin.recycle(savedStateFile.getDirectory());
                } catch (final IOException e) {
                    throw new UncheckedIOException("unable to recycle state file", e);
                }
            }
        }
    }
}
