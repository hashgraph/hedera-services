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

package com.hedera.node.app.service.token.impl.schemas;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.NFTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKEN_RELS_KEY;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_COMPARATOR;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticRecordsGenerator.asAccountId;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
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
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the token service
 */
public class TokenSchema extends Schema {
    private static final Logger log = LogManager.getLogger(TokenSchema.class);
    // These need to be big so databases are created at right scale. If they are too small then the on disk hash map
    // buckets will be too full which results in very poor performance. Have chosen 10 billion as should give us
    // plenty of runway.
    private static final long MAX_ACCOUNTS = 10_000_000_000L;
    private static final long MAX_TOKEN_RELS = 10_000_000_000L;
    private static final long MAX_MINTABLE_NFTS = 10_000_000_000L;
    private static final long FIRST_RESERVED_SYSTEM_CONTRACT = 350L;
    private static final long LAST_RESERVED_SYSTEM_CONTRACT = 399L;
    private static final long FIRST_POST_SYSTEM_FILE_ENTITY = 200L;

    private final Supplier<SortedSet<Account>> sysAccts;
    private final Supplier<SortedSet<Account>> stakingAccts;
    private final Supplier<SortedSet<Account>> treasuryAccts;
    private final Supplier<SortedSet<Account>> miscAccts;
    private final Supplier<SortedSet<Account>> blocklistAccts;

    /**
     * Constructor for this schema. Each of the supplier params should produce a {@link SortedSet} of
     * {@link Account} objects, where each account object represents a _synthetic record_ (see {@link
     * SyntheticRecordsGenerator} for more details). Even though these sorted sets contain account
     * objects, these account objects may or may not yet exist in state. They're usually not needed,
     * but are required for an event recovery situation.
     */
    public TokenSchema(
            @NonNull final Supplier<SortedSet<Account>> sysAcctRcds,
            @NonNull final Supplier<SortedSet<Account>> stakingAcctRcds,
            @NonNull final Supplier<SortedSet<Account>> treasuryAcctRcds,
            @NonNull final Supplier<SortedSet<Account>> miscAcctRcds,
            @NonNull final Supplier<SortedSet<Account>> blocklistAcctRcds,
            @NonNull final SemanticVersion version) {
        super(version);

        this.sysAccts = sysAcctRcds;
        this.stakingAccts = stakingAcctRcds;
        this.treasuryAccts = treasuryAcctRcds;
        this.miscAccts = miscAcctRcds;
        this.blocklistAccts = blocklistAcctRcds;
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
        final var isGenesis = ctx.previousStates().isEmpty();
        if (isGenesis) {
            createGenesisSchema(ctx);
        }
    }

