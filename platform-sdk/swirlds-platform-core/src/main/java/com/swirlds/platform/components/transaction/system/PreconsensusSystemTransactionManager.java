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
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Routs preconsensus system transactions to the appropriate handlers.
 */
public class PreconsensusSystemTransactionManager {

    /**
     * Class logger
     */
    private static final Logger logger = LogManager.getLogger(PreconsensusSystemTransactionManager.class);

    /**
     * The pre-consensus handle methods that have been registered
     */
    private final Map<Class<?>, List<PreconsensusSystemTransactionHandler<SystemTransaction>>> handlers =
            new HashMap<>();

    /**
     * Add a handle method
     *
     * @param clazz   the class of the transaction being handled
     * @param handler a method to handle this transaction type
     */
    @SuppressWarnings("unchecked")
    public <T extends SystemTransaction> void addHandler(
            @NonNull final Class<T> clazz, @NonNull final PreconsensusSystemTransactionHandler<T> handler) {

        Objects.requireNonNull(clazz);
        Objects.requireNonNull(handler);

        handlers.computeIfAbsent(clazz, k -> new ArrayList<>())
                .add((PreconsensusSystemTransactionHandler<SystemTransaction>) handler);
    }

    /**
     * Pass an individual transaction to all handlers that want it
     *
     * @param creatorId   the id of the creator of the transaction
     * @param transaction the transaction being handled
     */
    private void handleTransaction(@NonNull final NodeId creatorId, @NonNull final SystemTransaction transaction) {
        Objects.requireNonNull(creatorId, "creatorId must not be null");
        Objects.requireNonNull(transaction, "transaction must not be null");

        final List<PreconsensusSystemTransactionHandler<SystemTransaction>> relevantHandlers =
                handlers.get(transaction.getClass());

        if (relevantHandlers == null) {
            // no handlers exist that want this transaction type in this stage
            return;
        }

        for (final PreconsensusSystemTransactionHandler<SystemTransaction> handler : relevantHandlers) {
            try {
                handler.handle(creatorId, transaction);
            } catch (final RuntimeException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Error while handling system transaction preconsensus: handler: {}, id: {}, transaction: {}, error: {}",
                        handler,
                        creatorId,
                        transaction,
                        e);
            }
        }
    }

    /**
     * Handle a pre-consensus event by passing each included system transaction to the registered handlers
     *
     * @param event the pre-consensus event
     */
    public void handleEvent(@NonNull final EventImpl event) {
        // no pre-consensus handling methods have been registered
        if (handlers.isEmpty()) {
            return;
        }

        event.systemTransactionIterator()
                .forEachRemaining(transaction -> handleTransaction(event.getCreatorId(), transaction));
    }
}
