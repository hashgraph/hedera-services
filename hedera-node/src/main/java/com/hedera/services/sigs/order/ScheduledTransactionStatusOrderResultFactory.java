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

import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionID;

public class ScheduledTransactionStatusOrderResultFactory implements ScheduledTransactionOrderResultFactory<SignatureStatus> {
    private final boolean inHandleTxnDynamicContext;

    public ScheduledTransactionStatusOrderResultFactory(boolean inHandleTxnDynamicContext) {
        this.inHandleTxnDynamicContext = inHandleTxnDynamicContext;
    }

    @Override
    public ScheduledTransactionOrderResult<SignatureStatus> forValidOrder(byte[] transactionBody) {
        return new ScheduledTransactionOrderResult<>(transactionBody);
    }

    @Override
    public ScheduledTransactionOrderResult<SignatureStatus> forMissingSchedule(ScheduleID missing, TransactionID txnId) {
        SignatureStatus error = new SignatureStatus(
                SignatureStatusCode.INVALID_SCHEDULE_ID, ResponseCodeEnum.INVALID_SCHEDULE_ID,
                inHandleTxnDynamicContext, txnId, missing);
        return new ScheduledTransactionOrderResult<>(error);
    }
}
