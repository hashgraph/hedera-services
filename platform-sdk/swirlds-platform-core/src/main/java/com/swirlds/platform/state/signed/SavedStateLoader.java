package com.swirlds.platform.state.signed;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.signed.ReservedSignedState.createNullReservation;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.LegacySavedStateLoader;
import com.swirlds.platform.reconnect.emergency.EmergencySignedStateValidator;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
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

    private SavedStateLoader() {
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

        final ReservedSignedState state = emergencyRecoveryManager.isEmergencyStateRequired() ?
                loadEmergencyState() :
                loadLatestState(platformContext, savedStateFiles);

        final long loadedRound = state.isNull() ? -1 : state.get().getRound();
        cleanupUnusedStates(savedStateFiles, loadedRound);

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
     * @param platformContext the platform context
     * @param savedStateFiles the saved states to try
     * @return the loaded state
     */
    private ReservedSignedState loadLatestState(
            @NonNull final PlatformContext platformContext,
            @NonNull final List<SavedStateInfo> savedStateFiles) {

        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            try {
                return loadState(platformContext, savedStateFile);
            } catch (final IOException e) {
                logger.error(EXCEPTION.getMarker(), "Failed to load saved state from file: {}",
                        savedStateFile.stateFile(), e);
            }
        }

        return createNullReservation();
    }

    /**
     * Load the requested state.
     *
     * @param savedStateFile the state to load
     * @return the loaded state
     */
    @NonNull
    private ReservedSignedState loadState(
            @NonNull final PlatformContext platformContext,
            @NonNull final SavedStateInfo savedStateFile) throws IOException {

        final DeserializedSignedState deserializedSignedState = readStateFile(platformContext,
                savedStateFile.stateFile());

        // TODO hash checks

        return deserializedSignedState.reservedSignedState();
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
            final long loadedRound) throws IOException {

        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            if (savedStateFile.metadata().round() > loadedRound) {
                logger.warn(STARTUP.getMarker(),
                        "Recycling state file {} since it from round {}, "
                                + "which is later than the round of the state being loaded ({}).",
                        savedStateFile.stateFile(),
                        savedStateFile.metadata().round(),
                        loadedRound);

                recycleBin.recycle(savedStateFile.getDirectory());
            }
        }
    }

}
