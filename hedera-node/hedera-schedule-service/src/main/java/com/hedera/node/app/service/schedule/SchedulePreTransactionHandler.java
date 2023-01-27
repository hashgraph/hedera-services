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
package com.hedera.node.app.service.schedule;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.ScheduleTransactionMetadata;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/schedule_service.proto">Schedule
 * Service</a>.
 */
public interface SchedulePreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link HederaFunctionality#SCHEDULE_CREATE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ScheduleCreateTransactionBody}
     * @return the metadata for the schedule creation
     */
    ScheduleTransactionMetadata preHandleCreateSchedule(
            TransactionBody txn, AccountID payer, PreHandleDispatcher dispatcher);

    /**
     * Pre-handles a {@link HederaFunctionality#SCHEDULE_SIGN} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ScheduleSignTransactionBody}
     * @return the metadata for the schedule signing
     */
    ScheduleTransactionMetadata preHandleSignSchedule(
            TransactionBody txn, AccountID payer, PreHandleDispatcher dispatcher);

    /**
     * Pre-handles a {@link HederaFunctionality#SCHEDULE_DELETE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ScheduleDeleteTransactionBody}
     * @return the metadata for the schedule deletion
     */
    ScheduleTransactionMetadata preHandleDeleteSchedule(
            TransactionBody txn, AccountID payer, PreHandleDispatcher dispatcher);
}
