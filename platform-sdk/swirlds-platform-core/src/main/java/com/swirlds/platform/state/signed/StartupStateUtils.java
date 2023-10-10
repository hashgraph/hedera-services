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

import static com.swirlds.common.merkle.utility.MerkleUtils.rehashTree;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.GenesisStateBuilder.buildGenesisState;
import static com.swirlds.platform.state.signed.ReservedSignedState.createNullReservation;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.scratchpad.Scratchpad;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.logging.payloads.SavedStateLoadedPayload;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.recovery.RecoveryScratchpad;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for loading and manipulating state files at startup time.
 */
public final class StartupStateUtils {

    private static final Logger logger = LogManager.getLogger(StartupStateUtils.class);

    private StartupStateUtils() {}

    /**
     * If necessary, perform cleanup in preparation for emergency recovery.
     *
     * @param platformContext     the platform context
     * @param recycleBin          a utility for deleting files in a way that allows them to possibly be recovered
     * @param selfId              the ID of this node
     * @param swirldName          the name of this swirld
     * @param actualMainClassName the name of the app's SwirldMain class (may be a value provided by configuration)
     * @param epoch               the epoch that the platform wants to be in (defined either by the emergency recovery
     *                            file or the state loaded from disk if it is not overridden by a recovery file)
     * @param initialStateRound   the round number of the initial state
     */
    public static void doRecoveryCleanup(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId selfId,
            @NonNull final String swirldName,
            @NonNull final String actualMainClassName,
            @Nullable final Hash epoch,
            final long initialStateRound) {

        final Scratchpad<RecoveryScratchpad> recoveryScratchpad =
                new Scratchpad<>(platformContext, selfId, RecoveryScratchpad.class, RecoveryScratchpad.SCRATCHPAD_ID);
        recoveryScratchpad.logContents();

        final Hash previousEpoch = recoveryScratchpad.get(RecoveryScratchpad.EPOCH_HASH);

        if (Objects.equals(epoch, previousEpoch)) {
            // We are in the same epoch as when we were the last time he platform was shut down.
            return;
        }

        logger.info(
                STARTUP.getMarker(),
                "Entering new epoch, cleaning up file system in preparation for emergency recovery. "
                        + "Any states with a round number higher than {} will be recycled.",
                initialStateRound);

        final List<SavedStateInfo> savedStateFiles = getSavedStateFiles(actualMainClassName, selfId, swirldName);
        for (final SavedStateInfo stateInfo : savedStateFiles) {
            if (stateInfo.metadata().round() > initialStateRound) {
                recycleState(recycleBin, stateInfo);
            }
        }

        // Write the current epoch into the scratchpad. Once this completes, the platform will not do cleanup
        // the next time it boots with this epoch hash.
        recoveryScratchpad.set(RecoveryScratchpad.EPOCH_HASH, epoch);
    }

    /**
     * Get the initial state to be used by this node. May return a state loaded from disk, or may return a genesis state
     * if no valid state is found on disk.
     *
     * @param platformContext          the platform context
     * @param recycleBin               the recycle bin
     * @param softwareVersion          the software version of the app
     * @param genesisStateBuilder      a supplier that can build a genesis state
     * @param mainClassName            the name of the app's SwirldMain class
     * @param swirldName               the name of this swirld
     * @param selfId                   the node id of this node
     * @param configAddressBook        the address book from config.txt
     * @param emergencyRecoveryManager the emergency recovery manager
     * @return the initial state to be used by this node
     * @throws SignedStateLoadingException if there was a problem parsing states on disk and we are not configured to
     *                                     delete malformed states
     */
    @NonNull
    public static ReservedSignedState getInitialState(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final Supplier<SwirldState> genesisStateBuilder,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final NodeId selfId,
            @NonNull final AddressBook configAddressBook,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager)
            throws SignedStateLoadingException {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(mainClassName);
        Objects.requireNonNull(swirldName);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(configAddressBook);
        Objects.requireNonNull(emergencyRecoveryManager);

        final ReservedSignedState loadedState = StartupStateUtils.loadStateFile(
                platformContext,
                recycleBin,
                selfId,
                mainClassName,
                swirldName,
                softwareVersion,
                emergencyRecoveryManager);

        try (loadedState) {
            if (loadedState.isNotNull()) {
                logger.info(
                        STARTUP.getMarker(),
                        new SavedStateLoadedPayload(
                                loadedState.get().getRound(), loadedState.get().getConsensusTimestamp()));

                return copyInitialSignedState(platformContext, loadedState.get());
            }
        }

        final ReservedSignedState genesisState =
                buildGenesisState(platformContext, configAddressBook, softwareVersion, genesisStateBuilder.get());

        try (genesisState) {
            return copyInitialSignedState(platformContext, genesisState.get());
        }
    }

