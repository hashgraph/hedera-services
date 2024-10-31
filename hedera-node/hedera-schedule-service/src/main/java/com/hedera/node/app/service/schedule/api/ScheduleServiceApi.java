/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.api;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.key.KeyVerifier;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

public interface ScheduleServiceApi {

    /**
     * An executable transaction with the verifier to use for child signature verifications. If set,
     * "not before" (nbf) time is the earliest consensus time at which the transaction could be executed.
     */
    record ExecutableTxn(TransactionBody body, @Nullable Instant nbf) {}

    /**
     * Given a [start, end) interval and a supplier of a StoreFactory that can be used in the returned
     * iterator's remove() implementation to get a StoreFactory to purge a successfully executed txn,
     * returns an iterator over all ExecutableTxn this service wants to execute in the interval.
     */
    Iterator<ExecutableTxn> iterTxnsForInterval(
            Instant start, Instant end, Function<TransactionBody, Set<Key>> requiredKeysFn, KeyVerifier keyVerifier);
}
