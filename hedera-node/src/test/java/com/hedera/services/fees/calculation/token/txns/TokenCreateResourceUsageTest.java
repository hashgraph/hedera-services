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
import com.hedera.services.usage.token.TokenCreateUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenCreateResourceUsageTest {
    long now = 1_000_000L;
    TransactionBody nonTokenCreateTxn;
    TransactionBody tokenCreateTxn;
    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    AccountID treasury = IdUtils.asAccount("1.2.3");
    TransactionID txnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now))
                    .build();

    FeeData expected;

    StateView view;
    TokenCreateUsage usage;

    TokenCreateResourceUsage subject;
    TxnUsageEstimator txnUsageEstimator;

    @BeforeEach
    void setup() throws Throwable {
        expected = mock(FeeData.class);
        view = mock(StateView.class);

        tokenCreateTxn = mock(TransactionBody.class);
        given(tokenCreateTxn.hasTokenCreation()).willReturn(true);
        var tokenCreation = TokenCreateTransactionBody.newBuilder().setTreasury(treasury).build();
        given(tokenCreateTxn.getTokenCreation()).willReturn(tokenCreation);
        given(tokenCreateTxn.getTransactionID()).willReturn(txnId);

        nonTokenCreateTxn = mock(TransactionBody.class);
        given(nonTokenCreateTxn.hasTokenCreation()).willReturn(false);

        usage = mock(TokenCreateUsage.class);
        given(usage.get()).willReturn(expected);

        txnUsageEstimator = mock(TxnUsageEstimator.class);
        EstimatorFactory estimatorFactory = mock(EstimatorFactory.class);
        given(estimatorFactory.get(sigUsage, tokenCreateTxn, ESTIMATOR_UTILS))
                .willReturn(txnUsageEstimator);
        subject = new TokenCreateResourceUsage(estimatorFactory);
    }

    @Test
    void recognizesApplicability() {
        // expect:
        assertTrue(subject.applicableTo(tokenCreateTxn));
        assertFalse(subject.applicableTo(nonTokenCreateTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        final var mockStatic = mockStatic(TokenCreateUsage.class);
        mockStatic
                .when(() -> TokenCreateUsage.newEstimate(tokenCreateTxn, txnUsageEstimator))
                .thenReturn(usage);
        // when:
        var actual = subject.usageGiven(tokenCreateTxn, obj, view);

        // expect:
        assertSame(expected, actual);

        mockStatic.close();
    }
}
