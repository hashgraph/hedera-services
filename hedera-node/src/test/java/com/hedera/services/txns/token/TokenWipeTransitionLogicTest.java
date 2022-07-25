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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenWipeTransitionLogicTest {
    private AccountID accountID = IdUtils.asAccount("1.2.4");
    private TokenID id = IdUtils.asToken("1.2.3");
    private long wipeAmount = 100;
    private long totalAmount = 1000L;

    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private MerkleToken merkleToken;
    private Token token;

    private TransactionBody tokenWipeTxn;
    private TokenWipeTransitionLogic subject;
    private TypedTokenStore typedTokenStore;
    private AccountStore accountStore;
    private OptionValidator validator;
    private GlobalDynamicProperties dynamicProperties;
    private Account account;

    @BeforeEach
    private void setup() {
        accessor = mock(SignedTxnAccessor.class);
        merkleToken = mock(MerkleToken.class);
        token = mock(Token.class);
        account = mock(Account.class);

        txnCtx = mock(TransactionContext.class);

        typedTokenStore = mock(TypedTokenStore.class);
        accountStore = mock(AccountStore.class);
        validator = mock(ContextOptionValidator.class);
        dynamicProperties = mock(GlobalDynamicProperties.class);
        subject = new TokenWipeTransitionLogic(typedTokenStore, accountStore, txnCtx);
        given(txnCtx.accessor()).willReturn(accessor);
    }

    @Test
    void capturesInvalidWipe() {
        givenValidCommonTxnCtx();
        // and:
        doThrow(new InvalidTransactionException(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))
                .when(token)
                .wipe(any(), anyLong());
        // when:
        try {
            subject.doStateTransition();
        } catch (InvalidTransactionException e) {
            // then:
            assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, e.getResponseCode());
        }
    }

    @Test
    void followsHappyPathForCommon() {
        givenValidCommonTxnCtx();

        // when:
        subject.doStateTransition();

        // then:
        verify(token).wipe(any(), anyLong());
    }

    @Test
    void rejectsUniqueWhenNftsNotEnabled() {
        givenValidUniqueTxnCtx();
        given(dynamicProperties.areNftsEnabled()).willReturn(false);

        // expect:
        assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void followsHappyPathForUnique() {
        givenValidUniqueTxnCtx();
        // needed only in the context of this test
        Account acc = mock(Account.class);
        var treasury = mock(Account.class);
        TokenRelationship treasuryRel = mock(TokenRelationship.class);
        TokenRelationship accRel = mock(TokenRelationship.class);
        given(token.getTreasury()).willReturn(treasury);
        given(accountStore.loadAccount(any())).willReturn(acc);
        given(typedTokenStore.loadTokenRelationship(token, acc)).willReturn(accRel);
        given(typedTokenStore.loadTokenRelationship(token, token.getTreasury()))
                .willReturn(treasuryRel);

        // when:
        subject.doStateTransition();

        // then:
        verify(token).wipe(any(OwnershipTracker.class), any(TokenRelationship.class), anyList());
    }

    @Test
    void hasCorrectApplicability() {
        givenValidCommonTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(tokenWipeTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void setsFailInvalidIfUnhandledException() {
        givenValidCommonTxnCtx();
        // and:
        doThrow(InvalidTransactionException.class).when(token).wipe(any(), anyLong());

        // when:
        assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());
    }

    @Test
    void acceptsValidCommonTxn() {
        givenValidCommonTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void acceptsValidUniqueTxn() {
        givenValidUniqueTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void rejectsMissingAccount() {
        givenMissingAccount();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void rejectsInvalidZeroAmount() {
        givenInvalidZeroWipeAmount();

        // expect:
        assertEquals(INVALID_WIPING_AMOUNT, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void rejectsInvalidNegativeAmount() {
        givenInvalidNegativeWipeAmount();

        // expect:
        assertEquals(INVALID_WIPING_AMOUNT, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void rejectsBothAmountAndSerialNumbers() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);

        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .setAmount(10)
                                        .addAllSerialNumbers(List.of(1L, 2L)))
                        .build();

        assertEquals(INVALID_TRANSACTION_BODY, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void rejectsInvalidNftId() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);

        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .addAllSerialNumbers(List.of(-1L)))
                        .build();
        given(validator.maxBatchSizeWipeCheck(anyInt())).willReturn(OK);

        assertEquals(INVALID_NFT_ID, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    void propagatesErrorOnInvalidBatch() {
        givenValidUniqueTxnCtx();
        given(validator.maxBatchSizeWipeCheck(anyInt())).willReturn(BATCH_SIZE_LIMIT_EXCEEDED);

        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.semanticCheck().apply(tokenWipeTxn));
    }

    private void givenValidCommonTxnCtx() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .setAmount(wipeAmount))
                        .build();
        given(accessor.getTxn()).willReturn(tokenWipeTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(merkleToken.totalSupply()).willReturn(totalAmount);
        given(merkleToken.tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(typedTokenStore.loadToken(any())).willReturn(token);
        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(typedTokenStore.loadTokenRelationship(token, account))
                .willReturn(new TokenRelationship(token, account));
    }

    private void givenValidUniqueTxnCtx() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .addAllSerialNumbers(List.of(1L, 2L, 3L)))
                        .build();
        given(accessor.getTxn()).willReturn(tokenWipeTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(merkleToken.totalSupply()).willReturn(totalAmount);
        given(merkleToken.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(typedTokenStore.loadToken(any())).willReturn(token);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(dynamicProperties.areNftsEnabled()).willReturn(true);

        given(validator.maxBatchSizeWipeCheck(anyInt())).willReturn(OK);
    }

    private void givenMissingToken() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder())
                        .build();
    }

    private void givenMissingAccount() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder().setToken(id))
                        .build();
    }

    private void givenInvalidZeroWipeAmount() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .setAmount(0))
                        .build();
    }

    private void givenInvalidNegativeWipeAmount() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .setAmount(-1))
                        .build();
    }
}
