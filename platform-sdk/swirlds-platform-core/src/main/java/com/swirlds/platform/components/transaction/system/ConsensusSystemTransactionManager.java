/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.transaction.system;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Routes consensus system transactions to the appropriate handlers.
 */
public class ConsensusSystemTransactionManager {

    /**
     * Class logger
     */
    private static final Logger logger = LogManager.getLogger(ConsensusSystemTransactionManager.class);

    /**
     * The post-consensus handle methods that have been registered
     */
    private final Map<Class<?>, List<ConsensusSystemTransactionHandler<SystemTransaction>>> handlers = new HashMap<>();

    /**
     * Add a handle method
     *
     * @param clazz   the class of the transaction being handled
     * @param handler a method to handle this transaction type
     */
    @SuppressWarnings("unchecked")
    public <T extends SystemTransaction> void addHandler(
            @NonNull final Class<T> clazz, @NonNull final ConsensusSystemTransactionHandler<T> handler) {

        Objects.requireNonNull(clazz);
        Objects.requireNonNull(handler);

        handlers.computeIfAbsent(clazz, k -> new ArrayList<>())
                .add((ConsensusSystemTransactionHandler<SystemTransaction>) handler);
    }

    /**
     * Pass an individual transaction to all handlers that want it
     *
     * @param state        the state
     * @param creatorId    the id of the creator of the transaction
     * @param transaction  the transaction being handled
     * @param eventVersion the version of the event that contains the transaction
     */
    private void handleTransaction(
            @NonNull final State state,
            @NonNull final NodeId creatorId,
            @NonNull final SystemTransaction transaction,
            @Nullable SoftwareVersion eventVersion) {
        Objects.requireNonNull(creatorId, "creatorId must not be null");
        Objects.requireNonNull(transaction, "transaction must not be null");

        final List<ConsensusSystemTransactionHandler<SystemTransaction>> relevantHandlers =
                handlers.get(transaction.getClass());

        if (relevantHandlers == null) {
            // no handlers exist that want this transaction type
            return;
        }

        for (final ConsensusSystemTransactionHandler<SystemTransaction> handler : relevantHandlers) {
            try {
                handler.handle(state, creatorId, transaction, eventVersion);
            } catch (final RuntimeException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Error while handling system transaction post consensus: "
                                + "handler: {}, id: {}, transaction: {}, error: {}",
                        handler,
                        creatorId,
                        transaction,
                        e);
            }
        }
    }

    /**
     * Handle a post-consensus round by passing each included system transaction to the registered handlers
     *
     * @param state a mutable state
     * @param round the post-consensus round
     */
    public void handleRound(@NonNull final State state, @NonNull final ConsensusRound round) {
        // no post-consensus handling methods have been registered
        if (handlers.isEmpty()) {
            return;
        }

        Objects.requireNonNull(state);

        for (final EventImpl event : round.getConsensusEvents()) {
            event.systemTransactionIterator()
                    .forEachRemaining(transaction ->
                            handleTransaction(state, event.getCreatorId(), transaction, event.getSoftwareVersion()));
        }
    }
}
