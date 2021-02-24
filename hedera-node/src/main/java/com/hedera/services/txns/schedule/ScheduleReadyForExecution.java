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
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.TriggeredTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;

import static com.hedera.services.utils.MiscUtils.asTimestamp;

public abstract class ScheduleReadyForExecution {
    protected final ScheduleStore store;
    protected final TransactionContext txnCtx;

    ScheduleReadyForExecution(ScheduleStore store, TransactionContext context) {
        this.store = store;
        this.txnCtx = context;
    }

    private Transaction prepareTransaction(MerkleSchedule schedule) throws InvalidProtocolBufferException {
        var transactionBody = TransactionBody.parseFrom(schedule.transactionBody());
        var transactionId = TransactionID.newBuilder()
                .setAccountID(schedule.schedulingAccount().toGrpcAccountId())
                .setTransactionValidStart(asTimestamp(schedule.schedulingTXValidStart().toJava()))
                .setNonce(transactionBody.getTransactionID().getNonce())
                .setScheduled(true)
                .build();

        return Transaction.newBuilder()
                .setSignedTransactionBytes(
                        SignedTransaction.newBuilder()
                                .setBodyBytes(
                                        TransactionBody.newBuilder()
                                                .mergeFrom(transactionBody)
                                                .setTransactionID(transactionId)
                                                .build().toByteString())
                                .build().toByteString())
                .build();
    }

    ResponseCodeEnum processExecution(ScheduleID id) throws InvalidProtocolBufferException {
        var schedule = store.get(id);
        var transaction = prepareTransaction(schedule);

        txnCtx.trigger(
                new TriggeredTxnAccessor(
                        transaction.toByteArray(),
                        schedule.payer().toGrpcAccountId(),
                        id));

        return store.markAsExecuted(id);
    }

    @FunctionalInterface
    interface ExecutionProcessor {
        ResponseCodeEnum doProcess(ScheduleID id) throws InvalidProtocolBufferException;
    }
}
