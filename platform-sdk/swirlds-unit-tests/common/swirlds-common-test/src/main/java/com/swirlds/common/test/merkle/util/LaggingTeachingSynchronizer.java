/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.merkle.util;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.TeachingSynchronizer;
import com.swirlds.common.merkle.synchronization.internal.Lesson;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.threading.pool.StandardWorkGroup;

/**
 * A {@link TeachingSynchronizer} with simulated latency.
 */
public class LaggingTeachingSynchronizer extends TeachingSynchronizer {

    private final int latencyMilliseconds;

    /**
     * Create a new teaching synchronizer with simulated latency.
     */
    public LaggingTeachingSynchronizer(
            final MerkleDataInputStream in,
            final MerkleDataOutputStream out,
            final MerkleNode root,
            final int latencyMilliseconds,
            final Runnable breakConnection) {
        super(getStaticThreadManager(), in, out, root, breakConnection);
        this.latencyMilliseconds = latencyMilliseconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <T> AsyncOutputStream<Lesson<T>> buildOutputStream(
            final StandardWorkGroup workGroup, final SerializableDataOutputStream out) {
        return new LaggingAsyncOutputStream<>(out, workGroup, latencyMilliseconds);
    }
}
