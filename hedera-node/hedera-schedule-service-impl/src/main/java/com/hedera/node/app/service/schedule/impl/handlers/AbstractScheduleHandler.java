/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.utils.MiscUtils.functionOf;
import static com.hedera.node.app.service.mono.utils.MiscUtils.isSchedulable;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

import com.hedera.node.app.service.mono.exceptions.UnknownHederaFunctionality;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PreHandleDispatcher;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Provides some implementation support needed for both the {@link ScheduleCreateHandler} and {@link
 * ScheduleSignHandler}.
 */
abstract class AbstractScheduleHandler {
    protected void preHandleScheduledTxn(
            final PreHandleContext context,
            final TransactionBody scheduledTxn,
            final AccountID payerForNested,
            final PreHandleDispatcher dispatcher) {
        final var innerContext = context.createNestedContext(scheduledTxn, payerForNested);
        context.setInnerContext(innerContext);
        final HederaFunctionality scheduledFunction;
        try {
            scheduledFunction = functionOf(scheduledTxn);
        } catch (UnknownHederaFunctionality ex) {
            innerContext.status(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
            return;
        }

        if (!isSchedulable(scheduledFunction)) {
            innerContext.status(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
            return;
        }

        dispatcher.dispatch(innerContext);
        if (innerContext.failed()) {
            innerContext.status(UNRESOLVABLE_REQUIRED_SIGNERS);
        }
    }
}
