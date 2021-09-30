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

import com.hedera.services.utils.PlatformTxnAccessor;
import com.lmax.disruptor.EventHandler;

import java.util.function.Consumer;

public class ValidationHandler implements EventHandler<TransactionEvent> {
    long id;
    int numHandlers;
    boolean isLastHandler;
    Consumer<PlatformTxnAccessor>[] actions;

    public ValidationHandler(
            long id,
            int numHandlers,
            boolean isLastHandler,
            Consumer<PlatformTxnAccessor> ...actions
    ) {
        this.id = id;
        this.numHandlers = numHandlers;
        this.isLastHandler = isLastHandler;
        this.actions = actions;
    }

    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) {
        // Only handle events assigned to this handler
        if (sequence % numHandlers != id)
            return;

        try {
            // Don't process if we encountered a parsing error from the event publisher
            if (!event.isErrored()) {
                final var accessor = event.getAccessor();
                for (Consumer<PlatformTxnAccessor> action : actions)
                    action.accept(accessor);
            }
        } finally {
            // This is the last event handler so clear the references in the event slot. If we don't do this
            // the accessor object will have a hard link and not be GC'ed until this slot is overwritten by
            // another event.
            if (isLastHandler)
                event.clear();
        }
    }
}
