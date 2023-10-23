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
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;
import static com.hedera.node.app.spi.HapiUtils.FUNDING_ACCOUNT_EXPIRY;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
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
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.node.app.service.token.impl.BlocklistParser;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Genesis schema for the token service
 */
public class GenesisSchema extends Schema {
    private static final Logger log = LogManager.getLogger(GenesisSchema.class);
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;
    // These need to be big so databases are created at right scale. If they are too small then the on disk hash map
    // buckets will be too full which results in very poor performance. Have chosen 10 billion as should give us
    // plenty of runway.
    private static final long MAX_ACCOUNTS = 10_000_000_000l;
    private static final long MAX_TOKEN_RELS = 10_000_000_000l;
    private static final long MAX_MINTABLE_NFTS = 10_000_000_000l;
    private static final long FIRST_RESERVED_SYSTEM_CONTRACT = 350L;
    private static final long LAST_RESERVED_SYSTEM_CONTRACT = 399L;
    private static final long FIRST_POST_SYSTEM_FILE_ENTITY = 200L;

    private final BlocklistParser blocklistParser;

    /**
     * Create a new instance
     */
    public GenesisSchema() {
        super(GENESIS_VERSION);
        blocklistParser = new BlocklistParser();
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

        // Get the record builder for creating any necessary (synthetic) records
        final var recordsKeeper = ctx.genesisRecordsBuilder();

        // Get the map for storing all the created accounts
        final var accounts = ctx.newStates().<AccountID, Account>get(ACCOUNTS_KEY);

        // This key is used for all system accounts
        final var superUserKey = superUserKey(bootstrapConfig);

        // Create every system account, and add it to the accounts state. Now, it turns out that while accounts 1-100
        // (inclusive) are system accounts, accounts 200-349 (inclusive) and 400-750 (inclusive) are "clones" of system
        // accounts. So we can just create all of these up front. Basically, every account from 1 to 750 (inclusive)
        // other than those set aside for files (101-199 inclusive) and contracts (350-399 inclusive) are the same,
        // except for the treasury account, which has a balance
        // ---------- Create system accounts -------------------------
        final var systemAccts = new HashMap<Account, CryptoCreateTransactionBody.Builder>();
        for (long num = 1; num <= ledgerConfig.numSystemAccounts(); num++) {
            final var id = asAccountId(num, hederaConfig);
            if (accounts.contains(id)) {
                continue;
            }

            final var accountTinyBars = num == accountsConfig.treasury() ? ledgerConfig.totalTinyBarFloat() : 0;
            assert accountTinyBars >= 0L : "Negative account balance!";

            final var account = createAccount(id, accountTinyBars, bootstrapConfig.systemEntityExpiry(), superUserKey);
            systemAccts.put(account, newCryptoCreate(account));
            accounts.put(id, account);
        }
        recordsKeeper.systemAccounts(systemAccts);

        // ---------- Create staking fund accounts -------------------------
        final var stakingAccts = new HashMap<Account, CryptoCreateTransactionBody.Builder>();
        final var stakingRewardAccountId = asAccountId(accountsConfig.stakingRewardAccount(), hederaConfig);
        final var nodeRewardAccountId = asAccountId(accountsConfig.nodeRewardAccount(), hederaConfig);
        final var stakingFundAccounts = List.of(stakingRewardAccountId, nodeRewardAccountId);
        for (final var id : stakingFundAccounts) {
            if (accounts.contains(id)) {
                continue;
            }

            final var stakingFundAccount = createAccount(id, 0, FUNDING_ACCOUNT_EXPIRY, EMPTY_KEY_LIST);
            stakingAccts.put(stakingFundAccount, newCryptoCreate(stakingFundAccount));
            accounts.put(id, stakingFundAccount);
        }
        recordsKeeper.stakingAccounts(stakingAccts);

        // ---------- Create multi-use accounts -------------------------
        final var multiAccts = new HashMap<Account, CryptoCreateTransactionBody.Builder>();
        for (long num = 900; num <= 1000; num++) {
            final var id = asAccountId(num, hederaConfig);
            if (accounts.contains(id)) {
                continue;
            }

            final var account = createAccount(id, 0, bootstrapConfig.systemEntityExpiry(), superUserKey);
            multiAccts.put(account, newCryptoCreate(account));
            accounts.put(id, account);
        }
        recordsKeeper.miscAccounts(multiAccts);

        // ---------- Create treasury clones -------------------------
        // Since version 0.28.6, all of these clone accounts should either all exist (on a restart) or all not exist
        // (starting from genesis)
        final Account treasury = accounts.get(asAccountId(accountsConfig.treasury(), hederaConfig));
        final var treasuryClones = new HashMap<Account, CryptoCreateTransactionBody.Builder>();
        for (final var num : nonContractSystemNums(ledgerConfig.numReservedSystemEntities())) {
            final var nextCloneId = asAccountId(num, hederaConfig);
            if (accounts.contains(nextCloneId)) {
                continue;
            }

            final var nextClone = createAccount(
                    nextCloneId, 0, treasury.expirationSecond(), treasury.key(), treasury.declineReward());
            treasuryClones.put(nextClone, newCryptoCreate(nextClone));
            accounts.put(nextCloneId, nextClone);
        }
        recordsKeeper.treasuryClones(treasuryClones);
        log.info(
                "Created {} zero-balance accounts cloning treasury properties in the {}-{} range",
                treasuryClones.size(),
                FIRST_POST_SYSTEM_FILE_ENTITY,
                ledgerConfig.numReservedSystemEntities());

        // Create the network rewards state
        initializeNetworkRewards(ctx);
        initializeStakingNodeInfo(ctx);

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
        log.info(
                "Ledger float is {} tinyBars in {} accounts.",
                totalBalance,
                accounts.modifiedKeys().size());

        // ---------- Create blocklist accounts (if enabled) -------------------------
        final Map<Account, CryptoCreateTransactionBody.Builder> blocklistAccts = new HashMap<>();
        if (accountsConfig.blocklistEnabled()) {
            final var blocklistResourceName = accountsConfig.blocklistResource();
            final var blocklist = blocklistParser.parse(blocklistResourceName);
            if (blocklist.isEmpty()) {
                return;
            }

            final var aliases = ctx.newStates().<Bytes, AccountID>get(TokenServiceImpl.ALIASES_KEY);

            // We only want to create accounts that are not already in state, so we filter based on blocked account EVM
            // addresses that don't yet exist in state
            final var blockedToCreate = blocklist.stream()
                    .filter(blockedAccount -> aliases.get(blockedAccount.evmAddress()) == null)
                    .toList();

            for (final var blockedInfo : blockedToCreate) {
                final var newId = ctx.newEntityNum();
                final var account = blockedAccountWith(blockedInfo, bootstrapConfig)
                        .accountId(asAccount(newId))
                        .build();
                blocklistAccts.put(account, newCryptoCreate(account));
                accounts.put(account.accountIdOrThrow(), account);
                aliases.put(account.alias(), account.accountIdOrThrow());
            }
        }
        recordsKeeper.blocklistAccounts(blocklistAccts);
        log.info("Created {} blocklist accounts", blocklistAccts.size());
    }

