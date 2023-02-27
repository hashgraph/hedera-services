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
import com.swirlds.platform.components.transaction.system.SystemTransactionHandler;
import com.swirlds.platform.components.transaction.system.SystemTransactionManager;
import com.swirlds.platform.components.transaction.system.TypedSystemTransactionHandler;
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
 * Implementation of {@link SystemTransactionManager}
 */
public class SystemTransactionManagerImpl implements SystemTransactionManager {

    /**
     * Class logger
     */
    private static final Logger logger = LogManager.getLogger(SystemTransactionManagerImpl.class);

    /**
     * Contains a map from system transaction class to a list of registered handling methods for that class
     *
     * @param handlerMap the map from transaction class to handlers
     */
    private record TransactionHandlers(
            Map<Class<? extends SystemTransaction>, List<SystemTransactionHandler<SystemTransaction>>> handlerMap) {

        /**
         * Add a handler to the handlerMap
         *
         * @param handler the new handler being tracked
         */
        @SuppressWarnings("unchecked")
        private void addHandler(final TypedSystemTransactionHandler<?> handler) {
            handlerMap
                    .computeIfAbsent(handler.transactionClass(), k -> new ArrayList<>())
                    .add((SystemTransactionHandler<SystemTransaction>) handler.handleMethod());
        }
    }

    /**
     * The pre-consensus handle methods that have been registered
     */
    private final TransactionHandlers preConsensusHandlers = new TransactionHandlers(new HashMap<>());

    /**
     * The post-consensus handle methods that have been registered
     */
    private final TransactionHandlers postConsensusHandlers = new TransactionHandlers(new HashMap<>());

    /**
     * Constructor
     *
     * @param handlers the consumers that this manager will keep track of and pass system transactions into
     */
    @SuppressWarnings("unchecked")
    public SystemTransactionManagerImpl(
            final List<TypedSystemTransactionHandler<? extends SystemTransaction>> handlers) {

        for (final TypedSystemTransactionHandler<?> handler : handlers) {
            // decide which group of TransactionHandlers to add this handler to
            final TransactionHandlers relevantHandlers =
                    switch (handler.handleStage()) {
                        case PRE_CONSENSUS -> preConsensusHandlers;
                        case POST_CONSENSUS -> postConsensusHandlers;
                    };

            relevantHandlers.addHandler(handler);
        }
    }

    /**
     * Pass an individual transaction to all handlers that want it
     *
     * @param state         the state
     * @param creatorId     the id of the creator of the transaction
     * @param transaction   the transaction being handled
     * @param handleMethods the handlers the transaction must be passed into
     */
    private static void handleTransaction(
            final State state,
            final long creatorId,
            final SystemTransaction transaction,
            final TransactionHandlers handleMethods) {

        final List<SystemTransactionHandler<SystemTransaction>> relevantHandlers =
                handleMethods.handlerMap().get(transaction.getClass());

        if (relevantHandlers == null) {
            // no handlers exist that want this transaction type in this stage
            return;
        }

        for (final SystemTransactionHandler<SystemTransaction> handler : relevantHandlers) {
            try {
                handler.handle(state, creatorId, transaction);
            } catch (final RuntimeException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Error while handling system transaction: handler: {}, id: {}, transaction: {}, error: {}",
                        handler,
                        creatorId,
                        transaction,
                        e);
            }
        }
    }

    /**
     * Handles an event, by passing each included transaction into the relevant handlers
     *
     * @param state         the state
     * @param event         the event being handled
     * @param handleMethods the methods that the transactions must be passed into
     */
    private static void handleEvent(final State state, final EventImpl event, final TransactionHandlers handleMethods) {
        event.systemTransactionIterator()
                .forEachRemaining(
                        transaction -> handleTransaction(state, event.getCreatorId(), transaction, handleMethods));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handlePreConsensusEvent(final State state, final EventImpl event) {
        // no pre-consensus handling methods have been registered
        if (preConsensusHandlers.handlerMap().isEmpty()) {
            return;
        }

        handleEvent(state, event, preConsensusHandlers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handlePostConsensusRound(final State state, final ConsensusRound round) {
        // no post-consensus handling methods have been registered
        if (postConsensusHandlers.handlerMap().isEmpty()) {
            return;
        }

        for (final EventImpl event : round.getConsensusEvents()) {
            handleEvent(state, event, postConsensusHandlers);
        }
    }
}
