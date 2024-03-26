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

package com.swirlds.platform.state.signed;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.system.SystemExitCode.FATAL_ERROR;

import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hashes signed states after all modifications for a round have been completed.
 */
public class DefaultSignedStateHasher implements SignedStateHasher {
    /**
     * The logger for the SignedStateHasher class.
     */
    private static final Logger logger = LogManager.getLogger(DefaultSignedStateHasher.class);
    /**
     * The SignedStateMetrics object to record time spent hashing.  May be null.
     */
    private final SignedStateMetrics signedStateMetrics;

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
     * @param fatalErrorConsumer the FatalErrorConsumer to consume any fatal errors during hashing.
     * @throws NullPointerException if any of the {@code fatalErrorConsumer} parameter is {@code null}.
     */
    public DefaultSignedStateHasher(
            @Nullable final SignedStateMetrics signedStateMetrics,
            @NonNull final FatalErrorConsumer fatalErrorConsumer) {
        this.fatalErrorConsumer = Objects.requireNonNull(fatalErrorConsumer, "fatalErrorConsumer must not be null");
        this.signedStateMetrics = signedStateMetrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public StateAndRound hashState(@NonNull final StateAndRound stateAndRound) {
        final Instant start = Instant.now();
        try {
            MerkleCryptoFactory.getInstance()
                    .digestTreeAsync(stateAndRound.reservedSignedState().get().getState())
                    .get();

            if (signedStateMetrics != null) {
                signedStateMetrics
                        .getSignedStateHashingTimeMetric()
                        .update(Duration.between(start, Instant.now()).toMillis());
            }

            return stateAndRound;
        } catch (final ExecutionException e) {
            fatalErrorConsumer.fatalError("Exception occurred during SignedState hashing", e, FATAL_ERROR);
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "Interrupted while hashing state. Expect buggy behavior.");
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
