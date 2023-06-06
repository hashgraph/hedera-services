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
import static com.hedera.node.app.service.token.impl.test.handlers.util.AdapterUtils.txnFrom;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.factories.scenarios.TokenKycGrantScenarios.VALID_GRANT_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_KYC;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.TokenHandlerTestBase;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.util.Collections;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenGrantKycToAccountHandlerTest extends TokenHandlerTestBase {
    private final TokenGrantKycToAccountHandler subject = new TokenGrantKycToAccountHandler();

    @Mock
    private ReadableAccountStore accountStore;

    @Test
    void tokenValidGrantWithExtantTokenScenario() throws PreCheckException {
        final var payerAcct = newPayerAccount();
        given(accountStore.getAccountById(TEST_DEFAULT_PAYER)).willReturn(payerAcct);
        final var theTxn = txnFrom(VALID_GRANT_WITH_EXTANT_TOKEN);
        final var readableStore = mockKnownKycTokenStore();

        final var context = new FakePreHandleContext(accountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableStore);
        subject.preHandle(context);

        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_KYC_KT.asPbjKey()));
    }

    @Test
    void txnHasNoToken() throws PreCheckException {
        final var payerAcct = newPayerAccount();
        given(accountStore.getAccountById(TEST_DEFAULT_PAYER)).willReturn(payerAcct);
        final var missingTokenTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(TEST_DEFAULT_PAYER))
                .tokenGrantKyc(TokenGrantKycTransactionBody.newBuilder()
                        .account(AccountID.newBuilder().accountNum(1L))
                        .build())
                .build();

        final var context = new FakePreHandleContext(accountStore, missingTokenTxn);
        Assertions.assertThatThrownBy(() -> subject.preHandle(context))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TOKEN_ID));
    }

    @Test
    void txnHasNoAccount() throws PreCheckException {
        final var payerAcct = newPayerAccount();
        given(accountStore.getAccountById(TEST_DEFAULT_PAYER)).willReturn(payerAcct);
        final var missingAcctTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(TEST_DEFAULT_PAYER))
                .tokenGrantKyc(
                        TokenGrantKycTransactionBody.newBuilder().token(tokenId).build())
                .build();

        final var context = new FakePreHandleContext(accountStore, missingAcctTxn);
        Assertions.assertThatThrownBy(() -> subject.preHandle(context))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_ID));
    }

    private ReadableTokenStore mockKnownKycTokenStore() {
        final var tokenNum = KNOWN_TOKEN_WITH_KYC.getTokenNum();
        final var storedToken = new Token(
                tokenNum,
                "Test_KnownKycToken" + System.currentTimeMillis(),
                "KYC",
                10,
                10,
                treasury.accountNumOrThrow(),
                null,
                TOKEN_KYC_KT.asPbjKey(),
                null,
                null,
                null,
                null,
                null,
                0,
                false,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                -1,
                autoRenewSecs,
                expirationTime,
                memo,
                100000,
                false,
                false,
                false,
                Collections.emptyList());
        final var readableState = MapReadableKVState.<EntityNum, Token>builder(TOKENS)
                .value(EntityNum.fromLong(tokenNum), storedToken)
                .build();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableState);
        return new ReadableTokenStoreImpl(readableStates);
    }

    @Nested
    class HandleTests {
        @Mock
        private WritableTokenRelationStore tokenRelStore;

        @Mock(strictness = LENIENT)
        private HandleContext handleContext;

        @BeforeEach
        void setup() {
            given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(tokenRelStore);
        }

        @Test
        @DisplayName("Any null input argument should throw an exception")
        @SuppressWarnings("DataFlowIssue")
        void nullArgsThrowException() {
            assertThatThrownBy(() -> subject.handle(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When op tokenGrantKyc is null, tokenGrantKycOrThrow throws an exception")
        void nullTokenGrantKycThrowsException() {
            final var txnBody = TransactionBody.newBuilder().build();

            assertThatThrownBy(() -> subject.handle(handleContext)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When op token ID is null, tokenOrThrow throws an exception")
        void nullTokenIdThrowsException() {
            final var txnBody = newTxnBody(true, false);

            assertThatThrownBy(() -> subject.handle(handleContext)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When op account ID is null, accountOrThrow throws an exception")
        void nullAccountIdThrowsException() {
            final var txnBody = newTxnBody(false, true);
            given(handleContext.body()).willReturn(txnBody);

            assertThatThrownBy(() -> subject.handle(handleContext)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When getForModify returns empty, should not put or commit")
        void emptyGetForModifyShouldNotPersist() {
            given(tokenRelStore.getForModify(notNull(), notNull())).willReturn(Optional.empty());

            final var txnBody = newTxnBody(true, true);
            given(handleContext.body()).willReturn(txnBody);
            assertThatThrownBy(() -> subject.handle(handleContext))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_TOKEN_ID));

            verify(tokenRelStore, never()).put(any(TokenRelation.class));
        }

        @Test
        @DisplayName("Valid inputs should grant KYC and commit changes")
        void kycGrantedAndPersisted() {
            final var stateTokenRel =
                    newTokenRelationBuilder().kycGranted(false).build();
            given(tokenRelStore.getForModify(payerId, tokenId)).willReturn(Optional.of(stateTokenRel));

            final var txnBody = newTxnBody(true, true);
            given(handleContext.body()).willReturn(txnBody);
            subject.handle(handleContext);

            verify(tokenRelStore).put(newTokenRelationBuilder().kycGranted(true).build());
        }

        private TokenRelation.Builder newTokenRelationBuilder() {
            return TokenRelation.newBuilder()
                    .tokenNumber(token.tokenNumber())
                    .accountNumber(payerId.accountNumOrThrow());
        }

        private TransactionBody newTxnBody(final boolean tokenPresent, final boolean accountPresent) {
            TokenGrantKycTransactionBody.Builder builder = TokenGrantKycTransactionBody.newBuilder();
            if (tokenPresent) {
                builder.token(tokenId);
            }
            if (accountPresent) {
                builder.account(payerId);
            }
            return TransactionBody.newBuilder()
                    .tokenGrantKyc(builder.build())
                    .memo(this.getClass().getName() + System.currentTimeMillis())
                    .build();
        }
    }
}
