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

package com.swirlds.platform.components.transaction.system.internal;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionHandler;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionTypedHandler;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of {@link PostConsensusSystemTransactionManager}
 */
public class PostConsensusSystemTransactionManagerImpl implements PostConsensusSystemTransactionManager {

    /**
     * Class logger
     */
    private static final Logger logger = LogManager.getLogger(PostConsensusSystemTransactionManagerImpl.class);

    /**
     * The post-consensus handle methods that have been registered
     */
    private final Map<Class<? extends SystemTransaction>, List<PostConsensusSystemTransactionHandler<SystemTransaction>>>
            handlers = new HashMap<>();

    /**
     * Add a handle method
     *
     * @param handler the new handler being tracked
     */
    @SuppressWarnings("unchecked")
    private void addHandler(final PostConsensusSystemTransactionTypedHandler<?> handler) {
        handlers
                .computeIfAbsent(handler.transactionClass(), k -> new ArrayList<>())
                .add((PostConsensusSystemTransactionHandler<SystemTransaction>) handler.handleMethod());
    }

    /**
     * Constructor
     *
     * @param handlers the consumers that this manager will keep track of and pass system transactions into
     */
    public PostConsensusSystemTransactionManagerImpl(
            final List<PostConsensusSystemTransactionTypedHandler<?>> handlers) {

        handlers.forEach(this::addHandler);
    }

    /**
     * Pass an individual transaction to all handlers that want it
     *
     * @param state       the state
     * @param creatorId   the id of the creator of the transaction
     * @param transaction the transaction being handled
     */
    private void handleTransaction(
            final State state,
            final long creatorId,
            final SystemTransaction transaction) {

        final List<PostConsensusSystemTransactionHandler<SystemTransaction>> relevantHandlers =
                handlers.get(transaction.getClass());

        if (relevantHandlers == null) {
            // no handlers exist that want this transaction type
            return;
        }

        for (final PostConsensusSystemTransactionHandler<SystemTransaction> handler : relevantHandlers) {
            try {
                handler.handle(state, creatorId, transaction);
            } catch (final RuntimeException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Error while handling system transaction post consensus: handler: {}, id: {}, transaction: {}, error: {}",
                        handler,
                        creatorId,
                        transaction,
                        e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleRound(final State state, final ConsensusRound round) {
        // no post-consensus handling methods have been registered
        if (handlers.isEmpty()) {
            return;
        }

        for (final EventImpl event : round.getConsensusEvents()) {
            event.systemTransactionIterator().forEachRemaining(
                    transaction -> handleTransaction(state, event.getCreatorId(), transaction));
        }
    }
}
