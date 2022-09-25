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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.token.TokenUpdateUsage;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.fee.SigValueObj;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUpdateResourceUsageTest {
    private TokenUpdateResourceUsage subject;

    private TransactionBody nonTokenUpdateTxn;
    private TransactionBody tokenUpdateTxn;

    StateView view;
    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    FeeData expected;

    TokenUpdateUsage usage;

    long expiry = 1_234_567L;
    String symbol = "HEYMAOK";
    String name = "IsItReallyOk";
    String memo = "We just fake it all the time.";
    TokenID target = IdUtils.asToken("0.0.123");
    TokenInfo info =
            TokenInfo.newBuilder()
                    .setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey())
                    .setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey())
                    .setWipeKey(TxnHandlingScenario.TOKEN_WIPE_KT.asKey())
                    .setSupplyKey(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey())
                    .setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asKey())
                    .setFeeScheduleKey(TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT.asKey())
                    .setPauseKey(TxnHandlingScenario.TOKEN_PAUSE_KT.asKey())
                    .setSymbol(symbol)
                    .setName(name)
                    .setMemo(memo)
                    .setExpiry(Timestamp.newBuilder().setSeconds(expiry))
                    .build();

    TxnUsageEstimator txnUsageEstimator;

    @BeforeEach
    void setup() throws Throwable {
        expected = mock(FeeData.class);
        view = mock(StateView.class);

        tokenUpdateTxn = mock(TransactionBody.class);
        given(tokenUpdateTxn.hasTokenUpdate()).willReturn(true);
        given(tokenUpdateTxn.getTokenUpdate())
                .willReturn(TokenUpdateTransactionBody.newBuilder().setToken(target).build());

        nonTokenUpdateTxn = mock(TransactionBody.class);
        given(nonTokenUpdateTxn.hasTokenUpdate()).willReturn(false);

        usage = mock(TokenUpdateUsage.class);
        given(usage.givenCurrentAdminKey(Optional.of(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey())))
                .willReturn(usage);
        given(usage.givenCurrentWipeKey(Optional.of(TxnHandlingScenario.TOKEN_WIPE_KT.asKey())))
                .willReturn(usage);
        given(usage.givenCurrentKycKey(Optional.of(TxnHandlingScenario.TOKEN_KYC_KT.asKey())))
                .willReturn(usage);
        given(usage.givenCurrentSupplyKey(Optional.of(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey())))
                .willReturn(usage);
        given(usage.givenCurrentFreezeKey(Optional.of(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey())))
                .willReturn(usage);
        given(
                        usage.givenCurrentFeeScheduleKey(
                                Optional.of(TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT.asKey())))
                .willReturn(usage);
        given(usage.givenCurrentPauseKey(Optional.of(TxnHandlingScenario.TOKEN_PAUSE_KT.asKey())))
                .willReturn(usage);
        given(usage.givenCurrentSymbol(symbol)).willReturn(usage);
        given(usage.givenCurrentName(name)).willReturn(usage);
        given(usage.givenCurrentExpiry(expiry)).willReturn(usage);
        given(usage.givenCurrentMemo(memo)).willReturn(usage);
        given(usage.givenCurrentlyUsingAutoRenewAccount()).willReturn(usage);
        given(usage.get()).willReturn(expected);

        given(view.infoForToken(target)).willReturn(Optional.of(info));

        txnUsageEstimator = mock(TxnUsageEstimator.class);
        EstimatorFactory estimatorFactory = mock(EstimatorFactory.class);
        given(estimatorFactory.get(sigUsage, tokenUpdateTxn, ESTIMATOR_UTILS))
                .willReturn(txnUsageEstimator);
        subject = new TokenUpdateResourceUsage(estimatorFactory);
    }

    @Test
    void recognizesApplicability() {
        // expect:
        assertTrue(subject.applicableTo(tokenUpdateTxn));
        assertFalse(subject.applicableTo(nonTokenUpdateTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        final var mockStatic = mockStatic(TokenUpdateUsage.class);
        mockStatic
                .when(() -> TokenUpdateUsage.newEstimate(tokenUpdateTxn, txnUsageEstimator))
                .thenReturn(usage);

        // expect:
        assertEquals(expected, subject.usageGiven(tokenUpdateTxn, obj, view));

        // and:
        verify(usage).givenCurrentMemo(memo);

        mockStatic.close();
    }

    @Test
    void returnsDefaultIfInfoMissing() throws Exception {
        final var mockStatic = mockStatic(TokenUpdateUsage.class);
        mockStatic
                .when(() -> TokenUpdateUsage.newEstimate(tokenUpdateTxn, txnUsageEstimator))
                .thenReturn(usage);

        given(view.infoForToken(any())).willReturn(Optional.empty());

        // expect:
        assertEquals(FeeData.getDefaultInstance(), subject.usageGiven(tokenUpdateTxn, obj, view));

        mockStatic.close();
    }
}
