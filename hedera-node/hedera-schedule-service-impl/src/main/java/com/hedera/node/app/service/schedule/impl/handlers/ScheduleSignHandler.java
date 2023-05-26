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

package com.hedera.node.app.service.schedule.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PreHandleDispatcher;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_SIGN}.
 */
@Singleton
public class ScheduleSignHandler extends AbstractScheduleHandler implements TransactionHandler {

    @Inject
    public ScheduleSignHandler(@NonNull final PreHandleDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.scheduleSignOrThrow();
        final var id = op.scheduleIDOrElse(ScheduleID.DEFAULT);
        final var scheduleStore = context.createStore(ReadableScheduleStore.class);

        final var scheduleLookupResult = scheduleStore.get(id);
        if (scheduleLookupResult.isEmpty()) {
            throw new PreCheckException(INVALID_SCHEDULE_ID);
        }

        final var scheduledTxn = scheduleLookupResult.get().scheduledTxn();
        final var optionalPayer = scheduleLookupResult.get().designatedPayer();
        final var payerForNested = optionalPayer.orElse(
                scheduledTxn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT));

        preHandleScheduledTxn(context, scheduledTxn, payerForNested);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
