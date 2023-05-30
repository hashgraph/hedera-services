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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.AdapterUtils.mockStates;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.TokenDissociateFromAccountHandler;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.AutoRenewConfig;
import com.swirlds.config.api.Configuration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenDissociateFromAccountHandlerTest extends ParityTestBase {
    private static final AccountID ACCOUNT_1339 =
            AccountID.newBuilder().accountNum(MISC_ACCOUNT.getAccountNum()).build();
    private static final TokenID TOKEN_555 = TokenID.newBuilder().tokenNum(555).build();
    private static final TokenID TOKEN_666 = TokenID.newBuilder().tokenNum(666).build();

    private final TokenDissociateFromAccountHandler subject = new TokenDissociateFromAccountHandler();

    @Nested
    class PreHandleTests {
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
    }

    @Nested
    class HandleTests {
        @Test
        void rejectsNonexistingAccount() {
            final var context = mockContext();
            final var txn = newDissociateTxn(AccountID.newBuilder().build(), List.of(TOKEN_555));
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
        }

        @Test
        void rejectsExpiredAccount() {
            // Create and commit an account that is expired
            final var accountNumber = 12345L;
            writableAccountStore.put(Account.newBuilder()
                    .accountNumber(accountNumber)
                    .expiredAndPendingRemoval(true)
                    .deleted(false)
                    .build());
            writableAccountStore.commit();

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(
                    AccountID.newBuilder().accountNum(accountNumber).build(), List.of(TOKEN_555));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
        }

        @Test
        void rejectsDeletedAccount() {
            // Create and commit an account that is deleted
            final var accountNumber = 53135;
            writableAccountStore.put(Account.newBuilder()
                    .accountNumber(accountNumber)
                    .expiredAndPendingRemoval(false)
                    .deleted(true)
                    .build());
            writableAccountStore.commit();

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(
                    AccountID.newBuilder().accountNum(accountNumber).build(), List.of(TOKEN_555));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ACCOUNT_DELETED));
        }

        @Test
        void rejectsNonexistingTokenRel() {
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
        }

        @Test
        void rejectsPausedToken() {
            // Create a readable store with a paused token
            final var pausedToken = Token.newBuilder()
                    .tokenNumber(TOKEN_555.tokenNum())
                    .paused(true)
                    .build();
            readableTokenStore = newReadableStoreWithTokens(pausedToken);

            // Create the token rel for the paused token
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555.tokenNum())
                    .build());
            writableTokenRelStore.commit();

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_IS_PAUSED));
        }

        @Test // @todo: implement
        void acceptsTokenWithTreasury() {}

        @Test // @todo: implement
        void acceptsTokenWithoutTreasury() {}

        @Test // @todo: implement
        void acceptsDeletedToken() {}

        private ReadableTokenStore newReadableStoreWithTokens(Token... tokens) {
            final var backingMap = new HashMap<EntityNum, Token>();
            for (final Token token : tokens) {
                backingMap.put(
                        EntityNum.fromTokenId(fromPbj(TokenID.newBuilder()
                                .tokenNum(token.tokenNumber())
                                .build())),
                        token);
            }

            final var wrappingState = new MapWritableKVState<>(TOKENS_KEY, backingMap);
            return new ReadableTokenStoreImpl(mockStates(Map.of(TOKENS_KEY, wrappingState)));
        }
    }

    private HandleContext mockContext() {
        return mockContext(true, true);
    }

    private HandleContext mockContext(final boolean renewAccounts, final boolean renewContracts) {
        final var handleContext = mock(HandleContext.class);
        mockConfig(handleContext, renewAccounts, renewContracts);

        lenient().when(handleContext.writableStore(WritableAccountStore.class)).thenReturn(writableAccountStore);
        lenient().when(handleContext.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        lenient()
                .when(handleContext.writableStore(WritableTokenRelationStore.class))
                .thenReturn(writableTokenRelStore);

        return handleContext;
    }

    private void mockConfig(
            final HandleContext mockedContext, final boolean renewAccounts, final boolean renewContracts) {
        final var config = mock(Configuration.class);
        lenient().when(mockedContext.configuration()).thenReturn(config);

        final var autoRenewConfig = mock(AutoRenewConfig.class);
        lenient().when(autoRenewConfig.shouldAutoRenewAccounts()).thenReturn(renewAccounts);
        lenient().when(autoRenewConfig.shouldAutoRenewContracts()).thenReturn(renewContracts);
        lenient().when(config.getConfigData(AutoRenewConfig.class)).thenReturn(autoRenewConfig);
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
