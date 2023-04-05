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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.service.token.impl.records.PauseTokenRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenPauseHandlerTest extends TokenHandlerTestBase {
    private TokenPauseHandler subject;
    private TransactionBody tokenPauseTxn;

    @BeforeEach
    void setUp() {
        subject = new TokenPauseHandler();
        givenValidTxn();
        refreshStoresWithCurrentTokenInWritable();
    }

    @Test
    void pausesUnPausedToken() {
        unPauseKnownToken();
        assertFalse(writableStore.get(tokenId.tokenNum()).get().paused());

        subject.handle(tokenPauseTxn, new PauseTokenRecordBuilder(), writableStore);

        final var unpausedToken = writableStore.get(tokenId.tokenNum()).get();
        assertTrue(unpausedToken.paused());
    }

    @Test
    void pauseTokenFailsIfInvalidToken() {
        givenInvalidTokenInTxn();

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(tokenPauseTxn, new PauseTokenRecordBuilder(), writableStore));
        assertEquals(INVALID_TOKEN_ID, msg.getStatus());
    }

    @Test
    void failsForNullArguments() {
        final var builder = new PauseTokenRecordBuilder();
        assertThrows(
                NullPointerException.class, () -> subject.handle(null, builder, writableStore));
        assertThrows(NullPointerException.class, () -> subject.handle(tokenPauseTxn, null, writableStore));
        assertThrows(
                NullPointerException.class, () -> subject.handle(tokenPauseTxn, builder, null));
    }

    private void givenValidTxn() {
        tokenPauseTxn = TransactionBody.newBuilder()
                .tokenPause(TokenPauseTransactionBody.newBuilder().token(tokenId))
                .build();
    }

    private void givenInvalidTokenInTxn() {
        tokenPauseTxn = TransactionBody.newBuilder()
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
