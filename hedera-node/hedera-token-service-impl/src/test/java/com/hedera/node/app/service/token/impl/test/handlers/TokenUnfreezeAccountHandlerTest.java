/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.test.handlers.util.AdapterUtils.txnFrom;
import static com.hedera.node.app.service.token.impl.test.util.MetaAssertion.basicContextAssertions;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_INVALID_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_MISSING_FREEZE_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.VALID_UNFREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_NO_SPECIAL_KEYS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FREEZE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.spi.fixtures.Assertions;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

class TokenUnfreezeAccountHandlerTest {

    private static final AccountID ACCOUNT_13257 =
            AccountID.newBuilder().accountNum(13257L).build();
    private TokenUnfreezeAccountHandler subject;

    @BeforeEach
    void setUp() {
        subject = new TokenUnfreezeAccountHandler();
    }

    @Nested
    class PreHandleTests {
        private ReadableAccountStore accountStore;
        private ReadableTokenStore tokenStore;

        @BeforeEach
        void setUp() {
            accountStore = SigReqAdapterUtils.wellKnownAccountStoreAt();
            tokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt();
        }

        @Test
        void tokenUnfreezeWithNoToken() throws PreCheckException {
            final var theTxn = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_13257))
                    .tokenUnfreeze(
                            TokenUnfreezeAccountTransactionBody.newBuilder().account(ACCOUNT_13257))
                    .build();

            final var context = new FakePreHandleContext(accountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            Assertions.assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
        }

