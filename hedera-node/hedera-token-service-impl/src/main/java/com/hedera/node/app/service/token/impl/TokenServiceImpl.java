/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.migration.AccountStateTranslator;
import com.hedera.node.app.service.mono.state.migration.NftStateTranslator;
import com.hedera.node.app.service.mono.state.migration.StakingNodeInfoStateTranslator;
import com.hedera.node.app.service.mono.state.migration.TokenRelationStateTranslator;
import com.hedera.node.app.service.mono.state.migration.TokenStateTranslator;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.codec.NetworkingStakingTranslator;
import com.hedera.node.app.service.token.impl.schemas.SyntheticRecordsGenerator;
import com.hedera.node.app.service.token.impl.schemas.TokenSchema;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {
    public static final String NFTS_KEY = "NFTS";
    public static final String TOKENS_KEY = "TOKENS";
    public static final String ALIASES_KEY = "ALIASES";
    public static final String ACCOUNTS_KEY = "ACCOUNTS";
    public static final String TOKEN_RELS_KEY = "TOKEN_RELS";
    public static final String STAKING_INFO_KEY = "STAKING_INFOS";
    public static final String STAKING_NETWORK_REWARDS_KEY = "STAKING_NETWORK_REWARDS";
    private final Supplier<SortedSet<Account>> sysAccts;
    private final Supplier<SortedSet<Account>> stakingAccts;
    private final Supplier<SortedSet<Account>> treasuryAccts;
    private final Supplier<SortedSet<Account>> miscAccts;
    private final Supplier<SortedSet<Account>> blocklistAccts;
    private VirtualMap<UniqueTokenKey, UniqueTokenValue> nftsFs;
    private VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> trFs;
    private VirtualMap<EntityNumVirtualKey, OnDiskAccount> acctsFs;
    private MerkleMap<EntityNum, MerkleToken> tFs;
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingFs;
    private MerkleNetworkContext mnc;

    public void setStakingFs(MerkleMap<EntityNum, MerkleStakingInfo> stakingFs, MerkleNetworkContext mnc) {
        this.stakingFs = stakingFs;
        this.mnc = mnc;
    }

    /**
     * Constructor for the token service. Each of the given suppliers should produce a {@link SortedSet}
     * of {@link Account} objects, where each account object represents a SYNTHETIC RECORD (see {@link
     * SyntheticRecordsGenerator} for more details). Even though these sorted sets contain account objects,
     * these account objects may or may not yet exist in state. They're needed for event recovery circumstances
     * @param sysAccts
     * @param stakingAccts
     * @param treasuryAccts
     * @param miscAccts
     * @param blocklistAccts
     */
    public TokenServiceImpl(
            @NonNull final Supplier<SortedSet<Account>> sysAccts,
            @NonNull final Supplier<SortedSet<Account>> stakingAccts,
            @NonNull final Supplier<SortedSet<Account>> treasuryAccts,
            @NonNull final Supplier<SortedSet<Account>> miscAccts,
            @NonNull final Supplier<SortedSet<Account>> blocklistAccts) {
        this.sysAccts = sysAccts;
        this.stakingAccts = stakingAccts;
        this.treasuryAccts = treasuryAccts;
        this.miscAccts = miscAccts;
        this.blocklistAccts = blocklistAccts;
    }

    /**
     * Necessary default constructor. See all params constructor for more details
     */
    public TokenServiceImpl() {
        this.sysAccts = Collections::emptySortedSet;
        this.stakingAccts = Collections::emptySortedSet;
        this.treasuryAccts = Collections::emptySortedSet;
        this.miscAccts = Collections::emptySortedSet;
        this.blocklistAccts = Collections::emptySortedSet;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, final SemanticVersion version) {
        requireNonNull(registry);
        // We intentionally ignore the given (i.e. passed-in) version in this method
        registry.register(new TokenSchema(sysAccts, stakingAccts, treasuryAccts, miscAccts, blocklistAccts, RELEASE_045_VERSION));

        //        if(true)return;
        registry.register(new Schema(RELEASE_MIGRATION_VERSION) {
            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                System.out.println("BBM: migrating token service");

                // ---------- NFTs
                if (true) {
                    System.out.println("BBM: doing nfts...");
                    var nftsToState = ctx.newStates().<NftID, Nft>get(NFTS_KEY);
                    try {
                        VirtualMapLike.from(nftsFs)
                                .extractVirtualMapData(
                                        AdHocThreadManager.getStaticThreadManager(),
                                        entry -> {
                                            var nftId = entry.left();
                                            var toNftId = NftID.newBuilder()
                                                    .tokenId(TokenID.newBuilder()
                                                            .tokenNum(nftId.getNum())
                                                            .build())
                                                    .serialNumber(nftId.getTokenSerial())
                                                    .build();
                                            var fromNft = entry.right();
                                    var fromNft2 = new MerkleUniqueToken(fromNft.getOwner(),
                                            fromNft.getMetadata(), fromNft.getCreationTime());
                                    var translated = NftStateTranslator.nftFromMerkleUniqueToken(
                                            fromNft2);
                                            nftsToState.put(toNftId, translated);
                                        },
                                        1);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        if (nftsToState.isModified()) ((WritableKVStateBase) nftsToState).commit();
                        System.out.println("BBM: finished nfts");
                    }

                // ---------- Token Rels/Associations
                if (true) {
                    System.out.println("BBM: doing token rels...");
                    var tokenRelsToState = ctx.newStates().<EntityIDPair, TokenRelation>get(TOKEN_RELS_KEY);
                        try {
                        VirtualMapLike.from(trFs)
                                .extractVirtualMapData(
                                        AdHocThreadManager.getStaticThreadManager(),
                                        entry -> {
                                            var fromTokenRel = entry.right();
                                            var key = fromTokenRel.getKey();
                                            var translated =
                                                    TokenRelationStateTranslator.tokenRelationFromMerkleTokenRelStatus(
                                                            new MerkleTokenRelStatus(
                                                                    fromTokenRel.getBalance(),
                                                                    fromTokenRel.isFrozen(),
                                                                    fromTokenRel.isKycGranted(),
                                                                    fromTokenRel.isAutomaticAssociation(),
                                                                    fromTokenRel.getNumbers()));
                                            var newPair = EntityIDPair.newBuilder()
                                                    .accountId(AccountID.newBuilder()
                                                            .accountNum(key.getHiOrderAsLong())
                                                            .build())
                                                    .tokenId(TokenID.newBuilder()
                                                            .tokenNum(key.getLowOrderAsLong())
                                                            .build())
                                                    .build();
                                            tokenRelsToState.put(newPair, translated);
                                        },
                                        1);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    if (tokenRelsToState.isModified()) ((WritableKVStateBase) tokenRelsToState).commit();
                        System.out.println("BBM: finished token rels");
                    }

                // ---------- Accounts
                if (true) {
                    System.out.println("BBM: doing accounts");
                    var acctsToState = ctx.newStates().<AccountID, Account>get(ACCOUNTS_KEY);
                    try {
                        VirtualMapLike.from(acctsFs)
                                .extractVirtualMapData(
                                        AdHocThreadManager.getStaticThreadManager(),
                                        entry -> {
                                            var acctNum =
                                                    entry.left().asEntityNum().longValue();
                                            var fromAcct = entry.right();
                                            var toAcct = AccountStateTranslator.accountFromOnDiskAccount(fromAcct);
                                            acctsToState.put(
                                                    AccountID.newBuilder()
                                                            .accountNum(acctNum)
                                                            .build(),
                                                    toAcct);
                                        },
                                        1);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        if (acctsToState.isModified())
                            ((WritableKVStateBase) acctsToState).commit();
                        System.out.println("BBM: finished accts");
                    }

                // ---------- Tokens
                if (true) {
                    System.out.println("BBM: starting tokens (both fung and non-fung)");
                    var tokensToState = ctx.newStates().<TokenID, Token>get(TOKENS_KEY);
                        MerkleMapLike.from(tFs).forEachNode(
                                new BiConsumer<EntityNum, MerkleToken>() {
                                    @Override
                                    public void accept(EntityNum entityNum,
                                            MerkleToken merkleToken) {
                                        var toToken = TokenStateTranslator.tokenFromMerkle(
                                                merkleToken);
                                        tokensToState.put(
                                    TokenID.newBuilder()
                                            .tokenNum(entityNum.longValue())
                                            .build(),
                                                toToken);
                                    }
                                });
                        if (tokensToState.isModified())
                            ((WritableKVStateBase) tokensToState).commit();
                        System.out.println("BBM: finished tokens (fung and non-fung)");
                }

                // ---------- Staking Info
                if (true) {
                    System.out.println("BBM: starting staking info");
                    var stakingToState = ctx.newStates().<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY);
                    stakingFs.forEach(new BiConsumer<EntityNum, MerkleStakingInfo>() {
                        @Override
                        public void accept(EntityNum entityNum, MerkleStakingInfo merkleStakingInfo) {
                            var toStakingInfo =
                                    StakingNodeInfoStateTranslator.stakingInfoFromMerkleStakingInfo(merkleStakingInfo);
                            stakingToState.put(
                                    EntityNumber.newBuilder()
                                            .number(merkleStakingInfo.getKey().longValue())
                                            .build(),
                                    toStakingInfo);
                        }
                    });
                    if (stakingToState.isModified()) ((WritableKVStateBase) stakingToState).commit();
                    System.out.println("BBM: finished staking info");
                }

                // ---------- Staking Rewards
                if (true) {
                    System.out.println("BBM: starting staking rewards");
                    var srToState = ctx.newStates().<NetworkStakingRewards>getSingleton(STAKING_NETWORK_REWARDS_KEY);
                    var toSr = NetworkingStakingTranslator.networkStakingRewardsFromMerkleNetworkContext(mnc);
                    srToState.put(toSr);
                    if (srToState.isModified()) ((WritableSingletonStateBase) srToState).commit();
                    System.out.println("BBM: finished staking rewards");
                }

                nftsFs = null;
                trFs = null;
                acctsFs = null;
                tFs = null;

                stakingFs = null;
                mnc = null;
            }
        });
    }

    public void setNftsFromState(VirtualMap<UniqueTokenKey, UniqueTokenValue> fs) {
        this.nftsFs = fs;
    }

    public void setTokenRelsFromState(VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> fs) {
        this.trFs = fs;
    }

    public void setAcctsFromState(VirtualMap<EntityNumVirtualKey, OnDiskAccount> fs) {
        this.acctsFs = fs;
    }

    public void setTokensFromState(MerkleMap<EntityNum, MerkleToken> fs) {
        this.tFs = fs;
    }
}
