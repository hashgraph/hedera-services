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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.factories.scenarios.TokenWipeScenarios.VALID_WIPE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenWipeScenarios.WIPE_FOR_TOKEN_WITHOUT_KEY;
import static com.hedera.test.factories.scenarios.TokenWipeScenarios.WIPE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_WIPE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.token.TokenWipeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAccountWipeHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.ParityTestBase;
import com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.TokensConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

class TokenAccountWipeHandlerTest extends ParityTestBase {
    private static final AccountID ACCOUNT_4680 = BaseCryptoHandler.asAccount(4680);
    private static final AccountID TREASURY_ACCOUNT_9876 = BaseCryptoHandler.asAccount(9876);
    private static final TokenID TOKEN_531 = BaseTokenHandler.asToken(531);
    private final ConfigProvider configProvider = mock(ConfigProvider.class);

    private final TokenSupplyChangeOpsValidator validator = new TokenSupplyChangeOpsValidator(configProvider);
    private final TokenAccountWipeHandler subject = new TokenAccountWipeHandler(validator);

    @Nested
    class PureChecks {
        @SuppressWarnings("DataFlowIssue")
        @Test
        void nullArgsThrows() {
            assertThatThrownBy(() -> subject.pureChecks(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void noWipeTxnPresent() {
            final var nonWipeTxnBody = TokenBurnTransactionBody.newBuilder();
            final var txn = TransactionBody.newBuilder()
                    .transactionID(
                            TransactionID.newBuilder().accountID(ACCOUNT_4680).build())
                    .tokenBurn(nonWipeTxnBody)
                    .build();
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void noAccountIdPresent() {
            final var txn = newWipeTxn(null, TOKEN_531, 1);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
        }

        @Test
        void noTokenPresent() {
            final var txn = newWipeTxn(ACCOUNT_4680, null, 1);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_TOKEN_ID));
        }

        @Test
        void fungibleAndNonFungibleGiven() {
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 1, 1L);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_TRANSACTION_BODY));
        }