        @Test
        void tokenUnfreezeWithNoAccount() throws PreCheckException {
            final var theTxn = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_13257))
                    .tokenUnfreeze(TokenUnfreezeAccountTransactionBody.newBuilder()
                            .token(TokenID.newBuilder().tokenNum(123L)))
                    .build();

            final var context = new FakePreHandleContext(accountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            Assertions.assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class HandleTests {
        private static final TokenID MISSING_TOKEN_97531 =
                TokenID.newBuilder().tokenNum(97531).build();

        @Mock(strictness = Strictness.LENIENT)
        private HandleContext context;

        @Mock
        private ReadableTokenStore tokenStore;

        @Mock
        private ReadableAccountStore accountStore;

        @Mock
        private WritableTokenRelationStore tokenRelStore;

        @Mock
        private ExpiryValidator expiryValidator;

        @BeforeEach
        void setup() {
            given(context.readableStore(ReadableTokenStore.class)).willReturn(tokenStore);
            given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(tokenRelStore);
            given(context.expiryValidator()).willReturn(expiryValidator);
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void nullArgThrowsException() {
            assertThatThrownBy(() -> subject.handle(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void tokenNotPresentInTxnBody() {
            final var noTokenTxn = newUnfreezeTxn(null, ACCOUNT_13257);
            given(context.body()).willReturn(noTokenTxn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_TOKEN_ID));
            verifyNoPut();
        }

        @Test
        void accountNotPresentInTxnBody() {
            final var pbjToken = toPbj(KNOWN_TOKEN_WITH_FREEZE);
            final var noAcctTxn = newUnfreezeTxn(pbjToken, null);
            given(tokenStore.get(pbjToken))
                    .willReturn(Token.newBuilder().tokenId(pbjToken).build());
            given(context.body()).willReturn(noAcctTxn);
            given(tokenStore.getTokenMeta(pbjToken)).willReturn(tokenMetaWithFreezeKey());

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
            verifyNoPut();
        }

        @Test
        void tokenMetaNotFound() {
            final var token = MISSING_TOKEN_97531;
            given(tokenStore.get(token))
                    .willReturn(Token.newBuilder().tokenId(token).build());
            given(tokenStore.getTokenMeta(token)).willReturn(null);
            final var txn = newUnfreezeTxn(token);
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_TOKEN_ID));
            verifyNoPut();
        }

        @Test
        void tokenHasNoFreezeKey() {
            final var token = toPbj(KNOWN_TOKEN_NO_SPECIAL_KEYS);
            given(tokenStore.get(token))
                    .willReturn(Token.newBuilder().tokenId(token).build());
            given(tokenStore.getTokenMeta(token)).willReturn(tokenMetaWithFreezeKey(null));
            final var txn = newUnfreezeTxn(token);
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_HAS_NO_FREEZE_KEY));
            verifyNoPut();
        }

        @Test
        void accountNotFound() {
            final var token = toPbj(KNOWN_TOKEN_WITH_FREEZE);
            given(tokenStore.get(token))
                    .willReturn(Token.newBuilder().tokenId(token).build());
            given(tokenStore.getTokenMeta(token)).willReturn(tokenMetaWithFreezeKey());
            given(accountStore.getAccountById(ACCOUNT_13257)).willReturn(null);
            final var txn = newUnfreezeTxn(token);
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
            verifyNoPut();
        }

        @Test
        void tokenRelNotFound() throws HandleException {
            final var token = toPbj(KNOWN_TOKEN_WITH_FREEZE);
            given(tokenStore.get(token))
                    .willReturn(Token.newBuilder().tokenId(token).build());
            given(tokenStore.getTokenMeta(token)).willReturn(tokenMetaWithFreezeKey());
            given(accountStore.getAccountById(ACCOUNT_13257))
                    .willReturn(Account.newBuilder().accountId(ACCOUNT_13257).build());
            given(tokenRelStore.getForModify(ACCOUNT_13257, token)).willReturn(null);
            given(expiryValidator.expirationStatus(EntityType.ACCOUNT, false, 0))
                    .willReturn(OK);
            final var txn = newUnfreezeTxn(token);
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
            verifyNoPut();
        }

        @Test
        void tokenNotFound() throws HandleException {
            final var token = toPbj(KNOWN_TOKEN_WITH_FREEZE);
            given(tokenStore.get(token)).willReturn(null);
            final var txn = newUnfreezeTxn(token);
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_TOKEN_ID));
            verifyNoPut();
        }

        @Test
        void tokenDeleted() throws HandleException {
            final var token = toPbj(KNOWN_TOKEN_WITH_FREEZE);
            given(tokenStore.get(token))
                    .willReturn(Token.newBuilder().deleted(true).build());
            final var txn = newUnfreezeTxn(token);
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_WAS_DELETED));
            verifyNoPut();
        }

        @Test
        void tokenPaused() throws HandleException {
            final var token = toPbj(KNOWN_TOKEN_WITH_FREEZE);
            given(tokenStore.get(token))
                    .willReturn(Token.newBuilder()
                            .tokenId(token)
                            .freezeKey(FIRST_TOKEN_SENDER_KT.asPbjKey())
                            .paused(true)
                            .build());
            final var txn = newUnfreezeTxn(token);
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_IS_PAUSED));
            verifyNoPut();
        }

        @Test
        void tokenRelUnfreezeSuccessful() {
            final var token = toPbj(KNOWN_TOKEN_WITH_FREEZE);
            given(tokenStore.getTokenMeta(token)).willReturn(tokenMetaWithFreezeKey());
            given(accountStore.getAccountById(ACCOUNT_13257))
                    .willReturn(Account.newBuilder().accountId(ACCOUNT_13257).build());
            given(tokenRelStore.getForModify(ACCOUNT_13257, token))
                    .willReturn(TokenRelation.newBuilder()
                            .tokenId(token)
                            .accountId(ACCOUNT_13257)
                            .build());
            given(tokenStore.get(token))
                    .willReturn(Token.newBuilder().deleted(false).build());
            given(expiryValidator.expirationStatus(EntityType.ACCOUNT, false, 0))
                    .willReturn(OK);
            final var txn = newUnfreezeTxn(token);
            given(context.body()).willReturn(txn);

            subject.handle(context);
            verify(tokenRelStore)
                    .put(TokenRelation.newBuilder()
                            .tokenId(token)
                            .accountId(ACCOUNT_13257)
                            .frozen(false)
                            .build());
        }

        private void verifyNoPut() {
            verify(tokenRelStore, never()).put(any());
        }

        private ReadableTokenStore.TokenMetadata tokenMetaWithFreezeKey() {
            return tokenMetaWithFreezeKey(SECOND_TOKEN_SENDER_KT.asPbjKey());
        }

        private ReadableTokenStore.TokenMetadata tokenMetaWithFreezeKey(Key freezeKey) {
            return new ReadableTokenStore.TokenMetadata(
                    null, null, null, freezeKey, null, null, null, null, false, asAccount(25L), 2);
        }

        private TransactionBody newUnfreezeTxn(TokenID token) {
            return newUnfreezeTxn(token, ACCOUNT_13257);
        }

        private TransactionBody newUnfreezeTxn(TokenID token, AccountID account) {
            TokenUnfreezeAccountTransactionBody.Builder unfreezeTxnBodyBuilder =
                    TokenUnfreezeAccountTransactionBody.newBuilder();
            if (token != null) unfreezeTxnBodyBuilder.token(token);
            if (account != null) unfreezeTxnBodyBuilder.account(account);
            return TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_13257).build())
                    .tokenUnfreeze(unfreezeTxnBodyBuilder)
                    .build();
        }
    }

    @Nested
    class TokenUnfreezeAccountHandlerParityTest {
        private ReadableAccountStore accountStore;
        private ReadableTokenStore tokenStore;

        @BeforeEach
        void setUp() {
            accountStore = SigReqAdapterUtils.wellKnownAccountStoreAt();
            tokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt();
        }

        @Test
        void tokenUnfreezeWithExtantFreezable() throws PreCheckException {
            final var txn = txnFrom(VALID_UNFREEZE_WITH_EXTANT_TOKEN);

            final var context = new FakePreHandleContext(accountStore, txn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            subject.preHandle(context);

            assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
            assertThat(context.requiredNonPayerKeys(), contains(TOKEN_FREEZE_KT.asPbjKey()));
            basicContextAssertions(context, 1);
        }

        @Test
        void tokenUnfreezeMissingToken() throws PreCheckException {
            final var txn = txnFrom(UNFREEZE_WITH_MISSING_FREEZE_TOKEN);

            final var context = new FakePreHandleContext(accountStore, txn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            assertThrowsPreCheck(() -> subject.preHandle(context), TOKEN_HAS_NO_FREEZE_KEY);
        }

        @Test
        void tokenUnfreezeWithInvalidToken() throws PreCheckException {
            final var txn = txnFrom(UNFREEZE_WITH_INVALID_TOKEN);

            final var context = new FakePreHandleContext(accountStore, txn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
        }
    }
}
