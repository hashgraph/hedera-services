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

package com.hedera.node.app.service.schedule.impl.test;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.test.utils.IdUtils;

public class ScheduledTxnFactory {
    private static final long FEE = 123L;
    private static final String SCHEDULED_TXN_MEMO = "Wait for me!";
    public static final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
            .transactionFee(FEE).memo(SCHEDULED_TXN_MEMO).cryptoDelete(CryptoDeleteTransactionBody.newBuilder()
                    .deleteAccountID(PbjConverter.toPbj(IdUtils.asAccount("0.0.2")))
                    .transferAccountID(PbjConverter.toPbj(IdUtils.asAccount("0.0.75231")))).build();

    private ScheduledTxnFactory() {}

    public static TransactionBody scheduleCreateTxnWith(
            final Key scheduleAdminKey,
            final String scheduleMemo,
            final AccountID payer,
            final AccountID scheduler,
            final Timestamp validStart) {
        return scheduleCreateTxnWith(scheduleAdminKey, scheduleMemo, payer, scheduler, validStart, null, null);
    }

    public static TransactionBody scheduleCreateTxnWith(
            final Key scheduleAdminKey,
            final String scheduleMemo,
            final AccountID payer,
            final AccountID scheduler,
            final Timestamp validStart,
            final Timestamp expirationTime,
            final Boolean waitForExpiry) {
        final var creation = ScheduleCreateTransactionBody.newBuilder().memo(scheduleMemo)
                .scheduledTransactionBody(scheduledTxn);
        if (scheduleAdminKey != null) {
            creation.adminKey(scheduleAdminKey);
        }
        if (payer != null) {
            creation.payerAccountID(payer);
        }
        if (expirationTime != null) {
            creation.expirationTime(expirationTime);
        }
        if (waitForExpiry != null) {
            creation.waitForExpiry(waitForExpiry);
        }
        return TransactionBody.newBuilder().transactionID(
                        TransactionID.newBuilder().transactionValidStart(validStart).accountID(scheduler).build())
                .scheduleCreate(creation).build();
    }
}
