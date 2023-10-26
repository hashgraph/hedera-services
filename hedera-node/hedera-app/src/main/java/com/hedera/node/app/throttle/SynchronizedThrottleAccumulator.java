/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;

/**
 * Keeps track of the amount of usage of different TPS throttle categories and gas, and returns whether a given
 * transaction or query should be throttled based on that.
 * Meant to be used in multithreaded context
 */
public class SynchronizedThrottleAccumulator {

    private final ThrottleAccumulator frontendThrottle;

    @Inject
    public SynchronizedThrottleAccumulator(ThrottleAccumulator frontendThrottle) {
        this.frontendThrottle = requireNonNull(frontendThrottle, "frontendThrottle must not be null");
    }

    /*
     * Updates the throttle requirements for the given transaction and returns whether the transaction
     * should be throttled for the current time(Instant.now).
     *
     * @param txnInfo the transaction to update the throttle requirements for
     * @param state the current state of the node
     * @return whether the transaction should be throttled
     */
    public synchronized boolean shouldThrottle(@NonNull TransactionInfo txnInfo, HederaState state) {
        return frontendThrottle.shouldThrottle(txnInfo, Instant.now(), state);
    }

    /*
     * Updates the throttle requirements for the given query and returns whether the query should be throttled for the
     * current time(Instant.now).
     *
     * @param queryFunction the functionality of the query
     * @param query the query to update the throttle requirements for
     * @return whether the query should be throttled
     */
    public synchronized boolean shouldThrottle(HederaFunctionality queryFunction, Query query) {
        return frontendThrottle.shouldThrottle(queryFunction, Instant.now(), query);
    }
}
