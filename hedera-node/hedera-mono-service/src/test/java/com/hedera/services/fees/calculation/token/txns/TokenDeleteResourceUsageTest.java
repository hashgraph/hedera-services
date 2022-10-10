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
package com.hedera.services.fees.calculation.token.txns;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.token.TokenDeleteUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenDeleteResourceUsageTest {
    private TransactionBody nonTokenDeleteTxn;
    private TransactionBody tokenDeleteTxn;

    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    FeeData expected;

    StateView view;
    TokenDeleteUsage usage;

    TokenDeleteResourceUsage subject;
    TxnUsageEstimator txnUsageEstimator;

    @BeforeEach
    void setup() throws Throwable {
        expected = mock(FeeData.class);
        view = mock(StateView.class);

        tokenDeleteTxn = mock(TransactionBody.class);
        given(tokenDeleteTxn.hasTokenDeletion()).willReturn(true);

        nonTokenDeleteTxn = mock(TransactionBody.class);
        given(nonTokenDeleteTxn.hasTokenDeletion()).willReturn(false);

        usage = mock(TokenDeleteUsage.class);
        given(usage.get()).willReturn(expected);

        txnUsageEstimator = mock(TxnUsageEstimator.class);
        EstimatorFactory estimatorFactory = mock(EstimatorFactory.class);
        given(estimatorFactory.get(sigUsage, tokenDeleteTxn, ESTIMATOR_UTILS))
                .willReturn(txnUsageEstimator);
        subject = new TokenDeleteResourceUsage(estimatorFactory);
    }

    @Test
    void recognizesApplicability() {
        // expect:
        assertTrue(subject.applicableTo(tokenDeleteTxn));
        assertFalse(subject.applicableTo(nonTokenDeleteTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        final var mockStatic = mockStatic(TokenDeleteUsage.class);
        mockStatic
                .when(() -> TokenDeleteUsage.newEstimate(tokenDeleteTxn, txnUsageEstimator))
                .thenReturn(usage);

        // expect:
        assertEquals(expected, subject.usageGiven(tokenDeleteTxn, obj, view));

        mockStatic.close();
    }
}