    /**
     * Looks at the states on disk, chooses one to load, and then loads the chosen state.
     *
     * @param platformContext          the platform context
     * @param recycleBin               the recycle bin
     * @param selfId                   the ID of this node
     * @param mainClassName            the name of the main class
     * @param swirldName               the name of the swirld
     * @param currentSoftwareVersion   the current software version
     * @param emergencyRecoveryManager the emergency recovery manager
     * @return a reserved signed state (wrapped state will be null if no state could be loaded)
     * @throws SignedStateLoadingException if there was a problem parsing states on disk and we are not configured to
     *                                     delete malformed states
     */
    @NonNull
    static ReservedSignedState loadStateFile(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId selfId,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager)
            throws SignedStateLoadingException {

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final String actualMainClassName = stateConfig.getMainClassName(mainClassName);

        final List<SavedStateInfo> savedStateFiles = getSavedStateFiles(actualMainClassName, selfId, swirldName);
        logStatesFound(savedStateFiles);

        if (savedStateFiles.isEmpty()) {
            // No states were found on disk.
            return createNullReservation();
        }

        final boolean emergencyStateRequired = emergencyRecoveryManager.isEmergencyStateRequired();

        final ReservedSignedState state;
        if (emergencyStateRequired) {
            state = loadEmergencyState(
                    platformContext, recycleBin, currentSoftwareVersion, savedStateFiles, emergencyRecoveryManager);
        } else {
            state = loadLatestState(platformContext, recycleBin, currentSoftwareVersion, savedStateFiles);
        }

        return state;
    }

    /**
     * Create a copy of the initial signed state. There are currently data structures that become immutable after being
     * hashed, and we need to make a copy to force it to become mutable again.
     *
     * @param platformContext    the platform's context
     * @param initialSignedState the initial signed state
     * @return a copy of the initial signed state
     */
    public static @NonNull ReservedSignedState copyInitialSignedState(
            @NonNull final PlatformContext platformContext, @NonNull final SignedState initialSignedState) {
        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(initialSignedState);

        final State stateCopy = initialSignedState.getState().copy();
        final SignedState signedStateCopy =
                new SignedState(platformContext, stateCopy, "StartupStateUtils: copy initial state", false);
        signedStateCopy.setSigSet(initialSignedState.getSigSet());

        return signedStateCopy.reserve("Copied initial state");
    }

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

    /**
     * Load the latest state that is compatible with the emergency recovery file.
     *
     * @param platformContext          the platform context
     * @param recycleBin               the recycle bin
     * @param currentSoftwareVersion   the current software version
     * @param savedStateFiles          the saved states to try
     * @param emergencyRecoveryManager the emergency recovery manager
     * @return the loaded state
     */
    @NonNull
    private static ReservedSignedState loadEmergencyState(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final List<SavedStateInfo> savedStateFiles,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager)
            throws SignedStateLoadingException {

        final EmergencyRecoveryFile recoveryFile = emergencyRecoveryManager.getEmergencyRecoveryFile();
        logger.info(
                STARTUP.getMarker(),
                """
                        Loading state in emergency recovery mode. The emergency recovery file specifies the following:
                            Epoch hash:          {}
                            Epoch hash mnemonic: {}
                            Round:               {}""",
                recoveryFile.hash(),
                recoveryFile.hash().toMnemonic(),
                recoveryFile.round());

        ReservedSignedState state = null;
        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            if (!isSuitableInitialRecoveryState(emergencyRecoveryManager, savedStateFile)) {
                continue;
            }

            state = loadStateFile(platformContext, recycleBin, currentSoftwareVersion, savedStateFile);
            if (state != null) {
                break;
            }
        }

