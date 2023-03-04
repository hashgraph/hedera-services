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
import com.swirlds.platform.Connection;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.reconnect.ReconnectException;
import com.swirlds.platform.reconnect.ReconnectTeacher;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Executes emergency reconnect in the role of the teacher
 */
public class EmergencyReconnectTeacher {
    private static final Logger logger = LogManager.getLogger(EmergencyReconnectTeacher.class);
    private final EmergencyStateFinder stateFinder;
    private final int reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ThreadManager threadManager;

    /**
     * @param threadManager          responsible for managing thread lifecycles
     * @param stateFinder            finds an acceptable state for emergency reconnect
     * @param reconnectSocketTimeout the socket timeout to use when executing a reconnect
     * @param reconnectMetrics       tracks reconnect metrics
     */
    public EmergencyReconnectTeacher(
            final ThreadManager threadManager,
            final EmergencyStateFinder stateFinder,
            final int reconnectSocketTimeout,
            final ReconnectMetrics reconnectMetrics) {
        this.threadManager = threadManager;
        this.stateFinder = stateFinder;
        this.reconnectSocketTimeout = reconnectSocketTimeout;
        this.reconnectMetrics = reconnectMetrics;
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

            final ReservedSignedState reservedState =
                    stateFinder.find(round, hash, "EmergencyReconnectTeacher.execute");

            if (reservedState.isNotNull()) {
                final SignedState state = reservedState.get();
                writeHasState(connection, true);
                logger.info(
                        RECONNECT.getMarker(),
                        "Beginning emergency reconnect in the role of teacher for node {}",
                        connection.getOtherId());

                new ReconnectTeacher(
                                threadManager,
                                connection,
                                reservedState,
                                reconnectSocketTimeout,
                                connection.getSelfId().getId(),
                                connection.getOtherId().getId(),
                                state.getRound(),
                                reconnectMetrics)
                        .execute();
            } else {
                writeHasState(connection, false);
                logger.info(
                        RECONNECT.getMarker(),
                        "Peer {} requested to perform an emergency reconnect but no compatible state was found.",
                        connection.getOtherId());
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
