/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.records.BaseRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenPauseHandlerTest extends TokenHandlerTestBase {
    private TokenPauseHandler subject;
    private TransactionBody tokenPauseTxn;
    private PreHandleContext preHandleContext;

    @Mock
    private Account account;

    @Mock
    private AccountAccess accountAccess;

    @BeforeEach
    void setUp() throws PreCheckException {
        given(accountAccess.getAccountById(AccountID.newBuilder().accountNum(3L).build()))
                .willReturn(account);
        given(account.key()).willReturn(payerKey);

        subject = new TokenPauseHandler();
        givenValidTxn();
        refreshStoresWithCurrentTokenInWritable();
        preHandleContext = new PreHandleContext(accountAccess, tokenPauseTxn);
    }

    @Test
    void pausesUnPausedToken() {
        unPauseKnownToken();
        assertFalse(writableStore.get(tokenId.tokenNum()).get().paused());

        subject.handle(tokenPauseTxn, writableStore);

        final var unpausedToken = writableStore.get(tokenId.tokenNum()).get();
        assertTrue(unpausedToken.paused());
    }

    @Test
    void pauseTokenFailsIfInvalidToken() {
        givenInvalidTokenInTxn();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(tokenPauseTxn, writableStore));
        assertEquals(INVALID_TOKEN_ID, msg.getStatus());
    }

    @Test
    void failsForNullArguments() {
        assertThrows(NullPointerException.class, () -> subject.handle(null, writableStore));
        assertThrows(NullPointerException.class, () -> subject.handle(tokenPauseTxn, null));
    }

    @Test
    void failsInPrecheckIfTxnBodyHasNoToken() throws PreCheckException {
        final var txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenPause(TokenPauseTransactionBody.newBuilder())
                .build();
        preHandleContext = new PreHandleContext(accountAccess, txn);
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext, readableStore), INVALID_TOKEN_ID);
    }

    @Test
    void validatesTokenExistsInPreHandle() throws PreCheckException {
        givenInvalidTokenInTxn();
        preHandleContext = new PreHandleContext(accountAccess, tokenPauseTxn);
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext, readableStore), INVALID_TOKEN_ID);
    }

    @Test
    void preHandleAddsPauseKeyToContext() throws PreCheckException {
        subject.preHandle(preHandleContext, readableStore);
        assertEquals(1, preHandleContext.requiredNonPayerKeys().size());
    }

    @Test
    void preHandleSetsStatusWhenTokenMissing() throws PreCheckException {
        givenInvalidTokenInTxn();
        preHandleContext = new PreHandleContext(accountAccess, tokenPauseTxn);
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext, readableStore), INVALID_TOKEN_ID);
    }

    @Test
    void doesntAddAnyKeyIfPauseKeyMissing() throws PreCheckException {
        final var copy = token.copyBuilder().pauseKey(Key.DEFAULT).build();
        readableTokenState = MapReadableKVState.<EntityNum, Token>builder(TOKENS)
                .value(tokenEntityNum, copy)
                .build();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableTokenState);
        readableStore = new ReadableTokenStore(readableStates);

        subject.preHandle(preHandleContext, readableStore);

        assertEquals(0, preHandleContext.requiredNonPayerKeys().size());
    }

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(BaseRecordBuilder.class, subject.newRecordBuilder());
    }

    private void givenValidTxn() {
        tokenPauseTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenPause(TokenPauseTransactionBody.newBuilder().token(tokenId))
                .build();
    }

    private void givenInvalidTokenInTxn() {
        tokenPauseTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenPause(TokenPauseTransactionBody.newBuilder()
                        .token(TokenID.newBuilder().tokenNum(2).build()))
                .build();
    }

    private void unPauseKnownToken() {
        final var token = writableStore
                .get(tokenId.tokenNum())
                .get()
                .copyBuilder()
                .paused(false)
                .build();
        writableStore.put(token);
    }
}
