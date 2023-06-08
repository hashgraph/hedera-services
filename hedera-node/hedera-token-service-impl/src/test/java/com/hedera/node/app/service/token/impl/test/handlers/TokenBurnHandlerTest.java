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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.factories.scenarios.TokenBurnScenarios.BURN_FOR_TOKEN_WITHOUT_SUPPLY;
import static com.hedera.test.factories.scenarios.TokenBurnScenarios.BURN_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenBurnScenarios.BURN_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_SUPPLY_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenBurnHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.ParityTestBase;
import com.hedera.node.app.service.token.impl.util.IdConvenienceUtils;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenBurnHandlerTest extends ParityTestBase {
    private static final AccountID ACCOUNT_1339 = IdConvenienceUtils.fromAccountNum(1339);
    private static final TokenID TOKEN_123 = IdConvenienceUtils.fromTokenNum(123);
    private final TokenBurnHandler subject = new TokenBurnHandler();

    @Nested
    class PureChecks {
        @Test
        void noTokenPresent() {
            final var txn = newBurnTxn(null, 1);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn)).isInstanceOf(
                    PreCheckException.class).has(responseCode(INVALID_TOKEN_ID));
        }

        @Test
        void fungibleAndNonFungibleGiven() {
            final var txn = newBurnTxn(TOKEN_123, 1, 1L);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn)).isInstanceOf(
                    PreCheckException.class).has(responseCode(INVALID_TRANSACTION_BODY));
        }

        @Test
        void nonPositiveFungibleAmountGiven() {
            final var txn = newBurnTxn(TOKEN_123, -1);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn)).isInstanceOf(
                    PreCheckException.class).has(responseCode(INVALID_TOKEN_BURN_AMOUNT));
        }

        @Test
        void invalidNftSerialNumber() {
            final var txn = newBurnTxn(TOKEN_123, 0, 1L, 2L, 0L);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn)).isInstanceOf(
                    PreCheckException.class).has(responseCode(INVALID_TRANSACTION_BODY));
        }
    }

    @Nested
    // Tests that check prehandle parity with old prehandle code
    class PreHandleParity {

        @Test
        void parity_getsTokenBurnWithValidId() throws PreCheckException {
            final var theTxn = txnFrom(BURN_WITH_SUPPLY_KEYED_TOKEN);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            subject.preHandle(context);

            assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
            assertEquals(1, context.requiredNonPayerKeys().size());
            assertThat(context.requiredNonPayerKeys(), contains(TOKEN_SUPPLY_KT.asPbjKey()));
        }

        @Test
        void parity_getsTokenBurnWithMissingToken() throws PreCheckException {
            final var theTxn = txnFrom(BURN_WITH_MISSING_TOKEN);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
        }

        @Test
        void parity_getsTokenBurnWithoutSupplyKey() throws PreCheckException {
            final var theTxn = txnFrom(BURN_FOR_TOKEN_WITHOUT_SUPPLY);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            subject.preHandle(context);

            assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
            assertEquals(0, context.requiredNonPayerKeys().size());
        }
    }

    private TransactionBody newBurnTxn(TokenID token, long fungibleAmount, Long... nftSerialNums) {
        TokenBurnTransactionBody.Builder burnTxnBodyBuilder = TokenBurnTransactionBody.newBuilder();
        if (token != null) burnTxnBodyBuilder.token(token);
        burnTxnBodyBuilder.amount(fungibleAmount);
        burnTxnBodyBuilder.serialNumbers(nftSerialNums);
        return TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(ACCOUNT_1339).build())
                .tokenBurn(burnTxnBodyBuilder)
                .build();
    }
}
