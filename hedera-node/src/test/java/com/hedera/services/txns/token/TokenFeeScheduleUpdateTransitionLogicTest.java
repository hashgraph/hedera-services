/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenFeeScheduleUpdateTransitionLogicTest {
    private final TokenID target = IdUtils.asToken("0.0.666");
    private TransactionBody tokenFeeScheduleUpdateTxnBody;

    @Mock private Token token;
    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private TransactionContext txnCtx;
    @Mock private SignedTxnAccessor accessor;
    @Mock private Function<CustomFee, FcCustomFee> grpcFeeConverter;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private FcCustomFee firstMockFee;
    @Mock private FcCustomFee secondMockFee;
    @Mock private SigImpactHistorian sigImpactHistorian;

    private TokenFeeScheduleUpdateTransitionLogic subject;

    @BeforeEach
    void setup() {
        subject =
                new TokenFeeScheduleUpdateTransitionLogic(
                        tokenStore, txnCtx, accountStore, sigImpactHistorian, dynamicProperties);
    }

    @Test
    void validatesFeesListLength() {
        givenTxnCtx();
        given(token.hasFeeScheduleKey()).willReturn(true);

        TxnUtils.assertFailsWith(() -> subject.doStateTransition(), CUSTOM_FEES_LIST_TOO_LONG);
    }

    @Test
    void validatesTokenHasFeeScheduleKey() {
        givenTxnCtx();

        TxnUtils.assertFailsWith(() -> subject.doStateTransition(), TOKEN_HAS_NO_FEE_SCHEDULE_KEY);
    }

    @Test
    void happyPathWorks() {
        subject.setGrpcFeeConverter(grpcFeeConverter);
        given(grpcFeeConverter.apply(CustomFee.getDefaultInstance()))
                .willReturn(firstMockFee)
                .willReturn(secondMockFee);

        givenTxnCtx();
        given(dynamicProperties.maxCustomFeesAllowed()).willReturn(2);
        given(token.hasFeeScheduleKey()).willReturn(true);

        subject.doStateTransition();

        verify(firstMockFee).validateWith(token, accountStore, tokenStore);
        verify(secondMockFee).validateWith(token, accountStore, tokenStore);
        verify(firstMockFee).nullOutCollector();
        verify(secondMockFee).nullOutCollector();
        verify(token).setCustomFees(List.of(firstMockFee, secondMockFee));
        verify(tokenStore).commitToken(token);
        verify(sigImpactHistorian).markEntityChanged(target.getTokenNum());
    }

    @Test
    void rejectsInvalidTokenId() {
        assertEquals(
                INVALID_TOKEN_ID,
                subject.semanticCheck().apply(TransactionBody.getDefaultInstance()));
    }

    @Test
    void acceptsValidTokenId() {
        givenValidTokenId();

        assertEquals(OK, subject.semanticCheck().apply(tokenFeeScheduleUpdateTxnBody));
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTokenId();

        assertTrue(subject.applicability().test(tokenFeeScheduleUpdateTxnBody));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    private void givenValidTokenId() {
        tokenFeeScheduleUpdateTxnBody =
                TransactionBody.newBuilder()
                        .setTokenFeeScheduleUpdate(
                                TokenFeeScheduleUpdateTransactionBody.newBuilder()
                                        .setTokenId(target))
                        .build();
    }

    private void givenTxnCtx() {
        final TokenFeeScheduleUpdateTransactionBody tokenFeeScheduleUpdateTxn =
                TokenFeeScheduleUpdateTransactionBody.newBuilder()
                        .setTokenId(target)
                        .addCustomFees(CustomFee.getDefaultInstance())
                        .addCustomFees(CustomFee.getDefaultInstance())
                        .build();

        final var txn =
                TransactionBody.newBuilder()
                        .setTokenFeeScheduleUpdate(tokenFeeScheduleUpdateTxn)
                        .build();
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(tokenStore.loadToken(Id.fromGrpcToken(target))).willReturn(token);
    }
}
