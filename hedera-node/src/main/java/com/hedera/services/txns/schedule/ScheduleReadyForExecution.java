package com.hedera.services.txns.schedule;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.TriggeredTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;

public abstract class ScheduleReadyForExecution {
    protected final ScheduleStore store;
    protected final TransactionContext txnCtx;

    ScheduleReadyForExecution(ScheduleStore store, TransactionContext context) {
        this.store = store;
        this.txnCtx = context;
    }

    ResponseCodeEnum processExecution(ScheduleID id) throws InvalidProtocolBufferException {
        var schedule = store.get(id);
        var transaction = schedule.asScheduledTransaction();

        txnCtx.trigger(
                new TriggeredTxnAccessor(
                        transaction.toByteArray(),
                        schedule.effectivePayer().toGrpcAccountId(),
                        id));

        return store.markAsExecuted(id);
    }

    @FunctionalInterface
    interface ExecutionProcessor {
        ResponseCodeEnum doProcess(ScheduleID id) throws InvalidProtocolBufferException;
    }
}
