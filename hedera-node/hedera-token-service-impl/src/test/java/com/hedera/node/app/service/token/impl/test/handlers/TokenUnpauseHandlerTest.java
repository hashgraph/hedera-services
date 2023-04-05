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
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.records.UnPauseTokenRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUnpauseHandlerTest extends TokenHandlerTestBase {
    private TokenUnpauseHandler subject;
    private TransactionBody tokenUnpauseTxn;

    @BeforeEach
    void setUp() {
        subject = new TokenUnpauseHandler();
        givenValidTxn();
        refreshStoresWithCurrentTokenInWritable();
    }

    @Test
    void unPausesToken() {
        pauseKnownToken();
        assertTrue(writableStore.get(tokenId.tokenNum()).get().paused());

        subject.handle(tokenUnpauseTxn, new UnPauseTokenRecordBuilder(), writableStore);

        final var unpausedToken = writableStore.get(tokenId.tokenNum()).get();
        assertFalse(unpausedToken.paused());
    }

    @Test
    void unPausesTokenFailsIfInvalidToken() {
        pauseKnownToken();
        assertTrue(writableStore.get(tokenId.tokenNum()).get().paused());
        givenInvalidTokenInTxn();

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(tokenUnpauseTxn, new UnPauseTokenRecordBuilder(), writableStore));
        assertEquals(INVALID_TOKEN_ID, msg.getStatus());
    }

    @Test
    void failsForNullArguments() {
        assertThrows(
                NullPointerException.class, () -> subject.handle(null, new UnPauseTokenRecordBuilder(), writableStore));
        assertThrows(NullPointerException.class, () -> subject.handle(tokenUnpauseTxn, null, writableStore));
        assertThrows(
                NullPointerException.class,
                () -> subject.handle(tokenUnpauseTxn, new UnPauseTokenRecordBuilder(), null));
    }

    private void givenValidTxn() {
        tokenUnpauseTxn = TransactionBody.newBuilder()
                .tokenUnpause(TokenUnpauseTransactionBody.newBuilder().token(tokenId))
                .build();
    }

    private void givenInvalidTokenInTxn() {
        tokenUnpauseTxn = TransactionBody.newBuilder()
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
