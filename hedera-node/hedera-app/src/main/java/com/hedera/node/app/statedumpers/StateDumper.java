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

import static com.hedera.node.app.ids.EntityIdService.ENTITY_ID_STATE_KEY;
import static com.hedera.node.app.records.BlockRecordService.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.records.BlockRecordService.RUNNING_HASHES_STATE_KEY;
import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_ID_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.NFTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKEN_RELS_KEY;
import static com.hedera.node.app.state.recordcache.RecordCacheService.TXN_RECORD_QUEUE;
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
import static com.hedera.node.app.throttle.CongestionThrottleService.CONGESTION_LEVEL_STARTS_STATE_KEY;
import static com.hedera.node.app.throttle.CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY;
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
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.state.schedule.Schedule;
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
import com.hedera.node.app.service.contract.impl.state.InitialModServiceContractSchema;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.state.merkle.memory.InMemoryKey;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.hedera.node.app.state.merkle.queue.QueueNode;
import com.hedera.node.app.state.merkle.singleton.SingletonNode;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

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
            @NonNull final MerkleHederaState state, @NonNull final DumpCheckpoint checkpoint) {
        final SingletonNode<BlockInfo> blockInfoNode =
                requireNonNull(state.getChild(state.findNodeIndex(BlockRecordService.NAME, BLOCK_INFO_STATE_KEY)));
        final var blockInfo = blockInfoNode.getValue();
        final var dumpLoc = getExtantDumpLoc(
                "mod",
                Optional.ofNullable(blockInfo.consTimeOfLastHandledTxn())
                        .map(then -> Instant.ofEpochSecond(then.seconds(), then.nanos()))
                        .orElse(null));

        final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> uniqueTokens =
                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, NFTS_KEY)));
        dumpModUniqueTokens(Paths.get(dumpLoc, SEMANTIC_UNIQUE_TOKENS), uniqueTokens, checkpoint);

        final VirtualMap<OnDiskKey<EntityIDPair>, OnDiskValue<TokenRelation>> tokenRelations =
                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, TOKEN_RELS_KEY)));
        dumpModTokenRelations(Paths.get(dumpLoc, SEMANTIC_TOKEN_RELATIONS), tokenRelations, checkpoint);

        final VirtualMap<OnDiskKey<FileID>, OnDiskValue<com.hedera.hapi.node.state.file.File>> files =
                requireNonNull(state.getChild(state.findNodeIndex(FileService.NAME, FileServiceImpl.BLOBS_KEY)));
        dumpModFiles(Paths.get(dumpLoc, SEMANTIC_FILES), files, checkpoint);

        final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts =
                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, ACCOUNTS_KEY)));
        dumpModAccounts(Paths.get(dumpLoc, SEMANTIC_ACCOUNTS), accounts, checkpoint);

        final VirtualMap<OnDiskKey<ContractID>, OnDiskValue<Bytecode>> contracts = requireNonNull(state.getChild(
                state.findNodeIndex(ContractService.NAME, InitialModServiceContractSchema.BYTECODE_KEY)));
        dumpModContractBytecodes(Paths.get(dumpLoc, SEMANTIC_CONTRACT_BYTECODES), contracts, checkpoint);

        final VirtualMap<OnDiskKey<TopicID>, OnDiskValue<Topic>> topics = requireNonNull(
                state.getChild(state.findNodeIndex(ConsensusService.NAME, ConsensusServiceImpl.TOPICS_KEY)));
        dumpModTopics(Paths.get(dumpLoc, SEMANTIC_TOPICS), topics, checkpoint);

        final VirtualMap<OnDiskKey<ScheduleID>, OnDiskValue<Schedule>> scheduledTransactions =
                requireNonNull(state.getChild(state.findNodeIndex(ScheduleService.NAME, SCHEDULES_BY_ID_KEY)));
        dumpModScheduledTransactions(
                Paths.get(dumpLoc, SEMANTIC_SCHEDULED_TRANSACTIONS), scheduledTransactions, checkpoint);

        final VirtualMap<OnDiskKey<TokenID>, OnDiskValue<Token>> tokenTypes =
                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, TOKENS_KEY)));
        dumpModTokenType(Paths.get(dumpLoc, SEMANTIC_TOKEN_TYPE), tokenTypes, checkpoint);

        // Dump block info, running hashes and entity id
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

        // Dump staking info
        final MerkleMap<InMemoryKey<EntityNumber>, InMemoryValue<EntityNumber, StakingNodeInfo>> stakingInfoMap =
                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY)));
        dumpModStakingInfo(Paths.get(dumpLoc, SEMANTIC_STAKING_INFO), stakingInfoMap, checkpoint);

        // Dump staking rewards
        final SingletonNode<NetworkStakingRewards> stakingRewards =
                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, STAKING_NETWORK_REWARDS_KEY)));
        dumpModStakingRewards(Paths.get(dumpLoc, SEMANTIC_STAKING_REWARDS), stakingRewards.getValue(), checkpoint);

        // Dump txn record queue
        final QueueNode<TransactionRecordEntry> queue =
                requireNonNull(state.getChild(state.findNodeIndex(RecordCacheService.NAME, TXN_RECORD_QUEUE)));
        dumpModTxnRecordQueue(Paths.get(dumpLoc, SEMANTIC_TXN_RECORD_QUEUE), queue, checkpoint);

        // Dump congestion snapshots
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
