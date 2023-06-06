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
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_KNOWN_TOKEN;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_MISSING_TOKEN_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenDeleteHandler;
import com.hedera.node.app.service.token.impl.util.IdConvenienceUtils;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenDeleteHandlerTest extends ParityTestBase {
    private static final AccountID ACCOUNT_1339 = IdConvenienceUtils.fromAccountNum(1339);

    private final TokenDeleteHandler subject = new TokenDeleteHandler();

    @Nested
    class PreHandleTests {
        @Test
        void noTokenThrowsError() {
            final var txn = newDissociateTxn(null);

            assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_TOKEN_ID));
        }

        @Test
        void tokenDeletionWithValidTokenScenario() throws PreCheckException {
            final var theTxn = txnFrom(DELETE_WITH_KNOWN_TOKEN);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            subject.preHandle(context);

            Assertions.assertThat(context.payerKey()).isEqualTo(DEFAULT_PAYER_KT.asPbjKey());
            Assertions.assertThat(context.requiredNonPayerKeys()).containsExactly(TOKEN_ADMIN_KT.asPbjKey());
        }

        @Test
        void tokenDeletionWithMissingTokenScenario() throws PreCheckException {
            final var theTxn = txnFrom(DELETE_WITH_MISSING_TOKEN);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
        }

        @Test
        void tokenDeletionWithTokenWithoutAnAdminKeyScenario() throws PreCheckException {
            final var theTxn = txnFrom(DELETE_WITH_MISSING_TOKEN_ADMIN_KEY);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            subject.preHandle(context);

            Assertions.assertThat(context.payerKey()).isEqualTo(DEFAULT_PAYER_KT.asPbjKey());
            Assertions.assertThat(context.requiredNonPayerKeys()).isEmpty();
        }
    }

    private TransactionBody newDissociateTxn(TokenID token) {
        TokenDeleteTransactionBody.Builder deleteTokenTxnBodyBuilder = TokenDeleteTransactionBody.newBuilder();
        if (token != null) deleteTokenTxnBodyBuilder.token(token);
        return TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(ACCOUNT_1339).build())
                .tokenDeletion(deleteTokenTxnBodyBuilder)
                .build();
    }
}
