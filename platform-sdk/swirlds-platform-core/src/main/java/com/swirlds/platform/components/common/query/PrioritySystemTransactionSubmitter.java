/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.common.query;

import com.swirlds.common.system.transaction.internal.SystemTransaction;

/**
 * An object or method that submits a priority system transaction to the transaction pool for inclusion in an event.
 * Priority system transactions are included in events before other transactions. Generally, priority transactions will
 * be included in the very next event created. The only exception is if there is not enough room in the next event for
 * all priority transactions waiting.
 */
@FunctionalInterface
public interface PrioritySystemTransactionSubmitter {

    /**
     * Submits a priority system transaction for inclusion in an event. Submission is guaranteed to succeed.
     *
     * @param systemTransaction
     * 		the system transaction to submit
     * @return {@code true} if the transaction was successfully submitted, {@code false} otherwise
     */
    boolean submit(SystemTransaction systemTransaction);
}
