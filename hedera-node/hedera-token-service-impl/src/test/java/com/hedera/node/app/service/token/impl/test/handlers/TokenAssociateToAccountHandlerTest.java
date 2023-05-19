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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.swirlds.config.api.Configuration;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
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
            final var txn = mock(TransactionBody.class);
            final var context = mock(HandleContext.class);

            assertThatThrownBy(() -> subject.handle(null, context, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> subject.handle(
                            mock(TransactionBody.class), null, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> subject.handle(txn, context, null, writableTokenRelStore))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> subject.handle(txn, context, writableAccountStore, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void tokenStoreNotCreated() {
            final var txn = mock(TransactionBody.class);
            final var context = mock(HandleContext.class);
            given(context.createReadableStore(ReadableTokenStore.class)).willReturn(null);

            assertThatThrownBy(() -> subject.handle(txn, context, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void newTokenRelsExceedsSystemMax() {
            final var handleContext = mockContext(1, 2000L);
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE), toPbj(KNOWN_TOKEN_WITH_KYC));

            assertThatThrownBy(() -> subject.handle(txn, handleContext, writableAccountStore, writableTokenRelStore))
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

            assertThatThrownBy(() -> subject.handle(txn, context, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
        }

        @Test
        void anyTokenNotFound() {
            final var context = mockContext();
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE), TOKEN_300);

            assertThatThrownBy(() -> subject.handle(txn, context, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_TOKEN_ID));
        }

        @Test
        void tokenIsDeleted() {
            final var context = mockContext();
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE), toPbj(DELETED_TOKEN));

            assertThatThrownBy(() -> subject.handle(txn, context, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_WAS_DELETED));
        }

        @Test
        void tokenIsPaused() {
            final var context = mockContext();
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE), toPbj(KNOWN_TOKEN_WITH_PAUSE));

            assertThatThrownBy(() -> subject.handle(txn, context, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_IS_PAUSED));
        }

        @Test
        void exceedsTokenAssociationLimit() {
            // There are already 3 tokens already associated with the account we're putting in the transaction, so we
            // need maxTokensPerAccount to be at least 3
            final var context = mockContext(1000L, 3L);
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_IMMUTABLE));

            assertThatThrownBy(() -> subject.handle(txn, context, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED));
        }

        @Test
        void tokenAlreadyAssociatedWithAccount() {
            final var context = mockContext();
            final var txn = newAssociateTxn(toPbj(KNOWN_TOKEN_WITH_KYC));

            assertThatThrownBy(() -> subject.handle(txn, context, writableAccountStore, writableTokenRelStore))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT));
        }

        @Test
        void tokensAssociateToAccountWithNoTokenRels() {
            // Mock config context to allow unlimited token associations
            var context = mock(HandleContext.class);
            given(context.createReadableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);
            final var config = mock(Configuration.class);
            given(context.getConfiguration()).willReturn(config);
            given(config.getValue("areTokenAssociationsLimited", Boolean.class)).willReturn(false);
            given(config.getValue("maxNumTokenRels", Long.class)).willReturn(1000L);
            // Set maxTokensPerAccount to a value that will fail if areTokenAssociationsLimited
            // is incorrectly ignored
            given(config.getValue("maxTokensPerAccount", Long.class)).willReturn(0L);
            // Put a new account into the account store that has no tokens associated with it
            final var newAcctNum = 12345L;
            final var newAcctId = AccountID.newBuilder().accountNum(newAcctNum).build();
            writableAccountStore.put(Account.newBuilder()
                    .accountNumber(newAcctNum)
                    .headTokenNumber(0)
                    .build());
            final var txn = TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_888).build())
                    .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                            .account(newAcctId)
                            .tokens(toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY), toPbj(KNOWN_TOKEN_WITH_WIPE))
                            .build())
                    .build();

            subject.handle(txn, context, writableAccountStore, writableTokenRelStore);
            Assertions.assertThat(writableTokenRelStore.modifiedTokens())
                    .contains(
                            EntityNumPair.fromAccountTokenRel(fromPbj(newAcctId), KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY),
                            EntityNumPair.fromAccountTokenRel(fromPbj(newAcctId), KNOWN_TOKEN_WITH_WIPE));
            final var headToken = TokenID.newBuilder()
                    .tokenNum(writableAccountStore.getAccountById(newAcctId).headTokenNumber())
                    .build();
            final var headTokenRel =
                    writableTokenRelStore.get(newAcctId, headToken).get();
            Assertions.assertThat(headTokenRel.frozen()).isFalse();
            Assertions.assertThat(headTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(headTokenRel.previousToken()).isNotPositive();
            Assertions.assertThat(headTokenRel.tokenNumber())
                    .isEqualTo(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY.getTokenNum());
            Assertions.assertThat(headTokenRel.nextToken()).isEqualTo(KNOWN_TOKEN_WITH_WIPE.getTokenNum());
            final var nextToHeadTokenRel = writableTokenRelStore
                    .get(
                            newAcctId,
                            TokenID.newBuilder()
                                    .tokenNum(headTokenRel.nextToken())
                                    .build())
                    .get();
            Assertions.assertThat(nextToHeadTokenRel.frozen()).isFalse();
            Assertions.assertThat(nextToHeadTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(nextToHeadTokenRel.previousToken())
                    .isEqualTo(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY.getTokenNum());
            Assertions.assertThat(nextToHeadTokenRel.tokenNumber()).isEqualTo(KNOWN_TOKEN_WITH_WIPE.getTokenNum());
            Assertions.assertThat(nextToHeadTokenRel.nextToken()).isNotPositive();
        }

        @Test
        void tokensAssociateToAccountWithExistingTokenRels() {
            final var context = mockContext();
            final var newAcctNum = 21212L;
            final var newAcctId = AccountID.newBuilder().accountNum(newAcctNum).build();
            // put a new account into the account store that has two tokens associated with it
            writableAccountStore.put(Account.newBuilder()
                    .accountNumber(newAcctNum)
                    .headTokenNumber(KNOWN_TOKEN_WITH_WIPE.getTokenNum())
                    .build());

            writableAccountStore.commit();
            // put the pre-existing token rels into the rel store
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(newAcctNum)
                    .tokenNumber(KNOWN_TOKEN_WITH_WIPE.getTokenNum())
                    .nextToken(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY.getTokenNum())
                    .balance(100)
                    .build());
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(newAcctNum)
                    .tokenNumber(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY.getTokenNum())
                    .previousToken(KNOWN_TOKEN_WITH_WIPE.getTokenNum())
                    .balance(200)
                    .build());
            writableTokenRelStore.commit();
            final var txn = TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_888).build())
                    .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                            .account(newAcctId)
                            .tokens(toPbj(KNOWN_TOKEN_WITH_FREEZE), toPbj(KNOWN_TOKEN_WITH_KYC))
                            .build())
                    .build();

            subject.handle(txn, context, writableAccountStore, writableTokenRelStore);
            // Commit the results of the handle() calls in order to do further verification
            writableAccountStore.commit();
            writableTokenRelStore.commit();

            Assertions.assertThat(writableTokenRelStore.modifiedTokens())
                    .contains(
                            EntityNumPair.fromAccountTokenRel(fromPbj(newAcctId), KNOWN_TOKEN_WITH_FREEZE),
                            EntityNumPair.fromAccountTokenRel(fromPbj(newAcctId), KNOWN_TOKEN_WITH_KYC));
            final var headTokenId = TokenID.newBuilder()
                    .tokenNum(writableAccountStore.getAccountById(newAcctId).headTokenNumber())
                    .build();
            final var headTokenRel =
                    writableTokenRelStore.get(newAcctId, headTokenId).get();
            Assertions.assertThat(headTokenRel.previousToken()).isNotPositive();
            Assertions.assertThat(headTokenRel.tokenNumber()).isEqualTo(KNOWN_TOKEN_WITH_FREEZE.getTokenNum());
            Assertions.assertThat(headTokenRel.nextToken()).isEqualTo(KNOWN_TOKEN_WITH_KYC.getTokenNum());
            Assertions.assertThat(headTokenRel.frozen()).isTrue();
            Assertions.assertThat(headTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(headTokenRel.automaticAssociation()).isFalse();

            final var nextToHeadTokenRel = writableTokenRelStore
                    .get(
                            newAcctId,
                            TokenID.newBuilder()
                                    .tokenNum(headTokenRel.nextToken())
                                    .build())
                    .get();
            Assertions.assertThat(nextToHeadTokenRel.previousToken()).isEqualTo(KNOWN_TOKEN_WITH_FREEZE.getTokenNum());
            Assertions.assertThat(nextToHeadTokenRel.tokenNumber()).isEqualTo(KNOWN_TOKEN_WITH_KYC.getTokenNum());
            Assertions.assertThat(nextToHeadTokenRel.nextToken()).isEqualTo(KNOWN_TOKEN_WITH_WIPE.getTokenNum());
            Assertions.assertThat(nextToHeadTokenRel.frozen()).isFalse();
            // Note: this token doesn't actually have a KYC key even though its name implies that
            // it does
            Assertions.assertThat(nextToHeadTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(nextToHeadTokenRel.automaticAssociation()).isFalse();

            final var thirdTokenRel = writableTokenRelStore
                    .get(
                            newAcctId,
                            TokenID.newBuilder()
                                    .tokenNum(nextToHeadTokenRel.nextToken())
                                    .build())
                    .get();
            Assertions.assertThat(thirdTokenRel.previousToken()).isEqualTo(KNOWN_TOKEN_WITH_KYC.getTokenNum());
            Assertions.assertThat(thirdTokenRel.tokenNumber()).isEqualTo(KNOWN_TOKEN_WITH_WIPE.getTokenNum());
            Assertions.assertThat(thirdTokenRel.nextToken()).isEqualTo(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY.getTokenNum());
            Assertions.assertThat(thirdTokenRel.frozen()).isFalse();
            Assertions.assertThat(thirdTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(thirdTokenRel.automaticAssociation()).isFalse();

            final var fourthTokenRel = writableTokenRelStore
                    .get(
                            newAcctId,
                            TokenID.newBuilder()
                                    .tokenNum(thirdTokenRel.nextToken())
                                    .build())
                    .get();
            Assertions.assertThat(fourthTokenRel.previousToken()).isEqualTo(KNOWN_TOKEN_WITH_WIPE.getTokenNum());
            Assertions.assertThat(fourthTokenRel.tokenNumber())
                    .isEqualTo(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY.getTokenNum());
            Assertions.assertThat(fourthTokenRel.nextToken()).isNotPositive();
            Assertions.assertThat(fourthTokenRel.frozen()).isFalse();
            Assertions.assertThat(fourthTokenRel.kycGranted()).isFalse();
            Assertions.assertThat(fourthTokenRel.automaticAssociation()).isFalse();
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
            var handleContext = mock(HandleContext.class);
            final var config = mock(Configuration.class);
            given(handleContext.getConfiguration()).willReturn(config);
            Mockito.lenient()
                    .when(config.getValue("areTokenAssociationsLimited", Boolean.class))
                    .thenReturn(true);
            Mockito.lenient()
                    .when(config.getValue("maxNumTokenRels", Long.class))
                    .thenReturn(maxNumTokenRels);
            Mockito.lenient()
                    .when(config.getValue("maxTokensPerAccount", Long.class))
                    .thenReturn(maxTokensPerAccount);

            given(handleContext.createReadableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);

            return handleContext;
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
