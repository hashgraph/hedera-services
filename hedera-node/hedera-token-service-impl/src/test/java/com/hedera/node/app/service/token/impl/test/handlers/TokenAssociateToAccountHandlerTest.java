/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELETED_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_IMMUTABLE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FREEZE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_KYC;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_PAUSE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_WIPE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.ParityTestBase;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.TokensConfig;
import com.swirlds.config.api.Configuration;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

class TokenAssociateToAccountHandlerTest {
    private static final AccountID ACCOUNT_888 =
            AccountID.newBuilder().accountNum(888).build();
    private static final AccountID ACCOUNT_1339 =
            AccountID.newBuilder().accountNum(MISC_ACCOUNT.getAccountNum()).build();
    private static final TokenID TOKEN_300 = TokenID.newBuilder().tokenNum(300).build();
    private static final TokenID TOKEN_400 = TokenID.newBuilder().tokenNum(400).build();

    private TokenAssociateToAccountHandler subject;

    @BeforeEach
    void setUp() {
        subject = new TokenAssociateToAccountHandler(new AssetsLoader());
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

            assertThatThrownBy(() -> subject.pureChecks(txn))
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
                            TransactionID.newBuilder().accountID(ACCOUNT_1339).build())
                    .tokenAssociate(associateTxnBodyBuilder)
                    .build();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class HandleTests extends ParityTestBase {
        @SuppressWarnings("DataFlowIssue")
        @Test
        void nullArgs() {
            assertThatThrownBy(() -> subject.handle(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void tokenStoreNotCreated() {
            final var context = mock(HandleContext.class);
            given(context.readableStore(ReadableTokenStore.class)).willReturn(null);

            assertThatThrownBy(() -> subject.handle(context)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void newTokenRelsExceedsSystemMax() {
            final var context = mockContext(1, 2000L);
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE), toPbj(KNOWN_TOKEN_WITH_KYC));
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED));
        }

        @Test
        void accountNotFound() {
            final var context = mockContext();
            final var missingAcctId = AccountID.newBuilder().accountNum(99999L);
            final var txn = TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_888).build())
                    .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                            .account(missingAcctId)
                            .tokens(toPbj(KNOWN_TOKEN_IMMUTABLE))
                            .build())
                    .build();
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
        }

        @Test
        void anyTokenNotFound() {
            final var context = mockContext();
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE), TOKEN_300);
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_TOKEN_ID));
        }

        @Test
        void tokenIsDeleted() {
            final var context = mockContext();
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE), toPbj(DELETED_TOKEN));
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_WAS_DELETED));
        }

        @Test
        void tokenIsPaused() {
            final var context = mockContext();
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE), toPbj(KNOWN_TOKEN_WITH_PAUSE));
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_IS_PAUSED));
        }

        @Test
        void exceedsTokenAssociationLimitForAccount() {
            // There are 3 tokens already associated with the account we're putting in the transaction, so we
            // need maxTokensPerAccount to be at least 3
            final var context = mock(HandleContext.class);
            mockConfig(context, 2000L, true, 3);
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_WITH_FREEZE));
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED));
        }

        @Test
        void tokenAlreadyAssociatedWithAccount() {
            final var context = mockContext();
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_WITH_KYC));
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT));
        }

        @Test
        void tokensAssociateToAccountWithNoTokenRels() {
            // Mock config context to allow unlimited token associations
            var context = mock(HandleContext.class);
            // Set maxTokensPerAccount to a value that will fail if areTokenAssociationsLimited
            // is incorrectly ignored
            mockConfig(context, 2000L, false, 4);
            given(context.readableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);

            // Put a new account into the account store that has no tokens associated with it
            final var newAcctNum = 12345L;
            final var newAcctId = AccountID.newBuilder().accountNum(newAcctNum).build();
            writableAccountStore.put(Account.newBuilder()
                    .accountId(newAcctId)
                    .headTokenId(TokenID.DEFAULT)
                    .build());
            final var txn = TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_888).build())
                    .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                            .account(newAcctId)
                            .tokens(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY), toPbj(KNOWN_TOKEN_WITH_WIPE))
                            .build())
                    .build();
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            subject.handle(context);
            Assertions.assertThat(writableTokenRelStore.modifiedTokens())
                    .contains(
                            EntityIDPair.newBuilder()
                                    .accountId(newAcctId)
                                    .tokenId(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY))
                                    .build(),
                            EntityIDPair.newBuilder()
                                    .accountId(newAcctId)
                                    .tokenId(toPbj(KNOWN_TOKEN_WITH_WIPE))
                                    .build());
            final var headToken = writableAccountStore.getAccountById(newAcctId).headTokenId();
            final var headTokenRel = writableTokenRelStore.get(newAcctId, headToken);
            Assertions.assertThat(headTokenRel.frozen()).isFalse();
            Assertions.assertThat(headTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(headTokenRel.previousToken()).isNull();
            Assertions.assertThat(headTokenRel.tokenId()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_WIPE));
            Assertions.assertThat(headTokenRel.nextToken()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY));
            final var nextToHeadTokenRel = writableTokenRelStore.get(newAcctId, headTokenRel.nextToken());
            Assertions.assertThat(nextToHeadTokenRel.frozen()).isFalse();
            Assertions.assertThat(nextToHeadTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(nextToHeadTokenRel.previousToken()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_WIPE));
            Assertions.assertThat(nextToHeadTokenRel.tokenId()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY));
            Assertions.assertThat(nextToHeadTokenRel.nextToken()).isNull();
        }

        @Test
        void tokensAssociateToAccountWithExistingTokenRels() {
            final var context = mockContext();
            final var newAcctNum = 21212L;
            final var newAcctId = AccountID.newBuilder().accountNum(newAcctNum).build();
            // put a new account into the account store that has two tokens associated with it
            writableAccountStore.put(Account.newBuilder()
                    .accountId(newAcctId)
                    .headTokenId(TokenID.newBuilder().tokenNum(774L))
                    .build());

            // put the pre-existing token rels into the rel store
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountId(newAcctId)
                    .tokenId(toPbj(KNOWN_TOKEN_WITH_WIPE))
                    .nextToken(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY))
                    .balance(100)
                    .build());
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountId(newAcctId)
                    .tokenId(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY))
                    .previousToken(toPbj(KNOWN_TOKEN_WITH_WIPE))
                    .balance(200)
                    .build());

            final var txn = TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_888).build())
                    .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                            .account(newAcctId)
                            .tokens(toPbj(KNOWN_TOKEN_WITH_FREEZE), toPbj(KNOWN_TOKEN_WITH_KYC))
                            .build())
                    .build();
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            subject.handle(context);

            Assertions.assertThat(writableTokenRelStore.modifiedTokens())
                    .contains(
                            EntityIDPair.newBuilder()
                                    .accountId(newAcctId)
                                    .tokenId(toPbj(KNOWN_TOKEN_WITH_FREEZE))
                                    .build(),
                            EntityIDPair.newBuilder()
                                    .accountId(newAcctId)
                                    .tokenId(toPbj(KNOWN_TOKEN_WITH_KYC))
                                    .build());
            final var headTokenId =
                    writableAccountStore.getAccountById(newAcctId).headTokenId();
            final var headTokenRel = writableTokenRelStore.get(newAcctId, headTokenId);
            Assertions.assertThat(headTokenRel.previousToken()).isNull();
            Assertions.assertThat(headTokenRel.tokenId()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_KYC));
            Assertions.assertThat(headTokenRel.nextToken()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_FREEZE));
            Assertions.assertThat(headTokenRel.frozen()).isFalse();
            Assertions.assertThat(headTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(headTokenRel.automaticAssociation()).isFalse();

            final var nextToHeadTokenRel = writableTokenRelStore.get(newAcctId, headTokenRel.nextToken());
            Assertions.assertThat(nextToHeadTokenRel.previousToken().tokenNum())
                    .isEqualTo(KNOWN_TOKEN_WITH_KYC.getTokenNum());
            Assertions.assertThat(nextToHeadTokenRel.tokenId()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_FREEZE));
            Assertions.assertThat(nextToHeadTokenRel.nextToken()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_WIPE));
            Assertions.assertThat(nextToHeadTokenRel.frozen()).isTrue();
            // Note: this token doesn't actually have a KYC key even though its name implies that
            // it does
            Assertions.assertThat(nextToHeadTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(nextToHeadTokenRel.automaticAssociation()).isFalse();

            final var thirdTokenRel = writableTokenRelStore.get(newAcctId, nextToHeadTokenRel.nextToken());
            Assertions.assertThat(thirdTokenRel.previousToken()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_FREEZE));
            Assertions.assertThat(thirdTokenRel.tokenId()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_WIPE));
            Assertions.assertThat(thirdTokenRel.nextToken()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY));
            Assertions.assertThat(thirdTokenRel.frozen()).isFalse();
            Assertions.assertThat(thirdTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(thirdTokenRel.automaticAssociation()).isFalse();

            final var fourthTokenRel = writableTokenRelStore.get(newAcctId, thirdTokenRel.nextToken());
            Assertions.assertThat(fourthTokenRel.previousToken()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_WIPE));
            Assertions.assertThat(fourthTokenRel.tokenId()).isEqualTo(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY));
            Assertions.assertThat(fourthTokenRel.nextToken()).isNull();
            Assertions.assertThat(fourthTokenRel.frozen()).isFalse();
            Assertions.assertThat(fourthTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(fourthTokenRel.automaticAssociation()).isFalse();
        }

        @Test
        void missingAccountHeadTokenDoesntStopTokenAssociation() {
            final var context = mockContext();
            final var newAcctNum = 21212L;
            final var newAcctId = AccountID.newBuilder().accountNum(newAcctNum).build();
            // put a new account into the account store that has a bogus head token number
            writableAccountStore.put(Account.newBuilder()
                    .accountId(newAcctId)
                    .headTokenId(TOKEN_300)
                    .build());

            final var txn = TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_888).build())
                    .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                            .account(newAcctId)
                            .tokens(toPbj(KNOWN_TOKEN_WITH_FREEZE), toPbj(KNOWN_TOKEN_WITH_KYC))
                            .build())
                    .build();
            given(context.body()).willReturn(txn);
            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

            subject.handle(context);

            Assertions.assertThat(writableTokenRelStore.modifiedTokens())
                    .contains(
                            EntityIDPair.newBuilder()
                                    .accountId(newAcctId)
                                    .tokenId(toPbj(KNOWN_TOKEN_WITH_FREEZE))
                                    .build(),
                            EntityIDPair.newBuilder()
                                    .accountId(newAcctId)
                                    .tokenId(toPbj(KNOWN_TOKEN_WITH_KYC))
                                    .build());
            final var updatedAcct = writableAccountStore.getAccountById(newAcctId);
            Assertions.assertThat(updatedAcct).isNotNull();
            // The account's updated head token num will point to the first new token
            Assertions.assertThat(updatedAcct.headTokenId().tokenNum()).isEqualTo(KNOWN_TOKEN_WITH_KYC.getTokenNum());
            // And new token relations will still exist for the new token IDs
            Assertions.assertThat(writableTokenRelStore.get(newAcctId, toPbj(KNOWN_TOKEN_WITH_FREEZE)))
                    .isNotNull();
            Assertions.assertThat(writableTokenRelStore.get(newAcctId, toPbj(KNOWN_TOKEN_WITH_KYC)))
                    .isNotNull();
        }

        private TransactionBody newAssociateTxn(TokenID... ids) {
            return TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_888).build())
                    .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                            .account(ACCOUNT_1339)
                            .tokens(ids)
                            .build())
                    .build();
        }

        private HandleContext mockContext() {
            return mockContext(1000L, 2000L);
        }

        private HandleContext mockContext(final long maxNumTokenRels, final long maxTokensPerAccount) {
            final var handleContext = mock(HandleContext.class);
            mockConfig(handleContext, maxNumTokenRels, false, (int) maxTokensPerAccount);

            given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);

            return handleContext;
        }

        // The context passed in needs to be a mock
        private void mockConfig(
                final HandleContext mockedContext,
                final long maxAggregateRels,
                final boolean limitedRels,
                final int maxRelsPerAccount) {
            lenient()
                    .when(mockedContext.readableStore(ReadableTokenStore.class))
                    .thenReturn(readableTokenStore);
            final var config = mock(Configuration.class);
            lenient().when(mockedContext.configuration()).thenReturn(config);
            final var tokensConfig = mock(TokensConfig.class);
            lenient().when(tokensConfig.maxAggregateRels()).thenReturn(maxAggregateRels);
            lenient().when(tokensConfig.maxPerAccount()).thenReturn(maxRelsPerAccount);
            lenient().when(config.getConfigData(TokensConfig.class)).thenReturn(tokensConfig);
            final var entitiesConfig = mock(EntitiesConfig.class);
            lenient().when(config.getConfigData(EntitiesConfig.class)).thenReturn(entitiesConfig);
            lenient().when(entitiesConfig.limitTokenAssociations()).thenReturn(limitedRels);
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

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CalculateFeesTests extends ParityTestBase {

        @Test
        void onUnlimitedAutoAssociationsDisabledCallLegacyCalculateImplementation() {
            final var feeContext = mock(FeeContext.class);
            final var entitiesConfig = mock(EntitiesConfig.class);
            final var config = mock(Configuration.class);
            final var feeCalculator = mock(FeeCalculator.class);
            final var body = mock(TransactionBody.class);
            final var op = mock(TokenAssociateTransactionBody.class);
            final var account = mock(Account.class);
            final var readableAccountStore = mock(ReadableAccountStore.class);

            given(config.getConfigData(EntitiesConfig.class)).willReturn(entitiesConfig);
            given(entitiesConfig.unlimitedAutoAssociationsEnabled()).willReturn(false);
            given(feeContext.feeCalculator(any())).willReturn(feeCalculator);
            given(feeContext.configuration()).willReturn(config);
            given(feeContext.body()).willReturn(body);
            given(body.tokenAssociateOrThrow()).willReturn(op);
            given(feeContext.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
            given(op.accountOrThrow()).willReturn(ACCOUNT_888);
            given(readableAccountStore.getAccountById(any())).willReturn(account);

            subject.calculateFees(feeContext);

            verify(feeCalculator, times(1)).legacyCalculate(any());
        }

        @Test
        void oneSignatureAndOneAssociationCharged() {
            final var feeContext = mock(FeeContext.class);
            final var tokenAssociateTransactionBody = setupContextForNewCalculation(feeContext);

            given(feeContext.transactionCategory()).willReturn(TransactionCategory.USER);
            given(feeContext.numTxnSignatures()).willReturn(2); // the first signature is free
            given(tokenAssociateTransactionBody.tokens()).willReturn(List.of(TOKEN_300));

            final var associationPrice = 41666666L; // 0.05$ in tinybars
            final var signaturePrice = 1523587889L; // token associate vpt in tinybars
            final var fees = subject.calculateFees(feeContext);

            assertEquals(associationPrice + signaturePrice, fees.networkFee());
        }

        @Test
        void noChargedSignaturesAndOneAssociation() {
            final var feeContext = mock(FeeContext.class);
            final var tokenAssociateTransactionBody = setupContextForNewCalculation(feeContext);

            given(feeContext.transactionCategory()).willReturn(TransactionCategory.USER);
            given(feeContext.numTxnSignatures()).willReturn(1); // the first signature is free
            given(tokenAssociateTransactionBody.tokens()).willReturn(List.of(TOKEN_300));

            final var associationPrice = 41666666L; // 0.05$ in tinybars
            final var fees = subject.calculateFees(feeContext);

            assertEquals(associationPrice, fees.networkFee());
        }

        @Test
        void noChargedSignaturesAndTenAssociations() {
            final var feeContext = mock(FeeContext.class);
            final var tokenAssociateTransactionBody = setupContextForNewCalculation(feeContext);

            given(feeContext.transactionCategory()).willReturn(TransactionCategory.USER);
            given(feeContext.numTxnSignatures()).willReturn(1); // the first signature is free
            given(tokenAssociateTransactionBody.tokens())
                    .willReturn(List.of(
                            TokenID.newBuilder().tokenNum(300).build(),
                            TokenID.newBuilder().tokenNum(301).build(),
                            TokenID.newBuilder().tokenNum(302).build(),
                            TokenID.newBuilder().tokenNum(303).build(),
                            TokenID.newBuilder().tokenNum(304).build(),
                            TokenID.newBuilder().tokenNum(305).build(),
                            TokenID.newBuilder().tokenNum(306).build(),
                            TokenID.newBuilder().tokenNum(307).build(),
                            TokenID.newBuilder().tokenNum(308).build(),
                            TokenID.newBuilder().tokenNum(309).build()));

            final var associationPrice = 41666666L; // 0.05$ in tinybars
            final var fees = subject.calculateFees(feeContext);

            assertEquals(10 * associationPrice, fees.networkFee());
        }

        @Test
        void threeSignaturesAndOneAssociationCharged() {
            final var feeContext = mock(FeeContext.class);
            final var tokenAssociateTransactionBody = setupContextForNewCalculation(feeContext);

            given(feeContext.transactionCategory()).willReturn(TransactionCategory.USER);
            given(feeContext.numTxnSignatures()).willReturn(4); // the first signature is free
            given(tokenAssociateTransactionBody.tokens()).willReturn(List.of(TOKEN_300));

            final var associationPrice = 41666666L; // 0.05$ in tinybars
            final var signaturePrice = 1523587889L; // token associate vpt in tinybars
            final var fees = subject.calculateFees(feeContext);

            assertEquals(associationPrice + 3 * signaturePrice, fees.networkFee());
        }

        @Test
        void noSignaturesChargedOnDispatchedTransaction() {
            final var feeContext = mock(FeeContext.class);
            final var tokenAssociateTransactionBody = setupContextForNewCalculation(feeContext);

            given(feeContext.transactionCategory()).willReturn(TransactionCategory.CHILD);
            given(tokenAssociateTransactionBody.tokens()).willReturn(List.of(TOKEN_300));

            final var associationPrice = 41666666L; // 0.05$ in tinybars
            final var fees = subject.calculateFees(feeContext);

            assertEquals(associationPrice, fees.networkFee());
        }

        private TokenAssociateTransactionBody setupContextForNewCalculation(final FeeContext feeContext) {
            final var entitiesConfig = mock(EntitiesConfig.class);
            final var config = mock(Configuration.class);
            final var feeCalculator = mock(FeeCalculator.class);
            final var body = mock(TransactionBody.class);
            final var tokenAssociateTransactionBody = mock(TokenAssociateTransactionBody.class);
            final var exchangeRateInfo = mock(ExchangeRateInfo.class);
            final ExchangeRate exchangeRate = new ExchangeRate(1, 12, TimestampSeconds.DEFAULT);

            given(config.getConfigData(EntitiesConfig.class)).willReturn(entitiesConfig);
            given(entitiesConfig.unlimitedAutoAssociationsEnabled()).willReturn(true);
            given(feeContext.feeCalculator(any())).willReturn(feeCalculator);
            given(feeContext.configuration()).willReturn(config);
            given(feeContext.body()).willReturn(body);
            given(body.tokenAssociateOrThrow()).willReturn(tokenAssociateTransactionBody);
            given(feeContext.exchangeRateInfo()).willReturn(exchangeRateInfo);
            given(exchangeRateInfo.activeRate(any())).willReturn(exchangeRate);
            given(feeCalculator.getVptPrice()).willReturn(18283054670L);

            return tokenAssociateTransactionBody;
        }
    }
}