        return processRecoveryState(emergencyRecoveryManager, state);
    }

    /**
     * Check if a state is a suitable initial state for emergency recovery. A suitable state satisfies at least one of
     * these conditions:
     * <ul>
     *     <li>The state's root hash matches the exact epoch hash</li>
     *     <li>The state has a matching epoch hash</li>
     *     <li>The state's round is less than the recovery round</li>
     * </ul>
     *
     * @param emergencyRecoveryManager decides if a state is suitable for emergency recovery
     * @param savedStateFile           the state to check
     * @return true if the state is suitable to be an initial state for emergency recovery, false otherwise
     */
    private static boolean isSuitableInitialRecoveryState(
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final SavedStateInfo savedStateFile) {

        if (savedStateFile.metadata().hash() == null) {
            // This state was created with an old version of the metadata, do not consider it.
            // Any state written with the current software version will have a non-null value for this field.
            return false;
        }

        final Hash targetEpoch =
                emergencyRecoveryManager.getEmergencyRecoveryFile().hash();
        final long targetRound =
                emergencyRecoveryManager.getEmergencyRecoveryFile().round();

        final Hash stateHash = savedStateFile.metadata().hash();
        final Hash stateEpoch = savedStateFile.metadata().epochHash();
        final long stateRound = savedStateFile.metadata().round();

        final boolean isStateSuitable = isInEpoch(targetEpoch, stateHash, stateEpoch) || stateRound < targetRound;

        if (isStateSuitable) {
            logger.info(
                    STARTUP.getMarker(),
                    """
                            The following state meets the emergency recovery criteria:
                                File path:     {}
                                Hash:          {}
                                Hash Mnemonic: {}
                                Round:         {}""",
                    savedStateFile.stateFile(),
                    savedStateFile.metadata().hash(),
                    savedStateFile.metadata().hashMnemonic(),
                    savedStateFile.metadata().round());
        } else {
            logger.warn(
                    STARTUP.getMarker(),
                    """
                            The following state does not meet the emergency recovery criteria:
                                File path:     {}
                                Hash:          {}
                                Hash Mnemonic: {}
                                Round:         {}""",
                    savedStateFile.stateFile(),
                    savedStateFile.metadata().hash(),
                    savedStateFile.metadata().hashMnemonic(),
                    savedStateFile.metadata().round());
        }

        return isStateSuitable;
    }

    /**
     * Check if a provided state is in the hash epoch that is specified by the emergency recovery file.
     *
     * @param targetEpoch the hash epoch specified by the emergency recovery file
     * @param stateHash   the hash of the state
     * @param stateEpoch  the epoch hash of the state
     * @return true if the state is in the hash epoch, false otherwise. Null states are not in the hash epoch.
     */
    private static boolean isInEpoch(
            @Nullable final Hash targetEpoch, @Nullable final Hash stateHash, @Nullable final Hash stateEpoch) {

        if (stateHash == null) {
            // State is from an old version of the code that did not store state hash in metadata
            return false;
        }

        return stateHash.equals(targetEpoch) || Objects.equals(stateEpoch, targetEpoch);
    }

    /**
     * Once we have decided which state will be our initial state, do some additional logging and processing.
     *
     * @param emergencyRecoveryManager the emergency recovery manager
     * @param state                    the state that will be our initial state (null if we are starting from genesis)
     * @return the state that will be our initial state (converts null genesis state to a non-null wrapper)
     */
    @NonNull
    private static ReservedSignedState processRecoveryState(
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @Nullable final ReservedSignedState state) {

        final Hash targetEpoch =
                emergencyRecoveryManager.getEmergencyRecoveryFile().hash();
        final Hash stateHash = state == null ? null : state.get().getState().getHash();
        final Hash stateEpoch = state == null
                ? null
                : state.get().getState().getPlatformState().getPlatformData().getEpochHash();

        final boolean inEpoch = isInEpoch(targetEpoch, stateHash, stateEpoch);

        if (state == null) {
            logger.warn(
                    STARTUP.getMarker(),
                    "No state on disk met the criteria for emergency recovery, starting from genesis. "
                            + "This node will need to receive a state through an emergency reconnect.");
            return createNullReservation();
        } else if (inEpoch) {
            logger.info(
                    STARTUP.getMarker(),
                    "Loaded state is in the correct hash epoch, "
                            + "this node will not need to receive a state through an emergency reconnect.");

            state.get().markAsRecoveryState();

            // Ensure that the next round created has the proper epoch hash.
            state.get()
                    .getState()
                    .getPlatformState()
                    .getPlatformData()
                    .setNextEpochHash(
                            emergencyRecoveryManager.getEmergencyRecoveryFile().hash());

            // Signal that an emergency reconnect is not needed.
            emergencyRecoveryManager.emergencyStateLoaded();

            return state;
        } else {
            logger.warn(
                    STARTUP.getMarker(),
                    "Loaded state is not in the correct hash epoch, "
                            + "this node will need to receive a state through an emergency reconnect.");
            return state;
        }
    }

    /**
     * Load the latest state. If the latest state is invalid, try to load the next latest state. Repeat until a valid
     * state is found or there are no more states to try.
     *
     * @param platformContext        the platform context
     * @param recycleBin             the recycle bin
     * @param currentSoftwareVersion the current software version
     * @param savedStateFiles        the saved states to try
     * @return the loaded state
     */
    private static ReservedSignedState loadLatestState(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final List<SavedStateInfo> savedStateFiles)
            throws SignedStateLoadingException {

        logger.info(STARTUP.getMarker(), "Loading latest state from disk.");

        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            final ReservedSignedState state =
                    loadStateFile(platformContext, recycleBin, currentSoftwareVersion, savedStateFile);
            if (state != null) {
                return state;
            }
        }

        logger.warn(STARTUP.getMarker(), "No valid saved states were found on disk. Starting from genesis.");
        return createNullReservation();
    }

    /**
     * Load the requested state from file. If state can not be loaded, recycle the file and return null.
     *
     * @param platformContext        the platform context
     * @param recycleBin             the recycle bin
     * @param currentSoftwareVersion the current software version
     * @param savedStateFile         the state to load
     * @return the loaded state, or null if the state could not be loaded. Will be fully hashed if non-null.
     */
    @Nullable
    private static ReservedSignedState loadStateFile(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final SavedStateInfo savedStateFile)
            throws SignedStateLoadingException {

        logger.info(STARTUP.getMarker(), "Loading signed state from disk: {}", savedStateFile.stateFile());

        final DeserializedSignedState deserializedSignedState;
        try {
            deserializedSignedState = readStateFile(platformContext, savedStateFile.stateFile());
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "unable to load state file {}", savedStateFile.stateFile(), e);

            final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
            if (stateConfig.deleteInvalidStateFiles()) {
                recycleState(recycleBin, savedStateFile);
                return null;
            } else {
                throw new SignedStateLoadingException("unable to load state, this is unrecoverable");
            }
        }

        final State state = deserializedSignedState.reservedSignedState().get().getState();

        final Hash oldHash = deserializedSignedState.originalHash();
        final Hash newHash = rehashTree(state);

        final SoftwareVersion loadedVersion =
                state.getPlatformState().getPlatformData().getCreationSoftwareVersion();

        if (oldHash.equals(newHash)) {
            logger.info(STARTUP.getMarker(), "Loaded state's hash is the same as when it was saved.");
        } else if (loadedVersion.equals(currentSoftwareVersion)) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The saved state file {} was created with the current version of the software, "
                            + "but the state hash has changed. Unless the state was intentionally modified, "
                            + "this is a good indicator that there is probably a bug.",
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

        return deserializedSignedState.reservedSignedState();
    }

    /**
     * Recycle a state.
     *
     * @param recycleBin the recycle bin
     * @param stateInfo  the state to recycle
     */
    private static void recycleState(@NonNull final RecycleBin recycleBin, @NonNull final SavedStateInfo stateInfo) {
        logger.warn(STARTUP.getMarker(), "Moving state {} to the recycle bin.", stateInfo.stateFile());
        try {
            recycleBin.recycle(stateInfo.getDirectory());
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to recycle state", e);
        }
    }
}
