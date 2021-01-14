package com.hedera.services.txns.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class TokenWipeTransitionLogicTest {
    private AccountID account = IdUtils.asAccount("1.2.4");
    private TokenID id = IdUtils.asToken("1.2.3");
    private long wipeAmount = 100;
    private long totalAmount = 1000L;

    private TokenStore tokenStore;
    private TransactionContext txnCtx;
    private PlatformTxnAccessor accessor;
    private MerkleToken token;

    private TransactionBody tokenWipeTxn;
    private TokenWipeTransitionLogic subject;

    @BeforeEach
    private void setup() {
        tokenStore = mock(TokenStore.class);
        accessor = mock(PlatformTxnAccessor.class);
        token = mock(MerkleToken.class);

        txnCtx = mock(TransactionContext.class);

        subject = new TokenWipeTransitionLogic(tokenStore, txnCtx);
    }

    @Test
    public void capturesInvalidWipe() {
        givenValidTxnCtx();
        // and:
        given(tokenStore.wipe(account, id, wipeAmount, false)).willReturn(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
    }

    @Test
    public void followsHappyPath() {
        givenValidTxnCtx();
        // and:
        given(tokenStore.wipe(account, id, wipeAmount, false)).willReturn(OK);

        // when:
        subject.doStateTransition();

        // then:
        verify(tokenStore).wipe(account, id, wipeAmount, false);
        verify(txnCtx).setStatus(SUCCESS);
        verify(txnCtx).setNewTotalSupply(totalAmount);
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(tokenWipeTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    public void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtx();
        // and:
        given(tokenStore.wipe(any(), any(), anyLong(), anyBoolean()))
                .willThrow(IllegalArgumentException.class);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    public void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.syntaxCheck().apply(tokenWipeTxn));
    }

    @Test
    public void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.syntaxCheck().apply(tokenWipeTxn));
    }

    @Test
    public void rejectsMissingAccount() {
        givenMissingAccount();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.syntaxCheck().apply(tokenWipeTxn));
    }

    @Test
    public void rejectsInvalidZeroAmount() {
        givenInvalidZeroWipeAmount();

        // expect:
        assertEquals(INVALID_WIPING_AMOUNT, subject.syntaxCheck().apply(tokenWipeTxn));
    }

    @Test
    public void rejectsInvalidNegativeAmount() {
        givenInvalidNegativeWipeAmount();

        // expect:
        assertEquals(INVALID_WIPING_AMOUNT, subject.syntaxCheck().apply(tokenWipeTxn));
    }

    private void givenValidTxnCtx() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(id)
                        .setAccount(account)
                        .setAmount(wipeAmount))
                .build();
        given(accessor.getTxn()).willReturn(tokenWipeTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(tokenStore.resolve(id)).willReturn(id);
        given(tokenStore.get(id)).willReturn(token);
        given(token.totalSupply()).willReturn(totalAmount);
    }

    private void givenMissingToken() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder())
                .build();
    }

    private void givenMissingAccount() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(id))
                .build();
    }

    private void givenInvalidZeroWipeAmount() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(id)
                        .setAccount(account)
                        .setAmount(0))
                .build();
    }

    private void givenInvalidNegativeWipeAmount() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(id)
                        .setAccount(account)
                        .setAmount(-1))
                .build();
    }
}
