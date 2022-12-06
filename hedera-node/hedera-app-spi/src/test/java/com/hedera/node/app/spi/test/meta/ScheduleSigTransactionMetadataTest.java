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

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.ScheduleSigTransactionMetadata;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScheduleSigTransactionMetadataTest {
    @Mock private AccountKeyLookup keyLookup;
    @Mock private HederaKey payerKey;
    private AccountID payer = AccountID.newBuilder().setAccountNum(3L).build();
    private AccountID schedulePayer = AccountID.newBuilder().setAccountNum(4L).build();
    private ScheduleSigTransactionMetadata subject;

    @Test
    void getsInnerMetadata(){
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(schedulePayer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        subject = new ScheduleSigTransactionMetadata(keyLookup, createScheduleTransaction(), payer);
        final var innerMeta = new SigTransactionMetadata(keyLookup, any(), schedulePayer);
        subject.setScheduledMeta(innerMeta);

        assertEquals(innerMeta, subject.getScheduledMeta());
    }

     private TransactionBody createScheduleTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer);
        final var createTxnBody =
                ScheduleCreateTransactionBody.newBuilder()
                        .setScheduledTransactionBody(
                                SchedulableTransactionBody.newBuilder()
                                        .setMemo("test")
                                        .setTransactionFee(1_000_000L)
                        )
                        .setPayerAccountID(schedulePayer)
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setScheduleCreate(createTxnBody)
                .build();
    }
}
