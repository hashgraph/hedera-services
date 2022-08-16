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
package com.hedera.services.utils.accessors;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessorFactoryTest {
    private static final AccountID payerId = IdUtils.asAccount("0.0.456");
    private static final ScheduleID scheduleId = IdUtils.asSchedule("0.0.333333");

    @Mock private GlobalDynamicProperties properties;

    AccessorFactory subject;

    TransactionBody someTxn =
            TransactionBody.newBuilder()
                    .setTransactionID(TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
                    .setMemo("Hi!")
                    .build();
    TransactionBody tokenWipeTxn =
            TransactionBody.newBuilder()
                    .setTransactionID(TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
                    .setTokenWipe(TokenWipeAccountTransactionBody.getDefaultInstance())
                    .setMemo("Hi!")
                    .build();

    @BeforeEach
    void setUp() {
        subject = new AccessorFactory(properties);
    }

    @Test
    void constructsCorrectly() throws InvalidProtocolBufferException {
        SwirldTransaction platformTxn =
                new SwirldTransaction(
                        Transaction.newBuilder()
                                .setBodyBytes(someTxn.toByteString())
                                .build()
                                .toByteArray());
        assertTrue(subject.nonTriggeredTxn(platformTxn.getContents()) instanceof SignedTxnAccessor);

        SwirldTransaction wipeTxn =
                new SwirldTransaction(
                        Transaction.newBuilder()
                                .setBodyBytes(tokenWipeTxn.toByteString())
                                .build()
                                .toByteArray());
        assertTrue(subject.nonTriggeredTxn(wipeTxn.getContents()) instanceof SignedTxnAccessor);
    }

    @Test
    void constructsTriggeredCorrectly() throws InvalidProtocolBufferException {
        SwirldTransaction platformTxn =
                new SwirldTransaction(
                        Transaction.newBuilder()
                                .setBodyBytes(someTxn.toByteString())
                                .build()
                                .toByteArray());
        assertTrue(subject.nonTriggeredTxn(platformTxn.getContents()) instanceof SignedTxnAccessor);

        final var grpcWipeTxn =
                Transaction.newBuilder().setBodyBytes(tokenWipeTxn.toByteString()).build();

        var triggered = subject.triggeredTxn(grpcWipeTxn, payerId, scheduleId, true, true);

        assertTrue(triggered instanceof SignedTxnAccessor);

        assertTrue(triggered.congestionExempt());
        assertTrue(triggered.throttleExempt());
        assertEquals(payerId, triggered.getPayer());
        assertEquals(scheduleId, triggered.getScheduleRef());
    }

    @Test
    void uncheckedSpecializedAccessorThrows() {
        final var invalidTxnBytes = "InvalidTxnBytes".getBytes();
        final var txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(ByteString.copyFrom(invalidTxnBytes))
                        .build();
        assertThrows(
                IllegalArgumentException.class, () -> subject.uncheckedSpecializedAccessor(txn));
    }
}
