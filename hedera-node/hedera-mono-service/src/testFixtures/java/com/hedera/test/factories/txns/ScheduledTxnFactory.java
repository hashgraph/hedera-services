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

package com.hedera.test.factories.txns;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.*;

public class ScheduledTxnFactory {
    private static final long FEE = 123L;
    private static final String SCHEDULED_TXN_MEMO = "Wait for me!";
    public static final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
            .setTransactionFee(FEE)
            .setMemo(SCHEDULED_TXN_MEMO)
            .setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
                    .setDeleteAccountID(IdUtils.asAccount("0.0.2"))
                    .setTransferAccountID(IdUtils.asAccount("0.0.75231")))
            .build();

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
        final var creation = ScheduleCreateTransactionBody.newBuilder()
                .setMemo(scheduleMemo)
                .setScheduledTransactionBody(scheduledTxn);
        if (scheduleAdminKey != null) {
            creation.setAdminKey(scheduleAdminKey);
        }
        if (payer != null) {
            creation.setPayerAccountID(payer);
        }
        if (expirationTime != null) {
            creation.setExpirationTime(expirationTime);
        }
        if (waitForExpiry != null) {
            creation.setWaitForExpiry(waitForExpiry);
        }
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(validStart)
                        .setAccountID(scheduler)
                        .build())
                .setScheduleCreate(creation)
                .build();
    }
}
