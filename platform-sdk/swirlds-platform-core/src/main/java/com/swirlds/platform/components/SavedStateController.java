/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.state.signed.StateToDiskReason.FIRST_ROUND_AFTER_GENESIS;
import static com.swirlds.platform.state.signed.StateToDiskReason.FREEZE_STATE;
import static com.swirlds.platform.state.signed.StateToDiskReason.PERIODIC_SNAPSHOT;
import static com.swirlds.platform.state.signed.StateToDiskReason.RECONNECT;

import com.swirlds.common.config.StateConfig;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.StateToDiskReason;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controls which signed states should be written to disk based on input from other components
 */
public class SavedStateController {
    private static final Logger logger = LogManager.getLogger(SavedStateController.class);
    /**
     * The timestamp of the signed state that was most recently written to disk, or null if no timestamp was recently
     * written to disk.
     */
    private Instant previousSavedStateTimestamp;
    /** the state config */
    private final StateConfig stateConfig;

    /**
     * Create a new SavedStateController
     *
     * @param stateConfig the state config
     */
    public SavedStateController(@NonNull final StateConfig stateConfig) {
        this.stateConfig = Objects.requireNonNull(stateConfig);
    }

    /**
     * Determine if a signed state should be written to disk. If the state should be written, the state will be marked
     * and then written to disk outside the scope of this class.
     *
     * @param reservedSignedState the signed state in question
     */
    public synchronized void markSavedState(@NonNull final ReservedSignedState reservedSignedState) {
        try (reservedSignedState) {
            final SignedState signedState = reservedSignedState.get();
            final StateToDiskReason reason = shouldSaveToDisk(signedState, previousSavedStateTimestamp);

            if (reason != null) {
                markSavingToDisk(reservedSignedState, reason);
            }
            // if a null reason is returned, then there isn't anything to do, since the state shouldn't be saved
        }
    }

    /**
     * Notifies the controller that a signed state was received from another node during reconnect. The controller saves
     * its timestamp and marks it to be written to disk.
     *
     * @param reservedSignedState the signed state that was received from another node during reconnect
     */
    public synchronized void reconnectStateReceived(@NonNull final ReservedSignedState reservedSignedState) {
        try (reservedSignedState) {
            markSavingToDisk(reservedSignedState, RECONNECT);
        }
    }

    /**
     * This should be called at boot time when a signed state is read from the disk.
     *
     * @param signedState the signed state that was read from file at boot time
     */
    public synchronized void registerSignedStateFromDisk(@NonNull final SignedState signedState) {
        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
    }

    private void markSavingToDisk(@NonNull final ReservedSignedState state, @NonNull final StateToDiskReason reason) {
        final SignedState signedState = state.get();
        logger.info(
                STATE_TO_DISK.getMarker(),
                "Signed state from round {} created, will eventually be written to disk, for reason: {}",
                signedState.getRound(),
                reason);

        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
        signedState.markAsStateToSave(reason);
    }

    /**
     * Determines whether a signed state should eventually be written to disk
     * <p>
     * If it is determined that the state should be written to disk, this method returns the reason why
     * <p>
     * If it is determined that the state shouldn't be written to disk, then this method returns null
     *
     * @param signedState       the state in question
     * @param previousTimestamp the timestamp of the previous state that was saved to disk, or null if no previous state
     *                          was saved to disk
     * @return the reason why the state should be written to disk, or null if it shouldn't be written to disk
     */
    @Nullable
    private StateToDiskReason shouldSaveToDisk(
            @NonNull final SignedState signedState, @Nullable final Instant previousTimestamp) {

        if (signedState.isFreezeState()) {
            // the state right before a freeze should be written to disk
            return FREEZE_STATE;
        }

        final int saveStatePeriod = stateConfig.saveStatePeriod();
        if (saveStatePeriod <= 0) {
            // periodic state saving is disabled
            return null;
        }

        // FUTURE WORK: writing genesis state to disk is currently disabled if the saveStatePeriod is 0.
        // This is for testing purposes, to have a method of disabling state saving for tests.
        // Once a feature to disable all state saving has been added, this block should be moved in front of the
        // saveStatePeriod <=0 block, so that saveStatePeriod doesn't impact the saving of genesis state.
        if (previousTimestamp == null) {
            // the first round should be saved
            return FIRST_ROUND_AFTER_GENESIS;
        }

        if ((signedState.getConsensusTimestamp().getEpochSecond() / saveStatePeriod)
                > (previousTimestamp.getEpochSecond() / saveStatePeriod)) {
            return PERIODIC_SNAPSHOT;
        } else {
            // the period hasn't yet elapsed
            return null;
        }
    }
}
