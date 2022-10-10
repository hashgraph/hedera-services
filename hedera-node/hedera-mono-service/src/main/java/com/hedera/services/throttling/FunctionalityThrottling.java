/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import java.util.List;

public interface FunctionalityThrottling {
    /**
     * Verifies if this throttle has enough capacity to accept the given transaction.
     *
     * @param accessor the transaction accessor
     * @return true if the transaction should be throttled, false otherwise
     */
    boolean shouldThrottleTxn(TxnAccessor accessor);

    boolean shouldThrottleQuery(HederaFunctionality queryFunction, Query query);

    /**
     * Leaks the given amount previously reserved in this throttle's "gas bucket".
     *
     * @param value the amount of gas to release
     */
    void leakUnusedGasPreviouslyReserved(TxnAccessor accessor, long value);

    void rebuildFor(ThrottleDefinitions defs);

    void applyGasConfig();

    List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function);

    List<DeterministicThrottle> allActiveThrottles();

    GasLimitDeterministicThrottle gasLimitThrottle();

    void resetUsage();

    /**
     * Indicates if the last transaction passed to {@link
     * FunctionalityThrottling#shouldThrottleTxn(TxnAccessor)} was throttled because of insufficient
     * capacity in the gas limit throttle.
     *
     * @return whether the last transaction was throttled by the gas limit
     * @throws UnsupportedOperationException if this throttle cannot provide a meaningful answer
     */
    boolean wasLastTxnGasThrottled();
}