    /**
     * Creates a blocked Hedera account with the given memo and EVM address.
     * A blocked account has receiverSigRequired flag set to true, key set to the genesis key, and balance set to 0.
     *
     * @param blockedInfo record containing EVM address and memo for the blocked account
     * @return a Hedera account with the given memo and EVM address
     */
    @NonNull
    private Account.Builder blockedAccountWith(
            @NonNull final BlocklistParser.BlockedInfo blockedInfo, @NonNull final BootstrapConfig bootstrapConfig) {
        final var expiry = bootstrapConfig.systemEntityExpiry();
        final var acctBuilder = Account.newBuilder()
                .receiverSigRequired(true)
                .declineReward(true)
                .deleted(false)
                .expirationSecond(expiry)
                .smartContract(false)
                .key(superUserKey(bootstrapConfig))
                .autoRenewSeconds(expiry)
                .alias(blockedInfo.evmAddress());

        if (!blockedInfo.memo().isEmpty()) acctBuilder.memo(blockedInfo.memo());

        return acctBuilder;
    }

    private static AccountID asAccountId(final long acctNum, final HederaConfig hederaConfig) {
        return AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(acctNum)
                .build();
    }

    @VisibleForTesting
    public static long[] nonContractSystemNums(final long numReservedSystemEntities) {
        return LongStream.rangeClosed(FIRST_POST_SYSTEM_FILE_ENTITY, numReservedSystemEntities)
                .filter(i -> i < FIRST_RESERVED_SYSTEM_CONTRACT || i > LAST_RESERVED_SYSTEM_CONTRACT)
                .toArray();
    }

    @NonNull
    private Key superUserKey(@NonNull final BootstrapConfig bootstrapConfig) {
        final var superUserKeyBytes = bootstrapConfig.genesisPublicKey();
        if (superUserKeyBytes.length() != 32) {
            throw new IllegalStateException("'" + superUserKeyBytes + "' is not a possible Ed25519 public key");
        }
        return Key.newBuilder().ed25519(superUserKeyBytes).build();
    }

    @NonNull
    private Account createAccount(
            @NonNull final AccountID id, final long balance, final long expiry, @NonNull final Key key) {
        return createAccount(id, balance, expiry, key, true);
    }

    @NonNull
    private Account createAccount(
            @NonNull final AccountID id,
            final long balance,
            final long expiry,
            final Key key,
            final boolean declineReward) {
        return Account.newBuilder()
                .accountId(id)
                .receiverSigRequired(false)
                .deleted(false)
                .expirationSecond(expiry)
                .memo("")
                .smartContract(false)
                .key(key)
                .declineReward(declineReward)
                .autoRenewSeconds(expiry)
                .maxAutoAssociations(0)
                .tinybarBalance(balance)
                .build();
    }

    private void initializeStakingNodeInfo(@NonNull final MigrationContext ctx) {
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

    private static CryptoCreateTransactionBody.Builder newCryptoCreate(@NonNull final Account account) {
        return CryptoCreateTransactionBody.newBuilder()
                .key(account.key())
                .memo(account.memo())
                .declineReward(account.declineReward())
                .receiverSigRequired(account.receiverSigRequired())
                .autoRenewPeriod(Duration.newBuilder()
                        .seconds(account.autoRenewSeconds())
                        .build())
                .initialBalance(account.tinybarBalance())
                .alias(account.alias());
    }
}
