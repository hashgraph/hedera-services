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

import static com.hedera.node.app.service.token.impl.test.handlers.AdapterUtils.txnFrom;
import static com.hedera.test.factories.scenarios.TokenKycGrantScenarios.VALID_GRANT_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_KYC;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestoredToPbj;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.entity.AccountImpl;
import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;
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
    private AccountAccess accountAccess;

    @Test
    void tokenValidGrantWithExtantTokenScenario() throws PreCheckException {
        final var payerAcct = newPayerAccount();
        given(accountAccess.getAccountById(TEST_DEFAULT_PAYER)).willReturn(payerAcct);
        final var theTxn = txnFrom(VALID_GRANT_WITH_EXTANT_TOKEN);
        final var readableStore = mockKnownKycTokenStore();

        final var context = new PreHandleContext(accountAccess, theTxn);
        subject.preHandle(context, readableStore);

        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.requiredNonPayerKeys()), contains(TOKEN_KYC_KT.asPbjKey()));
    }

    private AccountImpl newPayerAccount() {
        return new AccountImpl(
                0,
                null,
                payerHederaKey,
                0,
                0,
                "test payer",
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                0,
                0,
                0);
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
        return new ReadableTokenStore(readableStates);
    }

    @Nested
    class HandleTests {
        @Mock
        private WritableTokenRelationStore tokenRelStore;

        @Test
        @DisplayName("Any null input argument should throw an exception")
        @SuppressWarnings("DataFlowIssue")
        void nullArgsThrowException() {
            assertThatThrownBy(() -> subject.handle(null, tokenRelStore)).isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> subject.handle(mock(TransactionBody.class), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When op tokenGrantKyc is null, tokenGrantKycOrThrow throws an exception")
        void nullTokenGrantKycThrowsException() {
            final var txnBody = TransactionBody.newBuilder().build();

            assertThatThrownBy(() -> subject.handle(txnBody, tokenRelStore)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When op token ID is null, tokenOrThrow throws an exception")
        void nullTokenIdThrowsException() {
            final var txnBody = newTxnBody(true, false);

            assertThatThrownBy(() -> subject.handle(txnBody, tokenRelStore)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When op account ID is null, accountOrThrow throws an exception")
        void nullAccountIdThrowsException() {
            final var txnBody = newTxnBody(false, true);

            assertThatThrownBy(() -> subject.handle(txnBody, tokenRelStore)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When getForModify returns empty, should not put or commit")
        void emptyGetForModifyShouldNotPersist() {
            given(tokenRelStore.getForModify(anyLong(), anyLong())).willReturn(Optional.empty());

            final var txnBody = newTxnBody(true, true);
            assertThatThrownBy(() -> subject.handle(txnBody, tokenRelStore)).isInstanceOf(NoSuchElementException.class);

            verify(tokenRelStore, never()).put(any(TokenRelation.class));
            verify(tokenRelStore, never()).commit();
        }

        @Test
        @DisplayName("Valid inputs should grant KYC and commit changes")
        void kycGrantedAndPersisted() {
            final var stateTokenRel =
                    newTokenRelationBuilder().kycGranted(false).build();
            given(tokenRelStore.getForModify(tokenId.tokenNum(), payerId.accountNumOrThrow()))
                    .willReturn(Optional.of(stateTokenRel));

            final var txnBody = newTxnBody(true, true);
            subject.handle(txnBody, tokenRelStore);

            verify(tokenRelStore).put(newTokenRelationBuilder().kycGranted(true).build());
            verify(tokenRelStore).commit();
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
