/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import com.swirlds.platform.state.signed.SignedStateValidationData;
import com.swirlds.platform.state.signed.SignedStateValidator;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates a signed state by summing the amount of weight held by the valid signatures on the state.
 */
public class DefaultSignedStateValidator implements SignedStateValidator {
    private static final Logger logger = LogManager.getLogger(DefaultSignedStateValidator.class);

    private final int hashDepth;

    public DefaultSignedStateValidator(@NonNull final PlatformContext platformContext) {
        hashDepth = platformContext
                .getConfiguration()
                .getConfigData(StateConfig.class)
                .debugHashDepth();
    }

    /**
     * {@inheritDoc}
     */
    public void validate(
            final SignedState signedState, final AddressBook addressBook, SignedStateValidationData previousStateData) {
        throwIfOld(signedState, previousStateData);
        signedState.pruneInvalidSignatures(addressBook);
        signedState.throwIfNotVerifiable();
    }

    /**
     * Check the signed state against metadata from a prior state, and throw if the new state is older. After a
     * reconnect, there is a slight possibility the teacher sent older state than we already had.  In that case we do
     * not want to keep the state, but try again with a different teacher.  This method checks that the received state
     * is at least as new as the state we started with, and throws if the received state is not acceptable.
     *
     * @param signedState       a newly received signed state from a reconnect process.
     * @param previousStateData The validation data from the current address book and prior state, before reconnect.
     * @throws SignedStateInvalidException if the signed state is not at least as new as the previous state.
     */
    private void throwIfOld(final SignedState signedState, final SignedStateValidationData previousStateData)
            throws SignedStateInvalidException {

        if (signedState.getState().getReadablePlatformState().getRound() < previousStateData.round()
                || signedState
                        .getState()
                        .getReadablePlatformState()
                        .getConsensusTimestamp()
                        .isBefore(previousStateData.consensusTimestamp())) {
            logger.error(
                    EXCEPTION.getMarker(),
                    """
                            State is too old. Failed reconnect state:
                            {}
                            Original reconnect state:
                            {}""",
                    signedState.getState().getInfoString(hashDepth),
                    previousStateData.getInfoString());
            throw new SignedStateInvalidException(("Received signed state is for a round smaller than or a "
                            + "consensus earlier than what we started with. Original round %d, received round %d. "
                            + "Original timestamp %s, received timestamp %s.")
                    .formatted(
                            previousStateData.round(),
                            signedState.getRound(),
                            previousStateData.consensusTimestamp(),
                            signedState.getConsensusTimestamp()));
        }
    }
}
