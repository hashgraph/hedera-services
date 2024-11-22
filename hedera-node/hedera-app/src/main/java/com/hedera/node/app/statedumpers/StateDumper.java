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

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.state.State;
import com.swirlds.state.merkle.queue.QueueNode;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * A utility class for dumping the state of the {@link MerkleStateRoot} to a directory.
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
            @NonNull final State state,
            @NonNull final DumpCheckpoint checkpoint,
            @NonNull final Set<MerkleStateChild> childrenToDump) {
        if (!(state instanceof MerkleStateRoot merkleState)) {
            throw new IllegalArgumentException("Expected a " + MerkleStateRoot.class.getSimpleName());
        }
        final SingletonNode<BlockInfo> blockInfoNode = requireNonNull(
                merkleState.getChild(merkleState.findNodeIndex(BlockRecordService.NAME, BLOCK_INFO_STATE_KEY)));
        final var blockInfo = blockInfoNode.getValue();
        final var dumpLoc = getExtantDumpLoc(
                "mod",
                Optional.ofNullable(blockInfo.consTimeOfLastHandledTxn())
                        .map(then -> Instant.ofEpochSecond(then.seconds(), then.nanos()))
                        .orElse(null));

        if (childrenToDump.contains(MerkleStateChild.NFTS)) {
            final VirtualMap uniqueTokens =
                    requireNonNull(merkleState.getChild(merkleState.findNodeIndex(TokenService.NAME, NFTS_KEY)));
            dumpModUniqueTokens(Paths.get(dumpLoc, SEMANTIC_UNIQUE_TOKENS), uniqueTokens, checkpoint);
        }

        if (childrenToDump.contains(MerkleStateChild.TOKEN_RELS)) {
            final VirtualMap tokenRelations =
                    requireNonNull(merkleState.getChild(merkleState.findNodeIndex(TokenService.NAME, TOKEN_RELS_KEY)));
            dumpModTokenRelations(Paths.get(dumpLoc, SEMANTIC_TOKEN_RELATIONS), tokenRelations, checkpoint);
        }

        if (childrenToDump.contains(MerkleStateChild.FILES)) {
            final VirtualMap files = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(FileService.NAME, V0490FileSchema.BLOBS_KEY)));
            dumpModFiles(Paths.get(dumpLoc, SEMANTIC_FILES), files, checkpoint);
        }

        if (childrenToDump.contains(MerkleStateChild.ACCOUNTS)) {
            final VirtualMap accounts =
                    requireNonNull(merkleState.getChild(merkleState.findNodeIndex(TokenService.NAME, ACCOUNTS_KEY)));
            dumpModAccounts(Paths.get(dumpLoc, SEMANTIC_ACCOUNTS), accounts, checkpoint);
        }

        if (childrenToDump.contains(MerkleStateChild.BYTECODE)) {
            final VirtualMap accounts =
                    requireNonNull(merkleState.getChild(merkleState.findNodeIndex(TokenService.NAME, ACCOUNTS_KEY)));
            final VirtualMap byteCodes = requireNonNull(merkleState.getChild(
                    merkleState.findNodeIndex(ContractService.NAME, V0490ContractSchema.BYTECODE_KEY)));
            dumpModContractBytecodes(Paths.get(dumpLoc, SEMANTIC_CONTRACT_BYTECODES), byteCodes, accounts, checkpoint);
        }

        if (childrenToDump.contains(MerkleStateChild.TOPICS)) {
            final VirtualMap topics = requireNonNull(merkleState.getChild(
                    merkleState.findNodeIndex(ConsensusService.NAME, ConsensusServiceImpl.TOPICS_KEY)));
            dumpModTopics(Paths.get(dumpLoc, SEMANTIC_TOPICS), topics, checkpoint);
        }

        if (childrenToDump.contains(MerkleStateChild.SCHEDULED_TRANSACTIONS)) {
            final VirtualMap scheduledTransactionsByKey = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(ScheduleService.NAME, SCHEDULES_BY_ID_KEY)));
            final VirtualMap scheduledTransactionsByEquality = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(ScheduleService.NAME, SCHEDULES_BY_EQUALITY_KEY)));
            final VirtualMap scheduledTransactionsByExpiry = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(ScheduleService.NAME, SCHEDULES_BY_EXPIRY_SEC_KEY)));
            dumpModScheduledTransactions(
                    Paths.get(dumpLoc, SEMANTIC_SCHEDULED_TRANSACTIONS),
                    scheduledTransactionsByKey,
                    scheduledTransactionsByEquality,
                    scheduledTransactionsByExpiry);
        }

        if (childrenToDump.contains(MerkleStateChild.TOKENS)) {
            final VirtualMap tokenTypes =
                    requireNonNull(merkleState.getChild(merkleState.findNodeIndex(TokenService.NAME, TOKENS_KEY)));
            dumpModTokenType(Paths.get(dumpLoc, SEMANTIC_TOKEN_TYPE), tokenTypes, checkpoint);
        }

        if (childrenToDump.contains(MerkleStateChild.BLOCK_METADATA)) {
            final SingletonNode<RunningHashes> runningHashesSingleton = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(BlockRecordService.NAME, RUNNING_HASHES_STATE_KEY)));
            final SingletonNode<BlockInfo> blocksStateSingleton = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(BlockRecordService.NAME, BLOCK_INFO_STATE_KEY)));
            final SingletonNode<EntityNumber> entityIdSingleton = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(EntityIdService.NAME, ENTITY_ID_STATE_KEY)));
            dumpModBlockInfo(
                    Paths.get(dumpLoc, SEMANTIC_BLOCK),
                    runningHashesSingleton.getValue(),
                    blocksStateSingleton.getValue(),
                    entityIdSingleton.getValue(),
                    checkpoint);
        }

        if (childrenToDump.contains(MerkleStateChild.STAKING_INFOS)) {
            final VirtualMap stakingInfoMap = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY)));
            dumpModStakingInfo(Paths.get(dumpLoc, SEMANTIC_STAKING_INFO), stakingInfoMap);
        }

        if (childrenToDump.contains(MerkleStateChild.STAKING_NETWORK_METADATA)) {
            final SingletonNode<NetworkStakingRewards> stakingRewards = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(TokenService.NAME, STAKING_NETWORK_REWARDS_KEY)));
            dumpModStakingRewards(Paths.get(dumpLoc, SEMANTIC_STAKING_REWARDS), stakingRewards.getValue());
        }

        if (childrenToDump.contains(MerkleStateChild.TRANSACTION_RECORD_QUEUE)) {
            final QueueNode<TransactionRecordEntry> queue = requireNonNull(
                    merkleState.getChild(merkleState.findNodeIndex(RecordCacheService.NAME, TXN_RECORD_QUEUE)));
            dumpModTxnRecordQueue(Paths.get(dumpLoc, SEMANTIC_TXN_RECORD_QUEUE), queue);
        }

        if (childrenToDump.contains(MerkleStateChild.THROTTLE_METADATA)) {
            final SingletonNode<CongestionLevelStarts> congestionLevelStartsSingletonNode =
                    requireNonNull(merkleState.getChild(merkleState.findNodeIndex(
                            CongestionThrottleService.NAME, CONGESTION_LEVEL_STARTS_STATE_KEY)));
            final SingletonNode<ThrottleUsageSnapshots> throttleUsageSnapshotsSingletonNode =
                    requireNonNull(merkleState.getChild(merkleState.findNodeIndex(
                            CongestionThrottleService.NAME, THROTTLE_USAGE_SNAPSHOTS_STATE_KEY)));
            dumpModCongestion(
                    Paths.get(dumpLoc, SEMANTIC_CONGESTION),
                    congestionLevelStartsSingletonNode.getValue(),
                    throttleUsageSnapshotsSingletonNode.getValue());
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
