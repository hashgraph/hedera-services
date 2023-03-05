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

package com.hedera.node.app.throttle;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Keeps track of the amount of usage of different throttle categories (by {@code id}), and returns
 * whether the throttle has been exceeded after applying the given incremental amount.
 */
public interface ThrottleAccumulator {

    /**
     * Test for capacity in the throttle bucket(s) associated with the given transaction, and
     * returns whether the throttle has been exceeded. (If there is no throttle associated with
     * the {@code transaction}, will always return true.)
     *
     * @param txn the transaction to consider throttling
     * @return true if the relevant throttle(s) have run out of capacity, false otherwise.
     * @throws NullPointerException if {@code txn} is {@code null}
     */
    boolean shouldThrottle(@NonNull TransactionBody txn);

    /**
     * Tests whether the given query should be throttled, assuming its functionality is as
     * specified.
     *
     * @param functionality the functionality of the query
     * @param query the query to test
     * @return true if the query should be throttled, false otherwise
     */
    boolean shouldThrottleQuery(@NonNull final HederaFunctionality functionality, @NonNull Query query);
}
