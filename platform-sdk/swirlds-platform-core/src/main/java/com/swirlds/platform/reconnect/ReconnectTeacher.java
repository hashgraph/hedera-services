/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.formatting.StringFormattingUtils.formattedList;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.TeachingSynchronizer;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.logging.payloads.ReconnectFinishPayload;
import com.swirlds.logging.payloads.ReconnectStartPayload;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.net.SocketException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates reconnect logic for the up to date node which is helping an out of date node obtain a recent
 * state.
 */
public class ReconnectTeacher {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(ReconnectTeacher.class);

    private final Connection connection;
    private final int reconnectSocketTimeout;

    private final NodeId selfId;
    private final NodeId otherId;
    private final long lastRoundReceived;

    private final ReconnectMetrics statistics;

    /**
     * After reconnect is finished, restore the socket timeout to the original value.
     */
    private int originalSocketTimeout;

    private final ThreadManager threadManager;

    /**
     * A function to check periodically if teaching should be stopped, e.g. when the
     * teacher has fallen behind.
     */
    @Nullable
    private final BooleanSupplier requestToStopTeaching;

    /**
     * @param threadManager          responsible for managing thread lifecycles
     * @param connection             the connection to be used for the reconnect
     * @param reconnectSocketTimeout the socket timeout to use during the reconnect
     * @param selfId                 this node's ID
     * @param otherId                the learner's ID
     * @param lastRoundReceived      the round of the state
     * @param statistics             reconnect metrics
     */
    public ReconnectTeacher(
            @NonNull final ThreadManager threadManager,
            @NonNull final Connection connection,
            final int reconnectSocketTimeout,
            @NonNull final NodeId selfId,
            @NonNull final NodeId otherId,
            final long lastRoundReceived,
            @Nullable final BooleanSupplier requestToStopTeaching,
            @NonNull final ReconnectMetrics statistics) {

        this.threadManager = Objects.requireNonNull(threadManager);
        this.connection = Objects.requireNonNull(connection);
        this.reconnectSocketTimeout = reconnectSocketTimeout;

        this.selfId = Objects.requireNonNull(selfId);
        this.otherId = Objects.requireNonNull(otherId);
        this.lastRoundReceived = lastRoundReceived;
        this.requestToStopTeaching = requestToStopTeaching;
        this.statistics = Objects.requireNonNull(statistics);
    }

    /**
     * increase socketTimout before performing reconnect
     *
     * @throws ReconnectException thrown when there is an error in the underlying protocol
     */
    private void increaseSocketTimeout() throws ReconnectException {
        try {
            originalSocketTimeout = connection.getTimeout();
            connection.setTimeout(reconnectSocketTimeout);
        } catch (final SocketException e) {
            throw new ReconnectException(e);
        }
    }

    /**
     * Reset socketTimeout to original value
     *
     * @throws ReconnectException thrown when there is an error in the underlying protocol
     */
    private void resetSocketTimeout() throws ReconnectException {
        if (!connection.connected()) {
            logger.debug(
                    RECONNECT.getMarker(),
                    "{} connection to {} is no longer connected. Returning.",
                    connection.getSelfId(),
                    connection.getOtherId());
            return;
        }

        try {
            connection.setTimeout(originalSocketTimeout);
        } catch (final SocketException e) {
            throw new ReconnectException(e);
        }
    }

    /**
     * Perform the reconnect operation.
     *
     * @throws ReconnectException thrown when current thread is interrupted, or when any I/O related errors occur, or
     *                            when there is an error in the underlying protocol
     */
    public void execute(final SignedState signedState) throws ReconnectException {

        // If the connection object to be used here has been disconnected on another thread, we can
        // not reconnect with this connection.
        if (!connection.connected()) {
            logger.debug(
                    RECONNECT.getMarker(),
                    "{} connection to {} is no longer connected. Returning.",
                    connection.getSelfId(),
                    connection.getOtherId());
            return;
        }
        logReconnectStart(signedState);
        increaseSocketTimeout();

        try {
            sendSignatures(signedState);
            reconnect(signedState);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReconnectException(e);
        } catch (final IOException e) {
            throw new ReconnectException(e);
        } finally {
            resetSocketTimeout();
        }
        logReconnectFinish();
    }

    private void logReconnectStart(final SignedState signedState) {
        logger.info(
                RECONNECT.getMarker(),
                () -> new ReconnectStartPayload(
                        "Starting reconnect in the role of the sender",
                        false,
                        selfId.id(),
                        otherId.id(),
                        lastRoundReceived));
        logger.info(
                RECONNECT.getMarker(),
                "The following state will be sent to the learner:\n{}\n{}",
                () -> signedState.getState().getPlatformState().getInfoString(),
                () -> new MerkleTreeVisualizer(signedState.getState())
                        .setDepth(StateSettings.getDebugHashDepth())
                        .render());
    }

    private void logReconnectFinish() {
        logger.info(
                RECONNECT.getMarker(),
                () -> new ReconnectFinishPayload(
                        "Finished reconnect in the role of the sender.",
                        false,
                        selfId.id(),
                        otherId.id(),
                        lastRoundReceived));
    }

    /**
     * Copy the signed state from this node to the other node.
     *
     * @throws InterruptedException thrown if the current thread is interrupted
     */
    private void reconnect(final SignedState signedState) throws InterruptedException, IOException {
        logger.info(RECONNECT.getMarker(), "Starting synchronization in the role of the sender.");
        statistics.incrementSenderStartTimes();

        connection.getDis().getSyncByteCounter().resetCount();
        connection.getDos().getSyncByteCounter().resetCount();

        final TeachingSynchronizer synchronizer = new TeachingSynchronizer(
                threadManager,
                new MerkleDataInputStream(connection.getDis()),
                new MerkleDataOutputStream(connection.getDos()),
                signedState.getState(),
                connection::disconnect,
                requestToStopTeaching);

        synchronizer.synchronize();
        connection.getDos().flush();

        statistics.incrementSenderEndTimes();
        logger.info(RECONNECT.getMarker(), "Finished synchronization in the role of the sender.");
    }

    /**
     * Copy the signatures on the signed state from this node to the other node.
     *
     * @throws IOException thrown when any I/O related errors occur
     */
    private void sendSignatures(final SignedState signedState) throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("Sending signatures from nodes ");
        formattedList(sb, signedState.getSigSet().iterator());
        sb.append(" (signing weight = ")
                .append(signedState.getSigningWeight())
                .append("/")
                .append(signedState.getAddressBook().getTotalWeight())
                .append(") for state hash ")
                .append(signedState.getState().getHash());

        logger.info(RECONNECT.getMarker(), sb);
        connection.getDos().writeSerializable(signedState.getSigSet(), true);
        connection.getDos().flush();
    }
}
