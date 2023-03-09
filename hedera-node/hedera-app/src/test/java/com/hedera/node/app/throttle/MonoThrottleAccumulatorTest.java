/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoThrottleAccumulatorTest {
    @Mock
    private FunctionalityThrottling hapiThrottling;

    private MonoThrottleAccumulator subject;

    @BeforeEach
    void setUp() {
        subject = new MonoThrottleAccumulator(hapiThrottling);
    }

    @Test
    void delegatesToMonoThrottlingForTransactions() {
        final ArgumentCaptor<TxnAccessor> captor = ArgumentCaptor.forClass(TxnAccessor.class);

        final var txnFunction = CryptoTransfer;
        given(hapiThrottling.shouldThrottleTxn(any())).willReturn(true);

        assertTrue(subject.shouldThrottle(TRANSACTION_BODY));

        verify(hapiThrottling).shouldThrottleTxn(captor.capture());
        final var throttledFunction = captor.getValue().getFunction();
        assertEquals(txnFunction, throttledFunction);
    }

    @Test
    void delegatesToMonoThrottlingForQueries() {
        final var mockQuery = Query.getDefaultInstance();
        final var queryFunction = HederaFunctionality.CryptoGetInfo;

        given(hapiThrottling.shouldThrottleQuery(queryFunction, mockQuery)).willReturn(true);

        assertTrue(subject.shouldThrottleQuery(queryFunction, mockQuery));
        verify(hapiThrottling).shouldThrottleQuery(queryFunction, mockQuery);
    }

    private static final AccountID ACCOUNT_ID =
            AccountID.newBuilder().setAccountNum(42L).build();
    private static final TransactionID TRANSACTION_ID =
            TransactionID.newBuilder().setAccountID(ACCOUNT_ID).build();
    private static final TransactionBody TRANSACTION_BODY = TransactionBody.newBuilder()
            .setTransactionID(TRANSACTION_ID)
            .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().build())
            .build();
}
