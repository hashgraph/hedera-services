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

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.workflows.handle.record.StreamManager;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.exceptions.NotImplementedException;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.HederaState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An implementation of {@link StreamManager} primarily responsible for managing state ({@link RunningHashes} and
 * {@link BlockInfo}), and delegating to a {@link StreamProducer} for writing to the stream file, hashing,
 * and performing other duties, possibly on background threads. All APIs of {@link StreamManager} can only be
 * called from the "handle" thread!
 *
 * todo: implement this class in another ticket
 */
@Singleton
public final class BlockStreamManagerImpl extends AbstractBlockManagerImpl {
    private static final Logger logger = LogManager.getLogger(BlockStreamManagerImpl.class);

    /**
     * Construct BlockRecordManager
     *
     * @param configProvider The configuration provider
     * @param state The current hedera state
     * @param streamProducer The stream file producer
     */
    @Inject
    public BlockStreamManagerImpl(
            @NonNull final ConfigProvider configProvider,
            @NonNull final HederaState state,
            @NonNull final BlockStreamProducer streamProducer) {
        super(configProvider, state, streamProducer);
    }

    // =================================================================================================================
    // BlockRecordManager implementation

    /**
     * {@inheritDoc}
     */
    public boolean startUserTransaction(
            @NonNull final Instant consensusTime,
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState) {
        throw new NotImplementedException();
    }

    // ========================================================================================================
    // OngoingBlockInfo Implementation

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Bytes lastBlockHash() {
        throw new NotImplementedException();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(final long blockNo) {
        throw new NotImplementedException();
    }
}
