/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Set;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {
    private static final int MAX_ACCOUNTS = 1024;
    private static final int MAX_TOKEN_RELS = 1042;
    private static final int MAX_MINTABLE_NFTS = 4096;
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;

    public static final String NFTS_KEY = "NFTS";
    public static final String TOKENS_KEY = "TOKENS";
    public static final String ALIASES_KEY = "ALIASES";
    public static final String ACCOUNTS_KEY = "ACCOUNTS";
    public static final String TOKEN_RELS_KEY = "TOKEN_RELS";
    public static final String STAKING_INFO_KEY = "STAKING_INFOS";
    public static final String STAKING_NETWORK_REWARDS_KEY = "STAKING_NETWORK_REWARDS";

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(tokenSchema());
    }

    private Schema tokenSchema() {
        // Everything on disk that can be
        return new Schema(GENESIS_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        StateDefinition.inMemory(TOKENS_KEY, TokenID.PROTOBUF, Token.PROTOBUF),
                        StateDefinition.onDisk(ACCOUNTS_KEY, AccountID.PROTOBUF, Account.PROTOBUF, MAX_ACCOUNTS),
                        StateDefinition.onDisk(ALIASES_KEY, ProtoBytes.PROTOBUF, AccountID.PROTOBUF, MAX_ACCOUNTS),
                        StateDefinition.onDisk(NFTS_KEY, NftID.PROTOBUF, Nft.PROTOBUF, MAX_MINTABLE_NFTS),
                        StateDefinition.onDisk(
                                TOKEN_RELS_KEY, EntityIDPair.PROTOBUF, TokenRelation.PROTOBUF, MAX_TOKEN_RELS),
                        StateDefinition.inMemory(STAKING_INFO_KEY, EntityNumber.PROTOBUF, StakingNodeInfo.PROTOBUF),
                        StateDefinition.singleton(STAKING_NETWORK_REWARDS_KEY, NetworkStakingRewards.PROTOBUF));
            }

            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                new SystemAccountsInitializer().createSystemAccounts(ctx);

                initializeNetworkRewards(ctx);
                updateStakingNodeInfo(ctx);
            }
        };
    }

    private void updateStakingNodeInfo(final MigrationContext ctx) {
        // TODO: This need to go through address book and set all the nodes
        final var config = ctx.configuration();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        final var stakingConfig = config.getConfigData(StakingConfig.class);
        final var numberOfNodes = 1;

        final long maxStakePerNode = ledgerConfig.totalTinyBarFloat() / numberOfNodes;
        final long minStakePerNode = maxStakePerNode / 2;

        final var numRewardHistoryStoredPeriods = stakingConfig.rewardHistoryNumStoredPeriods();
        final var stakingInfoState = ctx.newStates().get(STAKING_INFO_KEY);
        final var rewardSumHistory = new Long[numRewardHistoryStoredPeriods];
        Arrays.fill(rewardSumHistory, 0L);

        final var stakingInfo = StakingNodeInfo.newBuilder()
                .nodeNumber(0)
                .maxStake(maxStakePerNode)
                .minStake(minStakePerNode)
                .rewardSumHistory(Arrays.asList(rewardSumHistory))
                .weight(500)
                .build();
        stakingInfoState.put(EntityNumber.newBuilder().number(0L).build(), stakingInfo);
    }

    private void initializeNetworkRewards(final MigrationContext ctx) {
        // Set genesis network rewards state
        final var networkRewardsState = ctx.newStates().getSingleton(STAKING_NETWORK_REWARDS_KEY);
        final var networkRewards = NetworkStakingRewards.newBuilder()
                .pendingRewards(0)
                .totalStakedRewardStart(0)
                .totalStakedStart(0)
                .stakingRewardsActivated(true)
                .build();
        networkRewardsState.put(networkRewards);
    }
}
