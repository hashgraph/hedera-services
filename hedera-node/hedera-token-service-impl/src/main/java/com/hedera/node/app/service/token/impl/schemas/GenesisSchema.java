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

package com.hedera.node.app.service.token.impl.schemas;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.NFTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKEN_RELS_KEY;
import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;
import static com.hedera.node.app.spi.HapiUtils.FUNDING_ACCOUNT_EXPIRY;

import com.google.common.collect.Streams;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
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
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.LongStream;

/**
 * Genesis schema for the token service
 */
public class GenesisSchema extends Schema {
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;
    private static final int MAX_ACCOUNTS = 1024;
    private static final int MAX_TOKEN_RELS = 1042;
    private static final int MAX_MINTABLE_NFTS = 4096;

    private static final long LAST_RESERVED_FILE = 199L;
    private static final long FIRST_RESERVED_SYSTEM_CONTRACT = 350L;
    private static final long LAST_RESERVED_SYSTEM_CONTRACT = 399L;

    /**
     * Create a new instance
     */
    public GenesisSchema() {
        super(GENESIS_VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.inMemory(TOKENS_KEY, TokenID.PROTOBUF, Token.PROTOBUF),
                StateDefinition.onDisk(ACCOUNTS_KEY, AccountID.PROTOBUF, Account.PROTOBUF, MAX_ACCOUNTS),
                StateDefinition.onDisk(ALIASES_KEY, ProtoBytes.PROTOBUF, AccountID.PROTOBUF, MAX_ACCOUNTS),
                StateDefinition.onDisk(NFTS_KEY, NftID.PROTOBUF, Nft.PROTOBUF, MAX_MINTABLE_NFTS),
                StateDefinition.onDisk(TOKEN_RELS_KEY, EntityIDPair.PROTOBUF, TokenRelation.PROTOBUF, MAX_TOKEN_RELS),
                StateDefinition.inMemory(STAKING_INFO_KEY, EntityNumber.PROTOBUF, StakingNodeInfo.PROTOBUF),
                StateDefinition.singleton(STAKING_NETWORK_REWARDS_KEY, NetworkStakingRewards.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull MigrationContext ctx) {
        // We will use these various configs for creating accounts. It would be nice to consolidate them somehow
        final var accountsConfig = ctx.configuration().getConfigData(AccountsConfig.class);
        final var bootstrapConfig = ctx.configuration().getConfigData(BootstrapConfig.class);
        final var ledgerConfig = ctx.configuration().getConfigData(LedgerConfig.class);
        final var hederaConfig = ctx.configuration().getConfigData(HederaConfig.class);

        // Get the map for storing all the created accounts
        final var accounts = ctx.newStates().<AccountID, Account>get(ACCOUNTS_KEY);

        // This key is used for all system accounts
        final var superUserKey = superUserKey(bootstrapConfig);

        // Create every system account, and add it to the accounts state. Now, it turns out that while accounts 1-100
        // (inclusive) are system accounts, accounts 200-349 (inclusive) and 400-750 (inclusive) are "clones" of system
        // accounts. So we can just create all of these up front. Basically, every account from 1 to 750 (inclusive)
        // other than those set aside for files (101-199 inclusive) and contracts (350-399 inclusive) are the same,
        // except for the treasury account, which has a balance
        final var standardAccounts = Streams.concat(
                LongStream.rangeClosed(1, ledgerConfig.numSystemAccounts()),
                LongStream.range(LAST_RESERVED_FILE + 1, FIRST_RESERVED_SYSTEM_CONTRACT),
                LongStream.rangeClosed(LAST_RESERVED_SYSTEM_CONTRACT + 1, ledgerConfig.numReservedSystemEntities()),
                LongStream.rangeClosed(900, 1000));

        standardAccounts.forEach(i -> {
            final var balance = i == accountsConfig.treasury() ? ledgerConfig.totalTinyBarFloat() : 0L;
            final var expiry = bootstrapConfig.systemEntityExpiry();
            final var account = createAccount(hederaConfig, i, balance, expiry, superUserKey);
            accounts.put(account.accountIdOrThrow(), account);
        });

        // Create the staking reward account and the node staking reward account.
        final var stakingRewardAccount = createStakingAccount(hederaConfig, accountsConfig.stakingRewardAccount());
        accounts.put(stakingRewardAccount.accountIdOrThrow(), stakingRewardAccount);
        final var nodeRewardAccount = createStakingAccount(hederaConfig, accountsConfig.nodeRewardAccount());
        accounts.put(nodeRewardAccount.accountIdOrThrow(), nodeRewardAccount);

        // Create the network rewards state
        updateNetworkRewards(ctx);
        updateStakingNodeInfo(ctx);

        // Safety check -- add up the balances of all accounts, they must match 50,000,000,000 HBARs (config)
        var totalBalance = 0L;
        for (int i = 1; i < hederaConfig.firstUserEntity(); i++) {
            final var account = accounts.get(AccountID.newBuilder()
                    .shardNum(hederaConfig.shard())
                    .realmNum(hederaConfig.realm())
                    .accountNum(i)
                    .build());

            if (account != null) {
                totalBalance += account.tinybarBalance();
            }
        }

        if (totalBalance != ledgerConfig.totalTinyBarFloat()) {
            throw new IllegalStateException("Total balance of all accounts does not match the total float");
        }
    }

    @NonNull
    private Key superUserKey(@NonNull final BootstrapConfig bootstrapConfig) {
        final var superUserKeyBytes = bootstrapConfig.genesisPublicKey();
        if (superUserKeyBytes.length() != 32) {
            throw new IllegalStateException("'" + superUserKeyBytes + "' is not a possible Ed25519 public key");
        }
        return Key.newBuilder().ed25519(superUserKeyBytes).build();
    }

    private Account createStakingAccount(@NonNull final HederaConfig hederaConfig, final long num) {
        return createAccount(hederaConfig, num, 0, FUNDING_ACCOUNT_EXPIRY, EMPTY_KEY_LIST);
    }

    private Account createAccount(
            @NonNull final HederaConfig hederaConfig,
            final long num,
            final long balance,
            final long expiry,
            final Key key) {
        final var id = AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(num)
                .build();

        return Account.newBuilder()
                .accountId(id)
                .receiverSigRequired(false)
                .deleted(false)
                .expirationSecond(expiry)
                .memo("")
                .smartContract(false)
                .key(key)
                .declineReward(true)
                .autoRenewSeconds(expiry)
                .maxAutoAssociations(0)
                .tinybarBalance(balance)
                .build();
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

    private void updateNetworkRewards(final MigrationContext ctx) {
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
