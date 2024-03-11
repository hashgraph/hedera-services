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

package com.hedera.node.app.service.networkadmin.impl;

import static com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl.FREEZE_TIME_KEY;
import static com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl.UPGRADE_FILE_HASH_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.service.networkadmin.ReadableFreezeStore;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Default implementation of {@link ReadableFreezeStore}.
 */
public class ReadableFreezeStoreImpl implements ReadableFreezeStore {
    /** The underlying data storage classes that hold the freeze state data. */
    private final ReadableSingletonState<Timestamp> freezeTime;

    /** The underlying data storage class that holds the prepared update file hash.
     * May be null if no prepared update file has been set. */
    private final ReadableSingletonState<ProtoBytes> updateFileHash;

    /**
     * Create a new {@link ReadableFreezeStoreImpl} instance.
     * @param states the state to use
     */
    public ReadableFreezeStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.freezeTime = states.getSingleton(FREEZE_TIME_KEY);
        this.updateFileHash = states.getSingleton(UPGRADE_FILE_HASH_KEY);
    }

    @Override
    @Nullable
    public Timestamp freezeTime() {
        return freezeTime.get();
    }

    @Override
    @Nullable
    public Bytes updateFileHash() {
        ProtoBytes hash = updateFileHash.get();
        return (hash == null ? null : hash.value());
    }
}
