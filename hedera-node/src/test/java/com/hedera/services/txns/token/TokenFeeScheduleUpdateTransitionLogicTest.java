package com.hedera.services.txns.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.time.Instant;

import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHts;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@ExtendWith(LogCaptureExtension.class)
class TokenFeeScheduleUpdateTransitionLogicTest {
    private long thisSecond = 1_234_567L;
    private Instant now = Instant.ofEpochSecond(thisSecond);
    private TokenID target = IdUtils.asToken("1.2.666");
    private JKey adminKey = new JEd25519Key("w/e".getBytes());
    private TokenFeeScheduleUpdateTransactionBody tokenFeeScheduleUpdateTxn;
    private TransactionBody tokenFeeScheduleUpdateTxnBody;
    private Token token;
    private GlobalDynamicProperties globalDynamicProperties;

    private TypedTokenStore typedTokenStore;
    private AccountStore accountStore;
    private TransactionContext txnCtx;
    private PlatformTxnAccessor accessor;

    private com.hederahashgraph.api.proto.java.CustomFee customFixedFee = new CustomFeeBuilder(IdUtils.asAccount("7.7.7")).withFixedFee(fixedHts(300L));
    private com.hederahashgraph.api.proto.java.CustomFee withOnlyFeeCollectorCustomFee = new CustomFeeBuilder(IdUtils.asAccount("7.7.7")).withOnlyFeeCollector();

    private Token denom;
    private Id denomId = Id.fromGrpcToken(IdUtils.asToken("17.71.77"));
    private TokenID denonIdWithZeroNum = TokenID.newBuilder().setTokenNum(0).build();
    private AccountID feeCollector = IdUtils.asAccount("6.6.6");
    private Account collector = mock(Account.class);
    private CopyOnWriteIds associatedTokens = mock(CopyOnWriteIds.class);

    @Inject
    private LogCaptor logCaptor;

    @LoggingSubject
    private TokenFeeScheduleUpdateTransitionLogic subject;

    @BeforeEach
    public void setup() {
        typedTokenStore = mock(TypedTokenStore.class);
        accountStore = mock(AccountStore.class);
        accessor = mock(PlatformTxnAccessor.class);
        txnCtx = mock(TransactionContext.class);
        globalDynamicProperties = mock(GlobalDynamicProperties.class);
        token = mock(Token.class);
        denom = mock(Token.class);

        given(typedTokenStore.loadToken(Id.fromGrpcToken(target))).willReturn(token);
        subject = new TokenFeeScheduleUpdateTransitionLogic(typedTokenStore, txnCtx, accountStore, globalDynamicProperties);
    }

    @Test
    void happyPathWorks() {
        givenValidTxnCtx();
        given(token.isDeleted()).willReturn(false);
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(100);
        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);

