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
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_SELF_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.TokenDissociateFromAccountHandler;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class TokenDissociateFromAccountHandlerTest extends ParityTestBase {
    private static final AccountID ACCOUNT_1339 =
            AccountID.newBuilder().accountNum(MISC_ACCOUNT.getAccountNum()).build();
    private static final TokenID TOKEN_555 = TokenID.newBuilder().tokenNum(555).build();
    private static final TokenID TOKEN_666 = TokenID.newBuilder().tokenNum(666).build();

    private final TokenDissociateFromAccountHandler subject = new TokenDissociateFromAccountHandler();

    @SuppressWarnings("DataFlowIssue")
    @Test
    void nullArgsThrows() {
        assertThatThrownBy(() -> subject.preHandle(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void pureChecksRejectsDissociateWithMissingAccount() {
        final var txn = newDissociateTxn(null, List.of(TOKEN_555));

        assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_ID));
    }

    @Test
    void pureChecksRejectsDissociateWithRepeatedTokenId() {
        final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555, TOKEN_666, TOKEN_555));

        assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(TOKEN_ID_REPEATED_IN_TOKEN_LIST));
    }

    @Test
    void tokenDissociateWithKnownTargetScenario() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_DISSOCIATE_WITH_KNOWN_TARGET);

        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context);

        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), Matchers.contains(MISC_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenDissociateWithSelfPaidKnownTargetScenario() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_DISSOCIATE_WITH_SELF_PAID_KNOWN_TARGET);

        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context);

        assertEquals(0, context.requiredNonPayerKeys().size());
    }

    @Test
    void tokenDissociateWithCustomPaidKnownTargetScenario() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_DISSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET);

        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context);

        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), Matchers.contains(CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenDissociateWithMissingTargetScenario() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_DISSOCIATE_WITH_MISSING_TARGET);

        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    private TransactionBody newDissociateTxn(AccountID account, List<TokenID> tokens) {
        TokenDissociateTransactionBody.Builder associateTxnBodyBuilder = TokenDissociateTransactionBody.newBuilder();
        if (account != null) associateTxnBodyBuilder.account(account);
        if (tokens != null) associateTxnBodyBuilder.tokens(tokens);
        return TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(ACCOUNT_1339).build())
                .tokenDissociate(associateTxnBodyBuilder)
                .build();
    }
}
