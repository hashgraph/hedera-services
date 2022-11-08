/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.usage.token;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.times;

import com.hedera.services.hapi.fees.usage.token.TokenRevokeKycUsage;
import com.hedera.services.test.IdUtils;
import com.hedera.services.hapi.fees.usage.EstimatorFactory;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.hapi.utils.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRevokeKycUsageTest {
    private long now = 1_234_567L;
    private int numSigs = 3, sigSize = 100, numPayerKeys = 1;
    private SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
    private TokenID id = IdUtils.asToken("0.0.75231");

    private TokenRevokeKycTransactionBody op;
    private TransactionBody txn;

    private EstimatorFactory factory;
    private TxnUsageEstimator base;
    private TokenRevokeKycUsage subject;

    @BeforeEach
    void setUp() throws Exception {
        base = mock(TxnUsageEstimator.class);
        given(base.get()).willReturn(A_USAGES_MATRIX);

        factory = mock(EstimatorFactory.class);
        given(factory.get(any(), any(), any())).willReturn(base);
    }

    @Test
    void createsExpectedDelta() {
        givenOp();
        // and:
        subject = TokenRevokeKycUsage.newEstimate(txn, base);

        // when:
        var actual = subject.get();

        // then:
        assertEquals(A_USAGES_MATRIX, actual);
        // and:
        verify(base, times(2)).addBpt(FeeBuilder.BASIC_ENTITY_ID_SIZE);
    }

    @Test
    void assertSelf() {
        subject = TokenRevokeKycUsage.newEstimate(txn, base);
        assertEquals(subject, subject.self());
    }

    private void givenOp() {
        op = TokenRevokeKycTransactionBody.newBuilder().setToken(id).build();
        setTxn();
    }

    private void setTxn() {
        txn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(now)))
                        .setTokenRevokeKyc(op)
                        .build();
    }
}
