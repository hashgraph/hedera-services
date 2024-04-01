/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.token.api.StakingRewardsApi.clampedStakePeriodStart;
import static com.hedera.node.app.service.token.api.StakingRewardsApi.computeRewardFromDetails;
import static com.hedera.node.app.service.token.api.StakingRewardsApi.stakePeriodAt;
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
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
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
import com.hedera.node.app.service.token.AliasUtils;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.codec.NetworkingStakingTranslator;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the token service
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47TokenSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class InitialModServiceTokenSchema extends Schema {
    private static final Logger log = LogManager.getLogger(InitialModServiceTokenSchema.class);
    // These need to be big so databases are created at right scale. If they are too small then the on disk hash map
    // buckets will be too full which results in very poor performance. Have chosen 10 billion as should give us
    // plenty of runway.
    private static final long MAX_TOKENS = 1_000_000_000L;
    private static final long MAX_ACCOUNTS = 1_000_000_000L;
    private static final long MAX_TOKEN_RELS = 1_000_000_000L;
    private static final long MAX_MINTABLE_NFTS = 1_000_000_000L;
    private static final long FIRST_RESERVED_SYSTEM_CONTRACT = 350L;
    private static final long LAST_RESERVED_SYSTEM_CONTRACT = 399L;
    private static final long FIRST_POST_SYSTEM_FILE_ENTITY = 200L;

    private final Supplier<SortedSet<Account>> sysAccts;
    private final Supplier<SortedSet<Account>> stakingAccts;
    private final Supplier<SortedSet<Account>> treasuryAccts;
    private final Supplier<SortedSet<Account>> miscAccts;
    private final Supplier<SortedSet<Account>> blocklistAccts;

    private VirtualMap<EntityNumVirtualKey, OnDiskAccount> acctsFs;
    private MerkleMap<EntityNum, MerkleToken> tFs;
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingFs;
    private VirtualMap<UniqueTokenKey, UniqueTokenValue> nftsFs;
    private VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> trFs;
    private MerkleNetworkContext mnc;

    /**
     * Constructor for this schema. Each of the supplier params should produce a {@link SortedSet} of
     * {@link Account} objects, where each account object represents a _synthetic record_ (see {@link
     * SyntheticRecordsGenerator} for more details). Even though these sorted sets contain account
     * objects, these account objects may or may not yet exist in state. They're usually not needed,
     * but are required for an event recovery situation.
     */
    public InitialModServiceTokenSchema(
            final Supplier<SortedSet<Account>> sysAccts,
            final Supplier<SortedSet<Account>> stakingAccts,
            final Supplier<SortedSet<Account>> treasuryAccts,
            final Supplier<SortedSet<Account>> miscAccts,
            final Supplier<SortedSet<Account>> blocklistAccts,
            final SemanticVersion version) {
        super(version);

        this.sysAccts = sysAccts;
        this.stakingAccts = stakingAccts;
        this.treasuryAccts = treasuryAccts;
        this.miscAccts = miscAccts;
        this.blocklistAccts = blocklistAccts;
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.onDisk(TOKENS_KEY, TokenID.PROTOBUF, Token.PROTOBUF, MAX_TOKENS),
                StateDefinition.onDisk(ACCOUNTS_KEY, AccountID.PROTOBUF, Account.PROTOBUF, MAX_ACCOUNTS),
                StateDefinition.onDisk(ALIASES_KEY, ProtoBytes.PROTOBUF, AccountID.PROTOBUF, MAX_ACCOUNTS),
                StateDefinition.onDisk(NFTS_KEY, NftID.PROTOBUF, Nft.PROTOBUF, MAX_MINTABLE_NFTS),
                StateDefinition.onDisk(TOKEN_RELS_KEY, EntityIDPair.PROTOBUF, TokenRelation.PROTOBUF, MAX_TOKEN_RELS),
                StateDefinition.inMemory(STAKING_INFO_KEY, EntityNumber.PROTOBUF, StakingNodeInfo.PROTOBUF),
                StateDefinition.singleton(STAKING_NETWORK_REWARDS_KEY, NetworkStakingRewards.PROTOBUF));
    }

    public void setNftsFromState(@Nullable final VirtualMap<UniqueTokenKey, UniqueTokenValue> fs) {
        this.nftsFs = fs;
    }

    public void setTokenRelsFromState(@Nullable final VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> fs) {
        this.trFs = fs;
    }

    public void setAcctsFromState(@Nullable final VirtualMap<EntityNumVirtualKey, OnDiskAccount> fs) {
        this.acctsFs = fs;
    }

    public void setTokensFromState(@Nullable final MerkleMap<EntityNum, MerkleToken> fs) {
        this.tFs = fs;
    }

    public void setStakingFs(
            @Nullable final MerkleMap<EntityNum, MerkleStakingInfo> stakingFs,
            @Nullable final MerkleNetworkContext mnc) {
        this.stakingFs = stakingFs;
        this.mnc = mnc;
    }

    @Override
    public void restart(@NonNull MigrationContext ctx) {
        // We need to validate and mark any node that are removed during upgrade as deleted.
        // Since restart is called in the schema after an upgrade, and we don't want to depend on schema version change
        // validate all the nodeIds from the addressBook in state and mark them as deleted if they are not yet deleted
        // in staking info.
        final var stakingToState = ctx.newStates().<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY);
        final var networkInfo = ctx.networkInfo();
        stakingToState.keys().forEachRemaining(nodeId -> {
            final var stakingInfo = requireNonNull(stakingToState.get(nodeId));
            if (!networkInfo.containsNode(nodeId.number()) && !stakingInfo.deleted()) {
                stakingToState.put(
                        nodeId, stakingInfo.copyBuilder().deleted(true).build());
                log.info(
                        "Node {} is marked deleted since it is deleted from addressBook during restart.",
                        nodeId.number());
            }
        });
        if (stakingToState.isModified()) {
            ((WritableKVStateBase) stakingToState).commit();
        }
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var isGenesis = ctx.previousVersion() == null;
        if (isGenesis) {
            createGenesisSchema(ctx);
        }

        if (acctsFs != null) {
            log.info("BBM: migrating token service");

            // ---------- NFTs
            log.info("BBM: doing nfts...");
            final var nftsToState = new AtomicReference<>(ctx.newStates().<NftID, Nft>get(NFTS_KEY));
            final var numNftInsertions = new AtomicLong();
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
                                    var fromNft2 = new MerkleUniqueToken(
                                            fromNft.getOwner(), fromNft.getMetadata(), fromNft.getCreationTime());
                                    fromNft2.setKey(nftId.toEntityNumPair());
                                    fromNft2.setPrev(fromNft.getPrev());
                                    fromNft2.setNext(fromNft.getNext());
                                    fromNft2.setSpender(fromNft.getSpender());

                                    var translated = NftStateTranslator.nftFromMerkleUniqueToken(fromNft2);
                                    nftsToState.get().put(toNftId, translated);
                                    if (numNftInsertions.incrementAndGet() % 10_000 == 0) {
                                        // Make sure we are flushing data to disk as we go
                                        ((WritableKVStateBase) nftsToState.get()).commit();
                                        ctx.copyAndReleaseOnDiskState(NFTS_KEY);
                                        // And ensure we have the latest writable state
                                        nftsToState.set(ctx.newStates().get(NFTS_KEY));
                                    }
                                },
                                1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (nftsToState.get().isModified()) ((WritableKVStateBase) nftsToState.get()).commit();
            log.info("BBM: finished nfts");

            // ---------- Token Rels/Associations
            log.info("BBM: doing token rels...");
            final var numTokenRelInsertions = new AtomicLong();
            final var tokenRelsToState =
                    new AtomicReference<>(ctx.newStates().<EntityIDPair, TokenRelation>get(TOKEN_RELS_KEY));
            try {
                VirtualMapLike.from(trFs)
                        .extractVirtualMapData(
                                AdHocThreadManager.getStaticThreadManager(),
                                entry -> {
                                    var fromTokenRel = entry.right();
                                    var key = fromTokenRel.getKey();
                                    var translated = TokenRelationStateTranslator.tokenRelationFromOnDiskTokenRelStatus(
                                            fromTokenRel);
                                    var newPair = EntityIDPair.newBuilder()
                                            .accountId(AccountID.newBuilder()
                                                    .accountNum(key.getHiOrderAsLong())
                                                    .build())
                                            .tokenId(TokenID.newBuilder()
                                                    .tokenNum(key.getLowOrderAsLong())
                                                    .build())
                                            .build();
                                    tokenRelsToState.get().put(newPair, translated);
                                    if (numTokenRelInsertions.incrementAndGet() % 10_000 == 0) {
                                        // Make sure we are flushing data to disk as we go
                                        ((WritableKVStateBase) tokenRelsToState.get()).commit();
                                        ctx.copyAndReleaseOnDiskState(TOKEN_RELS_KEY);
                                        // And ensure we have the latest writable state
                                        tokenRelsToState.set(ctx.newStates().get(TOKEN_RELS_KEY));
                                    }
                                },
                                1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (tokenRelsToState.get().isModified()) ((WritableKVStateBase) tokenRelsToState.get()).commit();
            log.info("BBM: finished token rels");

            // ---------- Staking Info
            log.info("BBM: starting staking info");
            var stakingToState = ctx.newStates().<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY);
            MerkleMapLike.from(stakingFs).forEachNode((entityNum, merkleStakingInfo) -> {
                var toStakingInfo = StakingNodeInfoStateTranslator.stakingInfoFromMerkleStakingInfo(merkleStakingInfo);
                stakingToState.put(
                        EntityNumber.newBuilder()
                                .number(merkleStakingInfo.getKey().longValue())
                                .build(),
                        toStakingInfo);
            });
            if (stakingToState.isModified()) ((WritableKVStateBase) stakingToState).commit();
            final var stakingConfig = ctx.configuration().getConfigData(StakingConfig.class);
            final var currentStakingPeriod =
                    stakePeriodAt(mnc.consensusTimeOfLastHandledTxn(), stakingConfig.periodMins());
            final var numStoredPeriods = stakingConfig.rewardHistoryNumStoredPeriods();
            log.info("BBM: finished staking info");

            // ---------- Accounts
            log.info("BBM: doing accounts");
            final var numAccountInsertions = new AtomicLong();
            final var numAliasesInsertions = new AtomicLong();
            final var acctsToState = new AtomicReference<>(ctx.newStates().<AccountID, Account>get(ACCOUNTS_KEY));
            final var aliasesState = new AtomicReference<>(ctx.newStates().<ProtoBytes, AccountID>get(ALIASES_KEY));
            final Map<Long, Long> pendingRewards = new ConcurrentHashMap<>();
            try {
                VirtualMapLike.from(acctsFs)
                        .extractVirtualMapData(
                                AdHocThreadManager.getStaticThreadManager(),
                                entry -> {
                                    var acctNum = entry.left().asEntityNum().longValue();
                                    var fromAcct = entry.right();
                                    var toAcct = AccountStateTranslator.accountFromOnDiskAccount(fromAcct);
                                    acctsToState
                                            .get()
                                            .put(
                                                    AccountID.newBuilder()
                                                            .accountNum(acctNum)
                                                            .build(),
                                                    toAcct);
                                    if (!toAcct.deleted() && !toAcct.declineReward() && toAcct.hasStakedNodeId()) {
                                        final var stakedNodeId = toAcct.stakedNodeIdOrThrow();
                                        final var stakingInfo = stakingToState.get(new EntityNumber(stakedNodeId));
                                        final var reward = computeRewardFromDetails(
                                                toAcct,
                                                stakingInfo,
                                                currentStakingPeriod,
                                                clampedStakePeriodStart(
                                                        toAcct.stakePeriodStart(),
                                                        currentStakingPeriod,
                                                        numStoredPeriods));
                                        pendingRewards.merge(stakedNodeId, reward, Long::sum);
                                    }
                                    if (numAccountInsertions.incrementAndGet() % 10_000 == 0) {
                                        // Make sure we are flushing data to disk as we go
                                        ((WritableKVStateBase) acctsToState.get()).commit();
                                        ctx.copyAndReleaseOnDiskState(ACCOUNTS_KEY);
                                        // And ensure we have the latest writable state
                                        acctsToState.set(ctx.newStates().get(ACCOUNTS_KEY));
                                    }
                                    if (toAcct.alias().length() > 0) {
                                        aliasesState
                                                .get()
                                                .put(new ProtoBytes(toAcct.alias()), toAcct.accountIdOrThrow());
                                        if (toAcct.alias().toByteArray().length > 20) {
                                            final var result = AliasUtils.extractEvmAddress(toAcct.alias());
                                            if (result != null) {
                                                aliasesState.get().put(new ProtoBytes(result), toAcct.accountId());
                                            }
                                        }
                                        if (numAliasesInsertions.incrementAndGet() % 10_000 == 0) {
                                            // Make sure we are flushing data to disk as we go
                                            ((WritableKVStateBase) aliasesState.get()).commit();
                                            ctx.copyAndReleaseOnDiskState(ALIASES_KEY);
                                            // And ensure we have the latest writable state
                                            aliasesState.set(ctx.newStates().get(ALIASES_KEY));
                                        }
                                    }
                                },
                                1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (acctsToState.get().isModified()) ((WritableKVStateBase) acctsToState.get()).commit();
            // Also persist the per-node pending reward information
            MerkleMapLike.from(stakingFs).forEachNode((entityNum, ignore) -> {
                final var toKey = new EntityNumber(entityNum.longValue());
                final var info = requireNonNull(stakingToState.get(toKey));
                stakingToState.put(
                        toKey,
                        info.copyBuilder()
                                .pendingRewards(pendingRewards.getOrDefault(toKey.number(), 0L))
                                .build());
            });
            if (stakingToState.isModified()) ((WritableKVStateBase) stakingToState).commit();
            log.info("BBM: finished accts");

            // ---------- Tokens
            log.info("BBM: starting tokens (both fungible and non-fungible)");
            var tokensToState = ctx.newStates().<TokenID, Token>get(TOKENS_KEY);
            MerkleMapLike.from(tFs).forEachNode((entityNum, merkleToken) -> {
                var toToken = TokenStateTranslator.tokenFromMerkle(merkleToken);
                tokensToState.put(
                        TokenID.newBuilder().tokenNum(entityNum.longValue()).build(), toToken);
            });
            if (tokensToState.isModified()) ((WritableKVStateBase) tokensToState).commit();
            log.info("BBM: finished tokens (fung and non-fung)");

            // ---------- Staking Rewards
            log.info("BBM: starting staking rewards");
            final var srToState = ctx.newStates().<NetworkStakingRewards>getSingleton(STAKING_NETWORK_REWARDS_KEY);
            final var toSr = NetworkingStakingTranslator.networkStakingRewardsFromMerkleNetworkContext(mnc);
            srToState.put(toSr);
            if (srToState.isModified()) ((WritableSingletonStateBase) srToState).commit();
            log.info("BBM: finished staking rewards");
        } else {
            log.warn("BBM: no token 'from' state found");
        }

        nftsFs = null;
        trFs = null;
        acctsFs = null;
        tFs = null;

        stakingFs = null;
        mnc = null;
    }

    private void createGenesisSchema(@NonNull final MigrationContext ctx) {
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
        final var totalBalance = getTotalBalanceOfAllAccounts(accounts, hederaConfig);
        if (totalBalance != ledgerConfig.totalTinyBarFloat()) {
            throw new IllegalStateException("Total balance of all accounts does not match the total float: actual: "
                    + totalBalance + " vs expected: " + ledgerConfig.totalTinyBarFloat());
        }
        log.info(
                "Ledger float is {} tinyBars; {} modified accounts.",
                totalBalance,
                accounts.modifiedKeys().size());
    }

    /**
     * Get the total balance of all accounts. Since we cannot iterate over the accounts in VirtualMap,
     * we have to do this manually.
     * @param accounts The accounts map
     * @param hederaConfig The Hedera configuration
     * @return The total balance of all accounts
     */
    public long getTotalBalanceOfAllAccounts(
            @NonNull final WritableKVState<AccountID, Account> accounts, @NonNull final HederaConfig hederaConfig) {
        long totalBalance = 0;
        long i = 1; // Start with the first account ID
        long totalAccounts = accounts.size();
        do {
            Account account = accounts.get(asAccountId(i, hederaConfig));
            if (account != null) {
                totalBalance += account.tinybarBalance();
                totalAccounts--;
            }
            i++;
        } while (totalAccounts > 0);
        return totalBalance;
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
