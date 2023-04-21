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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.AdapterUtils.txnFrom;
import static com.hedera.node.app.service.token.impl.test.util.MetaAssertion.basicContextAssertions;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.REVOKE_FOR_TOKEN_WITHOUT_KYC;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.REVOKE_WITH_INVALID_TOKEN;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.REVOKE_WITH_MISSING_TXN_BODY;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.VALID_REVOKE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.TokenRevokeKycFromAccountHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenRevokeKycFromAccountHandlerTest {
    private static final TokenID TOKEN_10 = TokenID.newBuilder().tokenNum(10).build();
    private static final AccountID ACCOUNT_100 =
            AccountID.newBuilder().accountNum(100).build();

    private AccountAccess accountStore;
    private ReadableTokenStore tokenStore;
    private TokenRevokeKycFromAccountHandler subject;

    @BeforeEach
    void setUp() {
        accountStore = SigReqAdapterUtils.wellKnownAccountStoreAt();
        tokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt();
        subject = new TokenRevokeKycFromAccountHandler();
    }

    @Nested
    class PreHandleTests {
        @Test
        void tokenRevokeKycWithExtant() throws PreCheckException {
            final var txn = txnFrom(VALID_REVOKE_WITH_EXTANT_TOKEN);

            final var context = new PreHandleContext(accountStore, txn);
            subject.preHandle(context, tokenStore);

            assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
            assertThat(context.requiredNonPayerKeys(), contains(TOKEN_KYC_KT.asPbjKey()));
            basicContextAssertions(context, 1);
        }

        @Test
        void tokenRevokeMissingTxnBody() throws PreCheckException {
            final var txn = txnFrom(REVOKE_WITH_MISSING_TXN_BODY);

            final var context = new PreHandleContext(accountStore, txn);
            assertThrows(NullPointerException.class, () -> subject.preHandle(context, tokenStore));
        }

        @Test
        void tokenRevokeKycWithInvalidToken() throws PreCheckException {
            final var txn = txnFrom(REVOKE_WITH_INVALID_TOKEN);

            final var context = new PreHandleContext(accountStore, txn);
            assertThrowsPreCheck(() -> subject.preHandle(context, tokenStore), INVALID_TOKEN_ID);
            assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
            assertTrue(context.requiredNonPayerKeys().isEmpty());
        }

        @Test
        void tokenRevokeKycWithoutKyc() throws PreCheckException {
            final var txn = txnFrom(REVOKE_FOR_TOKEN_WITHOUT_KYC);

            final var context = new PreHandleContext(accountStore, txn);
            assertThrowsPreCheck(() -> subject.preHandle(context, tokenStore), TOKEN_HAS_NO_KYC_KEY);
            assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
            assertTrue(context.requiredNonPayerKeys().isEmpty());
        }
    }

    @Nested
    class HandleTests {
        private WritableTokenRelationStore tokenRelStore;

        @BeforeEach
        void setUp() {
            tokenRelStore = mock(WritableTokenRelationStore.class);
        }

        @Test
        @DisplayName("Any null input argument should throw an exception")
        @SuppressWarnings("DataFlowIssue")
        void nullArgsThrowException() {
            assertThatThrownBy(() -> subject.handle(null, tokenRelStore)).isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> subject.handle(mock(TransactionBody.class), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When op tokenRevokeKyc is null, tokenRevokeKycOrThrow throws an " + "exception")
        void nullTokenRevokeKycThrowsException() {
            final var txnBody = TransactionBody.newBuilder().build();

            assertThatThrownBy(() -> subject.handle(txnBody, tokenRelStore)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("When op token ID is null, tokenOrThrow throws an exception")
        void nullTokenIdThrowsException() {
            final var txnBody = newTxnBody(false, true);

            try {
                subject.handle(txnBody, tokenRelStore);
            } catch (HandleException result) {
                Assertions.assertThat(result.getStatus()).isEqualTo(INVALID_TOKEN_ID);
            } catch (Exception result) {
                fail("Expected HandleException, got " + result.getClass().getSimpleName());
            }
        }

        @Test
        @DisplayName("When op account ID is null, accountOrThrow throws an exception")
        void nullAccountIdThrowsException() {
            final var txnBody = newTxnBody(true, false);

            try {
                subject.handle(txnBody, tokenRelStore);
            } catch (HandleException result) {
                Assertions.assertThat(result.getStatus()).isEqualTo(INVALID_ACCOUNT_ID);
            } catch (Exception result) {
                fail("Expected HandleException, got " + result.getClass().getSimpleName());
            }
        }

        @Test
        @DisplayName("When getForModify returns empty, should not put or commit")
        void emptyGetForModifyShouldNotPersist() {
            given(tokenRelStore.getForModify(anyLong(), anyLong())).willReturn(Optional.empty());

            final var txnBody = newTxnBody(true, true);
            assertThatThrownBy(() -> subject.handle(txnBody, tokenRelStore)).isInstanceOf(NoSuchElementException.class);

            verify(tokenRelStore, never()).put(any(TokenRelation.class));
        }

        @Test
        @DisplayName("Valid inputs should grant KYC and commit changes")
        void kycRevokedAndPersisted() {
            final var stateTokenRel = newTokenRelationBuilder()
                    .tokenNumber(TOKEN_10.tokenNum())
                    .accountNumber(ACCOUNT_100.accountNumOrThrow())
                    .kycGranted(true)
                    .build();
            given(tokenRelStore.getForModify(TOKEN_10.tokenNum(), ACCOUNT_100.accountNumOrThrow()))
                    .willReturn(Optional.of(stateTokenRel));

            final var txnBody = newTxnBody(true, true);
            subject.handle(txnBody, tokenRelStore);

            verify(tokenRelStore)
                    .put(newTokenRelationBuilder().kycGranted(false).build());
        }

        private TokenRelation.Builder newTokenRelationBuilder() {
            return TokenRelation.newBuilder()
                    .tokenNumber(TOKEN_10.tokenNum())
                    .accountNumber(ACCOUNT_100.accountNumOrThrow());
        }

        private TransactionBody newTxnBody(final boolean tokenPresent, final boolean accountPresent) {
            TokenRevokeKycTransactionBody.Builder builder = TokenRevokeKycTransactionBody.newBuilder();
            if (tokenPresent) {

                builder.token(TOKEN_10);
            }
            if (accountPresent) {
                builder.account(ACCOUNT_100);
            }
            return TransactionBody.newBuilder()
                    .tokenRevokeKyc(builder.build())
                    .memo(this.getClass().getName() + System.currentTimeMillis())
                    .build();
        }
    }
}
