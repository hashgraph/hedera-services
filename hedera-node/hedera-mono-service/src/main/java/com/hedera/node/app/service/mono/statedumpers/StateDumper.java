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

package com.hedera.node.app.service.mono.statedumpers;

import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.ACCOUNTS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.NETWORK_CTX;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.RECORD_STREAM_RUNNING_HASH;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.SCHEDULE_TXS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.STAKING_INFO;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.STORAGE;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.TOKENS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.TOKEN_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.TOPICS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.UNIQUE_TOKENS;
import static com.hedera.node.app.service.mono.statedumpers.accounts.AccountDumpUtils.dumpMonoAccounts;
import static com.hedera.node.app.service.mono.statedumpers.associations.TokenAssociationsDumpUtils.dumpMonoTokenRelations;
import static com.hedera.node.app.service.mono.statedumpers.files.FilesDumpUtils.dumpMonoFiles;
import static com.hedera.node.app.service.mono.statedumpers.nfts.UniqueTokenDumpUtils.dumpMonoUniqueTokens;
import static com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.ScheduledTransactionsDumpUtils.dumpMonoScheduledTransactions;
import static com.hedera.node.app.service.mono.statedumpers.singleton.BlockInfoDumpUtils.dumpMonoBlockInfo;
import static com.hedera.node.app.service.mono.statedumpers.singleton.CongestionDumpUtils.dumpMonoCongestion;
import static com.hedera.node.app.service.mono.statedumpers.singleton.StakingRewardsDumpUtils.dumpMonoStakingRewards;
import static com.hedera.node.app.service.mono.statedumpers.tokentypes.TokenTypesDumpUtils.dumpMonoTokenType;

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.statedumpers.contracts.ContractBytecodesDumpUtils;
import com.hedera.node.app.service.mono.statedumpers.singleton.PayerRecordsDumpUtils;
import com.hedera.node.app.service.mono.statedumpers.singleton.StakingInfoDumpUtils;
import com.hedera.node.app.service.mono.statedumpers.topics.TopicDumpUtils;
import com.swirlds.common.merkle.MerkleInternal;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * A utility class for dumping the state of the {@link MerkleInternal} to a directory.
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

    public static void dumpMonoChildrenFrom(
            @NonNull final MerkleInternal state, @NonNull final DumpCheckpoint checkpoint) {
        final MerkleNetworkContext networkContext = state.getChild(NETWORK_CTX);
        final var dumpLoc = getExtantDumpLoc("mono", networkContext.consensusTimeOfLastHandledTxn());
        dumpMonoUniqueTokens(Paths.get(dumpLoc, SEMANTIC_UNIQUE_TOKENS), state.getChild(UNIQUE_TOKENS), checkpoint);
        dumpMonoTokenRelations(
                Paths.get(dumpLoc, SEMANTIC_TOKEN_RELATIONS), state.getChild(TOKEN_ASSOCIATIONS), checkpoint);
        dumpMonoFiles(Paths.get(dumpLoc, SEMANTIC_FILES), state.getChild(STORAGE), checkpoint);
        dumpMonoAccounts(Paths.get(dumpLoc, SEMANTIC_ACCOUNTS), state.getChild(ACCOUNTS), checkpoint);
        ContractBytecodesDumpUtils.dumpMonoContractBytecodes(
                Paths.get(dumpLoc, SEMANTIC_CONTRACT_BYTECODES),
                state.getChild(ACCOUNTS),
                state.getChild(STORAGE),
                checkpoint);
        TopicDumpUtils.dumpMonoTopics(Paths.get(dumpLoc, SEMANTIC_TOPICS), state.getChild(TOPICS), checkpoint);
        dumpMonoScheduledTransactions(
                Paths.get(dumpLoc, SEMANTIC_SCHEDULED_TRANSACTIONS), state.getChild(SCHEDULE_TXS), checkpoint);
        dumpMonoTokenType(Paths.get(dumpLoc, SEMANTIC_TOKEN_TYPE), state.getChild(TOKENS), checkpoint);
        dumpMonoBlockInfo(
                Paths.get(dumpLoc, SEMANTIC_BLOCK),
                networkContext,
                state.getChild(RECORD_STREAM_RUNNING_HASH),
                checkpoint);
        StakingInfoDumpUtils.dumpMonoStakingInfo(
                Paths.get(dumpLoc, SEMANTIC_STAKING_INFO), state.getChild(STAKING_INFO), checkpoint);
        dumpMonoStakingRewards(Paths.get(dumpLoc, SEMANTIC_STAKING_REWARDS), networkContext, checkpoint);
        PayerRecordsDumpUtils.dumpMonoPayerRecords(
                Paths.get(dumpLoc, SEMANTIC_TXN_RECORD_QUEUE),
                state.getChild(PAYER_RECORDS_OR_CONSOLIDATED_FCQ),
                checkpoint);
        dumpMonoCongestion(Paths.get(dumpLoc, SEMANTIC_CONGESTION), networkContext, checkpoint);
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
