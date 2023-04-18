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

package com.swirlds.platform.reconnect;

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Connection;
import com.swirlds.platform.components.state.query.LatestSignedStateProvider;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.unidirectional.NetworkProtocolResponder;
import com.swirlds.platform.state.signed.ReservedSignedState;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This reconnect implementation is used when sync gossip is enabled.
 */
public class ReconnectProtocolResponder implements NetworkProtocolResponder {
    private static final Logger logger = LogManager.getLogger(ReconnectProtocolResponder.class);

    private final LatestSignedStateProvider latestSignedStateProvider;
    private final ReconnectSettings settings;
    /**
     * This object is responsible for limiting the frequency of reconnect attempts (in the role of
     * the sender)
     */
    private final ReconnectThrottle reconnectThrottle;

    private final ReconnectMetrics stats;
    private final ThreadManager threadManager;

    /**
     * @param threadManager responsible for managing thread lifecycles
     * @param latestSignedStateProvider a function that provides the latest signed state, either
     *     strongly or weakly reserved. The caller is responsible for releasing the reservation when
     *     finished.
     * @param settings reconnect settings
     * @param reconnectThrottle limits when reconnect may start
     * @param stats reconnect metrics
     */
    public ReconnectProtocolResponder(
            final ThreadManager threadManager,
            final LatestSignedStateProvider latestSignedStateProvider,
            final ReconnectSettings settings,
            final ReconnectThrottle reconnectThrottle,
            final ReconnectMetrics stats) {
        this.threadManager = threadManager;
        this.latestSignedStateProvider = latestSignedStateProvider;
        this.settings = settings;
        this.reconnectThrottle = reconnectThrottle;
        this.stats = stats;
    }

    /** {@inheritDoc} */
    @Override
    public void protocolInitiated(final byte initialByte, final Connection connection) throws IOException {
        logger.info(
                RECONNECT.getMarker(),
                "{} got COMM_STATE_REQUEST from {}",
                connection.getSelfId(),
                connection.getOtherId());

        try (final ReservedSignedState state =
                latestSignedStateProvider.getLatestSignedState("ReconnectProtocolResponder.protocolInitiated()")) {

            if (state.isNull()) {
                logger.info(
                        RECONNECT.getMarker(),
                        "Rejecting reconnect request from node {} due to lack of a fully signed state",
                        connection.getOtherId().getId());
                ReconnectUtils.denyReconnect(connection);
                return;
            }

            if (!state.get().getState().isInitialized()) {
                ReconnectUtils.denyReconnect(connection);
                logger.warn(
                        RECONNECT.getMarker(),
                        "Rejecting reconnect request from node {} due to lack of an initialized signed state.",
                        connection.getOtherId().getId());
                return;
            } else if (!state.get().isComplete()) {
                // this is only possible if signed state manager violates its contractual obligations
                ReconnectUtils.denyReconnect(connection);
                logger.error(
                        RECONNECT.getMarker(),
                        "Rejecting reconnect request from node {} due to lack of a fully signed state."
                                + " The signed state manager attempted to provide a state that was not"
                                + " fully signed, which should not be possible.",
                        connection.getOtherId().getId());
                return;
            }

            if (!reconnectThrottle.initiateReconnect(connection.getOtherId().getId())) {
                ReconnectUtils.denyReconnect(connection);
                return;
            }

            try {
                ReconnectUtils.confirmReconnect(connection);
                new ReconnectTeacher(
                                threadManager,
                                connection,
                                state.getAndReserve("ReconnectProtocolResponder.protocolInitiated() reconnect"),
                                settings.getAsyncStreamTimeoutMilliseconds(),
                                connection.getSelfId().getId(),
                                connection.getOtherId().getId(),
                                state.get().getRound(),
                                stats)
                        .execute();
            } finally {
                reconnectThrottle.reconnectAttemptFinished();
            }
        }
    }
}
