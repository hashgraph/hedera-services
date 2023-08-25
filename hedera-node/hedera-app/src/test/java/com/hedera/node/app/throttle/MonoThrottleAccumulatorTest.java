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

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.ThrottleBucket;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.ThrottleGroup;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
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

    @Mock
    private ThrottleManager throttleManager;

    ThrottleGroup throttleGroup = ThrottleGroup.newBuilder()
            .operations(List.of(HederaFunctionality.CRYPTO_CREATE, HederaFunctionality.FREEZE))
            .milliOpsPerSec(100)
            .build();

    ThrottleBucket throttleBucket = ThrottleBucket.newBuilder()
            .name("throttle1")
            .burstPeriodMs(100L)
            .throttleGroups(throttleGroup)
            .build();

    ThrottleDefinitions throttleDefinitions = com.hedera.hapi.node.transaction.ThrottleDefinitions.newBuilder()
            .throttleBuckets(throttleBucket)
            .build();
    Bytes throttleDefinitionsByes =
            com.hedera.hapi.node.transaction.ThrottleDefinitions.PROTOBUF.toBytes(throttleDefinitions);

    @BeforeEach
    void setUp() throws InvalidProtocolBufferException {
        com.hederahashgraph.api.proto.java.ThrottleDefinitions throttleDefinitionsProto =
                com.hederahashgraph.api.proto.java.ThrottleDefinitions.parseFrom(throttleDefinitionsByes.toByteArray());
        when(throttleManager.throttleDefinitionsProto()).thenReturn(throttleDefinitionsProto);

        subject = new MonoThrottleAccumulator(hapiThrottling, throttleManager);
    }

    @Test
    void verifyTheConstructorIsConfiguringTheThrottleDefinitions() {
        verify(throttleManager, times(1)).throttleDefinitionsProto();
        verify(hapiThrottling, times(1)).rebuildFor(any());
    }

    @Test
    void delegatesToMonoThrottlingForTransactions() {
        final ArgumentCaptor<TxnAccessor> captor = ArgumentCaptor.forClass(TxnAccessor.class);

        given(hapiThrottling.shouldThrottleTxn(any())).willReturn(true);

        assertTrue(subject.shouldThrottle(TRANSACTION_BODY));

        verify(hapiThrottling).shouldThrottleTxn(captor.capture());
        final var throttledFunction = captor.getValue().getFunction();
        assertEquals(fromPbj(CRYPTO_TRANSFER), throttledFunction);
    }

    @Test
    void delegatesToMonoThrottlingForQueries() {
        final var mockQuery = Query.newBuilder().build();
        final var queryFunction = HederaFunctionality.CRYPTO_GET_INFO;

        given(hapiThrottling.shouldThrottleQuery(fromPbj(queryFunction), fromPbj(mockQuery)))
                .willReturn(true);

        assertTrue(subject.shouldThrottleQuery(queryFunction, mockQuery));
        verify(hapiThrottling).shouldThrottleQuery(fromPbj(queryFunction), fromPbj(mockQuery));
    }

    private static final AccountID ACCOUNT_ID =
            AccountID.newBuilder().accountNum(42L).build();
    private static final TransactionID TRANSACTION_ID =
            TransactionID.newBuilder().accountID(ACCOUNT_ID).build();
    private static final TransactionBody TRANSACTION_BODY = TransactionBody.newBuilder()
            .transactionID(TRANSACTION_ID)
            .cryptoTransfer(CryptoTransferTransactionBody.newBuilder().build())
            .build();
}
