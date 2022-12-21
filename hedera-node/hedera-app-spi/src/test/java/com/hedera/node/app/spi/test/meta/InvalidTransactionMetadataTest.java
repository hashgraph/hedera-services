/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.test.meta;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.spi.meta.InvalidTransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidTransactionMetadataTest {
    private InvalidTransactionMetadata subject;
    private AccountID payer = AccountID.newBuilder().setAccountNum(3L).build();

    @Test
    void gettersWork() {
        final var txn = createScheduleTransaction();
        subject = new InvalidTransactionMetadata(txn, payer, INVALID_PAYER_ACCOUNT_ID);
        assertEquals(INVALID_PAYER_ACCOUNT_ID, subject.status());
        assertEquals(payer, subject.payer());
        assertNull(subject.payerKey());
        assertEquals(List.of(), subject.requiredNonPayerKeys());
        assertNull(subject.scheduledMeta());
    }

    @Test
    void nullValuesThrow() {
        final var txn = createScheduleTransaction();
        assertThrows(
                NullPointerException.class,
                () -> new InvalidTransactionMetadata(null, payer, INVALID_PAYER_ACCOUNT_ID));
        assertThrows(
                NullPointerException.class,
                () -> new InvalidTransactionMetadata(txn, null, INVALID_PAYER_ACCOUNT_ID));
        assertThrows(
                NullPointerException.class, () -> new InvalidTransactionMetadata(txn, payer, null));
    }

    private TransactionBody createScheduleTransaction() {
        final var transactionID = TransactionID.newBuilder().setAccountID(payer);
        final var createTxnBody =
                ScheduleCreateTransactionBody.newBuilder()
                        .setScheduledTransactionBody(
                                SchedulableTransactionBody.newBuilder()
                                        .setMemo("test")
                                        .setTransactionFee(1_000_000L))
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setScheduleCreate(createTxnBody)
                .build();
    }
}
