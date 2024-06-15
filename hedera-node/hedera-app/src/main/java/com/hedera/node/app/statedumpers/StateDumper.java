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

package com.hedera.node.app.statedumpers;

import static com.hedera.node.app.ids.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_KEY;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_ID_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.NFTS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKEN_RELS_KEY;
import static com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema.TXN_RECORD_QUEUE;
import static com.hedera.node.app.statedumpers.accounts.AccountDumpUtils.dumpModAccounts;
import static com.hedera.node.app.statedumpers.associations.TokenAssociationsDumpUtils.dumpModTokenRelations;
import static com.hedera.node.app.statedumpers.contracts.ContractBytecodesDumpUtils.dumpModContractBytecodes;
import static com.hedera.node.app.statedumpers.files.FilesDumpUtils.dumpModFiles;
import static com.hedera.node.app.statedumpers.nfts.UniqueTokenDumpUtils.dumpModUniqueTokens;
import static com.hedera.node.app.statedumpers.scheduledtransactions.ScheduledTransactionsDumpUtils.dumpModScheduledTransactions;
import static com.hedera.node.app.statedumpers.singleton.BlockInfoDumpUtils.dumpModBlockInfo;
import static com.hedera.node.app.statedumpers.singleton.CongestionDumpUtils.dumpModCongestion;
import static com.hedera.node.app.statedumpers.singleton.PayerRecordsDumpUtils.dumpModTxnRecordQueue;
import static com.hedera.node.app.statedumpers.singleton.StakingInfoDumpUtils.dumpModStakingInfo;
import static com.hedera.node.app.statedumpers.singleton.StakingRewardsDumpUtils.dumpModStakingRewards;
import static com.hedera.node.app.statedumpers.tokentypes.TokenTypesDumpUtils.dumpModTokenType;
import static com.hedera.node.app.statedumpers.topics.TopicDumpUtils.dumpModTopics;
import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_KEY;
import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.DumpableLeaf;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.swirlds.platform.state.merkle.disk.OnDiskKey;
import com.swirlds.platform.state.merkle.disk.OnDiskValue;
import com.swirlds.platform.state.merkle.memory.InMemoryKey;
import com.swirlds.platform.state.merkle.memory.InMemoryValue;
import com.swirlds.platform.state.merkle.queue.QueueNode;
import com.swirlds.platform.state.merkle.singleton.SingletonNode;
import com.swirlds.state.HederaState;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * A utility class for dumping the state of the {@link MerkleHederaState} to a directory.
 */
public class StateDumper {
    private static final String SEMANTIC_UNIQUE_TOKENS = "uniqueTokens.txt";
    private static final String SEMANTIC_TOKEN_RELATIONS = "tokenRelations.txt";
    private static final String SEMANTIC_FILES = "files.txt";
    private static final String SEMANTIC_ACCOUNTS = "accounts.txt";
    private static final String SEMANTIC_CONTRACT_BYTECODES = "contractBytecodes.txt";
    private static final String SEMANTIC_TOPICS = "topics.txt";
    private static final String SEMANTIC_SCHEDULED_TRANSACTIONS = "scheduledTransactions.txt";
    private static final String SEMANTIC_TOKEN_TYPE = "tokenTypes.txt";

    private static final String SEMANTIC_BLOCK = "blockInfo.txt";
    private static final String SEMANTIC_STAKING_INFO = "stakingInfo.txt";
    private static final String SEMANTIC_STAKING_REWARDS = "stakingRewards.txt";
    private static final String SEMANTIC_TXN_RECORD_QUEUE = "transactionRecords.txt";
    private static final String SEMANTIC_CONGESTION = "congestion.txt";

