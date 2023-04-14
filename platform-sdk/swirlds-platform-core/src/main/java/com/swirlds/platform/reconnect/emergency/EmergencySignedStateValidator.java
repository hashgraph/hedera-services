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

package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.logging.LogMarker.SIGNED_STATE;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import com.swirlds.platform.state.signed.SignedStateValidationData;
import com.swirlds.platform.state.signed.SignedStateValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates the signed state received by the learner in an emergency reconnect. If the received state has the exact
 * round and hash requested, it does not need to be fully signed. If the state is for a later round, it must be signed
 * by at least half the network Weight to be considered valid. The emergency reconnect scenario is described in
 * disaster-recovery.md.
 */
public class EmergencySignedStateValidator implements SignedStateValidator {
    private static final Logger logger = LogManager.getLogger(EmergencySignedStateValidator.class);
    private final EmergencyRecoveryFile emergencyRecoveryFile;

    /**
     * @param emergencyRecoveryFile
     * 		the emergency recovery file
     */
    public EmergencySignedStateValidator(final EmergencyRecoveryFile emergencyRecoveryFile) {
        this.emergencyRecoveryFile = emergencyRecoveryFile;
    }

    /**
     * {@inheritDoc}
     *
     * If the {@code signedState} is matches the request round and hash exactly, this method updates the next epoch hash
     * via {@link com.swirlds.platform.state.PlatformData#setNextEpochHash(Hash)}. Doing so does not modify the hash,
     * but will trigger the epoch hash to update when the next round reaches consensus.
     * Note: the previous state is ignored by this validator.  Emergency round, emergency state hash, and epoch hash
     * are used instead.
     */
    @Override
    public void validate(
            final SignedState signedState,
            final AddressBook addressBook,
            final SignedStateValidationData previousStateData)
            throws SignedStateInvalidException {
        logger.info(
                SIGNED_STATE.getMarker(),
                "Requested round {} with hash {}, received round {} with hash {}",
                emergencyRecoveryFile.round(),
                emergencyRecoveryFile.hash(),
                signedState.getRound(),
                signedState.getState().getHash());

        if (signedState.getRound() > emergencyRecoveryFile.round()) {
            verifyLaterRoundIsValid(signedState, addressBook);
        } else if (signedState.getRound() < emergencyRecoveryFile.round()) {
            throwStateTooOld(signedState);
        } else {
            verifyStateHashMatches(signedState);
        }
    }

    private void verifyStateHashMatches(final SignedState signedState) {
        if (!signedState.getState().getHash().equals(emergencyRecoveryFile.hash())) {
            logger.error(
                    SIGNED_STATE.getMarker(),
                    """
							Emergency recovery signed state round matches the request but hash does not.
							Failed emergency reconnect state:
							{}
							{}
							""",
                    () -> signedState.getState().getPlatformState().getInfoString(),
                    () -> new MerkleTreeVisualizer(signedState.getState())
                            .setDepth(StateSettings.getDebugHashDepth())
                            .render());

            throw new SignedStateInvalidException("Emergency recovery signed state is for the requested round but "
                    + "the hash does not match. Requested %s, received %s"
                            .formatted(
                                    emergencyRecoveryFile.hash(),
                                    signedState.getState().getHash()));
        }

        // FUTURE WORK: move this to the calling code (saved state loading and emergency reconnect) when
        // reconnect is refactored such that it no longer needs to be called by sync
        signedState.getState().getPlatformState().getPlatformData().setNextEpochHash(emergencyRecoveryFile.hash());

        logger.info(
                SIGNED_STATE.getMarker(),
                "Emergency recovery signed state matches the requested round and hash. "
                        + "Validation succeeded, next epoch hash updated.");
    }

    private void throwStateTooOld(final SignedState signedState) {
        logger.error(
                SIGNED_STATE.getMarker(),
                """
						State is too old. Failed emergency reconnect state:
						{}
						{}""",
                () -> signedState.getState().getPlatformState().getInfoString(),
                () -> new MerkleTreeVisualizer(signedState.getState())
                        .setDepth(StateSettings.getDebugHashDepth())
                        .render());

        throw new SignedStateInvalidException(String.format(
                "Emergency recovery signed state is for a round smaller than requested. Requested %d, received %d",
                emergencyRecoveryFile.round(), signedState.getRound()));
    }

    private void verifyLaterRoundIsValid(final SignedState signedState, final AddressBook addressBook) {
        logger.info(
                SIGNED_STATE.getMarker(),
                "Emergency recovery signed state is for round later than requested. "
                        + "Validating that the state is signed by a majority weight.");

        // must be fully signed
        checkSignatures(signedState, addressBook);

        // must have the correct epoch hash
        checkEpochHash(signedState);

        logger.info(
                SIGNED_STATE.getMarker(),
                "Signed state is a later, fully signed state with the correct epoch hash. Validation succeeded.");
    }

    private void checkEpochHash(final SignedState signedState) {
        final Hash epochHash =
                signedState.getState().getPlatformState().getPlatformData().getEpochHash();
        if (!emergencyRecoveryFile.hash().equals(epochHash)) {
            logger.error(
                    SIGNED_STATE.getMarker(),
                    """
						State is fully signed but has an incorrect epoch hash. Failed emergency recovery state:
						{}
						{}""",
                    () -> signedState.getState().getPlatformState().getInfoString(),
                    () -> new MerkleTreeVisualizer(signedState.getState())
                            .setDepth(StateSettings.getDebugHashDepth())
                            .render());

            throw new SignedStateInvalidException(
                    """
						Emergency recovery signed state has an incorrect epoch hash!
						Expected:\t%s
						Was:\t%s"""
                            .formatted(emergencyRecoveryFile.hash(), epochHash));
        }
    }

    private static void checkSignatures(final SignedState signedState, final AddressBook addressBook) {
        signedState.pruneInvalidSignatures(addressBook);
        signedState.throwIfIncomplete();
    }
}
