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
 * Processing dispatch layer for pre-consensus transactions that uses an LMAX disruptor
 * to coordinate the execution sequence of the various stages that belong to this part of
 * the transaction lifecycle. The pre-consensus stages are:
 *
 * 1. Validation (parallel) - validation of independent aspects of a transaction (that is,
 * aspects that are independent of other transactions).
 * 2. Pre-fetch (parallel) - fetching of data that can be used during the serial execution
 * portion of the transaction (for example, fetching of contract EVM bytecode).
 *
 * The choice of wait strategy is outlined in {@code AbstractProcessor}. Please refer to that
 * class for a more detailed explanation.
 */
@Singleton
public class PreConsensusProcessor extends AbstractProcessor {
    public static final int DEFAULT_VALIDATION_HANDLERS = 1;

    PreConsensusPublisher publisher;

    @Inject
    public PreConsensusProcessor(
            NodeLocalProperties properties,
            ValidationHandlerFactory validationHandlerFactory,
            PreConsensusPublisher.Factory publisherFactory
    ) {
        super(properties.preConsensusRingBufferPower(), "pre-consensus-handler", (disruptor) -> {
            int numHandlers = Math.max(properties.preConsensusValidationHandlerCount(), DEFAULT_VALIDATION_HANDLERS);
            ValidationHandler validationHandlers[] = new ValidationHandler[numHandlers];
            for (int j = 0; j < numHandlers; j++) {
                validationHandlers[j] = validationHandlerFactory.createForPreConsensus(j, numHandlers, true);
            }

            disruptor.handleEventsWith(validationHandlers);
        });

        publisher = publisherFactory.create(disruptor.getRingBuffer());
    }

    public PreConsensusPublisher getPublisher() {
        return publisher;
    }
}