    public static void dumpModChildrenFrom(
            @NonNull final HederaState hederaState,
            @NonNull final DumpCheckpoint checkpoint,
            @NonNull final Set<DumpableLeaf> leaves) {
        if (!(hederaState instanceof MerkleHederaState state)) {
            throw new IllegalArgumentException("Expected a MerkleHederaState");
        }
        final SingletonNode<BlockInfo> blockInfoNode =
                requireNonNull(state.getChild(state.findNodeIndex(BlockRecordService.NAME, BLOCK_INFO_STATE_KEY)));
        final var blockInfo = blockInfoNode.getValue();
        final var dumpLoc = getExtantDumpLoc(
                "mod",
                Optional.ofNullable(blockInfo.consTimeOfLastHandledTxn())
                        .map(then -> Instant.ofEpochSecond(then.seconds(), then.nanos()))
                        .orElse(null));

        if (leaves.contains(DumpableLeaf.NFTS)) {
            final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> uniqueTokens =
                    requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, NFTS_KEY)));
            dumpModUniqueTokens(Paths.get(dumpLoc, SEMANTIC_UNIQUE_TOKENS), uniqueTokens, checkpoint);
        }

        if (leaves.contains(DumpableLeaf.TOKEN_RELS)) {
            final VirtualMap<OnDiskKey<EntityIDPair>, OnDiskValue<TokenRelation>> tokenRelations =
                    requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, TOKEN_RELS_KEY)));
            dumpModTokenRelations(Paths.get(dumpLoc, SEMANTIC_TOKEN_RELATIONS), tokenRelations, checkpoint);
        }

        if (leaves.contains(DumpableLeaf.FILES)) {
            final VirtualMap<OnDiskKey<FileID>, OnDiskValue<com.hedera.hapi.node.state.file.File>> files =
                    requireNonNull(state.getChild(state.findNodeIndex(FileService.NAME, V0490FileSchema.BLOBS_KEY)));
            dumpModFiles(Paths.get(dumpLoc, SEMANTIC_FILES), files, checkpoint);
        }

        if (leaves.contains(DumpableLeaf.ACCOUNTS)) {
            final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts = requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, ACCOUNTS_KEY)));
            dumpModAccounts(Paths.get(dumpLoc, SEMANTIC_ACCOUNTS), accounts, checkpoint);
        }

        if (leaves.contains(DumpableLeaf.BYTECODE)) {
            final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts = requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, ACCOUNTS_KEY)));
            final VirtualMap<OnDiskKey<ContractID>, OnDiskValue<Bytecode>> byteCodes = requireNonNull(
                    state.getChild(state.findNodeIndex(ContractService.NAME, V0490ContractSchema.BYTECODE_KEY)));
            dumpModContractBytecodes(
                    Paths.get(dumpLoc, SEMANTIC_CONTRACT_BYTECODES),
                    byteCodes,
                    accounts,
                    (StateMetadata<AccountID, Account>)
                            state.getServices().get(TokenService.NAME).get(ACCOUNTS_KEY),
                    checkpoint);
        }

        if (leaves.contains(DumpableLeaf.TOPICS)) {
            final VirtualMap<OnDiskKey<TopicID>, OnDiskValue<Topic>> topics = requireNonNull(
                    state.getChild(state.findNodeIndex(ConsensusService.NAME, ConsensusServiceImpl.TOPICS_KEY)));
            dumpModTopics(Paths.get(dumpLoc, SEMANTIC_TOPICS), topics, checkpoint);
        }

        if (leaves.contains(DumpableLeaf.SCHEDULED_TRANSACTIONS)) {
            final VirtualMap<OnDiskKey<ScheduleID>, OnDiskValue<Schedule>> scheduledTransactionsByKey =
                    requireNonNull(state.getChild(state.findNodeIndex(ScheduleService.NAME, SCHEDULES_BY_ID_KEY)));
            final VirtualMap<OnDiskKey<ProtoBytes>, OnDiskValue<ScheduleList>>
                    scheduledTransactionsByEquality = requireNonNull(
                            state.getChild(state.findNodeIndex(ScheduleService.NAME, SCHEDULES_BY_EQUALITY_KEY)));
            final VirtualMap<OnDiskKey<ProtoLong>, OnDiskValue<ScheduleList>> scheduledTransactionsByExpiry =
                    requireNonNull(state.getChild(state.findNodeIndex(ScheduleService.NAME, SCHEDULES_BY_EXPIRY_SEC_KEY)));
            dumpModScheduledTransactions(
                    Paths.get(dumpLoc, SEMANTIC_SCHEDULED_TRANSACTIONS),
                    scheduledTransactionsByKey,
                    scheduledTransactionsByEquality,
                    scheduledTransactionsByExpiry,
                    checkpoint);
        }

        if (leaves.contains(DumpableLeaf.TOKENS)) {
            final VirtualMap<OnDiskKey<TokenID>, OnDiskValue<Token>> tokenTypes =
                    requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, TOKENS_KEY)));
            dumpModTokenType(Paths.get(dumpLoc, SEMANTIC_TOKEN_TYPE), tokenTypes, checkpoint);
        }

        if (leaves.contains(DumpableLeaf.BLOCK_METADATA)) {
            final SingletonNode<RunningHashes> runningHashesSingleton =
                    requireNonNull(state.getChild(state.findNodeIndex(BlockRecordService.NAME, RUNNING_HASHES_STATE_KEY)));
            final SingletonNode<BlockInfo> blocksStateSingleton =
                    requireNonNull(state.getChild(state.findNodeIndex(BlockRecordService.NAME, BLOCK_INFO_STATE_KEY)));
            final SingletonNode<EntityNumber> entityIdSingleton =
                    requireNonNull(state.getChild(state.findNodeIndex(EntityIdService.NAME, ENTITY_ID_STATE_KEY)));
            dumpModBlockInfo(
                    Paths.get(dumpLoc, SEMANTIC_BLOCK),
                    runningHashesSingleton.getValue(),
                    blocksStateSingleton.getValue(),
                    entityIdSingleton.getValue(),
                    checkpoint);
        }

        if (leaves.contains(DumpableLeaf.STAKING_INFOS)) {
            final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> stakingInfoMap =
                    requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY)));
            dumpModStakingInfo(Paths.get(dumpLoc, SEMANTIC_STAKING_INFO), stakingInfoMap, checkpoint);
        }

        if (leaves.contains(DumpableLeaf.STAKING_NETWORK_METADATA)) {
            final SingletonNode<NetworkStakingRewards> stakingRewards =
                    requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, STAKING_NETWORK_REWARDS_KEY)));
            dumpModStakingRewards(Paths.get(dumpLoc, SEMANTIC_STAKING_REWARDS), stakingRewards.getValue(), checkpoint);
        }

        if (leaves.contains(DumpableLeaf.TRANSACTION_RECORD_QUEUE)) {
            final QueueNode<TransactionRecordEntry> queue =
                    requireNonNull(state.getChild(state.findNodeIndex(RecordCacheService.NAME, TXN_RECORD_QUEUE)));
            dumpModTxnRecordQueue(Paths.get(dumpLoc, SEMANTIC_TXN_RECORD_QUEUE), queue, checkpoint);
        }

        if (leaves.contains(DumpableLeaf.THROTTLE_METADATA)) {
            final SingletonNode<CongestionLevelStarts> congestionLevelStartsSingletonNode = requireNonNull(
                    state.getChild(state.findNodeIndex(CongestionThrottleService.NAME, CONGESTION_LEVEL_STARTS_STATE_KEY)));
            final SingletonNode<ThrottleUsageSnapshots> throttleUsageSnapshotsSingletonNode = requireNonNull(state.getChild(
                    state.findNodeIndex(CongestionThrottleService.NAME, THROTTLE_USAGE_SNAPSHOTS_STATE_KEY)));
            dumpModCongestion(
                    Paths.get(dumpLoc, SEMANTIC_CONGESTION),
                    congestionLevelStartsSingletonNode.getValue(),
                    throttleUsageSnapshotsSingletonNode.getValue(),
                    checkpoint);
        }
    }

    private static String getExtantDumpLoc(
            @NonNull final String stateType, @Nullable final Instant lastHandledConsensusTime) {
        final var dumpLoc = dirFor(stateType, lastHandledConsensusTime);
        new File(dumpLoc).mkdirs();
        return dumpLoc;
    }

    private static String dirFor(@NonNull final String stateType, @Nullable final Instant lastHandledConsensusTime) {
        final var effectiveTime = lastHandledConsensusTime == null ? Instant.EPOCH : lastHandledConsensusTime;
        return String.format("%s-%s", stateType, effectiveTime.toString().replace(":", "_"));
    }
}
