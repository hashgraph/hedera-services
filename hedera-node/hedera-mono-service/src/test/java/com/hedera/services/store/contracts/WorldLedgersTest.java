/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts;

import static com.hedera.services.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.services.ledger.properties.AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES;
import static com.hedera.services.ledger.properties.NftProperty.METADATA;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenProperty.DECIMALS;
import static com.hedera.services.ledger.properties.TokenProperty.NAME;
import static com.hedera.services.ledger.properties.TokenProperty.SYMBOL;
import static com.hedera.services.ledger.properties.TokenProperty.TOKEN_TYPE;
import static com.hedera.services.ledger.properties.TokenProperty.TOTAL_SUPPLY;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.URI_QUERY_NON_EXISTING_TOKEN_ERROR;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.StackedContractAliases;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.backing.HashMapBackingNfts;
import com.hedera.services.ledger.backing.HashMapBackingTokenRels;
import com.hedera.services.ledger.backing.HashMapBackingTokens;
import com.hedera.services.ledger.interceptors.AutoAssocTokenRelsCommitInterceptor;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fchashmap.FCHashMap;
import java.util.Collections;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorldLedgersTest {
    private static final NftId target = new NftId(0, 0, 123, 456);
    private static final TokenID nft = target.tokenId();
    private static final TokenID fungibleToken = TokenID.newBuilder().setTokenNum(789).build();
    private static final EntityId treasury = new EntityId(0, 0, 666);
    private static final EntityId notTreasury = new EntityId(0, 0, 777);
    private static final AccountID accountID = treasury.toGrpcAccountId();
    private static final AccountID ownerId = notTreasury.toGrpcAccountId();
    private static final AccountID spenderId = treasury.toGrpcAccountId();
    private static final Address alias =
            Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final ByteString pkAlias =
            ByteString.copyFrom(
                    Bytes.fromHexString(
                                    "3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d")
                            .toArrayUnsafe());
    private static final ByteString pkAliasBadProtobuf =
            ByteString.copyFrom(
                    Bytes.fromHexString(
                                    "ffff033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d")
                            .toArrayUnsafe());
    private static final ByteString pkAliasBadFormat =
            ByteString.copyFrom(
                    Bytes.fromHexString(
                                    "3a21ff3a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d")
                            .toArrayUnsafe());
    private static final Address pkAddress =
            Address.fromHexString("a94f5374fce5edbc8e2a8697c15331677e6ebf0b");
    private static final ByteString unsupportedAlias =
            ByteString.copyFromUtf8("This is not a supported alias");
    private static final Address sponsor = Address.fromHexString("0xcba");

    private static final NftId nftId = new NftId(0, 0, 123, 456);

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRelsLedger;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private ContractAliases aliases;
    @Mock private StaticEntityAccess staticEntityAccess;
    @Mock private SideEffectsTracker sideEffectsTracker;

    private WorldLedgers subject;

    @BeforeEach
    void setUp() {
        subject =
                new WorldLedgers(
                        aliases, tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);
    }

    @Test
    void usesStaticAccessIfNotUsableLedgers() {
        final var owner = EntityNum.fromLong(1001).toEvmAddress();
        given(staticEntityAccess.ownerOf(target)).willReturn(owner);

        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);

        assertSame(owner, subject.ownerOf(target));
    }

    @Test
    void resolvesOwnerDirectlyIfNotTreasury() {
        given(nftsLedger.get(target, OWNER)).willReturn(notTreasury);

        final var expected = notTreasury.toEvmAddress();
        final var actual = subject.ownerOf(target);
        assertEquals(expected, actual);
    }

    @Test
    void resolvesTreasuryOwner() {
        given(nftsLedger.get(target, OWNER)).willReturn(MISSING_ENTITY_ID);
        given(tokensLedger.get(nft, TokenProperty.TREASURY)).willReturn(treasury);

        final var expected = treasury.toEvmAddress();
        final var actual = subject.ownerOf(target);
        assertEquals(expected, actual);
    }

    @Test
    void metadataOfWorksWithStatic() {
        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        given(staticEntityAccess.metadataOf(nftId)).willReturn("There, the eyes are");

        assertEquals("There, the eyes are", subject.metadataOf(nftId));
    }

    @Test
    void metadataOfWorks() {
        given(nftsLedger.exists(nftId)).willReturn(true);
        given(nftsLedger.get(nftId, METADATA)).willReturn("There, the eyes are".getBytes());

        assertEquals("There, the eyes are", subject.metadataOf(nftId));
    }

    @Test
    void ownerIfPresentOnlyAvailableForMutable() {
        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        assertThrows(IllegalStateException.class, () -> subject.ownerIfPresent(nftId));
    }

    @Test
    void ownerIfPresentReturnsNullForNonexistentNftId() {
        assertNull(subject.ownerIfPresent(nftId));
    }

    @Test
    void ownerIfPresentReturnsOwnerIfPresent() {
        given(nftsLedger.contains(nftId)).willReturn(true);
        given(nftsLedger.get(nftId, OWNER)).willReturn(notTreasury);
        assertEquals(notTreasury, subject.ownerIfPresent(nftId));
    }

    @Test
    void staticAllowanceDelegatesAsExpected() {
        assertThrows(
                IllegalStateException.class,
                () -> subject.staticAllowanceOf(ownerId, spenderId, fungibleToken));

        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        given(staticEntityAccess.allowanceOf(ownerId, spenderId, fungibleToken)).willReturn(123L);
        assertEquals(123L, subject.staticAllowanceOf(ownerId, spenderId, fungibleToken));
    }

    @Test
    void staticApprovedDelegatesAsExpected() {
        assertThrows(IllegalStateException.class, () -> subject.staticApprovedSpenderOf(nftId));

        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        given(staticEntityAccess.approvedSpenderOf(nftId)).willReturn(Address.ALTBN128_ADD);
        assertEquals(Address.ALTBN128_ADD, subject.staticApprovedSpenderOf(nftId));
    }

    @Test
    void staticOperatorDelegatesAsExpected() {
        assertThrows(
                IllegalStateException.class,
                () -> subject.staticIsOperator(ownerId, spenderId, nft));

        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        given(staticEntityAccess.isOperator(ownerId, spenderId, nft)).willReturn(true);
        assertTrue(subject.staticIsOperator(ownerId, spenderId, nft));
    }

    @Test
    void approvedForAllLookupOnlyAvailableForMutable() {
        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        assertThrows(
                IllegalStateException.class,
                () -> subject.hasApprovedForAll(ownerId, accountID, nft));
    }

    @Test
    void approvedForAllFindsExpectedMissing() {
        given(accountsLedger.get(ownerId, APPROVE_FOR_ALL_NFTS_ALLOWANCES))
                .willReturn(Collections.emptySet());

        final var ans = subject.hasApprovedForAll(ownerId, accountID, nft);

        assertFalse(ans);
    }

    @Test
    void approvedForAllFindsExpectedPresent() {
        given(accountsLedger.get(ownerId, APPROVE_FOR_ALL_NFTS_ALLOWANCES))
                .willReturn(Set.of(FcTokenAllowanceId.from(nft, accountID)));

        final var ans = subject.hasApprovedForAll(ownerId, accountID, nft);

        assertTrue(ans);
    }

    @Test
    void metadataOfWorksWithNonExistent() {
        given(nftsLedger.exists(nftId)).willReturn(false);

        assertEquals(URI_QUERY_NON_EXISTING_TOKEN_ERROR, subject.metadataOf(nftId));
    }

    @Test
    void commitsAsExpectedNoHistorian() {
        subject.commit();

        verify(tokenRelsLedger).commit();
        verify(accountsLedger).commit();
        verify(nftsLedger).commit();
        verify(tokensLedger).commit();
        verify(aliases).commit(null);
    }

    @Test
    void aliasIsCanonicalCreate2SourceAddress() {
        given(aliases.isInUse(alias)).willReturn(true);

        assertSame(alias, subject.canonicalAddress(alias));
    }

    @Test
    void mirrorNoAliasIsCanonicalSourceWithLedgers() {
        final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
        given(accountsLedger.exists(id)).willReturn(true);
        given(accountsLedger.get(id, ALIAS)).willReturn(ByteString.EMPTY);

        assertSame(sponsor, subject.canonicalAddress(sponsor));
    }

    @Test
    void missingMirrorIsCanonicalSourceWithLedgers() {
        assertSame(sponsor, subject.canonicalAddress(sponsor));
    }

    @Test
    void missingMirrorIsCanonicalSourceWithStaticAccess() {
        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        assertSame(sponsor, subject.canonicalAddress(sponsor));
    }

    @Test
    void mirrorNoAliasIsCanonicalSourceWithStaticAccess() {
        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        given(staticEntityAccess.isExtant(sponsor)).willReturn(true);
        given(staticEntityAccess.alias(sponsor)).willReturn(ByteString.EMPTY);

        assertSame(sponsor, subject.canonicalAddress(sponsor));
    }

    @Test
    void mirrorWithAliasUsesEvmAddressAliasAsCanonicalSource() {
        final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
        given(accountsLedger.exists(id)).willReturn(true);
        given(accountsLedger.get(id, ALIAS)).willReturn(ByteString.copyFrom(alias.toArrayUnsafe()));
        assertEquals(alias, subject.canonicalAddress(sponsor));
    }

    @Test
    void mirrorWithAliasUsesECDSAKeyAliasAsCanonicalSource() {
        final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
        given(accountsLedger.exists(id)).willReturn(true);
        given(accountsLedger.get(id, ALIAS)).willReturn(pkAlias);
        assertEquals(pkAddress, subject.canonicalAddress(sponsor));
    }

    @Test
    void mirrorWithAliasSkipsUnsupportedAliasAsCanonicalSource() {
        final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
        given(accountsLedger.exists(id)).willReturn(true);
        given(accountsLedger.get(id, ALIAS))
                .willReturn(unsupportedAlias, pkAliasBadProtobuf, pkAliasBadFormat);
        assertEquals(sponsor, subject.canonicalAddress(sponsor));
        assertEquals(sponsor, subject.canonicalAddress(sponsor));
        assertEquals(sponsor, subject.canonicalAddress(sponsor));
    }

    @Test
    void commitsAsExpectedWithHistorian() {
        subject.commit(sigImpactHistorian);

        verify(tokenRelsLedger).commit();
        verify(accountsLedger).commit();
        verify(nftsLedger).commit();
        verify(tokensLedger).commit();
        verify(aliases).commit(sigImpactHistorian);
    }

    @Test
    void revertsAsExpected() {
        subject.revert();

        verify(tokenRelsLedger).rollback();
        verify(accountsLedger).rollback();
        verify(nftsLedger).rollback();
        verify(tokensLedger).rollback();
        verify(aliases).revert();

        verify(tokenRelsLedger).begin();
        verify(accountsLedger).begin();
        verify(nftsLedger).begin();
        verify(tokensLedger).begin();
    }

    @Test
    void wrapsAsExpectedWithCommitInterceptors() {
        final var liveTokenRels =
                new TransactionalLedger<>(
                        TokenRelProperty.class,
                        MerkleTokenRelStatus::new,
                        new HashMapBackingTokenRels(),
                        new ChangeSummaryManager<>());
        final var liveAccounts =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        new HashMapBackingAccounts(),
                        new ChangeSummaryManager<>());
        final var liveNfts =
                new TransactionalLedger<>(
                        NftProperty.class,
                        UniqueTokenAdapter::newEmptyMerkleToken,
                        new HashMapBackingNfts(),
                        new ChangeSummaryManager<>());
        final var liveTokens =
                new TransactionalLedger<>(
                        TokenProperty.class,
                        MerkleToken::new,
                        new HashMapBackingTokens(),
                        new ChangeSummaryManager<>());
        final FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();
        final var liveAliases = new AliasManager(() -> aliases);

        final var source =
                new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, liveTokens);
        assertTrue(source.areMutable());
        final var nullTokenRels =
                new WorldLedgers(liveAliases, null, liveAccounts, liveNfts, liveTokens);
        final var nullAccounts =
                new WorldLedgers(liveAliases, liveTokenRels, null, liveNfts, liveTokens);
        final var nullNfts =
                new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, null, liveTokens);
        final var nullTokens =
                new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, null);
        assertFalse(nullTokenRels.areMutable());
        assertFalse(nullAccounts.areMutable());
        assertFalse(nullNfts.areMutable());
        assertFalse(nullTokens.areMutable());

        final var wrappedUnusable = nullAccounts.wrapped(sideEffectsTracker);
        assertSame(
                ((StackedContractAliases) wrappedUnusable.aliases()).wrappedAliases(),
                nullAccounts.aliases());
        assertFalse(wrappedUnusable.areMutable());
        assertThrows(
                IllegalStateException.class,
                () -> wrappedUnusable.customizeForAutoAssociatingOp(sideEffectsTracker));

        final var wrappedSource = source.wrapped(sideEffectsTracker);
        assertSame(liveTokenRels, wrappedSource.tokenRels().getEntitiesLedger());
        assertSame(liveAccounts, wrappedSource.accounts().getEntitiesLedger());
        assertSame(liveNfts, wrappedSource.nfts().getEntitiesLedger());
        assertSame(liveTokens, wrappedSource.tokens().getEntitiesLedger());
        final var stackedAliases = (StackedContractAliases) wrappedSource.aliases();
        assertSame(liveAliases, stackedAliases.wrappedAliases());

        wrappedSource.customizeForAutoAssociatingOp(sideEffectsTracker);
        assertInstanceOf(
                AutoAssocTokenRelsCommitInterceptor.class,
                wrappedSource.tokenRels().getCommitInterceptor());
    }

    @Test
    void wrapsAsExpectedWithoutCommitInterceptors() {
        final var liveTokenRels =
                new TransactionalLedger<>(
                        TokenRelProperty.class,
                        MerkleTokenRelStatus::new,
                        new HashMapBackingTokenRels(),
                        new ChangeSummaryManager<>());
        final var liveAccounts =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        new HashMapBackingAccounts(),
                        new ChangeSummaryManager<>());
        final var liveNfts =
                new TransactionalLedger<>(
                        NftProperty.class,
                        UniqueTokenAdapter::newEmptyMerkleToken,
                        new HashMapBackingNfts(),
                        new ChangeSummaryManager<>());
        final var liveTokens =
                new TransactionalLedger<>(
                        TokenProperty.class,
                        MerkleToken::new,
                        new HashMapBackingTokens(),
                        new ChangeSummaryManager<>());
        final FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();
        final var liveAliases = new AliasManager(() -> aliases);

        final var source =
                new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, liveTokens);
        assertTrue(source.areMutable());
        final var nullTokenRels =
                new WorldLedgers(liveAliases, null, liveAccounts, liveNfts, liveTokens);
        final var nullAccounts =
                new WorldLedgers(liveAliases, liveTokenRels, null, liveNfts, liveTokens);
        final var nullNfts =
                new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, null, liveTokens);
        final var nullTokens =
                new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, null);
        assertFalse(nullTokenRels.areMutable());
        assertFalse(nullAccounts.areMutable());
        assertFalse(nullNfts.areMutable());
        assertFalse(nullTokens.areMutable());

        final var wrappedUnusable = nullAccounts.wrapped();
        assertSame(
                ((StackedContractAliases) wrappedUnusable.aliases()).wrappedAliases(),
                nullAccounts.aliases());
        assertFalse(wrappedUnusable.areMutable());

        final var wrappedSource = source.wrapped();

        assertSame(liveTokenRels, wrappedSource.tokenRels().getEntitiesLedger());
        assertSame(liveAccounts, wrappedSource.accounts().getEntitiesLedger());
        assertSame(liveNfts, wrappedSource.nfts().getEntitiesLedger());
        assertSame(liveTokens, wrappedSource.tokens().getEntitiesLedger());
        final var stackedAliases = (StackedContractAliases) wrappedSource.aliases();
        assertSame(liveAliases, stackedAliases.wrappedAliases());
    }

    @Test
    void mutableLedgersCheckForToken() {
        final var htsProxy = Address.ALTBN128_PAIRING;
        final var htsId = EntityIdUtils.tokenIdFromEvmAddress(htsProxy);

        given(tokensLedger.contains(htsId)).willReturn(true);

        assertTrue(subject.isTokenAddress(htsProxy));
    }

    @Test
    void staticLedgersUseEntityAccessForTokenTest() {
        final var htsProxy = Address.ALTBN128_PAIRING;

        given(staticEntityAccess.isTokenAccount(htsProxy)).willReturn(true);

        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);

        assertTrue(subject.isTokenAddress(htsProxy));
    }

    @Test
    void staticLedgersUseEntityAccessForTokenMetadata() {
        given(staticEntityAccess.decimalsOf(fungibleToken)).willReturn(decimals);
        given(staticEntityAccess.supplyOf(fungibleToken)).willReturn(totalSupply);
        given(staticEntityAccess.symbolOf(fungibleToken)).willReturn(symbol);
        given(staticEntityAccess.nameOf(fungibleToken)).willReturn(name);
        given(staticEntityAccess.balanceOf(accountID, fungibleToken)).willReturn(balance);
        given(staticEntityAccess.typeOf(fungibleToken)).willReturn(FUNGIBLE_COMMON);
        given(staticEntityAccess.isKyc(accountID, fungibleToken)).willReturn(false);
        given(staticEntityAccess.defaultFreezeStatus(fungibleToken)).willReturn(false);
        given(staticEntityAccess.defaultKycStatus(fungibleToken)).willReturn(false);
        given(staticEntityAccess.isFrozen(accountID, fungibleToken)).willReturn(true);

        subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);

        assertEquals(name, subject.nameOf(fungibleToken));
        assertEquals(symbol, subject.symbolOf(fungibleToken));
        assertEquals(decimals, subject.decimalsOf(fungibleToken));
        assertEquals(balance, subject.balanceOf(accountID, fungibleToken));
        assertEquals(totalSupply, subject.totalSupplyOf(fungibleToken));
        assertEquals(FUNGIBLE_COMMON, subject.typeOf(fungibleToken));
        assertFalse(subject.isKyc(accountID, fungibleToken));
        assertFalse(subject.defaultFreezeStatus(fungibleToken));
        assertFalse(subject.defaultKycStatus(fungibleToken));
        assertTrue(subject.isFrozen(accountID, fungibleToken));
    }

    @Test
    void failsIfNoFungibleTokenMetaAvailableFromLedgers() {
        assertFailsWith(() -> subject.nameOf(fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.symbolOf(fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.decimalsOf(fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.isKyc(accountID, fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.totalSupplyOf(fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.balanceOf(accountID, fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.isFrozen(accountID, fungibleToken), INVALID_TOKEN_ID);
    }

    @Test
    void failsIfAccountMissingFromLedgers() {
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        assertFailsWith(() -> subject.isKyc(accountID, fungibleToken), INVALID_ACCOUNT_ID);
        assertFailsWith(() -> subject.balanceOf(accountID, fungibleToken), INVALID_ACCOUNT_ID);
        assertFailsWith(() -> subject.isFrozen(accountID, fungibleToken), INVALID_ACCOUNT_ID);
    }

    @Test
    void getsTokenIsKycStatusWhenPresent() {
        final var key = Pair.of(accountID, fungibleToken);
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        given(accountsLedger.exists(accountID)).willReturn(true);
        given(tokenRelsLedger.exists(key)).willReturn(true);
        given(tokenRelsLedger.get(key, IS_KYC_GRANTED)).willReturn(true);
        assertTrue(subject.isKyc(accountID, fungibleToken));
    }

    @Test
    void getsAccountBalanceWhenPresent() {
        final var key = Pair.of(accountID, fungibleToken);
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        given(accountsLedger.exists(accountID)).willReturn(true);
        given(tokenRelsLedger.exists(key)).willReturn(true);
        given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(balance);
        assertEquals(balance, subject.balanceOf(accountID, fungibleToken));
    }

    @Test
    void getsTokenIsFrozenStatusWhenPresent() {
        final var key = Pair.of(accountID, fungibleToken);
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        given(accountsLedger.exists(accountID)).willReturn(true);
        given(tokenRelsLedger.exists(key)).willReturn(true);
        given(tokenRelsLedger.get(key, IS_FROZEN)).willReturn(true);
        assertTrue(subject.isFrozen(accountID, fungibleToken));
    }

    @Test
    void getsZeroBalanceWhenNoKeyPresent() {
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        given(accountsLedger.exists(accountID)).willReturn(true);
        assertEquals(0, subject.balanceOf(accountID, fungibleToken));
    }

    @Test
    void getsFungibleTokenMetaAvailableFromLedgers() {
        given(tokensLedger.get(fungibleToken, DECIMALS)).willReturn(decimals);
        given(tokensLedger.get(fungibleToken, TOTAL_SUPPLY)).willReturn(totalSupply);
        given(tokensLedger.get(fungibleToken, NAME)).willReturn(name);
        given(tokensLedger.get(fungibleToken, SYMBOL)).willReturn(symbol);
        given(tokensLedger.get(fungibleToken, TOKEN_TYPE)).willReturn(FUNGIBLE_COMMON);

        assertEquals(name, subject.nameOf(fungibleToken));
        assertEquals(symbol, subject.symbolOf(fungibleToken));
        assertEquals(decimals, subject.decimalsOf(fungibleToken));
        assertEquals(totalSupply, subject.totalSupplyOf(fungibleToken));
        assertEquals(FUNGIBLE_COMMON, subject.typeOf(fungibleToken));
    }

    private static final int decimals = 666666;
    private static final long totalSupply = 4242;
    private static final long balance = 2424;
    private static final String name = "Sunlight on a broken column";
    private static final String symbol = "THM1925";
}
