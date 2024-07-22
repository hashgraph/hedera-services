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

package com.hedera.node.app.records.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A simple implementation of {@link BlockRecordInfo} that uses the objects stored in state ({@link BlockInfo} and
 * {@link RunningHashes}).
 */
public final class BlockRecordInfoImpl implements BlockRecordInfo {
    private final BlockInfo blockInfo;
    private final RunningHashes runningHashes;

    /**
     * Constructor of {@code BlockRecordInfoImpl}.
     *
     * @param blockInfo the {@link BlockInfo} object
     * @param runningHashes the {@link RunningHashes} object
     */
    public BlockRecordInfoImpl(@NonNull final BlockInfo blockInfo, @NonNull final RunningHashes runningHashes) {
        this.blockInfo = requireNonNull(blockInfo);
        this.runningHashes = requireNonNull(runningHashes);
    }

    /** {@inheritDoc} */
    @Override
    public Bytes prngSeed() {
        return runningHashes.nMinus3RunningHash();
    }

    /** {@inheritDoc} */
    @Override
    public long blockNo() {
        return blockInfo.lastBlockNumber() + 1;
    }

    @Override
    public @NonNull Timestamp blockTimestamp() {
        // There should always be a current block and a first consensus time within it
        return blockInfo.firstConsTimeOfCurrentBlockOrThrow();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(final long blockNo) {
        return BlockRecordInfoUtils.blockHashByBlockNumber(blockInfo, blockNo);
    }
}
