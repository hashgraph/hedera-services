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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
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
class TokenUnpauseHandlerTest extends TokenHandlerTestBase {
    private TokenUnpauseHandler subject;
    private TransactionBody tokenUnpauseTxn;
    private PreHandleContext preHandleContext;

    @Mock
    private AccountAccess accountAccess;

    @Mock
    private Account account;

    @Mock
    private Account account;

    @BeforeEach
    void setUp() throws PreCheckException {
        given(accountAccess.getAccountById(payerId)).willReturn(account);
        given(account.key()).willReturn(payerKey);
        subject = new TokenUnpauseHandler();
        givenValidTxn();
        refreshStoresWithCurrentTokenInWritable();
        preHandleContext = new PreHandleContext(accountAccess, tokenUnpauseTxn);
    }

    @Test
    void unPausesToken() {
        pauseKnownToken();
        assertTrue(writableStore.get(tokenId.tokenNum()).get().paused());

        subject.handle(tokenUnpauseTxn, writableStore);

        final var unpausedToken = writableStore.get(tokenId.tokenNum()).get();
        assertFalse(unpausedToken.paused());
    }

    @Test
    void unPausesTokenFailsIfInvalidToken() {
        pauseKnownToken();
        assertTrue(writableStore.get(tokenId.tokenNum()).get().paused());
        givenInvalidTokenInTxn();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(tokenUnpauseTxn, writableStore));
        assertEquals(INVALID_TOKEN_ID, msg.getStatus());
    }

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(BaseRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    void failsForNullArguments() {
        assertThrows(NullPointerException.class, () -> subject.handle(null, writableStore));
        assertThrows(NullPointerException.class, () -> subject.handle(tokenUnpauseTxn, null));
    }

    @Test
    void validatesTokenExistsInPreHandle() throws PreCheckException {
        givenInvalidTokenInTxn();
        preHandleContext = new PreHandleContext(accountAccess, tokenUnpauseTxn);
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext, readableStore), INVALID_TOKEN_ID);
    }

    @Test
    void failsInPreCheckIfTxnBodyHasNoToken() throws PreCheckException {
        final var txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenUnpause(TokenUnpauseTransactionBody.newBuilder())
                .build();
        preHandleContext = new PreHandleContext(accountAccess, txn);
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
        preHandleContext = new PreHandleContext(accountAccess, tokenUnpauseTxn);
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

    private void givenValidTxn() {
        tokenUnpauseTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenUnpause(TokenUnpauseTransactionBody.newBuilder().token(tokenId))
                .build();
    }

    private void givenInvalidTokenInTxn() {
        tokenUnpauseTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenUnpause(TokenUnpauseTransactionBody.newBuilder()
                        .token(TokenID.newBuilder().tokenNum(2).build()))
                .build();
    }

    private void pauseKnownToken() {
        final var token = writableStore
                .get(tokenId.tokenNum())
                .get()
                .copyBuilder()
                .paused(true)
                .build();
        writableStore.put(token);
    }
}