        given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
        given(denom.getId()).willReturn(denomId);
        given(denom.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(typedTokenStore.loadPossiblyDeletedOrAutoRemovedToken(denomId)).willReturn(denom);

        given(collector.getAssociatedTokens()).willReturn(associatedTokens);
        given(associatedTokens.contains(denomId)).willReturn(true);
        given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(feeCollector)), any())).willReturn(collector);

        subject.doStateTransition();

        verify(typedTokenStore).persistToken(any());
    }

    @Test
    void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtxWithInvalidFee();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(100);

        assertFailsWith(() -> subject.doStateTransition(), CUSTOM_FEE_NOT_FULLY_SPECIFIED);
    }

    @Test
    void returnsFailingStatusFromUpdatingFeeScheduleInStore() {
        givenValidTxnCtx();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(0);

        assertFailsWith(() -> subject.doStateTransition(), CUSTOM_FEES_LIST_TOO_LONG);
    }

    private void givenValidTxnCtx() {
        CustomFee customFixedFeeWithDenom = new CustomFeeBuilder(
                feeCollector)
                .withFixedFee(fixedHts(300L)
                .setDenominatingTokenId(denomId.asGrpcToken())
                );

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(customFixedFeeWithDenom)
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    private void givenValidTxnCtxWithInvalidFee() {
        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(withOnlyFeeCollectorCustomFee)
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    private void givenValidTxnCtxWithRoyaltyFee() {
       final var royaltyFee =  CustomFee.newBuilder()
                .setRoyaltyFee(RoyaltyFee.newBuilder()
                        .setExchangeValueFraction(Fraction.newBuilder()
                                .setNumerator(9)
                                .setDenominator(10)))
                .build();

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(royaltyFee)
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    void givenValidTxnCtxWithFixedFeeWithInvalidDenominator(){
        final var fixedFee = new CustomFeeBuilder(
                IdUtils.asAccount("7.7.7"))
                .withFixedFee(fixedHts(300L)
                .setDenominatingTokenId(denomId.asGrpcToken())).getFixedFee();


        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(CustomFee.newBuilder().setFixedFee(fixedFee))
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    @Test
    void rejectsFixedFeeWithInvalidDenominator() {
        givenValidTxnCtxWithFixedFeeWithInvalidDenominator();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willThrow(new InvalidTransactionException(INVALID_TOKEN_ID_IN_CUSTOM_FEES));

        assertFailsWith(() -> subject.doStateTransition(), INVALID_TOKEN_ID_IN_CUSTOM_FEES);
    }

    @Test
    void rejectsFixedFeeWithInvalidDenominatorType() {
        givenValidTxnCtxWithFixedFeeWithInvalidDenominator();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
        given(denom.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        assertFailsWith(() -> subject.doStateTransition(), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
    }

    void givenValidTxnCtxWithFixedFeeWithNotAssociatedDenominator(){
        final var fixedFee = new CustomFeeBuilder(
                IdUtils.asAccount("7.7.7"))
                .withFixedFee(fixedHts(300L)
                        .setDenominatingTokenId(denomId.asGrpcToken())).getFixedFee();

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(CustomFee.newBuilder().setFixedFee(fixedFee) .setFeeCollectorAccountId(feeCollector))
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }



    @Test
    void rejectFixedFeeWithNonAssociatedDenominator() {
        givenValidTxnCtxWithFixedFeeWithNotAssociatedDenominator();

        given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
        given(denom.getId()).willReturn(denomId);
        given(denom.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);

        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(feeCollector), INVALID_CUSTOM_FEE_COLLECTOR)).willReturn(collector);
        given(collector.getAssociatedTokens()).willReturn(new CopyOnWriteIds()); //No associated tokens

        assertFailsWith(() -> subject.doStateTransition(), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }



    void givenValidTxnCtxWithRoyaltyWithInvalidDenominator(){
        final var royaltyFee = RoyaltyFee.newBuilder()
                .setExchangeValueFraction(Fraction.newBuilder()
                        .setNumerator(9)
                        .setDenominator(10))
                .setFallbackFee(
                        FixedFee.newBuilder()
                                .setAmount(10)
                                .setDenominatingTokenId(denomId.asGrpcToken())
                                .build())
                .build();

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(CustomFee.newBuilder().setRoyaltyFee(royaltyFee))
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    @Test
    void rejectsRoyaltyFeeWithInvalidFallbackDenominator() {
        givenValidTxnCtxWithRoyaltyWithInvalidDenominator();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willThrow(new InvalidTransactionException(INVALID_TOKEN_ID_IN_CUSTOM_FEES));

        assertFailsWith(() -> subject.doStateTransition(), INVALID_TOKEN_ID_IN_CUSTOM_FEES);
    }

    void givenValidTxnCtxWithRoyaltyFeeWithNoDenominator(){
        final var royaltyFee = RoyaltyFee.newBuilder()
                .setExchangeValueFraction(Fraction.newBuilder()
                        .setNumerator(9)
                        .setDenominator(10))
                .setFallbackFee(
                        FixedFee.newBuilder()
                                .setAmount(10)
                                .build())
                .build();

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(CustomFee.newBuilder().setRoyaltyFee(royaltyFee))
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    @Test
    void persistsRoyaltyFeeWithNoDenominator() {
        givenValidTxnCtxWithRoyaltyFeeWithNoDenominator();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        subject.doStateTransition();

        verify(typedTokenStore).persistToken(token);
    }

    void givenValidTxnCtxWithRoyaltyFeeWithSameDenominator(){
        final var royaltyFee = RoyaltyFee.newBuilder()
                .setExchangeValueFraction(Fraction.newBuilder()
                        .setNumerator(9)
                        .setDenominator(10))
                .setFallbackFee(
                        FixedFee.newBuilder()
                                .setAmount(10)
                                .setDenominatingTokenId(TokenID.newBuilder().setTokenNum(0).build())
                                .build())
                .build();

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(CustomFee.newBuilder().setRoyaltyFee(royaltyFee))
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    @Test
    void persistsRoyaltyFeeWithSameDenominator() {
        givenValidTxnCtxWithRoyaltyFeeWithSameDenominator();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        subject.doStateTransition();

        verify(typedTokenStore).persistToken(token);
    }

    void givenValidTxnCtxWithFixedFeeWithSameTokenDenominator(){
        final var fixedFee = FixedFee.newBuilder()
                                .setAmount(10)
                                .setDenominatingTokenId(denonIdWithZeroNum)
                                .build();


        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(CustomFee.newBuilder().setFixedFee(fixedFee))
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    @Test
    void rejectFixedFeeWithSameTokenDenominatorWithInvalidType() {
        givenValidTxnCtxWithFixedFeeWithSameTokenDenominator();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(denom.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(typedTokenStore.loadTokenOrFailWith(eq(Id.fromGrpcToken(denonIdWithZeroNum)),any())).willReturn(denom);

        assertFailsWith(() ->  subject.doStateTransition(), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
    }

    void givenValidTxnCtxWithFixedFeeWithNoDenominator(){
        final var fixedFee = FixedFee.newBuilder()
                                .setAmount(10)
                                .build();

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(CustomFee.newBuilder().setFixedFee(fixedFee))
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    @Test
    void persistsFixedFeeWithNoDenominator() {
        givenValidTxnCtxWithFixedFeeWithNoDenominator();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        subject.doStateTransition();

        verify(typedTokenStore).persistToken(token);
    }

    void givenValidTxnCtxWithRoyaltyFeeWithNotAssociatedDenominator(){
        final var royaltyFee = RoyaltyFee.newBuilder()
                .setExchangeValueFraction(Fraction.newBuilder()
                        .setNumerator(9)
                        .setDenominator(10))
                .setFallbackFee(
                        FixedFee.newBuilder()
                                .setAmount(10)
                                .setDenominatingTokenId(denomId.asGrpcToken())
                                .build())
                .build();

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(CustomFee.newBuilder().setRoyaltyFee(royaltyFee) .setFeeCollectorAccountId(feeCollector))
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    @Test
    void rejectRoyaltyFeeWithNonAssociatedDenominator() {
        givenValidTxnCtxWithRoyaltyFeeWithNotAssociatedDenominator();

        given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
        given(denom.getType()).willReturn(com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE);
        given(denom.getId()).willReturn(denomId);
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);

        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(feeCollector), INVALID_CUSTOM_FEE_COLLECTOR)).willReturn(collector);
        given(collector.getAssociatedTokens()).willReturn(new CopyOnWriteIds()); //No associated tokens

        assertFailsWith(() -> subject.doStateTransition(), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }


    private void givenValidTxnCtxWithRoyaltyFeeAndInvalidFraction() {
        final var royaltyFee =  CustomFee.newBuilder()
                .setRoyaltyFee(RoyaltyFee.newBuilder()
                        .setExchangeValueFraction(Fraction.newBuilder()
                                .setNumerator(11)
                                .setDenominator(10)))
                .build();

        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(target)
                .addCustomFees(royaltyFee)
                .build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    @Test
    void royaltyFeeFractionLessThanOne(){
        givenValidTxnCtxWithRoyaltyFeeAndInvalidFraction();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        assertFailsWith(() -> subject.doStateTransition(), ROYALTY_FRACTION_CANNOT_EXCEED_ONE);
    }

    @Test
    void royaltyFeeOnlyForNonFungibleTokens(){
        givenValidTxnCtxWithRoyaltyFee();
        given(globalDynamicProperties.maxCustomFeesAllowed()).willReturn(10);
        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);

        assertFailsWith(() -> subject.doStateTransition(), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
    }

    @Test
    void rejectsInvalidTokenId() {
        givenInvalidTokenId();

        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenFeeScheduleUpdateTxnBody));
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

    private void givenInvalidTokenId() {
        tokenFeeScheduleUpdateTxnBody = TransactionBody.newBuilder().build();
        tokenFeeScheduleUpdateTxn = TokenFeeScheduleUpdateTransactionBody.newBuilder().build();

        TransactionBody txn = mock(TransactionBody.class);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);
        given(txnCtx.consensusTime()).willReturn(now);
    }

    private void givenValidTokenId() {
        tokenFeeScheduleUpdateTxnBody = TransactionBody.newBuilder()
                .setTokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
                        .setTokenId(target))
                .build();
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }
}
