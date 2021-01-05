package com.hedera.services.sigs.order;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionID;

public interface ScheduledTransactionOrderResultFactory<T> {
    /**
     * Wrap the (successful) determination of a signing order in a {@link ScheduledTransactionOrderResult}.
     *
     * @param transactionBody a known scheduled tx transaction body.
     * @return the wrapper object.
     */
    ScheduledTransactionOrderResult<T> forValidOrder(byte[] transactionBody);

    /**
     * Report a missing schedule encountered when listing signing keys for some txn.
     *
     * @param id the missing schedule.
     * @param txnId the {@link TransactionID} of the problematic txn.
     * @return the error summary.
     */
    ScheduledTransactionOrderResult<T> forMissingSchedule(ScheduleID id, TransactionID txnId);
}
