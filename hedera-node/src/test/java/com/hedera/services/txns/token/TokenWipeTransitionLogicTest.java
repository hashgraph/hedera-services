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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

class TokenWipeTransitionLogicTest {
    private long tokenNum = 12345L;
    private long accountNum = 54321L;
    private long wipeAmount = 100;
    private AccountID accountID = IdUtils.asAccount("0.0." + accountNum);
    private TokenID tokenID = IdUtils.asToken("0.0." + tokenNum);
    private Id accountId = new Id(0,0,accountNum);
    private Id tokenId = new Id(0,0,tokenNum);

    private AccountStore accountStore;
    private TypedTokenStore tokenStore;
    private TransactionContext txnCtx;
    private PlatformTxnAccessor accessor;
    private Account account;
    private Token token;
    private TokenRelationship tokenRelationship;

    private TransactionBody tokenWipeTxn;
    private TokenWipeTransitionLogic subject;

    @BeforeEach
    private void setup() {
        tokenStore = mock(TypedTokenStore.class);
        accountStore = mock(AccountStore.class);
        accessor = mock(PlatformTxnAccessor.class);
        account = mock(Account.class);
        token = mock(Token.class);
        tokenRelationship = mock(TokenRelationship.class);

        txnCtx = mock(TransactionContext.class);

        subject = new TokenWipeTransitionLogic(tokenStore, accountStore, txnCtx);
    }

    @Test
    public void capturesInvalidWipe() {
        givenValidTxnCtx();
        // and:
        doThrow(new InvalidTransactionException(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))
                .when(tokenStore).loadTokenRelationship(token, account);

        // verify:
        assertFailsWith(() -> subject.doStateTransition(), TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        verify(token, never()).wipe(tokenRelationship, wipeAmount, false);
        verify(tokenStore, never()).persistTokenRelationship(tokenRelationship);
        verify(tokenStore, never()).persistToken(token);
    }

    @Test
    public void followsHappyPath() {
        givenValidTxnCtx();

        // when:
        subject.doStateTransition();

        // then:
        verify(token).wipe(tokenRelationship, wipeAmount, false);
        verify(tokenStore).persistTokenRelationship(tokenRelationship);
        verify(tokenStore).persistToken(token);
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(tokenWipeTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    public void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    public void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    public void rejectsMissingAccount() {
        givenMissingAccount();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    public void rejectsInvalidZeroAmount() {
        givenInvalidZeroWipeAmount();

        // expect:
        assertEquals(INVALID_WIPING_AMOUNT, subject.semanticCheck().apply(tokenWipeTxn));
    }

    @Test
    public void rejectsInvalidNegativeAmount() {
        givenInvalidNegativeWipeAmount();

        // expect:
        assertEquals(INVALID_WIPING_AMOUNT, subject.semanticCheck().apply(tokenWipeTxn));
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }

    private void givenValidTxnCtx() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(tokenID)
                        .setAccount(accountID)
                        .setAmount(wipeAmount))
                .build();
        given(accessor.getTxn()).willReturn(tokenWipeTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accountStore.loadAccount(accountId)).willReturn(account);
        given(tokenStore.loadToken(tokenId)).willReturn(token);
        given(tokenStore.loadTokenRelationship(token, account)).willReturn(tokenRelationship);
        given(tokenRelationship.getAccount()).willReturn(account);
        given(tokenRelationship.getToken()).willReturn(token);
    }

    private void givenMissingToken() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder())
                .build();
    }

    private void givenMissingAccount() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(tokenID))
                .build();
    }

    private void givenInvalidZeroWipeAmount() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(tokenID)
                        .setAccount(accountID)
                        .setAmount(0))
                .build();
    }

    private void givenInvalidNegativeWipeAmount() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(tokenID)
                        .setAccount(accountID)
                        .setAmount(-1))
                .build();
    }
}
