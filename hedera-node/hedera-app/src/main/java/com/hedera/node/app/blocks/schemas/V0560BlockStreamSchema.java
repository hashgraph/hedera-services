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

package com.hedera.node.app.blocks.schemas;

import static com.hedera.node.app.blocks.impl.BlockImplUtils.appendHash;
import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.blockHashByBlockNumber;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Defines the schema for state with two notable properties:
 * <ol>
 *     <li>It is needed for a new or reconnected node to construct the next block exactly as will
 *     nodes already in the network.</li>
 *     <li>It is derived from the block stream, and hence the natural provenance of the same service
 *     that is managing and producing blocks.</li>
 * </ol>
 * <p>
 * The particular items with these properties are,
 * <ol>
 *     <li>The <b>number of the last completed block</b>, which each node must increment in the next block.</li>
 *     <li>The <b>first consensus time of the last finished block</b>, for comparison with the consensus
 *     time at the start of the current block. Depending on the elapsed period between these times,
 *     the network may deterministically choose to purge expired entities, adjust node stakes and
 *     reward rates, or take other actions.</li>
 *     <li>The <b>last four values of the input block item running hash</b>, used to generate pseudorandom
 *     values for the {@link com.hedera.hapi.node.base.HederaFunctionality#UTIL_PRNG} operation.</li>
 *     <li>The <b>trailing 256 block hashes</b>, used to implement the EVM {@code BLOCKHASH} opcode.</li>
 * </ol>
 */
public class V0560BlockStreamSchema extends Schema {
    private static final String SHARED_BLOCK_RECORD_INFO = "SHARED_BLOCK_RECORD_INFO";
    private static final String SHARED_RUNNING_HASHES = "SHARED_RUNNING_HASHES";

    public static final String BLOCK_STREAM_INFO_KEY = "BLOCK_STREAM_INFO";

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(56).patch(0).build();

    private final Consumer<Bytes> migratedBlockHashConsumer;

    /**
     * Schema constructor.
     */
    public V0560BlockStreamSchema(@NonNull final Consumer<Bytes> migratedBlockHashConsumer) {
        super(VERSION);
        this.migratedBlockHashConsumer = requireNonNull(migratedBlockHashConsumer);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate(@NonNull final Configuration config) {
        return Set.of(StateDefinition.singleton(BLOCK_STREAM_INFO_KEY, BlockStreamInfo.PROTOBUF));
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        final var state = ctx.newStates().getSingleton(BLOCK_STREAM_INFO_KEY);
        if (ctx.previousVersion() == null) {
            state.put(BlockStreamInfo.DEFAULT);
        } else {
            final var blockStreamInfo = state.get();
            // This will be null if the previous version is before 0.56.0
            if (blockStreamInfo == null) {
                final BlockInfo blockInfo =
                        (BlockInfo) requireNonNull(ctx.sharedValues().get(SHARED_BLOCK_RECORD_INFO));
                final RunningHashes runningHashes =
                        (RunningHashes) requireNonNull(ctx.sharedValues().get(SHARED_RUNNING_HASHES));
                // Note that it is impossible to put the hash of block N into a state that includes
                // the state changes from block N, because the hash of block N is a function of exactly
                // those state changes---so act of putting the hash in state would change it; as a result,
                // the correct way to migrate from a record stream-based state is to save its last
                // block hash as the last block hash of the new state; and create a BlockStreamInfo with
                // the remaining block hashes
                final var lastBlockHash =
                        requireNonNull(blockHashByBlockNumber(blockInfo, blockInfo.lastBlockNumber()));
                migratedBlockHashConsumer.accept(lastBlockHash);
                final var trailingBlockHashes = blockInfo
                        .blockHashes()
                        .slice(lastBlockHash.length(), blockInfo.blockHashes().length() - lastBlockHash.length());
                state.put(BlockStreamInfo.newBuilder()
                        .blockTime(blockInfo.firstConsTimeOfLastBlock())
                        .blockNumber(blockInfo.lastBlockNumber())
                        .trailingBlockHashes(trailingBlockHashes)
                        .trailingOutputHashes(appendedHashes(runningHashes))
                        .blockEndTime(blockInfo.consTimeOfLastHandledTxn())
                        .postUpgradeWorkDone(false)
                        .creationSoftwareVersion(ctx.previousVersion())
                        .lastIntervalProcessTime(blockInfo.consTimeOfLastHandledTxn())
                        .lastHandleTime(blockInfo.consTimeOfLastHandledTxn())
                        .build());
            }
        }
    }

    private Bytes appendedHashes(final RunningHashes runningHashes) {
        var hashes = Bytes.EMPTY;
        hashes = appendHash(runningHashes.nMinus3RunningHash(), hashes, 4);
        hashes = appendHash(runningHashes.nMinus2RunningHash(), hashes, 4);
        hashes = appendHash(runningHashes.nMinus1RunningHash(), hashes, 4);
        return appendHash(runningHashes.runningHash(), hashes, 4);
    }
}
