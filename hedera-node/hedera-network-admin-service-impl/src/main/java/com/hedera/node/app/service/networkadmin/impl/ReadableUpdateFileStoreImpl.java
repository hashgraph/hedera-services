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
import static com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl.UPGRADE_FILE_ID_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.node.app.service.networkadmin.ReadableUpdateFileStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * Implementation of {@link ReadableUpdateFileStore}
 */
// This is a temporary location for this class. It will be moved to FileService.
// @todo('Issue #6856')
public class ReadableUpdateFileStoreImpl implements ReadableUpdateFileStore {
    /** The underlying data storage class that holds the file data. */
    private final ReadableKVState<FileID, byte[]> freezeFilesById;

    /** The underlying data storage class that holds the prepared update file number.
     * If null, no prepared update file has been set. */
    private final ReadableSingletonState<FileID> updateFileID;
    /** The underlying data storage class that holds the prepared update file hash.
     * May be null if no prepared update file has been set. */
    private final ReadableSingletonState<Bytes> updateFileHash;

    /**
     * Create a new {@link ReadableUpdateFileStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableUpdateFileStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.freezeFilesById = states.get(FreezeServiceImpl.UPGRADE_FILES_KEY);
        this.updateFileID = states.getSingleton(UPGRADE_FILE_ID_KEY);
        this.updateFileHash = states.getSingleton(UPGRADE_FILE_HASH_KEY);
    }

    @Override
    @NonNull
    public Optional<byte[]> get(@NonNull FileID fileId) {
        requireNonNull(fileId);
        final var file = freezeFilesById.get(fileId);
        return Optional.ofNullable(file);
    }

    @Override
    @NonNull
    public Optional<FileID> updateFileID() {
        FileID fileId = updateFileID.get();
        return (fileId == null ? Optional.empty() : Optional.of(fileId));
    }

    @Override
    @NonNull
    public Optional<Bytes> updateFileHash() {
        Bytes hash = updateFileHash.get();
        return (hash == null ? Optional.empty() : Optional.of(hash));
    }
}
