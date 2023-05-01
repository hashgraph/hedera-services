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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.test.factories.scenarios.TokenFreezeScenarios.VALID_FREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenFreezeAccountHandler;
import com.hedera.node.app.spi.fixtures.Assertions;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenFreezeAccountHandlerTest extends ParityTestBase {
    private static final AccountID ACCOUNT_13257 =
            AccountID.newBuilder().accountNum(13257).build();

    private final TokenFreezeAccountHandler subject = new TokenFreezeAccountHandler();

    @Nested
    class PreHandleTests {
        @Test
        void tokenFreezeWithExtantTokenScenario() throws PreCheckException {
            final var theTxn = txnFrom(VALID_FREEZE_WITH_EXTANT_TOKEN);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            subject.preHandle(context);

            assertEquals(1, context.requiredNonPayerKeys().size());
            assertThat(context.requiredNonPayerKeys(), contains(TOKEN_FREEZE_KT.asPbjKey()));
        }

        @Test
        void tokenFreezeWithNoToken() throws PreCheckException {
            final var theTxn = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_13257))
                    .tokenFreeze(TokenFreezeAccountTransactionBody.newBuilder().account(ACCOUNT_13257))
                    .build();

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            Assertions.assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
        }

        @Test
        void tokenFreezeWithNoAccount() throws PreCheckException {
            final var theTxn = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_13257))
                    .tokenFreeze(TokenFreezeAccountTransactionBody.newBuilder()
                            .token(TokenID.newBuilder().tokenNum(123L)))
                    .build();

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            Assertions.assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
        }
    }
}
