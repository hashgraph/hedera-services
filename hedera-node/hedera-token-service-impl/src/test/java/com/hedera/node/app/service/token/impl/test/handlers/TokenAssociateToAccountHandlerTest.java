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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenAssociateToAccountHandlerTest {
    private static final AccountID ACCOUNT_888 =
            AccountID.newBuilder().accountNum(888).build();
    private static final AccountID ACCOUNT_1216 =
            AccountID.newBuilder().accountNum(1216).build();
    private static final TokenID TOKEN_300 = TokenID.newBuilder().tokenNum(300).build();
    private static final TokenID TOKEN_400 = TokenID.newBuilder().tokenNum(400).build();

    private TokenAssociateToAccountHandler subject;

    @BeforeEach
    void setUp() {
        subject = new TokenAssociateToAccountHandler();
    }

    @Nested
    class PreHandleTests extends ParityTestBase {
        @SuppressWarnings("DataFlowIssue")
        @Test
        void nullArgsThrows() {
            assertThatThrownBy(() -> subject.preHandle(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void txnWithoutAccountThrows() throws PreCheckException {
            final var txn = newAssociateTxn(null, List.of(TOKEN_300));
            final var context = new FakePreHandleContext(readableAccountStore, txn);

            assertThatThrownBy(() -> subject.preHandle(context))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
        }

        @Test
        void txnWithRepeatedTokenIdsThrows() throws PreCheckException {
            final var txn = newAssociateTxn(ACCOUNT_888, List.of(TOKEN_300, TOKEN_400, TOKEN_300));
            final var context = new FakePreHandleContext(readableAccountStore, txn);

            assertThatThrownBy(() -> subject.preHandle(context))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(TOKEN_ID_REPEATED_IN_TOKEN_LIST));
        }

        @Test
        void txnWithEmptyTokenIdsSucceeds() throws PreCheckException {
            final var txn = newAssociateTxn(ACCOUNT_888, Collections.emptyList());
            final var context = new FakePreHandleContext(readableAccountStore, txn);

            subject.preHandle(context);
            Assertions.assertThat(context.requiredNonPayerKeys()).isNotEmpty();
        }

        private TransactionBody newAssociateTxn(AccountID account, List<TokenID> tokens) {
            TokenAssociateTransactionBody.Builder associateTxnBodyBuilder = TokenAssociateTransactionBody.newBuilder();
            if (tokens != null) associateTxnBodyBuilder.tokens(tokens);
            if (account != null) associateTxnBodyBuilder.account(account);
            return TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_1216).build())
                    .tokenAssociate(associateTxnBodyBuilder)
                    .build();
        }
    }

    @Nested
    class AssociateParityTests extends ParityTestBase {
        @Test
        void tokenAssociateWithKnownTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_KNOWN_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            subject.preHandle(context);

            assertEquals(1, context.requiredNonPayerKeys().size());
            assertThat(context.requiredNonPayerKeys(), Matchers.contains(MISC_ACCOUNT_KT.asPbjKey()));
        }

        @Test
        void tokenAssociateWithSelfPaidKnownTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            subject.preHandle(context);

            assertEquals(0, context.requiredNonPayerKeys().size());
        }

        @Test
        void tokenAssociateWithCustomPaidKnownTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            subject.preHandle(context);

            assertEquals(1, context.requiredNonPayerKeys().size());
            assertThat(context.requiredNonPayerKeys(), Matchers.contains(CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
        }

        @Test
        void tokenAssociateWithImmutableTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
        }

        @Test
        void tokenAssociateWithMissingTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_MISSING_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
        }
    }
}
