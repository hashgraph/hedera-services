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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.io.exceptions.BadIOException;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.system.NodeId;
import com.swirlds.logging.payloads.ReconnectFailurePayload;
import com.swirlds.platform.Connection;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.state.State;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A set of static methods to aid with reconnect
 */
public final class ReconnectUtils {
    private static final Logger logger = LogManager.getLogger(ReconnectUtils.class);

    private ReconnectUtils() {}

    /**
     * Validate that the other node is willing to reconnect with us.
     *
     * @param connection
     * 		the connection to use
     * @return true if the other node is willing to assist with reconnect
     * @throws IOException
     * 		thrown when any I/O related errors occur
     * @throws ReconnectException
     * 		thrown when the other node is unwilling to reconnect right now
     */
    public static boolean isNodeReadyForReconnect(final Connection connection) throws IOException, ReconnectException {
        final NodeId otherId = connection.getOtherId();
        final SyncInputStream dis = connection.getDis();
        final SyncOutputStream dos = connection.getDos();

        // send the request
        dos.write(ByteConstants.COMM_STATE_REQUEST);
        dos.flush();
        logger.info(RECONNECT.getMarker(), "Requesting to reconnect with node {}.", otherId);

        // read the response
        final byte stateResponse = dis.readByte();
        if (stateResponse == ByteConstants.COMM_STATE_ACK) {
            logger.info(RECONNECT.getMarker(), "Node {} is willing to help this node to reconnect.", otherId);
            return true;
        } else if (stateResponse == ByteConstants.COMM_STATE_NACK) {
            logger.info(
                    RECONNECT.getMarker(),
                    new ReconnectFailurePayload(
                            "Node " + otherId + " is unwilling to help this node to reconnect.",
                            ReconnectFailurePayload.CauseOfFailure.REJECTION));
            return false;
        } else {
            throw new BadIOException("COMM_STATE_REQUEST was sent but reply was " + stateResponse
                    + " instead of COMM_STATE_ACK or COMM_STATE_NACK");
        }
    }

    /**
     * Write a flag to the stream. Informs the receiver that reconnect will proceed.
     *
     * @param connection
     * 		the connection to use
     * @throws IOException
     * 		thrown when any I/O related errors occur
     */
    static void confirmReconnect(final Connection connection) throws IOException {
        connection.getDos().write(ByteConstants.COMM_STATE_ACK);
        connection.getDos().flush();
    }

    /**
     * Write a flag to the stream. Informs the receiver that reconnect will not proceed.
     *
     * @param connection
     * 		the connection to use
     * @throws IOException
     * 		thrown when any I/O related errors occur
     */
    static void denyReconnect(final Connection connection) throws IOException {
        connection.getDos().write(ByteConstants.COMM_STATE_NACK);
        connection.getDos().flush();
    }

    /**
     * Hash the working state to prepare for reconnect
     */
    static void hashStateForReconnect(final State workingState) {
        try {
            MerkleCryptoFactory.getInstance().digestTreeAsync(workingState).get();
        } catch (final ExecutionException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    () -> new ReconnectFailurePayload(
                                    "Error encountered while hashing state for reconnect",
                                    ReconnectFailurePayload.CauseOfFailure.ERROR)
                            .toString(),
                    e);
            throw new ReconnectException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error(
                    EXCEPTION.getMarker(),
                    () -> new ReconnectFailurePayload(
                                    "Interrupted while attempting to hash state",
                                    ReconnectFailurePayload.CauseOfFailure.ERROR)
                            .toString(),
                    e);
        }
    }
}
