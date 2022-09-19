/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.tokens;

import static com.hedera.services.ledger.backing.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.services.ledger.properties.AccountProperty.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_PAUSE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY_KT;
import static com.hedera.test.mocks.TestContextValidator.CONSENSUS_NOW;
import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;

import com.google.protobuf.StringValue;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.backing.BackingTokens;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HederaTokenStoreTest {
    private static final Key newKey = TxnHandlingScenario.TOKEN_REPLACE_KT.asKey();
    private static final JKey newFcKey = TxnHandlingScenario.TOKEN_REPLACE_KT.asJKeyUnchecked();
    private static final Key adminKey = TOKEN_ADMIN_KT.asKey();
    private static final Key kycKey = TOKEN_KYC_KT.asKey();
    private static final Key freezeKey = TOKEN_FREEZE_KT.asKey();
    private static final Key wipeKey = MISC_ACCOUNT_KT.asKey();
    private static final Key supplyKey = COMPLEX_KEY_ACCOUNT_KT.asKey();
    private static final Key feeScheduleKey = TOKEN_FEE_SCHEDULE_KT.asKey();
    private static final Key pauseKey = TOKEN_PAUSE_KT.asKey();

    private static final String symbol = "NOTHBAR";
    private static final String newSymbol = "REALLYSOM";
    private static final String newMemo = "NEWMEMO";
    private static final String memo = "TOKENMEMO";
    private static final String name = "TOKENNAME";
    private static final String newName = "NEWNAME";
    private static final int maxCustomFees = 5;
    private static final int associatedTokensCount = 2;
    private static final int numPositiveBalances = 1;
    private static final long expiry = CONSENSUS_NOW + 1_234_567L;
    private static final long newExpiry = CONSENSUS_NOW + 1_432_765L;
    private static final long totalSupply = 1_000_000L;
    private static final int decimals = 10;
    private static final long treasuryBalance = 50_000L;
    private static final long sponsorBalance = 1_000L;
    private static final TokenID misc = IdUtils.asToken("0.0.1");
    private static final TokenID nonfungible = IdUtils.asToken("0.0.2");
    private static final int maxAutoAssociations = 1234;
    private static final int alreadyUsedAutoAssocitaions = 123;
    private static final boolean freezeDefault = true;
    private static final long newAutoRenewPeriod = 2_000_000L;
    private static final AccountID payer = IdUtils.asAccount("0.0.12345");
    private static final AccountID autoRenewAccount = IdUtils.asAccount("0.0.5");
    private static final AccountID newAutoRenewAccount = IdUtils.asAccount("0.0.6");
    private static final AccountID primaryTreasury = IdUtils.asAccount("0.0.9898");
    private static final AccountID treasury = IdUtils.asAccount("0.0.3");
    private static final AccountID newTreasury = IdUtils.asAccount("0.0.1");
    private static final AccountID sponsor = IdUtils.asAccount("0.0.666");
    private static final AccountID counterparty = IdUtils.asAccount("0.0.777");
    private static final AccountID anotherFeeCollector = IdUtils.asAccount("0.0.777");
    private static final TokenID created = IdUtils.asToken("0.0.666666");
    private static final TokenID pending = IdUtils.asToken("0.0.555555");
    private static final int MAX_TOKENS_PER_ACCOUNT = 100;
    private static final int MAX_TOKEN_SYMBOL_UTF8_BYTES = 10;
    private static final int MAX_TOKEN_NAME_UTF8_BYTES = 100;
    private static final Pair<AccountID, TokenID> sponsorMisc = asTokenRel(sponsor, misc);
    private static final Pair<AccountID, TokenID> treasuryNft =
            asTokenRel(primaryTreasury, nonfungible);
    private static final Pair<AccountID, TokenID> newTreasuryNft =
            asTokenRel(newTreasury, nonfungible);
    private static final Pair<AccountID, TokenID> sponsorNft = asTokenRel(sponsor, nonfungible);
    private static final Pair<AccountID, TokenID> counterpartyNft =
            asTokenRel(counterparty, nonfungible);
    private static final Pair<AccountID, TokenID> treasuryMisc = asTokenRel(treasury, misc);
    private static final NftId aNft = new NftId(0, 0, 2, 1234);
    private static final NftId tNft = new NftId(0, 0, 2, 12345);
    private static final Pair<AccountID, TokenID> anotherFeeCollectorMisc =
            asTokenRel(anotherFeeCollector, misc);
    private EntityIdSource ids;
    private SideEffectsTracker sideEffectsTracker;
    private GlobalDynamicProperties properties;
    private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRelsLedger;
    private BackingTokens backingTokens;
    private HederaLedger hederaLedger;
    private UsageLimits usageLimits;
    private MerkleToken token;
    private MerkleToken nonfungibleToken;

    private HederaTokenStore subject;

    @BeforeEach
    void setup() {
        token = mock(MerkleToken.class);
        given(token.expiry()).willReturn(expiry);
        given(token.symbol()).willReturn(symbol);
        given(token.hasAutoRenewAccount()).willReturn(true);
        given(token.adminKey()).willReturn(Optional.of(TOKEN_ADMIN_KT.asJKeyUnchecked()));
        given(token.name()).willReturn(name);
        given(token.hasAdminKey()).willReturn(true);
        given(token.hasFeeScheduleKey()).willReturn(true);
        given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(treasury));
        given(token.tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(token.decimals()).willReturn(2);

        nonfungibleToken = mock(MerkleToken.class);
        given(nonfungibleToken.hasAdminKey()).willReturn(true);
        given(nonfungibleToken.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        ids = mock(EntityIdSource.class);
        given(ids.newTokenId(sponsor)).willReturn(created);

        hederaLedger = mock(HederaLedger.class);

        nftsLedger =
                (TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter>)
                        mock(TransactionalLedger.class);
        given(nftsLedger.get(aNft, OWNER)).willReturn(EntityId.fromGrpcAccountId(sponsor));
        given(nftsLedger.get(tNft, OWNER)).willReturn(EntityId.fromGrpcAccountId(primaryTreasury));
        given(nftsLedger.exists(aNft)).willReturn(true);
        given(nftsLedger.exists(tNft)).willReturn(true);

        accountsLedger =
                (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>)
                        mock(TransactionalLedger.class);
        given(accountsLedger.exists(treasury)).willReturn(true);
        given(accountsLedger.exists(anotherFeeCollector)).willReturn(true);
        given(accountsLedger.exists(autoRenewAccount)).willReturn(true);
        given(accountsLedger.exists(newAutoRenewAccount)).willReturn(true);
        given(accountsLedger.exists(primaryTreasury)).willReturn(true);
        given(accountsLedger.exists(sponsor)).willReturn(true);
        given(accountsLedger.exists(counterparty)).willReturn(true);
        given(accountsLedger.get(treasury, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(autoRenewAccount, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(newAutoRenewAccount, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(sponsor, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(counterparty, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(primaryTreasury, IS_DELETED)).willReturn(false);

        backingTokens = mock(BackingTokens.class);
        given(backingTokens.contains(misc)).willReturn(true);
        given(backingTokens.contains(nonfungible)).willReturn(true);
        given(backingTokens.getRef(created)).willReturn(token);
        given(backingTokens.getImmutableRef(created)).willReturn(token);
        given(backingTokens.getRef(misc)).willReturn(token);
        given(backingTokens.getImmutableRef(misc)).willReturn(token);
        given(backingTokens.getRef(nonfungible)).willReturn(nonfungibleToken);
        given(backingTokens.getImmutableRef(tNft.tokenId())).willReturn(nonfungibleToken);
        given(backingTokens.getImmutableRef(tNft.tokenId()).treasury())
                .willReturn(EntityId.fromGrpcAccountId(primaryTreasury));
        given(backingTokens.idSet()).willReturn(Set.of(created));

        tokenRelsLedger = mock(TransactionalLedger.class);
        given(tokenRelsLedger.exists(sponsorMisc)).willReturn(true);
        given(tokenRelsLedger.exists(treasuryNft)).willReturn(true);
        given(tokenRelsLedger.exists(sponsorNft)).willReturn(true);
        given(tokenRelsLedger.exists(counterpartyNft)).willReturn(true);
        given(tokenRelsLedger.get(sponsorMisc, TOKEN_BALANCE)).willReturn(sponsorBalance);
        given(tokenRelsLedger.get(sponsorMisc, IS_FROZEN)).willReturn(false);
        given(tokenRelsLedger.get(sponsorMisc, IS_KYC_GRANTED)).willReturn(true);
        given(tokenRelsLedger.exists(treasuryMisc)).willReturn(true);
        given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(true);
        given(tokenRelsLedger.get(treasuryMisc, TOKEN_BALANCE)).willReturn(treasuryBalance);
        given(tokenRelsLedger.get(treasuryMisc, IS_FROZEN)).willReturn(false);
        given(tokenRelsLedger.get(treasuryMisc, IS_KYC_GRANTED)).willReturn(true);
        given(tokenRelsLedger.get(treasuryNft, TOKEN_BALANCE)).willReturn(123L);
        given(tokenRelsLedger.get(treasuryNft, IS_FROZEN)).willReturn(false);
        given(tokenRelsLedger.get(treasuryNft, IS_KYC_GRANTED)).willReturn(true);
        given(tokenRelsLedger.get(sponsorNft, TOKEN_BALANCE)).willReturn(123L);
        given(tokenRelsLedger.get(sponsorNft, IS_FROZEN)).willReturn(false);
        given(tokenRelsLedger.get(sponsorNft, IS_KYC_GRANTED)).willReturn(true);
        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE)).willReturn(123L);
        given(tokenRelsLedger.get(counterpartyNft, IS_FROZEN)).willReturn(false);
        given(tokenRelsLedger.get(counterpartyNft, IS_KYC_GRANTED)).willReturn(true);
        given(tokenRelsLedger.get(newTreasuryNft, TOKEN_BALANCE)).willReturn(1L);

        properties = mock(GlobalDynamicProperties.class);
        given(properties.maxTokensRelsPerInfoQuery()).willReturn(MAX_TOKENS_PER_ACCOUNT);
        given(properties.maxTokenSymbolUtf8Bytes()).willReturn(MAX_TOKEN_SYMBOL_UTF8_BYTES);
        given(properties.maxTokenNameUtf8Bytes()).willReturn(MAX_TOKEN_NAME_UTF8_BYTES);
        given(properties.maxCustomFeesAllowed()).willReturn(maxCustomFees);

        usageLimits = mock(UsageLimits.class);

        sideEffectsTracker = new SideEffectsTracker();
        subject =
                new HederaTokenStore(
                        ids,
                        usageLimits,
                        TEST_VALIDATOR,
                        sideEffectsTracker,
                        properties,
                        tokenRelsLedger,
                        nftsLedger,
                        backingTokens);
        subject.setAccountsLedger(accountsLedger);
        subject.setHederaLedger(hederaLedger);
    }

    @Test
    void injectsTokenRelsLedger() {
        verify(hederaLedger).setTokenRelsLedger(tokenRelsLedger);
        verify(hederaLedger).setNftsLedger(nftsLedger);
    }

    @Test
    void applicationRejectsMissing() {
        final var change = mock(Consumer.class);

        given(backingTokens.contains(misc)).willReturn(false);

        assertThrows(IllegalArgumentException.class, () -> subject.apply(misc, change));
    }

    @Test
    void applicationAlwaysReplacesModifiableToken() {
        final var change = mock(Consumer.class);
        final var modifiableToken = mock(MerkleToken.class);
        given(backingTokens.getRef(misc)).willReturn(modifiableToken);
        willThrow(IllegalStateException.class).given(change).accept(modifiableToken);

        assertThrows(IllegalArgumentException.class, () -> subject.apply(misc, change));
    }

    @Test
    void applicationWorks() {
        final var change = mock(Consumer.class);
        final var inOrder = Mockito.inOrder(change, backingTokens);

        subject.apply(misc, change);

        inOrder.verify(backingTokens).getRef(misc);
        inOrder.verify(change).accept(token);
        inOrder.verify(backingTokens).put(misc, token);
    }

    @Test
    void deletionWorksAsExpected() {
        TokenStore.DELETION.accept(token);

        verify(token).setDeleted(true);
    }

    @Test
    void rejectsDeletionMissingAdminKey() {
        given(token.adminKey()).willReturn(Optional.empty());

        final var outcome = subject.delete(misc);

        assertEquals(TOKEN_IS_IMMUTABLE, outcome);
    }

    @Test
    void rejectsDeletionTokenAlreadyDeleted() {
        given(token.isDeleted()).willReturn(true);

        final var outcome = subject.delete(misc);

        assertEquals(TOKEN_WAS_DELETED, outcome);
    }

    @Test
    void rejectsMissingDeletion() {
        final var mockSubject = mock(TokenStore.class);
        given(mockSubject.resolve(misc)).willReturn(TokenStore.MISSING_TOKEN);
        willCallRealMethod().given(mockSubject).delete(misc);

        final var outcome = mockSubject.delete(misc);

        assertEquals(INVALID_TOKEN_ID, outcome);
        verify(mockSubject, never()).apply(any(), any());
    }

    @Test
    void getDelegates() {
        assertSame(token, subject.get(misc));
    }

    @Test
    void getThrowsIseOnMissing() {
        given(backingTokens.contains(misc)).willReturn(false);

        assertThrows(IllegalArgumentException.class, () -> subject.get(misc));
    }

    @Test
    void getCanReturnPending() {
        subject.pendingId = pending;
        subject.pendingCreation = token;

        assertSame(token, subject.get(pending));
    }

    @Test
    void existenceCheckUnderstandsPendingIdOnlyAppliesIfCreationPending() {
        assertFalse(subject.exists(HederaTokenStore.NO_PENDING_ID));
    }

    @Test
    void existenceCheckIncludesPending() {
        subject.pendingId = pending;

        assertTrue(subject.exists(pending));
    }

    @Test
    void freezingRejectsMissingAccount() {
        given(accountsLedger.exists(sponsor)).willReturn(false);

        final var status = subject.freeze(sponsor, misc);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void associatingRejectsDeletedTokens() {
        given(token.isDeleted()).willReturn(true);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKEN_WAS_DELETED, status);
    }

    @Test
    void associatingRejectsMissingToken() {
        given(backingTokens.contains(misc)).willReturn(false);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(INVALID_TOKEN_ID, status);
    }

    @Test
    void associatingRejectsMissingAccounts() {
        given(accountsLedger.exists(sponsor)).willReturn(false);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void realAssociationsExist() {
        assertTrue(subject.associationExists(sponsor, misc));
    }

    @Test
    void noAssociationsWithMissingAccounts() {
        given(accountsLedger.exists(sponsor)).willReturn(false);

        assertFalse(subject.associationExists(sponsor, misc));
    }

    @Test
    void associatingRejectsAlreadyAssociatedTokens() {
        given(tokenRelsLedger.contains(Pair.of(sponsor, misc))).willReturn(true);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void cannotAutoAssociateIfAccountReachedTokenAssociationLimit() {
        given(tokenRelsLedger.contains(Pair.of(sponsor, misc))).willReturn(false);
        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(properties.areTokenAssociationsLimited()).willReturn(true);
        given(properties.maxTokensPerAccount()).willReturn(associatedTokensCount);
        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
    }

    @Test
    void cannotAutoAssociateIfUsageLimitsReached() {
        given(tokenRelsLedger.contains(Pair.of(sponsor, misc))).willReturn(false);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, status);
    }

    @Test
    void autoAssociatingHappyPathWorksOnEmptyExistingAssociations() {
        final var key = asTokenRel(sponsor, misc);

        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(sponsor, MAX_AUTOMATIC_ASSOCIATIONS))
                .willReturn(maxAutoAssociations);
        given(accountsLedger.get(sponsor, USED_AUTOMATIC_ASSOCIATIONS))
                .willReturn(alreadyUsedAutoAssocitaions);
        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);

        given(token.hasKycKey()).willReturn(true);
        given(token.hasFreezeKey()).willReturn(true);
        given(token.accountsAreFrozenByDefault()).willReturn(true);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(OK, status);
        verify(tokenRelsLedger).create(key);
        verify(tokenRelsLedger).set(key, TokenRelProperty.IS_FROZEN, true);
        verify(tokenRelsLedger).set(key, TokenRelProperty.IS_KYC_GRANTED, false);
        verify(tokenRelsLedger).set(key, TokenRelProperty.IS_AUTOMATIC_ASSOCIATION, true);
        verify(accountsLedger).set(sponsor, NUM_ASSOCIATIONS, associatedTokensCount + 1);
        verify(accountsLedger)
                .set(sponsor, USED_AUTOMATIC_ASSOCIATIONS, alreadyUsedAutoAssocitaions + 1);
    }

    @Test
    void autoAssociatingHappyPathWorksOnAccountWithExistingAssociations() {
        final var key = asTokenRel(sponsor, misc);

        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(sponsor, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
        given(accountsLedger.get(sponsor, MAX_AUTOMATIC_ASSOCIATIONS))
                .willReturn(maxAutoAssociations);
        given(accountsLedger.get(sponsor, USED_AUTOMATIC_ASSOCIATIONS))
                .willReturn(alreadyUsedAutoAssocitaions);
        given(properties.areTokenAssociationsLimited()).willReturn(true);
        given(properties.maxTokensPerAccount()).willReturn(associatedTokensCount + 1);

        given(token.hasKycKey()).willReturn(true);
        given(token.hasFreezeKey()).willReturn(true);
        given(token.accountsAreFrozenByDefault()).willReturn(true);
        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(OK, status);
        verify(tokenRelsLedger).create(key);
        verify(tokenRelsLedger).set(key, TokenRelProperty.IS_FROZEN, true);
        verify(tokenRelsLedger).set(key, TokenRelProperty.IS_KYC_GRANTED, false);
        verify(tokenRelsLedger).set(key, TokenRelProperty.IS_AUTOMATIC_ASSOCIATION, true);
        verify(accountsLedger).set(sponsor, NUM_ASSOCIATIONS, associatedTokensCount + 1);
    }

    @Test
    void associatingFailsWhenAutoAssociationLimitReached() {

        given(accountsLedger.get(sponsor, MAX_AUTOMATIC_ASSOCIATIONS))
                .willReturn(maxAutoAssociations);
        given(accountsLedger.get(sponsor, USED_AUTOMATIC_ASSOCIATIONS))
                .willReturn(maxAutoAssociations);
        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);

        // auto associate a fungible token
        var status = subject.autoAssociate(sponsor, misc);
        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);

        // auto associate a fungibleUnique token
        status = subject.autoAssociate(sponsor, nonfungible);
        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);
    }

    @Test
    void grantingKycRejectsMissingAccount() {
        given(accountsLedger.exists(sponsor)).willReturn(false);

        final var status = subject.grantKyc(sponsor, misc);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void grantingKycRejectsDetachedAccount() {
        final var detachedSponsor = AccountID.newBuilder().setAccountNum(666_666).build();
        given(accountsLedger.exists(detachedSponsor)).willReturn(true);
        given(accountsLedger.get(detachedSponsor, IS_DELETED)).willReturn(false);

        final var status = subject.grantKyc(detachedSponsor, misc);

        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void grantingKycRejectsDeletedAccount() {
        given(accountsLedger.exists(sponsor)).willReturn(true);
        given(accountsLedger.get(sponsor, IS_DELETED)).willReturn(true);

        final var status = subject.grantKyc(sponsor, misc);

        assertEquals(ACCOUNT_DELETED, status);
    }

    @Test
    void revokingKycRejectsMissingAccount() {
        given(accountsLedger.exists(sponsor)).willReturn(false);

        final var status = subject.revokeKyc(sponsor, misc);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void adjustingRejectsMissingAccount() {
        given(accountsLedger.exists(sponsor)).willReturn(false);

        final var status = subject.adjustBalance(sponsor, misc, 1);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingSender() {
        given(accountsLedger.exists(sponsor)).willReturn(false);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingReceiver() {
        given(accountsLedger.exists(counterparty)).willReturn(false);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingNftInstance() {
        given(nftsLedger.exists(aNft)).willReturn(false);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_NFT_ID, status);
    }

    @Test
    void changingOwnerRejectsUnassociatedReceiver() {
        given(tokenRelsLedger.exists(counterpartyNft)).willReturn(false);
        given(accountsLedger.get(counterparty, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(0);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void changingOwnerAutoAssociatesCounterpartyWithOpenSlots() {
        final long startSponsorNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startSponsorANfts = 1;
        final long startCounterpartyANfts = 0;

        given(accountsLedger.get(counterparty, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(100);
        given(accountsLedger.get(counterparty, USED_AUTOMATIC_ASSOCIATIONS)).willReturn(0);
        given(accountsLedger.get(sponsor, NUM_NFTS_OWNED)).willReturn(startSponsorNfts);
        given(accountsLedger.get(counterparty, NUM_NFTS_OWNED)).willReturn(startCounterpartyNfts);
        given(tokenRelsLedger.get(sponsorNft, TOKEN_BALANCE)).willReturn(startSponsorANfts);
        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE))
                .willReturn(startCounterpartyANfts);
        given(tokenRelsLedger.exists(counterpartyNft)).willReturn(false);
        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(sponsor, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
        given(accountsLedger.get(counterparty, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(counterparty, NUM_POSITIVE_BALANCES))
                .willReturn(numPositiveBalances);
        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(OK, status);
        verify(accountsLedger).set(counterparty, NUM_ASSOCIATIONS, associatedTokensCount + 1);
        verify(accountsLedger).set(counterparty, NUM_POSITIVE_BALANCES, numPositiveBalances + 1);
    }

    @Test
    void changingOwnerRejectsIllegitimateOwner() {
        given(nftsLedger.get(aNft, OWNER)).willReturn(EntityId.fromGrpcAccountId(counterparty));

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, status);
    }

    @Test
    void changingOwnerDoesTheExpected() {
        final long startSponsorNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startSponsorANfts = 4;
        final long startCounterpartyANfts = 1;
        final var receiver = EntityId.fromGrpcAccountId(counterparty);
        final var nftNumPair1 = NftNumPair.fromLongs(1111, 111);
        final var nftId1 = nftNumPair1.nftId();
        final var nftNumPair2 = NftNumPair.fromLongs(1112, 112);
        final var nftId2 = nftNumPair2.nftId();
        final var nftNumPair3 = NftNumPair.fromLongs(1113, 113);
        final var nftId3 = nftNumPair3.nftId();
        given(accountsLedger.get(sponsor, NUM_NFTS_OWNED)).willReturn(startSponsorNfts);
        given(accountsLedger.get(counterparty, NUM_NFTS_OWNED)).willReturn(startCounterpartyNfts);
        given(tokenRelsLedger.get(sponsorNft, TOKEN_BALANCE)).willReturn(startSponsorANfts);
        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE))
                .willReturn(startCounterpartyANfts);
        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(sponsor, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
        given(accountsLedger.get(counterparty, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(counterparty, NUM_POSITIVE_BALANCES))
                .willReturn(numPositiveBalances);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(OK, status);
        verify(accountsLedger, never())
                .set(counterparty, NUM_ASSOCIATIONS, associatedTokensCount + 1);
        verify(accountsLedger, never())
                .set(counterparty, NUM_POSITIVE_BALANCES, numPositiveBalances + 1);
        verify(nftsLedger).set(aNft, OWNER, receiver);
        verify(accountsLedger).set(sponsor, NUM_NFTS_OWNED, startSponsorNfts - 1);
        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, startCounterpartyNfts + 1);
        verify(tokenRelsLedger).set(sponsorNft, TOKEN_BALANCE, startSponsorANfts - 1);
        verify(tokenRelsLedger).set(counterpartyNft, TOKEN_BALANCE, startCounterpartyANfts + 1);
        assertSoleTokenChangesAreForNftTransfer(aNft, sponsor, counterparty);
    }

    @Test
    void changingOwnerDoesTheExpectedWithTreasuryReturn() {
        final long startTreasuryNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startTreasuryTNfts = 4;
        final long startCounterpartyTNfts = 1;
        final var sender = EntityId.fromGrpcAccountId(counterparty);
        final var receiver = EntityId.fromGrpcAccountId(primaryTreasury);
        final var muti = EntityNumPair.fromLongs(tNft.tokenId().getTokenNum(), tNft.serialNo());
        given(backingTokens.getImmutableRef(tNft.tokenId()).treasury()).willReturn(receiver);
        given(accountsLedger.get(primaryTreasury, NUM_NFTS_OWNED)).willReturn(startTreasuryNfts);
        given(accountsLedger.get(counterparty, NUM_NFTS_OWNED)).willReturn(startCounterpartyNfts);
        given(tokenRelsLedger.get(treasuryNft, TOKEN_BALANCE)).willReturn(startTreasuryTNfts);
        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE))
                .willReturn(startCounterpartyTNfts);
        given(nftsLedger.get(tNft, OWNER)).willReturn(EntityId.fromGrpcAccountId(counterparty));
        given(accountsLedger.get(primaryTreasury, NUM_ASSOCIATIONS))
                .willReturn(associatedTokensCount);
        given(accountsLedger.get(primaryTreasury, NUM_POSITIVE_BALANCES))
                .willReturn(numPositiveBalances);
        given(accountsLedger.get(counterparty, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(counterparty, NUM_POSITIVE_BALANCES))
                .willReturn(numPositiveBalances);

        final var status = subject.changeOwner(tNft, counterparty, primaryTreasury);

        assertEquals(OK, status);
        verify(nftsLedger).set(tNft, OWNER, MISSING_ENTITY_ID);
        verify(accountsLedger).set(primaryTreasury, NUM_NFTS_OWNED, startTreasuryNfts + 1);
        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, startCounterpartyNfts - 1);
        verify(tokenRelsLedger).set(treasuryNft, TOKEN_BALANCE, startTreasuryTNfts + 1);
        verify(tokenRelsLedger).set(counterpartyNft, TOKEN_BALANCE, startCounterpartyTNfts - 1);
        verify(accountsLedger).set(primaryTreasury, NUM_POSITIVE_BALANCES, numPositiveBalances);
        verify(accountsLedger).set(counterparty, NUM_POSITIVE_BALANCES, numPositiveBalances - 1);
        assertSoleTokenChangesAreForNftTransfer(tNft, counterparty, primaryTreasury);
    }

    @Test
    void changingOwnerDoesTheExpectedWithTreasuryExit() {
        final long startTreasuryNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startTreasuryTNfts = 4;
        final long startCounterpartyTNfts = 1;
        final var sender = EntityId.fromGrpcAccountId(primaryTreasury);
        final var receiver = EntityId.fromGrpcAccountId(counterparty);
        final var nftNumPair3 = NftNumPair.fromLongs(1113, 113);
        final var nftId3 = nftNumPair3.nftId();
        given(accountsLedger.get(primaryTreasury, NUM_NFTS_OWNED)).willReturn(startTreasuryNfts);
        given(accountsLedger.get(counterparty, NUM_NFTS_OWNED)).willReturn(startCounterpartyNfts);
        given(tokenRelsLedger.get(treasuryNft, TOKEN_BALANCE)).willReturn(startTreasuryTNfts);
        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE))
                .willReturn(startCounterpartyTNfts);
        given(nftsLedger.get(tNft, OWNER)).willReturn(EntityId.MISSING_ENTITY_ID);
        given(backingTokens.getImmutableRef(tNft.tokenId()).treasury()).willReturn(sender);
        given(accountsLedger.get(primaryTreasury, NUM_ASSOCIATIONS))
                .willReturn(associatedTokensCount);
        given(accountsLedger.get(primaryTreasury, NUM_POSITIVE_BALANCES))
                .willReturn(numPositiveBalances);
        given(accountsLedger.get(counterparty, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(counterparty, NUM_POSITIVE_BALANCES))
                .willReturn(numPositiveBalances);

        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);

        assertEquals(OK, status);
        verify(nftsLedger).set(tNft, OWNER, receiver);
        verify(accountsLedger).set(primaryTreasury, NUM_NFTS_OWNED, startTreasuryNfts - 1);
        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, startCounterpartyNfts + 1);
        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, startCounterpartyNfts + 1);
        verify(tokenRelsLedger).set(treasuryNft, TOKEN_BALANCE, startTreasuryTNfts - 1);
        verify(tokenRelsLedger).set(counterpartyNft, TOKEN_BALANCE, startCounterpartyTNfts + 1);
        verify(accountsLedger).set(primaryTreasury, NUM_POSITIVE_BALANCES, numPositiveBalances);
        verify(accountsLedger).set(counterparty, NUM_POSITIVE_BALANCES, numPositiveBalances);
        assertSoleTokenChangesAreForNftTransfer(tNft, primaryTreasury, counterparty);
    }

    @Test
    void changingOwnerWildCardDoesTheExpectedWithTreasury() {
        final long startTreasuryNfts = 1;
        final long startCounterpartyNfts = 0;
        final long startTreasuryTNfts = 1;
        final long startCounterpartyTNfts = 0;
        given(accountsLedger.get(primaryTreasury, NUM_NFTS_OWNED)).willReturn(startTreasuryNfts);
        given(accountsLedger.get(counterparty, NUM_NFTS_OWNED)).willReturn(startCounterpartyNfts);
        given(tokenRelsLedger.get(treasuryNft, TOKEN_BALANCE)).willReturn(startTreasuryTNfts);
        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE))
                .willReturn(startCounterpartyTNfts);

        final var status = subject.changeOwnerWildCard(tNft, primaryTreasury, counterparty);

        assertEquals(OK, status);
        verify(accountsLedger).set(primaryTreasury, NUM_NFTS_OWNED, 0L);
        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, 1L);
        verify(tokenRelsLedger).set(treasuryNft, TOKEN_BALANCE, startTreasuryTNfts - 1);
        verify(tokenRelsLedger).set(counterpartyNft, TOKEN_BALANCE, startCounterpartyTNfts + 1);
        assertSoleTokenChangesAreForNftTransfer(tNft, primaryTreasury, counterparty);
    }

    @Test
    void changingOwnerWildCardRejectsFromFreezeAndKYC() {
        given(tokenRelsLedger.get(treasuryNft, IS_FROZEN)).willReturn(true);

        final var status = subject.changeOwnerWildCard(tNft, primaryTreasury, counterparty);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void changingOwnerWildCardRejectsToFreezeAndKYC() {
        given(tokenRelsLedger.get(counterpartyNft, IS_FROZEN)).willReturn(true);

        final var status = subject.changeOwnerWildCard(tNft, primaryTreasury, counterparty);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void changingOwnerRejectsFromFreezeAndKYC() {
        given(tokenRelsLedger.get(treasuryNft, IS_FROZEN)).willReturn(true);

        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void changingOwnerRejectsToFreezeAndKYC() {
        given(tokenRelsLedger.get(counterpartyNft, IS_FROZEN)).willReturn(true);

        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void updateRejectsInvalidExpiry() {
        final var op =
                updateWith(NO_KEYS, misc, true, true, false).toBuilder()
                        .setExpiry(Timestamp.newBuilder().setSeconds(expiry - 1))
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_EXPIRATION_TIME, outcome);
    }

    @Test
    void canExtendImmutableExpiry() {
        given(token.hasAdminKey()).willReturn(false);
        final var op =
                updateWith(NO_KEYS, misc, false, false, false).toBuilder()
                        .setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234))
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
    }

    @Test
    void cannotUpdateImmutableTokenWithNewFeeScheduleKey() {
        given(token.hasAdminKey()).willReturn(false);
        given(token.hasFeeScheduleKey()).willReturn(true);
        final var op =
                updateWith(NO_KEYS, misc, false, false, false).toBuilder()
                        .setFeeScheduleKey(feeScheduleKey)
                        .setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234))
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_IS_IMMUTABLE, outcome);
    }

    @Test
    void cannotUpdateImmutableTokenWithNewPauseKey() {
        given(token.hasAdminKey()).willReturn(false);
        given(token.hasPauseKey()).willReturn(true);
        final var op =
                updateWith(NO_KEYS, misc, false, false, false).toBuilder()
                        .setPauseKey(pauseKey)
                        .setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234))
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_IS_IMMUTABLE, outcome);
    }

    @Test
    void ifImmutableWillStayImmutable() {
        givenUpdateTarget(ALL_KEYS, token);
        given(token.hasFeeScheduleKey()).willReturn(false);
        final var op =
                updateWith(ALL_KEYS, misc, false, false, false).toBuilder()
                        .setFeeScheduleKey(feeScheduleKey)
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_FEE_SCHEDULE_KEY, outcome);
    }

    @Test
    void cannotUpdateNewPauseKeyIfTokenHasNoPauseKey() {
        givenUpdateTarget(ALL_KEYS, token);
        given(token.hasPauseKey()).willReturn(false);
        final var op =
                updateWith(ALL_KEYS, misc, false, false, false).toBuilder()
                        .setPauseKey(pauseKey)
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_PAUSE_KEY, outcome);
    }

    @Test
    void updateRejectsInvalidNewAutoRenew() {
        given(accountsLedger.exists(newAutoRenewAccount)).willReturn(false);
        final var op = updateWith(NO_KEYS, misc, true, true, false, true, false);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_AUTORENEW_ACCOUNT, outcome);
    }

    @Test
    void updateRejectsInvalidNewAutoRenewPeriod() {
        final var op =
                updateWith(NO_KEYS, misc, true, true, false, false, false).toBuilder()
                        .setAutoRenewPeriod(enduring(-1L))
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_RENEWAL_PERIOD, outcome);
    }

    @Test
    void updateRejectsMissingToken() {
        given(backingTokens.contains(misc)).willReturn(false);
        givenUpdateTarget(ALL_KEYS, token);
        final var op = updateWith(ALL_KEYS, misc, true, true, true);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_TOKEN_ID, outcome);
    }

    @Test
    void updateRejectsInappropriateKycKey() {
        givenUpdateTarget(NO_KEYS, token);
        final var op = updateWith(EnumSet.of(KeyType.KYC), misc, false, false, false);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_KYC_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateFreezeKey() {
        givenUpdateTarget(NO_KEYS, token);
        final var op = updateWith(EnumSet.of(KeyType.FREEZE), misc, false, false, false);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_FREEZE_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateWipeKey() {
        givenUpdateTarget(NO_KEYS, token);
        final var op = updateWith(EnumSet.of(KeyType.WIPE), misc, false, false, false);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_WIPE_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateSupplyKey() {
        givenUpdateTarget(NO_KEYS, token);
        final var op = updateWith(EnumSet.of(KeyType.SUPPLY), misc, false, false, false);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, outcome);
    }

    @Test
    void updateRejectsZeroTokenBalanceKey() {
        final Set<TokenID> tokenSet = new HashSet<>();
        tokenSet.add(nonfungible);
        givenUpdateTarget(ALL_KEYS, nonfungibleToken);
        final var op =
                updateWith(ALL_KEYS, nonfungible, true, true, true).toBuilder()
                        .setExpiry(Timestamp.newBuilder().setSeconds(0))
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES, outcome);
    }

    @Test
    void updateHappyPathIgnoresZeroExpiry() {
        final Set<TokenID> tokenSet = new HashSet<>();
        tokenSet.add(misc);
        givenUpdateTarget(ALL_KEYS, token);
        final var op =
                updateWith(ALL_KEYS, misc, true, true, true).toBuilder()
                        .setExpiry(Timestamp.newBuilder().setSeconds(0))
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
        verify(token, never()).setExpiry(anyLong());
    }

    @Test
    void updateRemovesAdminKeyWhenAppropos() {
        givenUpdateTarget(EnumSet.noneOf(KeyType.class), token);
        final var op = updateWith(EnumSet.of(KeyType.EMPTY_ADMIN), misc, false, false, false);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
        verify(token).setAdminKey(MerkleToken.UNUSED_KEY);
    }

    @Test
    void updateHappyPathWorksForEverythingWithNewExpiry() {
        givenUpdateTarget(ALL_KEYS, token);
        final var op =
                updateWith(ALL_KEYS, misc, true, true, true).toBuilder()
                        .setExpiry(Timestamp.newBuilder().setSeconds(newExpiry))
                        .setFeeScheduleKey(newKey)
                        .build();

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
        verify(token).setSymbol(newSymbol);
        verify(token).setName(newName);
        verify(token).setExpiry(newExpiry);
        verify(token).setTreasury(EntityId.fromGrpcAccountId(newTreasury));
        verify(token).setAdminKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
        verify(token).setFreezeKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
        verify(token).setKycKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
        verify(token).setSupplyKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
        verify(token).setWipeKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
        verify(token)
                .setFeeScheduleKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
    }

    @Test
    void updateHappyPathWorksWithNewMemo() {
        givenUpdateTarget(ALL_KEYS, token);
        final var op = updateWith(NO_KEYS, misc, false, false, false, false, false, false, true);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
        verify(token).setMemo(newMemo);
    }

    @Test
    void updateHappyPathWorksWithNewMemoForNonfungible() {
        given(token.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        givenUpdateTarget(ALL_KEYS, token);
        final var op = updateWith(NO_KEYS, misc, false, false, false, false, false, false, true);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
        verify(token).setMemo(newMemo);
    }

    @Test
    void updateHappyPathWorksWithNewAutoRenewAccount() {
        givenUpdateTarget(ALL_KEYS, token);
        final var op = updateWith(ALL_KEYS, misc, true, true, true, true, true);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
        verify(token).setAutoRenewAccount(EntityId.fromGrpcAccountId(newAutoRenewAccount));
        verify(token).setAutoRenewPeriod(newAutoRenewPeriod);
    }

    enum KeyType {
        WIPE,
        FREEZE,
        SUPPLY,
        KYC,
        ADMIN,
        EMPTY_ADMIN,
        FEE_SCHEDULE,
        PAUSE
    }

    private static final EnumSet<KeyType> NO_KEYS = EnumSet.noneOf(KeyType.class);
    private static final EnumSet<KeyType> ALL_KEYS =
            EnumSet.complementOf(EnumSet.of(KeyType.EMPTY_ADMIN));

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury) {
        return updateWith(keys, tokenId, useNewName, useNewSymbol, useNewTreasury, false, false);
    }

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury,
            final boolean useNewAutoRenewAccount,
            final boolean useNewAutoRenewPeriod) {
        return updateWith(
                keys,
                tokenId,
                useNewSymbol,
                useNewName,
                useNewTreasury,
                useNewAutoRenewAccount,
                useNewAutoRenewPeriod,
                false,
                false);
    }

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury,
            final boolean useNewAutoRenewAccount,
            final boolean useNewAutoRenewPeriod,
            final boolean setInvalidKeys,
            final boolean useNewMemo) {
        final var invalidKey = Key.getDefaultInstance();
        final var op = TokenUpdateTransactionBody.newBuilder().setToken(tokenId);
        if (useNewSymbol) {
            op.setSymbol(newSymbol);
        }
        if (useNewName) {
            op.setName(newName);
        }
        if (useNewMemo) {
            op.setMemo(StringValue.newBuilder().setValue(newMemo).build());
        }
        if (useNewTreasury) {
            op.setTreasury(newTreasury);
        }
        if (useNewAutoRenewAccount) {
            op.setAutoRenewAccount(newAutoRenewAccount);
        }
        if (useNewAutoRenewPeriod) {
            op.setAutoRenewPeriod(enduring(newAutoRenewPeriod));
        }
        for (final var key : keys) {
            switch (key) {
                case WIPE:
                    op.setWipeKey(setInvalidKeys ? invalidKey : newKey);
                    break;
                case FREEZE:
                    op.setFreezeKey(setInvalidKeys ? invalidKey : newKey);
                    break;
                case SUPPLY:
                    op.setSupplyKey(setInvalidKeys ? invalidKey : newKey);
                    break;
                case KYC:
                    op.setKycKey(setInvalidKeys ? invalidKey : newKey);
                    break;
                case ADMIN:
                    op.setAdminKey(setInvalidKeys ? invalidKey : newKey);
                    break;
                case EMPTY_ADMIN:
                    op.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY);
                    break;
            }
        }
        return op.build();
    }

    private void givenUpdateTarget(final EnumSet<KeyType> keys, final MerkleToken token) {
        if (keys.contains(KeyType.WIPE)) {
            given(token.hasWipeKey()).willReturn(true);
        }
        if (keys.contains(KeyType.FREEZE)) {
            given(token.hasFreezeKey()).willReturn(true);
        }
        if (keys.contains(KeyType.SUPPLY)) {
            given(token.hasSupplyKey()).willReturn(true);
        }
        if (keys.contains(KeyType.KYC)) {
            given(token.hasKycKey()).willReturn(true);
        }
        if (keys.contains(KeyType.FEE_SCHEDULE)) {
            given(token.hasFeeScheduleKey()).willReturn(true);
        }
        if (keys.contains(KeyType.PAUSE)) {
            given(token.hasPauseKey()).willReturn(true);
        }
    }

    @Test
    void understandsPendingCreation() {
        assertFalse(subject.isCreationPending());

        subject.pendingId = misc;
        assertTrue(subject.isCreationPending());
    }

    @Test
    void adjustingRejectsMissingToken() {
        given(backingTokens.contains(misc)).willReturn(false);

        final var status = subject.adjustBalance(sponsor, misc, 1);

        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, status);
    }

    @Test
    void freezingRejectsUnfreezableToken() {
        given(token.freezeKey()).willReturn(Optional.empty());

        final var status = subject.freeze(treasury, misc);

        assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY, status);
    }

    @Test
    void grantingRejectsUnknowableToken() {
        given(token.kycKey()).willReturn(Optional.empty());

        final var status = subject.grantKyc(treasury, misc);

        assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY, status);
    }

    @Test
    void freezingRejectsDeletedToken() {
        givenTokenWithFreezeKey(true);
        given(token.isDeleted()).willReturn(true);

        final var status = subject.freeze(treasury, misc);

        assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
    }

    @Test
    void unfreezingInvalidWithoutFreezeKey() {
        final var status = subject.unfreeze(treasury, misc);

        assertEquals(TOKEN_HAS_NO_FREEZE_KEY, status);
    }

    @Test
    void performsValidFreeze() {
        givenTokenWithFreezeKey(false);

        subject.freeze(treasury, misc);

        verify(tokenRelsLedger).set(treasuryMisc, TokenRelProperty.IS_FROZEN, true);
    }

    private void givenTokenWithFreezeKey(boolean freezeDefault) {
        given(token.freezeKey()).willReturn(Optional.of(TOKEN_TREASURY_KT.asJKeyUnchecked()));
        given(token.accountsAreFrozenByDefault()).willReturn(freezeDefault);
    }

    @Test
    void adjustingRejectsDeletedToken() {
        given(token.isDeleted()).willReturn(true);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
    }

    @Test
    void adjustingRejectsPausedToken() {
        given(token.isPaused()).willReturn(true);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(ResponseCodeEnum.TOKEN_IS_PAUSED, status);
    }

    @Test
    void adjustingRejectsFungibleUniqueToken() {
        given(token.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON, status);
    }

    @Test
    void refusesToAdjustFrozenRelationship() {
        given(tokenRelsLedger.get(treasuryMisc, IS_FROZEN)).willReturn(true);

        final var status = subject.adjustBalance(treasury, misc, -1);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void refusesToAdjustRevokedKycRelationship() {
        given(tokenRelsLedger.get(treasuryMisc, IS_KYC_GRANTED)).willReturn(false);

        final var status = subject.adjustBalance(treasury, misc, -1);

        assertEquals(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN, status);
    }

    @Test
    void refusesInvalidAdjustment() {
        final var status = subject.adjustBalance(treasury, misc, -treasuryBalance - 1);

        assertEquals(INSUFFICIENT_TOKEN_BALANCE, status);
    }

    @Test
    void adjustmentFailsOnAutomaticAssociationLimitNotSet() {
        given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(false);
        given(accountsLedger.get(anotherFeeCollector, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(0);

        final var status = subject.adjustBalance(anotherFeeCollector, misc, -1);
        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void adjustmentFailsOnAutomaticAssociationLimitReached() {
        given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(false);
        given(tokenRelsLedger.get(anotherFeeCollectorMisc, IS_FROZEN)).willReturn(false);
        given(tokenRelsLedger.get(anotherFeeCollectorMisc, IS_KYC_GRANTED)).willReturn(true);
        given(tokenRelsLedger.get(anotherFeeCollectorMisc, TOKEN_BALANCE)).willReturn(0L);
        given(accountsLedger.get(anotherFeeCollector, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(3);
        given(accountsLedger.get(anotherFeeCollector, NUM_ASSOCIATIONS)).willReturn(1);
        given(accountsLedger.get(anotherFeeCollector, USED_AUTOMATIC_ASSOCIATIONS)).willReturn(3);
        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);

        final var status = subject.adjustBalance(anotherFeeCollector, misc, 1);

        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);
        verify(tokenRelsLedger, never()).set(anotherFeeCollectorMisc, TOKEN_BALANCE, 1L);
        verify(accountsLedger, never()).set(anotherFeeCollector, USED_AUTOMATIC_ASSOCIATIONS, 4);
    }

    @Test
    void adjustmentWorksAndIncrementsAlreadyUsedAutoAssociationCountForNewAssociation() {
        given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(false);
        given(tokenRelsLedger.get(anotherFeeCollectorMisc, IS_FROZEN)).willReturn(false);
        given(tokenRelsLedger.get(anotherFeeCollectorMisc, IS_KYC_GRANTED)).willReturn(true);
        given(tokenRelsLedger.get(anotherFeeCollectorMisc, TOKEN_BALANCE)).willReturn(0L);
        given(accountsLedger.get(anotherFeeCollector, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(5);
        given(accountsLedger.get(anotherFeeCollector, USED_AUTOMATIC_ASSOCIATIONS)).willReturn(3);
        given(accountsLedger.get(anotherFeeCollector, NUM_ASSOCIATIONS))
                .willReturn(associatedTokensCount);
        given(accountsLedger.get(anotherFeeCollector, NUM_POSITIVE_BALANCES))
                .willReturn(numPositiveBalances);
        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);

        final var status = subject.adjustBalance(anotherFeeCollector, misc, 1);

        assertEquals(OK, status);
        verify(tokenRelsLedger).set(anotherFeeCollectorMisc, TOKEN_BALANCE, 1L);
        verify(accountsLedger).set(anotherFeeCollector, USED_AUTOMATIC_ASSOCIATIONS, 4);
        verify(accountsLedger)
                .set(anotherFeeCollector, NUM_ASSOCIATIONS, associatedTokensCount + 1);
        verify(accountsLedger)
                .set(anotherFeeCollector, NUM_POSITIVE_BALANCES, numPositiveBalances + 1);
    }

    @Test
    void performsValidAdjustment() {
        given(tokenRelsLedger.get(treasuryMisc, TOKEN_BALANCE)).willReturn(1L);
        given(accountsLedger.get(treasury, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
        given(accountsLedger.get(treasury, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);

        subject.adjustBalance(treasury, misc, -1);

        verify(tokenRelsLedger).set(treasuryMisc, TOKEN_BALANCE, 0L);
        verify(accountsLedger).set(treasury, NUM_POSITIVE_BALANCES, numPositiveBalances - 1);
    }

    @Test
    void rollbackReclaimsIdAndClears() {
        subject.pendingId = created;
        subject.pendingCreation = token;

        subject.rollbackCreation();

        verify(backingTokens, never()).put(created, token);
        verify(ids).reclaimLastId();
        assertSame(HederaTokenStore.NO_PENDING_ID, subject.pendingId);
        assertNull(subject.pendingCreation);
    }

    @Test
    void commitAndRollbackThrowIseIfNoPendingCreation() {
        assertThrows(IllegalStateException.class, subject::commitCreation);
        assertThrows(IllegalStateException.class, subject::rollbackCreation);
    }

    @Test
    void commitPutsToMapAndClears() {
        subject.pendingId = created;
        subject.pendingCreation = token;

        subject.commitCreation();

        verify(backingTokens).put(created, token);
        assertSame(HederaTokenStore.NO_PENDING_ID, subject.pendingId);
        assertNull(subject.pendingCreation);
    }

    @Test
    void adaptsBehaviorToFungibleType() {
        final var aa = AccountAmount.newBuilder().setAccountID(sponsor).setAmount(100).build();
        final var fungibleChange =
                BalanceChange.changingFtUnits(Id.fromGrpcToken(misc), misc, aa, payer);
        fungibleChange.setExpectedDecimals(2);
        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(5);
        given(accountsLedger.get(sponsor, NUM_POSITIVE_BALANCES)).willReturn(2);

        assertEquals(2, subject.get(misc).decimals());
        assertEquals(2, fungibleChange.getExpectedDecimals());

        final var result = subject.tryTokenChange(fungibleChange);
        Assertions.assertEquals(OK, result);
    }

    @Test
    void failsIfMismatchingDecimals() {
        final var aa = AccountAmount.newBuilder().setAccountID(sponsor).setAmount(100).build();
        final var fungibleChange =
                BalanceChange.changingFtUnits(Id.fromGrpcToken(misc), misc, aa, payer);
        assertFalse(fungibleChange.hasExpectedDecimals());

        fungibleChange.setExpectedDecimals(4);

        assertEquals(2, subject.get(misc).decimals());
        assertEquals(4, fungibleChange.getExpectedDecimals());

        final var result = subject.tryTokenChange(fungibleChange);
        Assertions.assertEquals(UNEXPECTED_TOKEN_DECIMALS, result);
    }

    @Test
    void decimalMatchingWorks() {
        assertEquals(2, subject.get(misc).decimals());
        assertTrue(subject.matchesTokenDecimals(misc, 2));
        assertFalse(subject.matchesTokenDecimals(misc, 4));
    }

    @Test
    void updateExpiryInfoRejectsInvalidExpiry() {
        final var op =
                updateWith(NO_KEYS, misc, true, true, false).toBuilder()
                        .setExpiry(Timestamp.newBuilder().setSeconds(expiry - 1))
                        .build();

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_EXPIRATION_TIME, outcome);
    }

    @Test
    void updateExpiryInfoCanExtendImmutableExpiry() {
        given(token.hasAdminKey()).willReturn(false);
        final var op =
                updateWith(NO_KEYS, misc, false, false, false).toBuilder()
                        .setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234))
                        .build();

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(OK, outcome);
    }

    @Test
    void updateExpiryInfoRejectsInvalidNewAutoRenew() {
        given(accountsLedger.exists(newAutoRenewAccount)).willReturn(false);
        final var op = updateWith(NO_KEYS, misc, true, true, false, true, false);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_AUTORENEW_ACCOUNT, outcome);
    }

    @Test
    void updateExpiryInfoRejectsInvalidNewAutoRenewPeriod() {
        final var op =
                updateWith(NO_KEYS, misc, true, true, false, false, false).toBuilder()
                        .setAutoRenewPeriod(enduring(-1L))
                        .build();

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_RENEWAL_PERIOD, outcome);
    }

    @Test
    void updateExpiryInfoRejectsMissingToken() {
        given(backingTokens.contains(misc)).willReturn(false);
        givenUpdateTarget(ALL_KEYS, token);
        final var op = updateWith(ALL_KEYS, misc, true, true, true);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_TOKEN_ID, outcome);
    }

    TokenCreateTransactionBody.Builder fullyValidTokenCreateAttempt() {
        return TokenCreateTransactionBody.newBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(expiry))
                .setMemo(memo)
                .setAdminKey(adminKey)
                .setKycKey(kycKey)
                .setFreezeKey(freezeKey)
                .setWipeKey(wipeKey)
                .setSupplyKey(supplyKey)
                .setFeeScheduleKey(feeScheduleKey)
                .setSymbol(symbol)
                .setName(name)
                .setInitialSupply(totalSupply)
                .setTreasury(treasury)
                .setDecimals(decimals)
                .setFreezeDefault(freezeDefault);
    }

    private void assertSoleTokenChangesAreForNftTransfer(
            final NftId nft, final AccountID from, final AccountID to) {
        final var tokenChanges = sideEffectsTracker.getNetTrackedTokenUnitAndOwnershipChanges();
        final var ownershipChange = tokenChanges.get(0);
        assertEquals(nft.tokenId(), ownershipChange.getToken());
        final var nftTransfer = ownershipChange.getNftTransfers(0);
        assertEquals(nft.serialNo(), nftTransfer.getSerialNumber());
        assertEquals(from, nftTransfer.getSenderAccountID());
        assertEquals(to, nftTransfer.getReceiverAccountID());
    }

    private Duration enduring(final long secs) {
        return Duration.newBuilder().setSeconds(secs).build();
    }
}
