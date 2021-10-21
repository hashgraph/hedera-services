package com.hedera.services.disruptor;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.txns.TransitionLogicLookup;
import com.lmax.disruptor.EventHandler;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Disruptor event handler that is responsible for executing pre-fetch logic associated with
 * the transaction's transition logic class.
 */
public class PreFetchHandler implements EventHandler<TransactionEvent> {
    private static final Logger logger = LogManager.getLogger(PreFetchHandler.class);

    long id;
    int numHandlers;
    boolean isLastHandler;
    TransitionLogicLookup lookup;

    @AssistedInject
    public PreFetchHandler(
            @Assisted long id,
            @Assisted int numHandlers,
            @Assisted boolean isLastHandler,
            TransitionLogicLookup lookup
    ) {
        this.id = id;
        this.numHandlers = numHandlers;
        this.isLastHandler = isLastHandler;
        this.lookup = lookup;
    }

    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) {
        // Only handle events assigned to this handler
        if (sequence % numHandlers != id)
            return;

        try {
            // Don't process if we encountered a parsing error from the event publisher
            if (!event.isErrored()) {
                final var accessor = event.getAccessor();
                final var opt = lookup.lookupFor(accessor.getFunction(), accessor.getTxn());

                if (!opt.isEmpty()) {
                    final var logic = opt.get();
                    if (logic instanceof PreFetchableTransition)
                        ((PreFetchableTransition) logic).preFetch(accessor);
                }
            }
        } catch(Exception e) {
            // Don't propagate exception, since pre-fetch is done during prepare phase, all
            // actions are optional.
            logger.warn("Unhandled exception while executing preFetch logic", e);
        } finally {
            // This is the last event handler so clear the references in the event slot. If we don't do this
            // the accessor object will have a hard link and not be GC'ed until this slot is overwritten by
            // another event.
            if (isLastHandler)
                event.clear();
        }
    }

    /**
     * Dagger factory for PreFetchHandler instances. The last handler flag must be provided
     * by the layer using the handlers. Any other parameters are filled in by the Dagger DI layer.
     */
    @AssistedFactory
    public interface Factory {
        PreFetchHandler create(long id, int numHandlers, boolean isLastHandler);
    }
}
