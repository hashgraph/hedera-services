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
package com.hedera.services.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenBurnTransitionLogicTest {
    private final long amount = 123L;
    private final TokenID grpcId = IdUtils.asToken("1.2.3");
    private final Id treasuryId = new Id(2, 4, 6);

    @Mock private TransactionContext txnCtx;
    @Mock private SignedTxnAccessor accessor;
    @Mock private TransactionBody transactionBody;
    @Mock private TokenBurnTransactionBody burnTransactionBody;
    @Mock private OptionValidator validator;
    @Mock private TypedTokenStore tokenStore;
    @Mock private AccountStore accountStore;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private TransactionBody tokenBurnTxn;

    private BurnLogic burnLogic;
    private TokenBurnTransitionLogic subject;

    @BeforeEach
    void setup() {
        burnLogic = new BurnLogic(validator, tokenStore, accountStore, dynamicProperties);
        subject = new TokenBurnTransitionLogic(txnCtx, burnLogic);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(tokenBurnTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void rejectsUniqueWhenNftsNotEnabled() {
        givenValidUniqueTxnCtx();
        given(dynamicProperties.areNftsEnabled()).willReturn(false);

        // expect:
        assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void rejectsInvalidNegativeAmount() {
        givenInvalidNegativeAmount();

        // expect:
        assertEquals(INVALID_TOKEN_BURN_AMOUNT, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void allowsZeroAmount() {
        givenInvalidZeroAmount();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void rejectsInvalidTxnBodyWithBothProps() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);

        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .addAllSerialNumbers(List.of(1L))
                                        .setAmount(1)
                                        .setToken(grpcId))
                        .build();

        assertEquals(INVALID_TRANSACTION_BODY, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void allowsTxnBodyWithNoProps() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(TokenBurnTransactionBody.newBuilder().setToken(grpcId))
                        .build();

        assertEquals(OK, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void rejectsInvalidTxnBodyWithInvalidBatch() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .addAllSerialNumbers(
                                                LongStream.range(-20L, 0L)
                                                        .boxed()
                                                        .collect(Collectors.toList()))
                                        .setToken(grpcId))
                        .build();

        given(validator.maxBatchSizeBurnCheck(tokenBurnTxn.getTokenBurn().getSerialNumbersCount()))
                .willReturn(OK);
        assertEquals(INVALID_NFT_ID, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void propagatesErrorOnBatchSizeExceeded() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .addAllSerialNumbers(
                                                LongStream.range(1, 5)
                                                        .boxed()
                                                        .collect(Collectors.toList()))
                                        .setToken(grpcId))
                        .build();

        given(validator.maxBatchSizeBurnCheck(tokenBurnTxn.getTokenBurn().getSerialNumbersCount()))
                .willReturn(BATCH_SIZE_LIMIT_EXCEEDED);
        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.semanticCheck().apply(tokenBurnTxn));
    }

    @Test
    void callsBurnLogicWithCorrectParams() {
        burnLogic = mock(BurnLogic.class);
        subject = new TokenBurnTransitionLogic(txnCtx, burnLogic);

        var grpcId = IdUtils.asToken("0.0.1");
        var amount = 4321L;
        List<Long> serialNumbersList = List.of(1L, 2L, 3L);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);
        given(transactionBody.getTokenBurn()).willReturn(burnTransactionBody);
        given(burnTransactionBody.getToken()).willReturn(grpcId);
        given(burnTransactionBody.getAmount()).willReturn(amount);
        given(burnTransactionBody.getSerialNumbersList()).willReturn(serialNumbersList);
        subject.doStateTransition();

        verify(burnLogic).burn(Id.fromGrpcToken(grpcId), amount, serialNumbersList);
    }

    private void givenValidTxnCtx() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .setAmount(amount))
                        .build();
    }

    private void givenValidUniqueTxnCtx() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .addAllSerialNumbers(List.of(1L)))
                        .build();
    }

    private void givenMissingToken() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(TokenBurnTransactionBody.newBuilder().build())
                        .build();
    }

    private void givenInvalidNegativeAmount() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .setAmount(-1)
                                        .build())
                        .build();
    }

    private void givenInvalidZeroAmount() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .setAmount(0)
                                        .build())
                        .build();
    }
}