    private void createGenesisSchema(@NonNull MigrationContext ctx) {
        // Create the network rewards state
        initializeNetworkRewards(ctx);
        initializeStakingNodeInfo(ctx);

        // Get the map for storing all the created accounts
        final var accounts = ctx.newStates().<AccountID, Account>get(ACCOUNTS_KEY);

        // We will use these various configs for creating accounts. It would be nice to consolidate them somehow
        final var ledgerConfig = ctx.configuration().getConfigData(LedgerConfig.class);
        final var hederaConfig = ctx.configuration().getConfigData(HederaConfig.class);
        final var accountsConfig = ctx.configuration().getConfigData(AccountsConfig.class);

        // ---------- Create system accounts -------------------------
        int counter = 0;
        for (final Account acct : sysAccts.get()) {
            final var id = requireNonNull(acct.accountId());
            if (!accounts.contains(id)) {
                accounts.put(id, acct);
                counter++;
            }
        }
        log.info(
                "Created {} system accounts (from {} total synthetic records)",
                counter,
                sysAccts.get().size());

        // ---------- Create staking fund accounts -------------------------
        counter = 0;
        for (final Account acct : stakingAccts.get()) {
            final var id = requireNonNull(acct.accountId());
            if (!accounts.contains(id)) {
                accounts.put(id, acct);
                counter++;
            }
        }
        log.info(
                "Created {} staking accounts (from {} total synthetic records)",
                counter,
                stakingAccts.get().size());

        // ---------- Create miscellaneous accounts -------------------------
        counter = 0;
        for (final Account acct : treasuryAccts.get()) {
            final var id = requireNonNull(acct.accountId());
            if (!accounts.contains(id)) {
                accounts.put(id, acct);
                counter++;
            }
        }
        log.info(
                "Created {} treasury clones (from {} total synthetic records)",
                counter,
                treasuryAccts.get().size());

        // ---------- Create treasury clones -------------------------
        counter = 0;
        for (final Account acct : miscAccts.get()) {
            final var id = requireNonNull(acct.accountId());
            if (!accounts.contains(id)) {
                accounts.put(id, acct);
                counter++;
            }
        }
        log.info(
                "Created {} miscellaneous accounts (from {} total synthetic records)",
                counter,
                miscAccts.get().size());

        // ---------- Create blocklist accounts -------------------------
        counter = 0;
        final var newBlocklistAccts = new TreeSet<>(ACCOUNT_COMPARATOR);
        if (accountsConfig.blocklistEnabled()) {
            final var existingAliases = ctx.newStates().<Bytes, AccountID>get(TokenServiceImpl.ALIASES_KEY);

            for (final Account acctWithoutId : blocklistAccts.get()) {
                final var acctWithIdBldr = acctWithoutId.copyBuilder();
                final Account accountWithId;
                if (!existingAliases.contains(acctWithoutId.alias())) {
                    // The account does not yet exist in state, so we create it with a new entity ID. This is where we
                    // replace the placeholder entity IDs assigned in the SyntheticRegordsGenerator with actual, real
                    // entity IDs
                    final var id = asAccountId(ctx.newEntityNum(), hederaConfig);
                    accountWithId = acctWithIdBldr.accountId(id).build();

                    // Put the account and its alias in state
                    accounts.put(accountWithId.accountIdOrThrow(), accountWithId);
                    existingAliases.put(accountWithId.alias(), accountWithId.accountIdOrThrow());
                    counter++;
                } else {
                    // The account already exists in state, so we look up its existing ID, but do NOT re-add it to state
                    final var existingAcctId = existingAliases.get(acctWithoutId.alias());
                    accountWithId = acctWithIdBldr.accountId(existingAcctId).build();
                }
                newBlocklistAccts.add(accountWithId);
            }
        }
        // Since we may have replaced the placeholder entity IDs, we need to overwrite the builder's blocklist records.
        // The overwritten "record" (here represented as an Account object) will simply be a copy of the record already
        // there, but with a real entity ID instead of a placeholder entity ID
        final var recordBuilder = ctx.genesisRecordsBuilder();
        if (!newBlocklistAccts.isEmpty()) {
            recordBuilder.blocklistAccounts(newBlocklistAccts);
        }
        log.info(
                "Overwrote {} blocklist records (from {} total synthetic records)",
                newBlocklistAccts.size(),
                blocklistAccts.get().size());
        log.info(
                "Created {} blocklist accounts (from {} total synthetic records)",
                counter,
                blocklistAccts.get().size());

        // ---------- Balances Safety Check -------------------------
        // Aadd up the balances of all accounts, they must match 50,000,000,000 HBARs (config)
        var totalBalance = 0L;
        for (int i = 1; i < hederaConfig.firstUserEntity(); i++) {
            final var account = accounts.get(asAccountId(i, hederaConfig));
            if (account != null) {
                totalBalance += account.tinybarBalance();
            }
        }
        if (totalBalance != ledgerConfig.totalTinyBarFloat()) {
            throw new IllegalStateException("Total balance of all accounts does not match the total float: actual: "
                    + totalBalance + " vs expected: " + ledgerConfig.totalTinyBarFloat());
        }
        log.info(
                "Ledger float is {} tinyBars; {} modified accounts.",
                totalBalance,
                accounts.modifiedKeys().size());
    }

    @VisibleForTesting
    public static long[] nonContractSystemNums(final long numReservedSystemEntities) {
        return LongStream.rangeClosed(FIRST_POST_SYSTEM_FILE_ENTITY, numReservedSystemEntities)
                .filter(i -> i < FIRST_RESERVED_SYSTEM_CONTRACT || i > LAST_RESERVED_SYSTEM_CONTRACT)
                .toArray();
    }

    private void initializeStakingNodeInfo(@NonNull final MigrationContext ctx) {
        final var config = ctx.configuration();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        final var stakingConfig = config.getConfigData(StakingConfig.class);
        final var addressBook = ctx.networkInfo().addressBook();
        final var numberOfNodes = addressBook.size();

        final long maxStakePerNode = ledgerConfig.totalTinyBarFloat() / numberOfNodes;
        final long minStakePerNode = maxStakePerNode / 2;

        final var numRewardHistoryStoredPeriods = stakingConfig.rewardHistoryNumStoredPeriods();
        final var stakingInfoState = ctx.newStates().get(STAKING_INFO_KEY);
        final var rewardSumHistory = new Long[numRewardHistoryStoredPeriods + 1];
        Arrays.fill(rewardSumHistory, 0L);

        for (final var node : addressBook) {
            final var nodeNumber = node.nodeId();
            final var stakingInfo = StakingNodeInfo.newBuilder()
                    .nodeNumber(nodeNumber)
                    .maxStake(maxStakePerNode)
                    .minStake(minStakePerNode)
                    .rewardSumHistory(Arrays.asList(rewardSumHistory))
                    .weight(500)
                    .build();
            stakingInfoState.put(EntityNumber.newBuilder().number(nodeNumber).build(), stakingInfo);
        }
    }

    private void initializeNetworkRewards(@NonNull final MigrationContext ctx) {
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
