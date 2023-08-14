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

import static com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl.UPGRADE_FILE_HASH_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.service.networkadmin.ReadableUpgradeStore;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * Implementation of {@link ReadableUpgradeStore}
 */
public class ReadableUpgradeStoreImpl implements ReadableUpgradeStore {
    /** The underlying data storage class that holds the prepared update file hash.
     * May be null if no prepared update file has been set. */
    private final ReadableSingletonState<ProtoBytes> updateFileHash;

    /**
     * Create a new {@link ReadableUpgradeStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableUpgradeStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.updateFileHash = states.getSingleton(UPGRADE_FILE_HASH_KEY);
    }

    @Override
    @NonNull
    public Optional<Bytes> updateFileHash() {
        ProtoBytes hash = updateFileHash.get();
        return (hash == null || hash.value() == null ? Optional.empty() : Optional.of(hash.value()));
    }
}
