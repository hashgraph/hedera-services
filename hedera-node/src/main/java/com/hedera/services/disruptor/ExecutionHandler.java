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

import com.hedera.services.state.logic.StandardProcessLogic;
import com.lmax.disruptor.EventHandler;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExecutionHandler implements EventHandler<TransactionEvent> {
    private static final Logger logger = LogManager.getLogger(ExecutionHandler.class);

    boolean isLastHandler;
    StandardProcessLogic processLogic;

    @AssistedInject
    public ExecutionHandler(@Assisted boolean isLastHandler, StandardProcessLogic processLogic) {
        this.isLastHandler = isLastHandler;
        this.processLogic = processLogic;
    }

    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) {
        try {
            // Don't process if we encountered a parsing error from the event publisher
            if (!event.isErrored()) {
                processLogic.incorporateConsensusTxn(
                        event.getAccessor(),
                        event.getConsensusTime(),
                        event.getSubmittingMember()
                );
            }
        } catch(Exception e) {
            logger.warn("Unhandled exception while execution consensus logic", e);

            // Do we need to have a top-level catch block? What transaction status do we set?
//            event.getAccessor().setValidationStatus(INVALID_TRANSACTION);
        } finally {
            // This is the last event handler so clear the references in the event slot. If we don't do this
            // the accessor object will have a hard link and not be GC'ed until this slot is overwritten by
            // another event.
            if (isLastHandler)
                event.clear();
        }
    }

    /**
     * Dagger factory for ExecutionHandler instances. The last handler flag must be provided
     * by the layer using the handlers. Any other parameters are filled in by the Dagger DI layer.
     */
    @AssistedFactory
    public interface Factory {
        ExecutionHandler create(boolean isLastHandler);
    }
}
