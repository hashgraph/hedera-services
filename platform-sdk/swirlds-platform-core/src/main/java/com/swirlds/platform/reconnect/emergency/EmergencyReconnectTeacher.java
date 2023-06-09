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

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.reconnect.ReconnectException;
import com.swirlds.platform.reconnect.ReconnectTeacher;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFinder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Executes emergency reconnect in the role of the teacher
 */
public class EmergencyReconnectTeacher {
    private static final Logger logger = LogManager.getLogger(EmergencyReconnectTeacher.class);
    private final SignedStateFinder stateFinder;
    private final int reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ThreadManager threadManager;

    @Nullable
    private final BooleanSupplier requestToStopTeaching;

    /**
     * @param threadManager          responsible for managing thread lifecycles
     * @param stateFinder            finds an acceptable state for emergency reconnect
     * @param reconnectSocketTimeout the socket timeout to use when executing a reconnect
     * @param requestToStopTeaching  to be checked periodically if teaching should be stopped
     * @param reconnectMetrics       tracks reconnect metrics
     */
    public EmergencyReconnectTeacher(
            final ThreadManager threadManager,
            final SignedStateFinder stateFinder,
            final int reconnectSocketTimeout,
            @Nullable final BooleanSupplier requestToStopTeaching,
            final ReconnectMetrics reconnectMetrics) {
        this.threadManager = threadManager;
        this.stateFinder = stateFinder;
        this.reconnectSocketTimeout = reconnectSocketTimeout;
        this.requestToStopTeaching = requestToStopTeaching;
        this.reconnectMetrics = reconnectMetrics;
    }

    /**
     * Builds a predicate used to find a state in a {@link com.swirlds.platform.state.signed.SignedStateManager} that is
     * safe to use for an emergency reconnect. Finds a signed state for the given round number and hash even if it is
     * not fully signed, or a later round that is signed by more than half the network weight.
     *
     * @param round the state must have a round greater than this if it does not exactly match the hash
     * @param hash  the hash of the state to find, ignored if the round is greater than the requested round
     * @return a predicate that decides if a state is suitable for an emergency reconnect
     */
    public static @NonNull Predicate<SignedState> emergencyStateCriteria(final long round, @NonNull final Hash hash) {
        return (final SignedState signedState) -> {
            if (signedState.isComplete() && signedState.getRound() > round) {
                return true;
            }
            return signedState.getRound() == round
                    && hash.equals(signedState.getState().getHash());
        };
    }

    /**
     * Performs emergency reconnect in the role of the teacher on the provided connection
     *
     * @param connection the connection to perform reconnect on
     */
    public void execute(final Connection connection) {
        try {
            final long round = connection.getDis().readLong();
            final Hash hash = connection.getDis().readSerializable();
            try (final ReservedSignedState reservedState =
                    stateFinder.find(emergencyStateCriteria(round, hash), "EmergencyReconnectTeacher.execute() find")) {
                if (reservedState.isNotNull()) {
                    writeHasState(connection, true);
                    logger.info(
                            RECONNECT.getMarker(),
                            "Beginning emergency reconnect in the role of teacher for node {}",
                            connection.getOtherId());

                    new ReconnectTeacher(
                                    threadManager,
                                    connection,
                                    reconnectSocketTimeout,
                                    connection.getSelfId(),
                                    connection.getOtherId(),
                                    reservedState.get().getRound(),
                                    requestToStopTeaching,
                                    reconnectMetrics)
                            .execute(reservedState.get());
                } else {
                    writeHasState(connection, false);
                    logger.info(
                            RECONNECT.getMarker(),
                            "Peer {} requested to perform an emergency reconnect but no compatible state was found.",
                            connection.getOtherId());
                }
            }
        } catch (final IOException e) {
            throw new ReconnectException(e);
        }
    }

    private static void writeHasState(final Connection connection, final boolean hasState) throws IOException {
        connection.getDos().writeBoolean(hasState);
        connection.getDos().flush();
    }
}
