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

import com.hedera.hapi.node.base.FileID;
import com.hedera.node.app.service.networkadmin.ReadableSpecialFileStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link ReadableSpecialFileStore}
 */
// This is a temporary location for this class. It will be moved to FileService.
// @todo('Issue #6856')
public class ReadableSpecialFileStoreImpl implements ReadableSpecialFileStore {
    /** The underlying data storage class that holds the file data. */
    private final ReadableKVState<FileID, byte[]> freezeFilesById;

    /** The underlying data storage class that holds the prepared update file number.
     * If null, no prepared update file has been set. */
    private final ReadableSingletonState<FileID> preparedUpdateFileID;
    /** The underlying data storage class that holds the prepared update file hash.
     * May be null if no prepared update file has been set. */
    private final ReadableSingletonState<Bytes> preparedUpdateFileHash;

    /**
     * Create a new {@link ReadableSpecialFileStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableSpecialFileStoreImpl(@NonNull final ReadableStates states) {
        Objects.requireNonNull(states);
        this.freezeFilesById = states.get(FreezeServiceImpl.UPGRADE_FILES_KEY);
        this.preparedUpdateFileID = states.getSingleton("preparedUpdateFileID");
        this.preparedUpdateFileHash = states.getSingleton("preparedUpdateFileHash");
    }

    @Override
    @NonNull
    public Optional<byte[]> get(FileID fileId) {
        final var file = freezeFilesById.get(fileId);
        return Optional.ofNullable(file);
    }

    @Override
    @Nullable
    public FileID preparedUpdateFileID() {
        return preparedUpdateFileID.get();
    }

    @Override
    @Nullable
    public Bytes preparedUpdateFileHash() {
        return preparedUpdateFileHash.get();
    }
}
