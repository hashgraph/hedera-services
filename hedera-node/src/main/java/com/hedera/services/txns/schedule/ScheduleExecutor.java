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
package com.hedera.services.txns.schedule;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

/** Class used to generate the TriggeredTxnAccessor for a scheduled transaction. */
@Singleton
public final class ScheduleExecutor {
    private AccessorFactory factory;

    @Inject
    public ScheduleExecutor(AccessorFactory factory) {
        this.factory = factory;
    }

    /**
     * Called to trigger the transaction after it's signatures have been checked, inside another
     * transaction.
     */
    ResponseCodeEnum processImmediateExecution(
            @Nonnull ScheduleID id,
            @Nonnull ScheduleStore store,
            @Nonnull TransactionContext txnCtx)
            throws InvalidProtocolBufferException {
        Objects.requireNonNull(txnCtx, "The active transaction context cannot be null");

        final var triggerResult = getTriggeredTxnAccessor(id, store, true);

        if (triggerResult.getLeft() == OK) {
            txnCtx.trigger(triggerResult.getRight());
        }
        return triggerResult.getLeft();
    }

    /**
     * Called to get the TriggeredTxnAccessor that can be used to execute a scheduled transaction.
     * Signatures must be checked before using it.
     */
    Pair<ResponseCodeEnum, TxnAccessor> getTriggeredTxnAccessor(
            @Nonnull final ScheduleID id,
            @Nonnull final ScheduleStore store,
            final boolean isImmediate)
            throws InvalidProtocolBufferException {

        Objects.requireNonNull(id, "The id of the scheduled transaction cannot be null");
        Objects.requireNonNull(store, "The schedule entity store cannot be null");

        final var executionStatus = store.preMarkAsExecuted(id);
        if (executionStatus != OK) {
            return Pair.of(executionStatus, null);
        }

        final var schedule = store.get(id);
        return Pair.of(OK, getTxnAccessor(id, schedule, !isImmediate));
    }

    TxnAccessor getTxnAccessor(
            final ScheduleID id,
            @Nonnull final ScheduleVirtualValue schedule,
            final boolean throttleAndCongestionExempt)
            throws InvalidProtocolBufferException {

        final var transaction = schedule.asSignedTxn();
        return factory.triggeredTxn(
                transaction,
                schedule.effectivePayer().toGrpcAccountId(),
                id,
                throttleAndCongestionExempt,
                throttleAndCongestionExempt);
    }
}
