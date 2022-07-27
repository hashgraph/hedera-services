/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.throttling;

import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import java.time.Instant;

public interface TimedFunctionalityThrottling extends FunctionalityThrottling {
    /**
     * Verifies if the frontend throttle has enough capacity to handle the transaction
     *
     * @param accessor - the transaction accessor
     * @return true if the transaction should be throttled, false if the system can handle the TX
     *     execution
     */
    @Override
    default boolean shouldThrottleTxn(TxnAccessor accessor) {
        return shouldThrottleTxn(accessor, Instant.now());
    }

    @Override
    default boolean shouldThrottleQuery(HederaFunctionality queryFunction, Query query) {
        return shouldThrottleQuery(queryFunction, Instant.now(), query);
    }

    /**
     * Verifies if the frontend throttle has enough capacity to handle the transaction
     *
     * @param accessor - the transaction accessor
     * @param now - the instant for which throttlign should be calculated
     * @return true if the transaction should be throttled, false if the system can handle the TX
     *     execution
     */
    boolean shouldThrottleTxn(TxnAccessor accessor, Instant now);

    boolean shouldThrottleQuery(HederaFunctionality queryFunction, Instant now, Query query);
}
