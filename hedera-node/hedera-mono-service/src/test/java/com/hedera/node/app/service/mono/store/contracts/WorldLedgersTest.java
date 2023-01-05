/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.FUNGIBLE_COMMON;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES;
import static com.hedera.node.app.service.mono.ledger.properties.NftProperty.METADATA;
import static com.hedera.node.app.service.mono.ledger.properties.NftProperty.OWNER;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.DECIMALS;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.NAME;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.SYMBOL;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.TOKEN_TYPE;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.TOTAL_SUPPLY;
import static com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.node.app.service.mono.state.merkle.MerkleToken.convertToEvmKey;
import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.node.app.service.mono.state.submerkle.RichInstant.fromJava;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract.URI_QUERY_NON_EXISTING_TOKEN_ERROR;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHbar;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHts;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fractional;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Paused;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.tokens.TokenKey;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.accounts.StackedContractAliases;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingAccounts;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingNfts;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingTokenRels;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingTokens;
import com.hedera.node.app.service.mono.ledger.interceptors.AutoAssocTokenRelsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.ChangeSummaryManager;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.enums.TokenSupplyType;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.customfees.LedgerCustomFeeSchedules;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCHashMap;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.codec.DecoderException;
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
    private static final NftId nftId = new NftId(0, 0, 123, 456);
    private static final TokenID nft = nftId.tokenId();
    private static final long serialNo = nftId.serialNo();
    private static final Address nftAddress = asTypedEvmAddress(nft);
    private static final TokenID fungibleToken =
            TokenID.newBuilder().setShardNum(0L).setRealmNum(0L).setTokenNum(789).build();
    private static final Address fungibleTokenAddress = asTypedEvmAddress(fungibleToken);
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

    private static final AccountID payerAccountId = asAccount("0.0.9");
    private static final AccountID autoRenew = asAccount("0.0.6");
    private static final TokenID denomTokenId = asToken("0.0.5");
    private static final TokenID nftTokenId = asToken("0.0.3");
    private final Instant nftCreation = Instant.ofEpochSecond(1_234_567L, 8);
    private final byte[] nftMeta = "abcdefgh".getBytes();

    private final UniqueTokenAdapter targetNft =
            UniqueTokenAdapter.wrap(
                    new MerkleUniqueToken(
                            EntityId.fromGrpcAccountId(accountID), nftMeta, fromJava(nftCreation)));

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRelsLedger;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private ContractAliases aliases;
    @Mock private StaticEntityAccess staticEntityAccess;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private TokenInfo tokenInfo;
    @Mock private EvmTokenInfo evmTokenInfo;
    @Mock private TokenNftInfo tokenNftInfo;
    @Mock private EvmNftInfo evmNftInfo;

    @Mock
    private List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee>
            customFees;

    @Mock private JKey key;

    private TokenAccessorImpl tokenAccessor;
    private WorldLedgers worldLedgers;
    private MerkleToken token;
    private final ByteString ledgerId = ByteString.copyFromUtf8("0x03");
    private final String tokenMemo = "Goodbye and keep cold";
    private final CustomFeeBuilder builder = new CustomFeeBuilder(payerAccountId);
    private final CustomFee customFixedFeeInHbar = builder.withFixedFee(fixedHbar(100L));
    private final CustomFee customFixedFeeInHts =
            builder.withFixedFee(fixedHts(denomTokenId, 100L));
    private final CustomFee customFixedFeeSameToken = builder.withFixedFee(fixedHts(50L));
    private final CustomFee customFractionalFee =
            builder.withFractionalFee(
                    fractional(15L, 100L).setMinimumAmount(10L).setMaximumAmount(50L));
    private final List<CustomFee> grpcCustomFees =
            List.of(
                    customFixedFeeInHbar,
                    customFixedFeeInHts,
                    customFixedFeeSameToken,
                    customFractionalFee);

    @BeforeEach
    void setUp() throws Throwable {
        worldLedgers =
                new WorldLedgers(
                        aliases, tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);
        tokenAccessor();
        token =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "UnfrozenToken",
                        "UnfrozenTokenName",
                        true,
                        true,
                        EntityId.fromGrpcAccountId(accountID),
                        789);

        setUpToken(token);
    }

    @Test
    void usesStaticAccessIfNotUsableLedgers() {
        final var owner = EntityNum.fromLong(1001).toEvmAddress();
        given(staticEntityAccess.ownerOf(nftId)).willReturn(owner);

        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);

        assertSame(owner, worldLedgers.ownerOf(nftId));
    }

    @Test
    void resolvesOwnerDirectlyIfNotTreasury() {
        given(nftsLedger.get(nftId, OWNER)).willReturn(notTreasury);

        final var expected = notTreasury.toEvmAddress();
        final var actual = tokenAccessor.ownerOf(nftAddress, serialNo);
        assertEquals(expected, actual);
    }

    @Test
    void resolvesTreasuryOwner() {
        given(nftsLedger.get(nftId, OWNER)).willReturn(MISSING_ENTITY_ID);
        given(tokensLedger.get(nft, TokenProperty.TREASURY)).willReturn(treasury);

        final var expected = treasury.toEvmAddress();
        final var actual = tokenAccessor.ownerOf(nftAddress, serialNo);
        assertEquals(expected, actual);
    }

    @Test
    void metadataOfWorksWithStatic() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        given(staticEntityAccess.metadataOf(nftId)).willReturn("There, the eyes are");

        assertEquals("There, the eyes are", worldLedgers.metadataOf(nftId));
    }

    @Test
    void metadataOfWorks() {
        given(nftsLedger.exists(nftId)).willReturn(true);
        given(nftsLedger.get(nftId, METADATA)).willReturn("There, the eyes are".getBytes());

        assertEquals("There, the eyes are", tokenAccessor.metadataOf(nftAddress, serialNo));
    }

    @Test
    void ownerIfPresentOnlyAvailableForMutable() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        assertThrows(IllegalStateException.class, () -> worldLedgers.ownerIfPresent(nftId));
    }

    @Test
    void ownerIfPresentReturnsNullForNonexistentNftId() {
        assertNull(worldLedgers.ownerIfPresent(nftId));
    }

    @Test
    void ownerIfPresentReturnsOwnerIfPresent() {
        given(nftsLedger.contains(nftId)).willReturn(true);
        given(nftsLedger.get(nftId, OWNER)).willReturn(notTreasury);
        assertEquals(notTreasury, worldLedgers.ownerIfPresent(nftId));
    }

    @Test
    void staticAllowanceDelegatesAsExpected() {
        assertThrows(
                IllegalStateException.class,
                () -> worldLedgers.staticAllowanceOf(ownerId, spenderId, fungibleToken));

        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        tokenAccessor();

        given(staticEntityAccess.allowanceOf(ownerId, spenderId, fungibleToken)).willReturn(123L);
        assertEquals(
                123L,
                tokenAccessor.staticAllowanceOf(
                        asTypedEvmAddress(ownerId),
                        asTypedEvmAddress(spenderId),
                        asTypedEvmAddress(fungibleToken)));
    }

    @Test
    void staticApprovedDelegatesAsExpected() {
        assertThrows(
                IllegalStateException.class, () -> worldLedgers.staticApprovedSpenderOf(nftId));

        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        tokenAccessor();

        given(staticEntityAccess.approvedSpenderOf(nftId)).willReturn(Address.ALTBN128_ADD);
        assertEquals(
                Address.ALTBN128_ADD, tokenAccessor.staticApprovedSpenderOf(nftAddress, serialNo));
    }

    @Test
    void staticOperatorDelegatesAsExpected() {
        assertThrows(
                IllegalStateException.class,
                () -> worldLedgers.staticIsOperator(ownerId, spenderId, nft));

        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        tokenAccessor();

        given(staticEntityAccess.isOperator(ownerId, spenderId, nft)).willReturn(true);
        assertTrue(
                tokenAccessor.staticIsOperator(
                        asTypedEvmAddress(ownerId),
                        asTypedEvmAddress(spenderId),
                        asTypedEvmAddress(nft)));
    }

    @Test
    void approvedForAllLookupOnlyAvailableForMutable() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        assertThrows(
                IllegalStateException.class,
                () -> worldLedgers.hasApprovedForAll(ownerId, accountID, nft));
    }

    @Test
    void approvedForAllFindsExpectedMissing() {
        given(accountsLedger.get(ownerId, APPROVE_FOR_ALL_NFTS_ALLOWANCES))
                .willReturn(Collections.emptySet());

        final var ans = worldLedgers.hasApprovedForAll(ownerId, accountID, nft);

        assertFalse(ans);
    }

    @Test
    void approvedForAllFindsExpectedPresent() {
        given(accountsLedger.get(ownerId, APPROVE_FOR_ALL_NFTS_ALLOWANCES))
                .willReturn(Set.of(FcTokenAllowanceId.from(nft, accountID)));

        final var ans = worldLedgers.hasApprovedForAll(ownerId, accountID, nft);

        assertTrue(ans);
    }

    @Test
    void metadataOfWorksWithNonExistent() {
        given(nftsLedger.exists(nftId)).willReturn(false);

        assertEquals(URI_QUERY_NON_EXISTING_TOKEN_ERROR, worldLedgers.metadataOf(nftId));
    }

    @Test
    void staticTokenInfoWorks() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        tokenAccessor();

        given(staticEntityAccess.infoForToken(fungibleToken)).willReturn(Optional.of(tokenInfo));
        given(tokenInfo.getPauseStatus()).willReturn(Paused);
        given(tokenInfo.getMemo()).willReturn(tokenMemo);
        given(tokenInfo.getTokenId()).willReturn(fungibleToken);
        given(tokenInfo.getSymbol()).willReturn("UnfrozenToken");
        given(tokenInfo.getName()).willReturn("UnfrozenTokenName");
        given(tokenInfo.getTreasury()).willReturn(accountID);
        given(tokenInfo.getTotalSupply()).willReturn(100L);
        given(tokenInfo.getDecimals()).willReturn(1);
        given(tokenInfo.getCustomFeesList()).willReturn(grpcCustomFees);

        final var tokenInfo = tokenAccessor.infoForToken(fungibleTokenAddress, ledgerId).get();

        assertEquals(Paused, tokenInfo.getPauseStatus());
        assertEquals(token.memo(), tokenInfo.getMemo());
        assertEquals(fungibleToken, tokenInfo.getTokenId());
        assertEquals(token.symbol(), tokenInfo.getSymbol());
        assertEquals(token.name(), tokenInfo.getName());
        assertEquals(token.treasury().toGrpcAccountId(), tokenInfo.getTreasury());
        assertEquals(token.totalSupply(), tokenInfo.getTotalSupply());
        assertEquals(token.decimals(), tokenInfo.getDecimals());
        assertEquals(token.grpcFeeSchedule(), tokenInfo.getCustomFeesList());
    }

    @Test
    void staticEvmTokenInfoWorks() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);

        given(staticEntityAccess.evmInfoForToken(fungibleToken))
                .willReturn(Optional.of(evmTokenInfo));
        given(evmTokenInfo.getMemo()).willReturn(tokenMemo);
        given(evmTokenInfo.getSymbol()).willReturn("UnfrozenToken");
        given(evmTokenInfo.getName()).willReturn("UnfrozenTokenName");
        given(evmTokenInfo.getTotalSupply()).willReturn(100L);
        given(evmTokenInfo.getDecimals()).willReturn(1);

        final var tokenInfo = worldLedgers.evmInfoForToken(fungibleToken, ledgerId).get();

        assertEquals(token.memo(), tokenInfo.getMemo());
        assertEquals(token.symbol(), tokenInfo.getSymbol());
        assertEquals(token.name(), tokenInfo.getName());
        assertEquals(token.totalSupply(), tokenInfo.getTotalSupply());
        assertEquals(token.decimals(), tokenInfo.getDecimals());
    }

    @Test
    void nonStaticEvmTokenInfoWorks() {
        given(tokensLedger.getImmutableRef(fungibleToken)).willReturn(token);

        final var tokenInfo = tokenAccessor.evmInfoForToken(fungibleTokenAddress, ledgerId).get();

        assertEquals(token.memo(), tokenInfo.getMemo());
        assertEquals(token.symbol(), tokenInfo.getSymbol());
        assertEquals(token.name(), tokenInfo.getName());
        assertEquals(token.totalSupply(), tokenInfo.getTotalSupply());
        assertEquals(token.decimals(), tokenInfo.getDecimals());
    }

    @Test
    void nonStaticTokenInfoWorks() {
        given(tokensLedger.getImmutableRef(fungibleToken)).willReturn(token);

        final var tokenInfo = worldLedgers.infoForToken(fungibleToken, ledgerId).get();

        assertEquals(Paused, tokenInfo.getPauseStatus());
        assertEquals(token.memo(), tokenInfo.getMemo());
        assertEquals(fungibleToken, tokenInfo.getTokenId());
        assertEquals(token.symbol(), tokenInfo.getSymbol());
        assertEquals(token.name(), tokenInfo.getName());
        assertEquals(token.treasury().toGrpcAccountId(), tokenInfo.getTreasury());
        assertEquals(token.totalSupply(), tokenInfo.getTotalSupply());
        assertEquals(token.decimals(), tokenInfo.getDecimals());
        assertEquals(token.grpcFeeSchedule(), tokenInfo.getCustomFeesList());
    }

    @Test
    void nonStaticTokenEvmInfoWorksForMissingToken() {
        given(tokensLedger.getImmutableRef(fungibleToken)).willReturn(null);

        final var tokenInfo = worldLedgers.evmInfoForToken(fungibleToken, ledgerId);
        assertEquals(Optional.empty(), tokenInfo);
    }

    @Test
    void nonStaticTokenInfoWorksForMissingToken() {
        given(tokensLedger.getImmutableRef(fungibleToken)).willReturn(null);

        final var tokenInfo = worldLedgers.infoForToken(fungibleToken, ledgerId);
        assertEquals(Optional.empty(), tokenInfo);
    }

    @Test
    void staticNftTokenInfoWorks() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        final var nftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(1L).build();

        given(staticEntityAccess.infoForNft(nftId)).willReturn(Optional.of(tokenNftInfo));

        given(tokenNftInfo.getLedgerId()).willReturn(ledgerId);
        given(tokenNftInfo.getNftID()).willReturn(nftId);
        given(tokenNftInfo.getAccountID()).willReturn(accountID);
        given(tokenNftInfo.getSpenderId()).willReturn(null);
        given(tokenNftInfo.getCreationTime()).willReturn(fromJava(nftCreation).toGrpc());
        given(tokenNftInfo.getMetadata()).willReturn(ByteString.copyFrom(nftMeta));

        final var tokenNftInfo = worldLedgers.infoForNft(nftId, ledgerId).get();

        assertEquals(ledgerId, tokenNftInfo.getLedgerId());
        assertEquals(nftId, tokenNftInfo.getNftID());
        assertEquals(accountID, tokenNftInfo.getAccountID());
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcAccountId(tokenNftInfo.getSpenderId()));
        assertEquals(fromJava(nftCreation).toGrpc(), tokenNftInfo.getCreationTime());
        assertArrayEquals(nftMeta, tokenNftInfo.getMetadata().toByteArray());
    }

    @Test
    void staticEvmNftTokenInfoWorks() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        tokenAccessor();
        final var nftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(1L).build();
        final var nftAddr = asTypedEvmAddress(nftTokenId);

        given(staticEntityAccess.infoForNft(nftId)).willReturn(Optional.of(tokenNftInfo));

        given(tokenNftInfo.getNftID()).willReturn(nftId);
        given(tokenNftInfo.getAccountID()).willReturn(accountID);
        given(tokenNftInfo.getCreationTime()).willReturn(fromJava(nftCreation).toGrpc());
        given(tokenNftInfo.getMetadata()).willReturn(ByteString.copyFrom(nftMeta));
        given(tokenNftInfo.getSpenderId()).willReturn(spenderId);

        final var tokenNftInfo = tokenAccessor.evmNftInfo(nftAddr, 1L, ledgerId).get();

        assertEquals(nftId.getSerialNumber(), tokenNftInfo.getSerialNumber());
        assertEquals(asTypedEvmAddress(accountID), tokenNftInfo.getAccount());
        assertEquals(fromJava(nftCreation).toGrpc().getSeconds(), tokenNftInfo.getCreationTime());
        assertArrayEquals(nftMeta, tokenNftInfo.getMetadata());
        assertEquals(asTypedEvmAddress(spenderId), tokenNftInfo.getSpender());
    }

    @Test
    void nonStaticNftTokenInfoWorks() {
        final var nftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(1L).build();
        final var targetKey = NftId.withDefaultShardRealm(nftTokenId.getTokenNum(), 1L);
        given(nftsLedger.contains(targetKey)).willReturn(true);
        given(nftsLedger.getImmutableRef(targetKey)).willReturn(targetNft);

        final var tokenNftInfo = worldLedgers.infoForNft(nftId, ledgerId).get();

        assertEquals(ledgerId, tokenNftInfo.getLedgerId());
        assertEquals(nftId, tokenNftInfo.getNftID());
        assertEquals(accountID, tokenNftInfo.getAccountID());
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcAccountId(tokenNftInfo.getSpenderId()));
        assertEquals(fromJava(nftCreation).toGrpc(), tokenNftInfo.getCreationTime());
        assertArrayEquals(nftMeta, tokenNftInfo.getMetadata().toByteArray());
    }

    @Test
    void nonStaticNftTokenInfoWorksForMissingSerialNumber() {
        final var nftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(1L).build();
        final var targetKey = NftId.withDefaultShardRealm(nftTokenId.getTokenNum(), 1L);
        given(nftsLedger.contains(targetKey)).willReturn(false);

        final var tokenNftInfo = worldLedgers.infoForNft(nftId, ledgerId);

        assertEquals(Optional.empty(), tokenNftInfo);
    }

    @Test
    void nonStaticNftTokenInfoWorksForWildCardOwner() {
        final var nftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(1L).build();
        final var targetKey = NftId.withDefaultShardRealm(nftTokenId.getTokenNum(), 1L);
        targetNft.setOwner(MISSING_ENTITY_ID);
        given(nftsLedger.contains(targetKey)).willReturn(true);
        given(nftsLedger.getImmutableRef(targetKey)).willReturn(targetNft);
        given(tokensLedger.getImmutableRef(nftTokenId)).willReturn(token);

        final var tokenNftInfo = worldLedgers.infoForNft(nftId, ledgerId).get();

        assertEquals(ledgerId, tokenNftInfo.getLedgerId());
        assertEquals(nftId, tokenNftInfo.getNftID());
        assertEquals(accountID, tokenNftInfo.getAccountID());
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcAccountId(tokenNftInfo.getSpenderId()));
        assertEquals(fromJava(nftCreation).toGrpc(), tokenNftInfo.getCreationTime());
        assertArrayEquals(nftMeta, tokenNftInfo.getMetadata().toByteArray());
    }

    @Test
    void nonStaticNftTokenInfoWorksForWildCardOwnerWithMissingToken() {
        final var nftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(1L).build();
        final var targetKey = NftId.withDefaultShardRealm(nftTokenId.getTokenNum(), 1L);
        targetNft.setOwner(MISSING_ENTITY_ID);
        given(nftsLedger.contains(targetKey)).willReturn(true);
        given(nftsLedger.getImmutableRef(targetKey)).willReturn(targetNft);
        given(tokensLedger.getImmutableRef(nftTokenId)).willReturn(null);

        final var tokenNftInfo = worldLedgers.infoForNft(nftId, ledgerId);

        assertEquals(Optional.empty(), tokenNftInfo);
    }

    @Test
    void staticTokenCustomFeesInfoWorks() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);

        given(customFees.size()).willReturn(3);
        given(staticEntityAccess.infoForTokenCustomFees(fungibleToken)).willReturn(customFees);

        final var customFeesInfo = worldLedgers.infoForTokenCustomFees(fungibleToken).get();
        assertEquals(3, customFeesInfo.size());
    }

    @Test
    void nonStaticTokenCustomFeesInfoWorks() {
        given(tokensLedger.getImmutableRef(fungibleToken)).willReturn(token);

        final var customFeesInfo = tokenAccessor.infoForTokenCustomFees(fungibleTokenAddress).get();
        assertEquals(4, customFeesInfo.size());
    }

    @Test
    void nonStaticTokenCustomFeesInfoWorksWithMissingToken() {
        given(tokensLedger.getImmutableRef(fungibleToken)).willReturn(null);

        final var customFeesInfo = worldLedgers.infoForTokenCustomFees(fungibleToken);
        assertEquals(Optional.empty(), customFeesInfo);
    }

    @Test
    void nonStaticTokenCustomFeesInfoWorkOnThrownException() {
        given(tokensLedger.getImmutableRef(fungibleToken)).willThrow(NullPointerException.class);

        final var customFeesInfo = worldLedgers.infoForTokenCustomFees(fungibleToken);
        assertEquals(Optional.empty(), customFeesInfo);
    }

    @Test
    void staticKeyInfoWorks() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        tokenAccessor();
        EvmKey evmKey = convertToEvmKey(asKeyUnchecked(key));
        given(staticEntityAccess.keyOf(fungibleToken, TokenProperty.ADMIN_KEY)).willReturn(key);
        given(staticEntityAccess.keyOf(fungibleToken, TokenProperty.FREEZE_KEY)).willReturn(key);
        given(staticEntityAccess.keyOf(fungibleToken, TokenProperty.KYC_KEY)).willReturn(key);
        given(staticEntityAccess.keyOf(fungibleToken, TokenProperty.WIPE_KEY)).willReturn(key);
        given(staticEntityAccess.keyOf(fungibleToken, TokenProperty.PAUSE_KEY)).willReturn(key);
        given(staticEntityAccess.keyOf(fungibleToken, TokenProperty.FEE_SCHEDULE_KEY))
                .willReturn(key);
        given(staticEntityAccess.keyOf(fungibleToken, TokenProperty.SUPPLY_KEY)).willReturn(key);

        final var evmAdminKey = tokenAccessor.keyOf(fungibleTokenAddress, TokenKey.ADMIN_KEY);
        final var adminKey = worldLedgers.keyOf(fungibleToken, TokenProperty.ADMIN_KEY);
        final var freezeKey = worldLedgers.keyOf(fungibleToken, TokenProperty.FREEZE_KEY);
        final var kycKey = worldLedgers.keyOf(fungibleToken, TokenProperty.KYC_KEY);
        final var wipeKey = worldLedgers.keyOf(fungibleToken, TokenProperty.WIPE_KEY);
        final var pauseKey = worldLedgers.keyOf(fungibleToken, TokenProperty.PAUSE_KEY);
        final var feeScheduleKey =
                worldLedgers.keyOf(fungibleToken, TokenProperty.FEE_SCHEDULE_KEY);
        final var supplyKey = worldLedgers.keyOf(fungibleToken, TokenProperty.SUPPLY_KEY);

        assertEquals(evmKey.getEd25519(), evmAdminKey.getEd25519());
        assertEquals(key, adminKey);
        assertEquals(key, freezeKey);
        assertEquals(key, kycKey);
        assertEquals(key, wipeKey);
        assertEquals(key, pauseKey);
        assertEquals(key, feeScheduleKey);
        assertEquals(key, supplyKey);
    }

    @Test
    void nonStaticKeyInfoWorks() throws DecoderException {
        given(tokensLedger.get(fungibleToken, TokenProperty.ADMIN_KEY))
                .willReturn(TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey());
        given(tokensLedger.get(fungibleToken, TokenProperty.FREEZE_KEY))
                .willReturn(TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey());
        given(tokensLedger.get(fungibleToken, TokenProperty.KYC_KEY))
                .willReturn(TxnHandlingScenario.TOKEN_KYC_KT.asJKey());
        given(tokensLedger.get(fungibleToken, TokenProperty.WIPE_KEY))
                .willReturn(MISC_ACCOUNT_KT.asJKey());
        given(tokensLedger.get(fungibleToken, TokenProperty.PAUSE_KEY))
                .willReturn(TxnHandlingScenario.TOKEN_PAUSE_KT.asJKey());
        given(tokensLedger.get(fungibleToken, TokenProperty.FEE_SCHEDULE_KEY))
                .willReturn(MISC_ACCOUNT_KT.asJKey());
        given(tokensLedger.get(fungibleToken, TokenProperty.SUPPLY_KEY))
                .willReturn(MISC_ACCOUNT_KT.asJKey());

        final var adminKey = worldLedgers.keyOf(fungibleToken, TokenProperty.ADMIN_KEY);
        final var freezeKey = worldLedgers.keyOf(fungibleToken, TokenProperty.FREEZE_KEY);
        final var kycKey = worldLedgers.keyOf(fungibleToken, TokenProperty.KYC_KEY);
        final var wipeKey = worldLedgers.keyOf(fungibleToken, TokenProperty.WIPE_KEY);
        final var pauseKey = worldLedgers.keyOf(fungibleToken, TokenProperty.PAUSE_KEY);
        final var feeScheduleKey =
                worldLedgers.keyOf(fungibleToken, TokenProperty.FEE_SCHEDULE_KEY);
        final var supplyKey = worldLedgers.keyOf(fungibleToken, TokenProperty.SUPPLY_KEY);

        assertEquals(TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey(), adminKey);
        assertEquals(TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey(), freezeKey);
        assertEquals(TxnHandlingScenario.TOKEN_KYC_KT.asJKey(), kycKey);
        assertEquals(MISC_ACCOUNT_KT.asJKey(), wipeKey);
        assertEquals(TxnHandlingScenario.TOKEN_PAUSE_KT.asJKey(), pauseKey);
        assertEquals(MISC_ACCOUNT_KT.asJKey(), feeScheduleKey);
        assertEquals(MISC_ACCOUNT_KT.asJKey(), supplyKey);
    }

    @Test
    void commitsAsExpectedNoHistorian() {
        worldLedgers.commit();

        verify(tokenRelsLedger).commit();
        verify(accountsLedger).commit();
        verify(nftsLedger).commit();
        verify(tokensLedger).commit();
        verify(aliases).commit(null);
    }

    @Test
    void aliasIsCanonicalCreate2SourceAddress() {
        given(aliases.isInUse(alias)).willReturn(true);

        assertSame(alias, worldLedgers.canonicalAddress(alias));
    }

    @Test
    void mirrorNoAliasIsCanonicalSourceWithLedgers() {
        final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
        given(accountsLedger.exists(id)).willReturn(true);
        given(accountsLedger.get(id, ALIAS)).willReturn(ByteString.EMPTY);

        assertSame(sponsor, tokenAccessor.canonicalAddress(sponsor));
    }

    @Test
    void missingMirrorIsCanonicalSourceWithLedgers() {
        assertSame(sponsor, worldLedgers.canonicalAddress(sponsor));
    }

    @Test
    void missingMirrorIsCanonicalSourceWithStaticAccess() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        assertSame(sponsor, worldLedgers.canonicalAddress(sponsor));
    }

    @Test
    void mirrorNoAliasIsCanonicalSourceWithStaticAccess() {
        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        given(staticEntityAccess.isExtant(sponsor)).willReturn(true);
        given(staticEntityAccess.alias(sponsor)).willReturn(ByteString.EMPTY);

        assertSame(sponsor, worldLedgers.canonicalAddress(sponsor));
    }

    @Test
    void mirrorWithAliasUsesEvmAddressAliasAsCanonicalSource() {
        final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
        given(accountsLedger.exists(id)).willReturn(true);
        given(accountsLedger.get(id, ALIAS)).willReturn(ByteString.copyFrom(alias.toArrayUnsafe()));
        assertEquals(alias, worldLedgers.canonicalAddress(sponsor));
    }

    @Test
    void mirrorWithAliasUsesECDSAKeyAliasAsCanonicalSource() {
        final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
        given(accountsLedger.exists(id)).willReturn(true);
        given(accountsLedger.get(id, ALIAS)).willReturn(pkAlias);
        assertEquals(pkAddress, worldLedgers.canonicalAddress(sponsor));
    }

    @Test
    void mirrorWithAliasSkipsUnsupportedAliasAsCanonicalSource() {
        final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
        given(accountsLedger.exists(id)).willReturn(true);
        given(accountsLedger.get(id, ALIAS))
                .willReturn(unsupportedAlias, pkAliasBadProtobuf, pkAliasBadFormat);
        assertEquals(sponsor, worldLedgers.canonicalAddress(sponsor));
        assertEquals(sponsor, worldLedgers.canonicalAddress(sponsor));
        assertEquals(sponsor, worldLedgers.canonicalAddress(sponsor));
    }

    @Test
    void commitsAsExpectedWithHistorian() {
        worldLedgers.commit(sigImpactHistorian);

        verify(tokenRelsLedger).commit();
        verify(accountsLedger).commit();
        verify(nftsLedger).commit();
        verify(tokensLedger).commit();
        verify(aliases).commit(sigImpactHistorian);
    }

    @Test
    void revertsAsExpected() {
        worldLedgers.revert();

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

        final var liveFeeSchedules = new LedgerCustomFeeSchedules(liveTokens);
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
        assertSame(wrappedSource.tokens(), wrappedSource.customFeeSchedules().getTokens());
        assertNotSame(liveFeeSchedules, wrappedSource.customFeeSchedules());
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

        final var liveFeeSchedules = new LedgerCustomFeeSchedules(liveTokens);
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
        assertSame(wrappedSource.tokens(), wrappedSource.customFeeSchedules().getTokens());
        assertNotSame(liveFeeSchedules, wrappedSource.customFeeSchedules());
        final var stackedAliases = (StackedContractAliases) wrappedSource.aliases();
        assertSame(liveAliases, stackedAliases.wrappedAliases());
    }

    @Test
    void mutableLedgersCheckForToken() {
        final var htsProxy = Address.ALTBN128_PAIRING;
        final var htsId = EntityIdUtils.tokenIdFromEvmAddress(htsProxy);

        given(tokensLedger.contains(htsId)).willReturn(true);

        assertTrue(tokenAccessor.isTokenAddress(htsProxy));
    }

    @Test
    void staticLedgersUseEntityAccessForTokenTest() {
        final var htsProxy = Address.ALTBN128_PAIRING;

        given(staticEntityAccess.isTokenAccount(htsProxy)).willReturn(true);

        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);

        assertTrue(worldLedgers.isTokenAddress(htsProxy));
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

        worldLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        tokenAccessor();

        assertEquals(name, tokenAccessor.nameOf(fungibleTokenAddress));
        assertEquals(symbol, tokenAccessor.symbolOf(fungibleTokenAddress));
        assertEquals(decimals, tokenAccessor.decimalsOf(fungibleTokenAddress));
        assertEquals(
                balance,
                tokenAccessor.balanceOf(asTypedEvmAddress(accountID), fungibleTokenAddress));
        assertEquals(totalSupply, tokenAccessor.totalSupplyOf(fungibleTokenAddress));
        assertEquals(FUNGIBLE_COMMON, tokenAccessor.typeOf(fungibleTokenAddress));
        assertFalse(tokenAccessor.isKyc(asTypedEvmAddress(accountID), fungibleTokenAddress));
        assertFalse(tokenAccessor.defaultFreezeStatus(fungibleTokenAddress));
        assertFalse(tokenAccessor.defaultKycStatus(fungibleTokenAddress));
        assertTrue(tokenAccessor.isFrozen(asTypedEvmAddress(accountID), fungibleTokenAddress));
    }

    @Test
    void failsIfNoFungibleTokenMetaAvailableFromLedgers() {
        assertFailsWith(() -> worldLedgers.nameOf(fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> worldLedgers.symbolOf(fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> worldLedgers.decimalsOf(fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> worldLedgers.isKyc(accountID, fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> worldLedgers.totalSupplyOf(fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> worldLedgers.balanceOf(accountID, fungibleToken), INVALID_TOKEN_ID);
        assertFailsWith(() -> worldLedgers.isFrozen(accountID, fungibleToken), INVALID_TOKEN_ID);
    }

    @Test
    void failsIfAccountMissingFromLedgers() {
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        assertFailsWith(() -> worldLedgers.isKyc(accountID, fungibleToken), INVALID_ACCOUNT_ID);
        assertFailsWith(() -> worldLedgers.balanceOf(accountID, fungibleToken), INVALID_ACCOUNT_ID);
        assertFailsWith(() -> worldLedgers.isFrozen(accountID, fungibleToken), INVALID_ACCOUNT_ID);
    }

    @Test
    void getsTokenIsKycStatusWhenPresent() {
        final var key = Pair.of(accountID, fungibleToken);
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        given(accountsLedger.exists(accountID)).willReturn(true);
        given(tokenRelsLedger.exists(key)).willReturn(true);
        given(tokenRelsLedger.get(key, IS_KYC_GRANTED)).willReturn(true);
        assertTrue(worldLedgers.isKyc(accountID, fungibleToken));
    }

    @Test
    void getsAccountBalanceWhenPresent() {
        final var key = Pair.of(accountID, fungibleToken);
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        given(accountsLedger.exists(accountID)).willReturn(true);
        given(tokenRelsLedger.exists(key)).willReturn(true);
        given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(balance);
        assertEquals(balance, worldLedgers.balanceOf(accountID, fungibleToken));
    }

    @Test
    void getsTokenIsFrozenStatusWhenPresent() {
        final var key = Pair.of(accountID, fungibleToken);
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        given(accountsLedger.exists(accountID)).willReturn(true);
        given(tokenRelsLedger.exists(key)).willReturn(true);
        given(tokenRelsLedger.get(key, IS_FROZEN)).willReturn(true);
        assertTrue(worldLedgers.isFrozen(accountID, fungibleToken));
    }

    @Test
    void getsZeroBalanceWhenNoKeyPresent() {
        given(tokensLedger.exists(fungibleToken)).willReturn(true);
        given(accountsLedger.exists(accountID)).willReturn(true);
        assertEquals(
                0,
                tokenAccessor.balanceOf(
                        asTypedEvmAddress(accountID), asTypedEvmAddress(fungibleToken)));
    }

    @Test
    void getsFungibleTokenMetaAvailableFromLedgers() {
        given(tokensLedger.get(fungibleToken, DECIMALS)).willReturn(decimals);
        given(tokensLedger.get(fungibleToken, TOTAL_SUPPLY)).willReturn(totalSupply);
        given(tokensLedger.get(fungibleToken, NAME)).willReturn(name);
        given(tokensLedger.get(fungibleToken, SYMBOL)).willReturn(symbol);
        given(tokensLedger.get(fungibleToken, TOKEN_TYPE)).willReturn(FUNGIBLE_COMMON);

        assertEquals(name, tokenAccessor.nameOf(fungibleTokenAddress));
        assertEquals(symbol, tokenAccessor.symbolOf(fungibleTokenAddress));
        assertEquals(decimals, tokenAccessor.decimalsOf(fungibleTokenAddress));
        assertEquals(totalSupply, tokenAccessor.totalSupplyOf(fungibleTokenAddress));
        assertEquals(FUNGIBLE_COMMON, tokenAccessor.typeOf(fungibleTokenAddress));
    }

    private void setUpToken(final MerkleToken token) throws DecoderException {
        token.setMemo(tokenMemo);
        token.setPauseKey(TxnHandlingScenario.TOKEN_PAUSE_KT.asJKey());
        token.setDeleted(true);
        token.setPaused(true);
        token.setSupplyType(TokenSupplyType.FINITE);
        token.setFeeScheduleFrom(grpcCustomFees);
        token.setTokenType(FUNGIBLE_COMMON);
    }

    private void tokenAccessor() {
        tokenAccessor = new TokenAccessorImpl(worldLedgers);
    }

    private static final int decimals = 666666;
    private static final long totalSupply = 4242;
    private static final long balance = 2424;
    private static final String name = "Sunlight on a broken column";
    private static final String symbol = "THM1925";
}
