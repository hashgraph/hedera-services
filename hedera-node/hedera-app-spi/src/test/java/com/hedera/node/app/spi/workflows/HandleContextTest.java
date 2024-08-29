/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleContextTest {
    private static final TransactionBody MISSING_PAYER_ID =
            TransactionBody.newBuilder().transactionID(TransactionID.DEFAULT).build();

    private static final AccountID PAYER_ID =
            AccountID.newBuilder().accountNum(1_234L).build();
    private static final TransactionBody WITH_PAYER_ID = TransactionBody.newBuilder()
            .transactionID(TransactionID.newBuilder().accountID(PAYER_ID))
            .build();

    @Mock
    private Predicate<Key> signatureTest;

    @Mock
    private VerificationAssistant assistant;

    @Test
    void defaultDispatchPrecedingThrowsOnMissingTransactionId() {
        final var subject = mock(HandleContext.class);
        doCallRealMethod()
                .when(subject)
                .dispatchPrecedingTransaction(TransactionBody.DEFAULT, StreamBuilder.class, signatureTest);
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.dispatchPrecedingTransaction(
                        TransactionBody.DEFAULT, StreamBuilder.class, signatureTest));
    }

    @Test
    void defaultDispatchPrecedingUsesPayerIdFromBodyWhenSet() {
        final var subject = mock(HandleContext.class);
        doCallRealMethod()
                .when(subject)
                .dispatchPrecedingTransaction(WITH_PAYER_ID, StreamBuilder.class, signatureTest);
        subject.dispatchPrecedingTransaction(WITH_PAYER_ID, StreamBuilder.class, signatureTest);
        verify(subject).dispatchPrecedingTransaction(WITH_PAYER_ID, StreamBuilder.class, signatureTest, PAYER_ID);
    }

    @Test
    void defaultDispatchChildWithPredicateThrowsOnMissingTransactionId() {
        final var subject = mock(HandleContext.class);
        doCallRealMethod()
                .when(subject)
                .dispatchScheduledChildTransaction(MISSING_PAYER_ID, StreamBuilder.class, signatureTest);
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.dispatchScheduledChildTransaction(MISSING_PAYER_ID, StreamBuilder.class, signatureTest));
    }

    @Test
    void defaultDispatchChildWithPredicateUsesIdFromTransactionIfSet() {
        final var subject = mock(HandleContext.class);
        doCallRealMethod()
                .when(subject)
                .dispatchScheduledChildTransaction(WITH_PAYER_ID, StreamBuilder.class, signatureTest);
        subject.dispatchScheduledChildTransaction(WITH_PAYER_ID, StreamBuilder.class, signatureTest);
        verify(subject)
                .dispatchChildTransaction(
                        WITH_PAYER_ID,
                        StreamBuilder.class,
                        signatureTest,
                        PAYER_ID,
                        SCHEDULED,
                        HandleContext.ConsensusThrottling.ON);
    }
}
