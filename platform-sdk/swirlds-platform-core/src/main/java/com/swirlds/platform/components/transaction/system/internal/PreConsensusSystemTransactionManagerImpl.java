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
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionHandler;
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionTypedHandler;
import com.swirlds.platform.internal.EventImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of {@link PreConsensusSystemTransactionManager}
 */
public class PreConsensusSystemTransactionManagerImpl implements PreConsensusSystemTransactionManager {

    /**
     * Class logger
     */
    private static final Logger logger = LogManager.getLogger(PreConsensusSystemTransactionManagerImpl.class);

    /**
     * The pre-consensus handle methods that have been registered
     */
    private final Map<Class<? extends SystemTransaction>, List<PreConsensusSystemTransactionHandler<SystemTransaction>>>
            handlers = new HashMap<>();

    /**
     * Add a handle method
     *
     * @param handler the new handler being tracked
     */
    @SuppressWarnings("unchecked")
    private void addHandler(final PreConsensusSystemTransactionTypedHandler<?> handler) {
        handlers.computeIfAbsent(handler.transactionClass(), k -> new ArrayList<>())
                .add((PreConsensusSystemTransactionHandler<SystemTransaction>) handler.handleMethod());
    }

    /**
     * Constructor
     *
     * @param handlers the consumers that this manager will keep track of and pass system transactions into
     */
    public PreConsensusSystemTransactionManagerImpl(
            final List<PreConsensusSystemTransactionTypedHandler<? extends SystemTransaction>> handlers) {

        handlers.forEach(this::addHandler);
    }

    /**
     * Pass an individual transaction to all handlers that want it
     *
     * @param creatorId   the id of the creator of the transaction
     * @param transaction the transaction being handled
     */
    private void handleTransaction(final long creatorId, final SystemTransaction transaction) {
        final List<PreConsensusSystemTransactionHandler<SystemTransaction>> relevantHandlers =
                handlers.get(transaction.getClass());

        if (relevantHandlers == null) {
            // no handlers exist that want this transaction type in this stage
            return;
        }

        for (final PreConsensusSystemTransactionHandler<SystemTransaction> handler : relevantHandlers) {
            try {
                handler.handle(creatorId, transaction);
            } catch (final RuntimeException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Error while handling system transaction pre-consensus: handler: {}, id: {}, transaction: {}, error: {}",
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
    public void handleEvent(final EventImpl event) {
        // no pre-consensus handling methods have been registered
        if (handlers.isEmpty()) {
            return;
        }

        event.systemTransactionIterator()
                .forEachRemaining(transaction -> handleTransaction(event.getCreatorId(), transaction));
    }
}
