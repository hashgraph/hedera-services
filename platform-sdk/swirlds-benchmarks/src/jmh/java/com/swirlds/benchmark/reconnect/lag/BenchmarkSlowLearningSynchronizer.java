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

package com.swirlds.benchmark.reconnect.lag;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.LearningSynchronizer;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.internal.QueryResponse;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.threading.pool.StandardWorkGroup;

/**
 * A {@link LearningSynchronizer} with simulated delay.
 */
public class BenchmarkSlowLearningSynchronizer extends LearningSynchronizer {

    private final long delayStorageMicroseconds;
    private final long delayNetworkMicroseconds;

    /**
     * Create a new learning synchronizer with simulated latency.
     */
    public BenchmarkSlowLearningSynchronizer(
            final MerkleDataInputStream in,
            final MerkleDataOutputStream out,
            final MerkleNode root,
            final long delayStorageMicroseconds,
            final long delayNetworkMicroseconds,
            final Runnable breakConnection,
            final ReconnectConfig reconnectConfig) {
        super(getStaticThreadManager(), in, out, root, breakConnection, reconnectConfig);

        this.delayStorageMicroseconds = delayStorageMicroseconds;
        this.delayNetworkMicroseconds = delayNetworkMicroseconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AsyncOutputStream<QueryResponse> buildOutputStream(
            final StandardWorkGroup workGroup, final SerializableDataOutputStream out) {
        return new BenchmarkSlowAsyncOutputStream<>(
                out, workGroup, delayStorageMicroseconds, delayNetworkMicroseconds, reconnectConfig);
    }
}
