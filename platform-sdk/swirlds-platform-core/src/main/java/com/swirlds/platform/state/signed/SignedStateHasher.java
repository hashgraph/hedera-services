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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hashes signed states after all modifications for a round have been compoleted.
 */
public class SignedStateHasher {
    /**
     * The logger for the SignedStateHasher class.
     */
    private static final Logger logger = LogManager.getLogger(SignedStateHasher.class);
    /**
     * The SignedStateMetrics object to record time spent hashing.  May be null.
     */
    private final SignedStateMetrics signedStateMetrics;

    /**
     * The StateHashedTrigger to notify if the hashing is successful.
     */
    private final StateHashedTrigger stateHashedTrigger;

    /**
     * The FatalErrorConsumer to notify with any fatal errors that occur during hashing.
     */
    private final FatalErrorConsumer fatalErrorConsumer;

    /**
     * Constructs a SignedStateHasher to hash SignedStates.  If the signedStateMetrics object is not null, the time
     * spent hashing is recorded. Any fatal errors that occur are passed to the provided FatalErrorConsumer. The hash is
     * dispatched to the provided StateHashedTrigger.
     *
     * @param signedStateMetrics the SignedStateMetrics instance to record time spent hashing.
     * @param stateHashedTrigger the StateHashedTrigger dispatcher to notify with hash.
     * @param fatalErrorConsumer the FatalErrorConsumer to consume any fatal errors during hashing.
     */
    public SignedStateHasher(
            SignedStateMetrics signedStateMetrics,
            StateHashedTrigger stateHashedTrigger,
            FatalErrorConsumer fatalErrorConsumer) {
        this.stateHashedTrigger = CommonUtils.throwArgNull(stateHashedTrigger, "stateHashedTrigger");
        this.fatalErrorConsumer = CommonUtils.throwArgNull(fatalErrorConsumer, "fatalErrorConsumer");
        this.signedStateMetrics = signedStateMetrics;
    }

    /**
     * Hashes a SignedState.
     *
     * @param signedState the SignedState to hash.
     */
    public void hashState(final SignedState signedState) {
        final Instant start = Instant.now();
        try {
            final Hash hash = MerkleCryptoFactory.getInstance()
                    .digestTreeAsync(signedState.getState())
                    .get();

            if (signedStateMetrics != null) {
                signedStateMetrics
                        .getSignedStateHashingTimeMetric()
                        .update(Duration.between(start, Instant.now()).toMillis());
            }

            stateHashedTrigger.dispatch(signedState.getRound(), hash);

        } catch (final ExecutionException e) {
            fatalErrorConsumer.fatalError("Exception occurred during SignedState hashing", e, null);
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "Interrupted while hashing state. Expect buggy behavior.");
            Thread.currentThread().interrupt();
        }
    }
}
