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

import com.hedera.services.context.properties.NodeLocalProperties;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Processing dispatch layer for consensus transactions that uses an LMAX disruptor
 * to coordinate the execution sequence of the various stages that belong to this part of
 * the transaction lifecycle. The consensus stages are:
 *
 * 1. Validation (parallel) - validation of independent aspects of a transaction (that is,
 * aspects that are independent of other transactions).
 * 2. Validation (serial) - validation of dependent aspects of a transaction (that is,
 * aspects that rely on other transactions).
 * 3. Pre-fetch (parallel) - fetching of data that can be used during the serial execution
 * portion of the transaction (for example, loading of merkle leaves for update).
 * 4. Initialization (serial) - transaction log (ledger) initialization, advancing of consensus clock
 * 5. Execution (serial) - application of transition logic.
 *
 * The choice of wait strategy is outlined in {@code AbstractProcessor}. Please refer to that
 * class for a more detailed explanation.
 */
@Singleton
public class ConsensusProcessor extends AbstractProcessor {
    public static final int DEFAULT_VALIDATION_HANDLERS = 2;

    ConsensusPublisher publisher;

    @Inject
    public ConsensusProcessor(
            NodeLocalProperties properties,
            ValidationHandlerFactory validationHandlerFactory,
            ExecutionHandler.Factory executionHandlerFactory,
            ConsensusPublisher.Factory publisherFactory
    ) {
        super(properties.consensusRingBufferPower(), "consensus-handler", (disruptor) -> {
            int numHandlers = Math.max(properties.consensusValidationHandlerCount(), DEFAULT_VALIDATION_HANDLERS);
            ValidationHandler validationHandlers[] = new ValidationHandler[numHandlers];
            for (int j = 0; j < numHandlers; j++) {
                validationHandlers[j] = validationHandlerFactory.createForConsensus(j, numHandlers, false);
            }

            disruptor.handleEventsWith(validationHandlers)
                    .then(executionHandlerFactory.create(true));
        });

        publisher = publisherFactory.create(disruptor.getRingBuffer());
    }

    public ConsensusPublisher getPublisher() {
        return publisher;
    }
}
