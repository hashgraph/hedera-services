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
import static org.mockito.Mockito.verify;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.token.TokenAssociateUsage;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenAssociateResourceUsageTest {
    private static final AccountID target = IdUtils.asAccount("1.2.3");
    private static final int numSigs = 10;
    private static final int sigsSize = 100;
    private static final int numPayerKeys = 3;
    private static final SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    private static final SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    private static final long expiry = 1_234_567L;
    private static final TokenID firstToken = IdUtils.asToken("0.0.123");
    private static final TokenID secondToken = IdUtils.asToken("0.0.124");

    private MerkleAccount account;
    private MerkleMap<EntityNum, MerkleAccount> accounts;
    private TransactionBody nonTokenAssociateTxn;
    private TransactionBody tokenAssociateTxn;
    private StateView view;
    private TokenAssociateUsage usage;
    private FeeData expected;

    private TokenAssociateResourceUsage subject;
    private TxnUsageEstimator txnUsageEstimator;

    @BeforeEach
    void setup() {
        expected = mock(FeeData.class);

        account = mock(MerkleAccount.class);
        given(account.getExpiry()).willReturn(expiry);
        accounts = mock(MerkleMap.class);
        given(accounts.get(EntityNum.fromAccountId(target))).willReturn(account);
        view = mock(StateView.class);
        given(view.accounts()).willReturn(accounts);

        tokenAssociateTxn = mock(TransactionBody.class);
        given(tokenAssociateTxn.hasTokenAssociate()).willReturn(true);
        given(tokenAssociateTxn.getTokenAssociate())
                .willReturn(
                        TokenAssociateTransactionBody.newBuilder()
                                .setAccount(IdUtils.asAccount("1.2.3"))
                                .addTokens(firstToken)
                                .addTokens(secondToken)
                                .build());

        nonTokenAssociateTxn = mock(TransactionBody.class);
        given(nonTokenAssociateTxn.hasTokenAssociate()).willReturn(false);

        usage = mock(TokenAssociateUsage.class);
        given(usage.givenCurrentExpiry(expiry)).willReturn(usage);
        given(usage.get()).willReturn(expected);

        txnUsageEstimator = mock(TxnUsageEstimator.class);
        EstimatorFactory estimatorFactory = mock(EstimatorFactory.class);
        given(estimatorFactory.get(sigUsage, tokenAssociateTxn, ESTIMATOR_UTILS))
                .willReturn(txnUsageEstimator);
        subject = new TokenAssociateResourceUsage(estimatorFactory);
    }

    @Test
    void recognizesApplicability() {
        assertTrue(subject.applicableTo(tokenAssociateTxn));
        assertFalse(subject.applicableTo(nonTokenAssociateTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws InvalidTxBodyException {
        final var mockStatic = mockStatic(TokenAssociateUsage.class);
        mockStatic
                .when(() -> TokenAssociateUsage.newEstimate(tokenAssociateTxn, txnUsageEstimator))
                .thenReturn(usage);

        assertEquals(expected, subject.usageGiven(tokenAssociateTxn, obj, view));
        verify(usage).givenCurrentExpiry(expiry);

        mockStatic.close();
    }

    @Test
    void returnsDefaultIfInfoMissing() throws InvalidTxBodyException {
        given(accounts.get(EntityNum.fromAccountId(target))).willReturn(null);

        assertEquals(
                FeeData.getDefaultInstance(), subject.usageGiven(tokenAssociateTxn, obj, view));
    }
}