        @Test
        void nonPositiveFungibleAmountGiven() {
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, -1);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_WIPING_AMOUNT));
        }

        @Test
        void emptyNftSerialNumbers() {
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_WIPING_AMOUNT));
        }

        @Test
        void invalidNftSerialNumber() {
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0, 1L, 2L, 0L);
            Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_NFT_ID));
        }
    }

    @Nested
    // Tests that check prehandle parity with old prehandle code
    class PreHandle {
        @SuppressWarnings("DataFlowIssue")
        @Test
        void nullArgsThrows() {
            assertThatThrownBy(() -> subject.preHandle(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void tokenWipeWithValidExtantTokenScenario() throws PreCheckException {
            final var theTxn = txnFrom(VALID_WIPE_WITH_EXTANT_TOKEN);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            subject.preHandle(context);

            assertEquals(1, context.requiredNonPayerKeys().size());
            assertThat(context.requiredNonPayerKeys(), contains(TOKEN_WIPE_KT.asPbjKey()));
        }

        @Test
        void tokenWipeWithMissingTokenScenario() throws PreCheckException {
            final var theTxn = txnFrom(WIPE_WITH_MISSING_TOKEN);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
        }

        @Test
        void tokenWipeWithoutKeyScenario() throws PreCheckException {
            final var theTxn = txnFrom(WIPE_FOR_TOKEN_WITHOUT_KEY);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, readableTokenStore);
            subject.preHandle(context);

            assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
            assertEquals(0, context.requiredNonPayerKeys().size());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Handle {
        @Mock
        private ExpiryValidator validator;
        private WritableTokenStore writableTokenStore;
        private WritableNftStore writableNftStore;

        @SuppressWarnings("DataFlowIssue")
        @Test
        void nullArgsThrows() {
            assertThatThrownBy(() -> subject.handle(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void invalidFungibleAmount() {
            mockConfig();
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, -1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_WIPING_AMOUNT));
        }

        @Test
        void accountDoesntExist() {
            mockConfig();
            // Both stores are intentionally empty
            writableAccountStore = newWritableStoreWithAccounts();
            writableTokenStore = newWritableStoreWithTokens();
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
        }

        @Test
        void fungibleAmountExceedsBatchSize() {
            final var maxBatchSize = 5;
            mockConfig(maxBatchSize, true);
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .build());
            writableTokenStore = newWritableStoreWithTokens();
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, maxBatchSize + 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(BATCH_SIZE_LIMIT_EXCEEDED));
        }

        @Test
        void nftAmountExceedsBatchSize() {
            mockConfig(2, true);
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .build());
            writableTokenStore = newWritableStoreWithTokens();
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0, 1L, 2L, 3L);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(BATCH_SIZE_LIMIT_EXCEEDED));
        }

        @Test
        void tokenIdNotFound() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .build());
            writableTokenStore = newWritableStoreWithTokens(); // Intentionally empty
            final var txn = newWipeTxn(ACCOUNT_4680, BaseTokenHandler.asToken(999), 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_TOKEN_ID));
        }

        @Test
        void tokenIsDeleted() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(5)
                    .deleted(true) // Intentionally deleted
                    .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_WAS_DELETED));
        }

        @Test
        void tokenIsPaused() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(5)
                    .paused(true) // Intentionally paused
                    .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_IS_PAUSED));
        }

        @Test
        void tokenDoesntHaveWipeKey() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .build());
            final var totalFungibleSupply = 5;
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey((Key) null) // Intentionally missing wipe key
                    .totalSupply(totalFungibleSupply)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                    .tokenNumber(TOKEN_531.tokenNum())
                    .balance(totalFungibleSupply)
                    .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, totalFungibleSupply + 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_HAS_NO_WIPE_KEY));
        }

        @Test
        void accountRelDoesntExist() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(5)
                    .build());
            // Intentionally has no token rels:
            writableTokenRelStore = newWritableStoreWithTokenRels();
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
        }

        @Test
        void givenAccountIsTreasury() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(5)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(TokenRelation.newBuilder()
                    .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .tokenNumber(TOKEN_531.tokenNum())
                    .build());
            final var txn = newWipeTxn(TREASURY_ACCOUNT_9876, TOKEN_531, 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT));
        }

        @Test
        void fungibleAmountNegatesSupply() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .numberTreasuryTitles(0)
                            .numberPositiveBalances(1)
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .numberPositiveBalances(0)
                            .build());
            final var totalTokenSupply = 5;
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(totalTokenSupply)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(
                    TokenRelation.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(0)
                            .build(),
                    TokenRelation.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(totalTokenSupply)
                            .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, totalTokenSupply + 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_WIPING_AMOUNT));
        }

        @Test
        void fungibleAmountNegatesBalance() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .numberTreasuryTitles(0)
                            .numberPositiveBalances(1)
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .numberPositiveBalances(0)
                            .build());
            final var currentTokenBalance = 3;
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(currentTokenBalance + 2)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(
                    TokenRelation.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(0)
                            .build(),
                    TokenRelation.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(currentTokenBalance)
                            .build());
            // The fungible amount is less than the total token supply, but one more than the token balance. Therefore,
            // we should see an error thrown
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, currentTokenBalance + 1);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_WIPING_AMOUNT));
        }

        @Test
        void fungibleAmountBurnedWithLeftoverAccountBalance() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .numberTreasuryTitles(0)
                            .numberPositiveBalances(1)
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .numberPositiveBalances(1)
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(5)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(
                    TokenRelation.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(1)
                            .build(),
                    TokenRelation.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(4)
                            .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 3);
            final var context = mockContext(txn);

            subject.handle(context);

            final var acct = writableAccountStore.get(ACCOUNT_4680);
            Assertions.assertThat(acct.numberPositiveBalances()).isEqualTo(1);
            final var treasuryAcct = writableAccountStore.get(TREASURY_ACCOUNT_9876);
            Assertions.assertThat(treasuryAcct.numberTreasuryTitles()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.numberPositiveBalances()).isEqualTo(1);
            final var token = writableTokenStore.get(TOKEN_531);
            Assertions.assertThat(token.totalSupply()).isEqualTo(2);
            final var acctTokenRel = writableTokenRelStore.get(ACCOUNT_4680, TOKEN_531);
            Assertions.assertThat(acctTokenRel.balance()).isEqualTo(1);
            final var treasuryTokenRel = writableTokenRelStore.get(TREASURY_ACCOUNT_9876, TOKEN_531);
            // Nothing should've happened to the treasury token balance
            Assertions.assertThat(treasuryTokenRel.balance()).isEqualTo(1);
        }

        @Test
        void fungibleAmountBurnedWithNoLeftoverAccountBalance() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .numberTreasuryTitles(0)
                            .numberPositiveBalances(1)
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .numberPositiveBalances(1)
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(5)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(
                    TokenRelation.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(3)
                            .build(),
                    TokenRelation.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(2)
                            .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 2);
            final var context = mockContext(txn);

            subject.handle(context);

            final var acct = writableAccountStore.get(ACCOUNT_4680);
            Assertions.assertThat(acct.numberPositiveBalances()).isEqualTo(0);
            final var treasuryAcct = writableAccountStore.get(TREASURY_ACCOUNT_9876);
            Assertions.assertThat(treasuryAcct.numberTreasuryTitles()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.numberPositiveBalances()).isEqualTo(1);
            final var token = writableTokenStore.get(TOKEN_531);
            Assertions.assertThat(token.totalSupply()).isEqualTo(3);
            final var acctTokenRel = writableTokenRelStore.get(ACCOUNT_4680, TOKEN_531);
            Assertions.assertThat(acctTokenRel.balance()).isZero();
            final var treasuryTokenRel = writableTokenRelStore.get(TREASURY_ACCOUNT_9876, TOKEN_531);
            // Nothing should've happened to the treasury token balance
            Assertions.assertThat(treasuryTokenRel.balance()).isEqualTo(3);
        }

        @Test
        void nftSerialNumDoesntExist() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(10)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                    .tokenNumber(TOKEN_531.tokenNum())
                    .build());
            writableNftStore = newWritableStoreWithNfts(); // Intentionally empty

            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0, 1L);
            final var context = mockContext(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_NFT_ID));
        }

        @Test
        void nftNotOwnedByAccount() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(10)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                    .tokenNumber(TOKEN_531.tokenNum())
                    .build());
            writableNftStore = newWritableStoreWithNfts(Nft.newBuilder()
                    .id(UniqueTokenId.newBuilder()
                            .tokenTypeNumber(TOKEN_531.tokenNum())
                            .serialNumber(1)
                            .build())
                    .ownerNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .build());

            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0, 1L);
            final var context = mockContext(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ACCOUNT_DOES_NOT_OWN_WIPED_NFT));
        }

        @Test
        void numNftSerialsNegatesSupply() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .numberTreasuryTitles(0)
                            .numberPositiveBalances(1)
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .numberPositiveBalances(0)
                            .build());
            final var totalTokenSupply = 1;
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(totalTokenSupply)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(
                    TokenRelation.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .build(),
                    TokenRelation.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(totalTokenSupply)
                            .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0, 1L, 2L);
            final var context = mockContext(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_WIPING_AMOUNT));
        }

        @Test
        void nftSerialsWipedWithLeftoverNftSerials() {
            // i.e. leftover NFT serials remaining with the owning account

            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .numberTreasuryTitles(0)
                            .numberPositiveBalances(1)
                            .numberOwnedNfts(3)
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .numberPositiveBalances(1)
                            .numberOwnedNfts(1)
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(10)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(
                    TokenRelation.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(3)
                            .build(),
                    TokenRelation.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(1)
                            .build());
            writableNftStore = newWritableStoreWithNfts(
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(1)
                                    .build())
                            .ownerNumber(0) // treasury owns this NFT
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(2)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(3)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(4)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0, 2L, 3L);
            final var context = mockContext(txn);

            subject.handle(context);

            final var acct = writableAccountStore.get(ACCOUNT_4680);
            Assertions.assertThat(acct.numberOwnedNfts()).isEqualTo(1);
            Assertions.assertThat(acct.numberPositiveBalances()).isEqualTo(1);
            final var treasuryAcct = writableAccountStore.get(TREASURY_ACCOUNT_9876);
            // The treasury still owns its NFT, so its counts shouldn't change
            Assertions.assertThat(treasuryAcct.numberOwnedNfts()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.numberTreasuryTitles()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.numberPositiveBalances()).isEqualTo(1);
            final var token = writableTokenStore.get(TOKEN_531);
            // Verify that 2 NFTs were removed from the total supply
            Assertions.assertThat(token.totalSupply()).isEqualTo(8);
            final var tokenRel = writableTokenRelStore.get(ACCOUNT_4680, TOKEN_531);
            Assertions.assertThat(tokenRel.balance()).isEqualTo(1);
            // Verify the treasury's NFT wasn't removed
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 1)))
                    .isNotNull();
            // Verify that two of the account's NFTs were removed, and that the final one remains
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 2)))
                    .isNull();
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 3)))
                    .isNull();
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 4)))
                    .isNotNull();
        }

        @Test
        void nftSerialsWipedWithNoLeftoverNftSerials() {
            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .numberTreasuryTitles(0)
                            .numberPositiveBalances(1)
                            .numberOwnedNfts(3)
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .numberPositiveBalances(1)
                            .numberOwnedNfts(1)
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(10)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(
                    TokenRelation.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(3)
                            .build(),
                    TokenRelation.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(1)
                            .build());
            writableNftStore = newWritableStoreWithNfts(
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(1)
                                    .build())
                            .ownerNumber(0) // treasury owns this NFT
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(2)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(3)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(4)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0, 2L, 3L, 4L);
            final var context = mockContext(txn);

            subject.handle(context);

            final var acct = writableAccountStore.get(ACCOUNT_4680);
            Assertions.assertThat(acct.numberOwnedNfts()).isZero();
            Assertions.assertThat(acct.numberPositiveBalances()).isZero();
            final var treasuryAcct = writableAccountStore.get(TREASURY_ACCOUNT_9876);
            // The treasury still owns its NFT, so its counts shouldn't change
            Assertions.assertThat(treasuryAcct.numberOwnedNfts()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.numberTreasuryTitles()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.numberPositiveBalances()).isEqualTo(1);
            final var token = writableTokenStore.get(TOKEN_531);
            // Verify that 3 NFTs were removed from the total supply
            Assertions.assertThat(token.totalSupply()).isEqualTo(7);
            final var tokenRel = writableTokenRelStore.get(ACCOUNT_4680, TOKEN_531);
            Assertions.assertThat(tokenRel.balance()).isZero();
            // Verify the treasury's NFT wasn't removed
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 1)))
                    .isNotNull();
            // Verify that the account's NFTs were removed
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 2)))
                    .isNull();
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 3)))
                    .isNull();
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 4)))
                    .isNull();
        }

        @Test
        void duplicateNftSerials() {
            // This is a success case, and should be identical to the case without no duplicates above

            mockConfig();
            mockOkExpiryValidator();
            writableAccountStore = newWritableStoreWithAccounts(
                    Account.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .numberTreasuryTitles(0)
                            .numberPositiveBalances(1)
                            .numberOwnedNfts(3)
                            .build(),
                    Account.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .numberTreasuryTitles(1)
                            .numberPositiveBalances(1)
                            .numberOwnedNfts(1)
                            .build());
            writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                    .tokenNumber(TOKEN_531.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .treasuryAccountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                    .wipeKey(TOKEN_WIPE_KT.asPbjKey())
                    .totalSupply(10)
                    .build());
            writableTokenRelStore = newWritableStoreWithTokenRels(
                    TokenRelation.newBuilder()
                            .accountNumber(ACCOUNT_4680.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(3)
                            .build(),
                    TokenRelation.newBuilder()
                            .accountNumber(TREASURY_ACCOUNT_9876.accountNumOrThrow())
                            .tokenNumber(TOKEN_531.tokenNum())
                            .balance(1)
                            .build());
            writableNftStore = newWritableStoreWithNfts(
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(1)
                                    .build())
                            .ownerNumber(0) // treasury owns this NFT
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(2)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(3)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build(),
                    Nft.newBuilder()
                            .id(UniqueTokenId.newBuilder()
                                    .tokenTypeNumber(TOKEN_531.tokenNum())
                                    .serialNumber(4)
                                    .build())
                            .ownerNumber(ACCOUNT_4680.accountNumOrThrow())
                            .build());
            final var txn = newWipeTxn(ACCOUNT_4680, TOKEN_531, 0, 2L, 2L, 3L, 3L, 4L, 4L, 2L, 3L, 4L);
            final var context = mockContext(txn);

            subject.handle(context);

            final var acct = writableAccountStore.get(ACCOUNT_4680);
            Assertions.assertThat(acct.numberOwnedNfts()).isZero();
            Assertions.assertThat(acct.numberPositiveBalances()).isZero();
            final var treasuryAcct = writableAccountStore.get(TREASURY_ACCOUNT_9876);
            // The treasury still owns its NFT, so its counts shouldn't change
            Assertions.assertThat(treasuryAcct.numberOwnedNfts()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.numberTreasuryTitles()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.numberPositiveBalances()).isEqualTo(1);
            final var token = writableTokenStore.get(TOKEN_531);
            // Verify that 3 NFTs were removed from the total supply
            Assertions.assertThat(token.totalSupply()).isEqualTo(7);
            final var tokenRel = writableTokenRelStore.get(ACCOUNT_4680, TOKEN_531);
            Assertions.assertThat(tokenRel.balance()).isZero();
            // Verify the treasury's NFT wasn't removed
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 1)))
                    .isNotNull();
            // Verify that the account's NFTs were removed
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 2)))
                    .isNull();
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 3)))
                    .isNull();
            Assertions.assertThat(writableNftStore.get(new UniqueTokenId(TOKEN_531.tokenNum(), 4)))
                    .isNull();
        }

        private void mockOkExpiryValidator() {
            given(validator.expirationStatus(notNull(), anyBoolean(), anyLong()))
                    .willReturn(OK);
        }

        private HandleContext mockContext(TransactionBody txn) {
            final var context = mock(HandleContext.class);

            given(context.body()).willReturn(txn);

            given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
            given(context.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
            given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
            given(context.writableStore(WritableNftStore.class)).willReturn(writableNftStore);

            given(context.expiryValidator()).willReturn(validator);

            return context;
        }

        private void mockConfig() {
            mockConfig(100, true);
        }

        private void mockConfig(final int maxBatchSize, final boolean nftsEnabled) {
            final var mockTokensConfig = mock(TokensConfig.class);
            lenient().when(mockTokensConfig.nftsAreEnabled()).thenReturn(nftsEnabled);
            lenient().when(mockTokensConfig.nftsMaxBatchSizeWipe()).thenReturn(maxBatchSize);

            final var mockConfig = mock(VersionedConfiguration.class);
            lenient().when(mockConfig.getConfigData(TokensConfig.class)).thenReturn(mockTokensConfig);

            given(configProvider.getConfiguration()).willReturn(mockConfig);
        }
    }

    private TransactionBody newWipeTxn(AccountID accountId, TokenID token, long fungibleAmount, Long... nftSerialNums) {
        final TokenWipeAccountTransactionBody.Builder wipeTxnBodyBuilder = TokenWipeAccountTransactionBody.newBuilder();
        if (accountId != null) wipeTxnBodyBuilder.account(accountId);
        if (token != null) wipeTxnBodyBuilder.token(token);
        wipeTxnBodyBuilder.amount(fungibleAmount);
        wipeTxnBodyBuilder.serialNumbers(nftSerialNums);
        return TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(ACCOUNT_4680).build())
                .tokenWipe(wipeTxnBodyBuilder)
                .build();
    }
}
