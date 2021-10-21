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

import com.hedera.services.ServicesState;
import com.hedera.services.context.properties.NodeLocalProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Processing dispatch layer for transactions submitted during the prepare stage (aka expand
 * signatures) that uses an LMAX disruptor to coordinate the execution sequence of the various
 * steps that belong to this part of the transaction lifecycle. The prepare steps currently are:
 * <ol>
 * <li>Pre-fetch (parallel) - fetching of data that can be used during the serial execution
 * portion of the transaction (for example, loading of EVM contract bytecode).</li>
 * </ol>
 * The choice of wait strategy is outlined in {@code AbstractProcessor}. Please refer to that
 * class for a more detailed explanation.
 */
@Singleton
public class PrepareStageProcessor extends AbstractProcessor {
    private static final Logger logger = LogManager.getLogger(ServicesState.class);

    public static final int DEFAULT_PREFETCH_HANDLERS = 2;

    PrepareStagePublisher publisher;

    @Inject
    public PrepareStageProcessor(
            NodeLocalProperties properties,
            PreFetchHandler.Factory preFetchHandlerFactory
    ) {
        super(properties.prepareRingBufferPower(), "prepare-handler", disruptor -> {
            int numHandlers = Math.max(properties.preparePreFetchHandlerCount(), DEFAULT_PREFETCH_HANDLERS);
            PreFetchHandler[] preFetchHandlers = new PreFetchHandler[numHandlers];
            for (int j = 0; j < numHandlers; j++) {
                preFetchHandlers[j] = preFetchHandlerFactory.create(j, numHandlers, true);
            }

            disruptor.handleEventsWith(preFetchHandlers);
        });

        publisher = new PrepareStagePublisher(disruptor.getRingBuffer());
    }

    public PrepareStagePublisher getPublisher() {
        return publisher;
    }
}
