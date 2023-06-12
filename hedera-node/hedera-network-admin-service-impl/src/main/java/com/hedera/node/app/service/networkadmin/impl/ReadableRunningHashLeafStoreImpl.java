/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl;

import static com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl.RUNNING_HASHES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.networkadmin.ReadableRunningHashLeafStore;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The underlying data storage class that holds the record running hash leaf data.
 */
public class ReadableRunningHashLeafStoreImpl implements ReadableRunningHashLeafStore {

    private final ReadableSingletonState<RecordsRunningHashLeaf> runningHashState;

    /**
     * Create a new {@link ReadableRunningHashLeafStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableRunningHashLeafStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.runningHashState = states.getSingleton(RUNNING_HASHES_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public RunningHash getNMinusThreeRunningHash() {
        return requireNonNull(runningHashState.get()).getNMinus3RunningHash();
    }

    @NonNull
    @Override
    public Hash getRunningHash() {
        return requireNonNull(runningHashState.get()).getRunningHash().getHash();
    }
}
