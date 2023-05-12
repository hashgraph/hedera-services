/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.eventflow;

import com.swirlds.common.test.TransactionUtils;
import com.swirlds.platform.test.event.emitter.EventEmitterFactory;
import com.swirlds.platform.test.event.emitter.StandardEventEmitter;
import com.swirlds.platform.test.event.source.EventSource;
import com.swirlds.platform.test.event.source.StandardEventSource;
import java.time.Instant;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Utility functions for {@link EventFlowTests}
 */
public class EventFlowTestUtils {

    /** The average number of transactions per event. */
    private static final double TX_PER_EVENT_AVG = 3;

    /** The standard deviation for the number of transactions per event. */
    private static final double TX_PER_EVENT_STD_DEV = 3;

    /**
     * Creates a supplier of a consensus time estimator that does a bad job.
     *
     * @param random
     * 		source of randomness
     * @return a supplier of estimated consensus time
     */
    public static Supplier<Instant> inaccurateConsensusTimeEstimater(final Random random) {
        return () -> Instant.now().minusMillis(random.nextInt(5000));
    }

    public static StandardEventEmitter createEventEmitter(final Random random, final int numNodes) {
        return createEventEmitter(random, numNodes, 0.0);
    }

    /**
     * Creates a {@link StandardEventEmitter} with custom event sources that generate transactions with incrementing
     * long values such that no transaction is the same as another across all sources.
     *
     * @param random
     * 		the random instance to use for the {@link EventEmitterFactory}
     * @return the event generator
     */
    public static StandardEventEmitter createEventEmitter(
            final Random random, final int numNodes, final double systemTransactionRatio) {
        // Create standard event sources that generate events with incrementing transactions instead of random
        // transactions
        final Supplier<EventSource<?>> eventSourceSupplier = () -> new StandardEventSource(
                false,
                (r) -> TransactionUtils.incrementingMixedTransactions(
                        r, TX_PER_EVENT_AVG, TX_PER_EVENT_STD_DEV, systemTransactionRatio));

        final EventEmitterFactory eventGeneratorFactory = new EventEmitterFactory(random, numNodes);
        eventGeneratorFactory.getSourceFactory().addCustomSource((nodeIndex) -> true, eventSourceSupplier);
        return eventGeneratorFactory.newStandardEmitter();
    }
}
