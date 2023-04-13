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
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
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
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.records.BaseRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleException;
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

    @Mock private AccountAccess accountAccess;

    @BeforeEach
    void setUp() {
        given(accountAccess.getKey(AccountID.newBuilder().accountNum(3L).build()))
                .willReturn(withKey(payerHederaKey));
        subject = new TokenUnpauseHandler();
        givenValidTxn();
        refreshStoresWithCurrentTokenInWritable();
        preHandleContext = new PreHandleContext(accountAccess, tokenUnpauseTxn, payerId);
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

        final var msg =
                assertThrows(
                        HandleException.class,
                        () -> subject.handle(tokenUnpauseTxn, writableStore));
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
    void validatesTokenExistsInPreHandle() {
        givenInvalidTokenInTxn();
        preHandleContext = new PreHandleContext(accountAccess, tokenUnpauseTxn, payerId);
        assertEquals(OK, preHandleContext.getStatus());

        subject.preHandle(preHandleContext, readableStore);

        assertEquals(INVALID_TOKEN_ID, preHandleContext.getStatus());
    }

    @Test
    void failsInPreCheckIfTxnBodyHasNoToken() {
        final var txn =
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                        .tokenUnpause(TokenUnpauseTransactionBody.newBuilder())
                        .build();
        preHandleContext = new PreHandleContext(accountAccess, txn, payerId);
        assertEquals(OK, preHandleContext.getStatus());

        subject.preHandle(preHandleContext, readableStore);

        assertEquals(INVALID_TOKEN_ID, preHandleContext.getStatus());
    }

    @Test
    void preHandleAddsPauseKeyToContext() {
        subject.preHandle(preHandleContext, readableStore);

        assertEquals(1, preHandleContext.getRequiredNonPayerKeys().size());
    }

    @Test
    void preHandleSetsStatusWhenTokenMissing() {
        givenInvalidTokenInTxn();
        preHandleContext = new PreHandleContext(accountAccess, tokenUnpauseTxn, payerId);
        subject.preHandle(preHandleContext, readableStore);

        assertEquals(0, preHandleContext.getRequiredNonPayerKeys().size());
        assertEquals(INVALID_TOKEN_ID, preHandleContext.getStatus());
    }

    @Test
    void doesntAddAnyKeyIfPauseKeyMissing() {
        final var copy = token.copyBuilder().pauseKey(Key.DEFAULT).build();
        readableTokenState =
                MapReadableKVState.<EntityNum, Token>builder(TOKENS)
                        .value(tokenEntityNum, copy)
                        .build();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableTokenState);
        readableStore = new ReadableTokenStore(readableStates);

        subject.preHandle(preHandleContext, readableStore);

        assertEquals(0, preHandleContext.getRequiredNonPayerKeys().size());
        assertEquals(OK, preHandleContext.getStatus());
    }

    private void givenValidTxn() {
        tokenUnpauseTxn =
                TransactionBody.newBuilder()
                        .tokenUnpause(TokenUnpauseTransactionBody.newBuilder().token(tokenId))
                        .build();
    }

    private void givenInvalidTokenInTxn() {
        tokenUnpauseTxn =
                TransactionBody.newBuilder()
                        .tokenUnpause(
                                TokenUnpauseTransactionBody.newBuilder()
                                        .token(TokenID.newBuilder().tokenNum(2).build()))
                        .build();
    }

    private void pauseKnownToken() {
        final var token =
                writableStore.get(tokenId.tokenNum()).get().copyBuilder().paused(true).build();
        writableStore.put(token);
    }
}
