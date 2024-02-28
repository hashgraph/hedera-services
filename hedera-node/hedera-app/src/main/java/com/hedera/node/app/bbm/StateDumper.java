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

package com.hedera.node.app.bbm;

import static com.hedera.node.app.bbm.accounts.AccountDumpUtils.dumpModAccounts;
import static com.hedera.node.app.bbm.accounts.AccountDumpUtils.dumpMonoAccounts;
import static com.hedera.node.app.bbm.associations.TokenAssociationsDumpUtils.dumpModTokenRelations;
import static com.hedera.node.app.bbm.associations.TokenAssociationsDumpUtils.dumpMonoTokenRelations;
import static com.hedera.node.app.bbm.contracts.ContractBytecodesDumpUtils.dumpModContractBytecodes;
import static com.hedera.node.app.bbm.files.FilesDumpUtils.dumpModFiles;
import static com.hedera.node.app.bbm.files.FilesDumpUtils.dumpMonoFiles;
import static com.hedera.node.app.bbm.nfts.UniqueTokenDumpUtils.dumpModUniqueTokens;
import static com.hedera.node.app.bbm.nfts.UniqueTokenDumpUtils.dumpMonoUniqueTokens;
import static com.hedera.node.app.records.BlockRecordService.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.ACCOUNTS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.NETWORK_CTX;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.STORAGE;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.TOKEN_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.UNIQUE_TOKENS;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.NFTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKEN_RELS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.bbm.contracts.ContractBytecodesDumpUtils;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.state.InitialModServiceContractSchema;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.state.merkle.singleton.SingletonNode;
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

    public static void dumpMonoChildrenFrom(
            @NonNull final MerkleHederaState state, @NonNull final DumpCheckpoint checkpoint) {
        final MerkleNetworkContext networkContext = state.getChild(NETWORK_CTX);
        final var dumpLoc = getExtantDumpLoc("mono", networkContext.consensusTimeOfLastHandledTxn());
        dumpMonoUniqueTokens(Paths.get(dumpLoc, SEMANTIC_UNIQUE_TOKENS), state.getChild(UNIQUE_TOKENS), checkpoint);
        dumpMonoTokenRelations(
                Paths.get(dumpLoc, SEMANTIC_TOKEN_RELATIONS), state.getChild(TOKEN_ASSOCIATIONS), checkpoint);
        dumpMonoFiles(Paths.get(dumpLoc, SEMANTIC_FILES), state.getChild(STORAGE), checkpoint);
        dumpMonoAccounts(Paths.get(dumpLoc, SEMANTIC_TOKEN_RELATIONS), state.getChild(ACCOUNTS), checkpoint);

        ContractBytecodesDumpUtils.dumpMonoContractBytecodes(
                Paths.get(dumpLoc, SEMANTIC_CONTRACT_BYTECODES),
                state.getChild(ACCOUNTS),
                state.getChild(STORAGE),
                checkpoint);
    }

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

        final VirtualMap<OnDiskKey<TokenAssociation>, OnDiskValue<TokenRelation>> tokenRelations =
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
