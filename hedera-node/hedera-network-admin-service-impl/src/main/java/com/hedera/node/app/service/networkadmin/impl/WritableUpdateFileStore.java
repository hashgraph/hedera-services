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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with freeze states.
 */
// This is a temporary location for this class. It will be moved to FileService.
// @todo('Issue #6856')
public class WritableUpdateFileStore extends ReadableUpdateFileStoreImpl {
    /** The underlying data storage class that holds the update file number. */
    private final WritableSingletonState<FileID> updateFileID;
    /** The underlying data storage class that holds the update file hash. */
    private final WritableSingletonState<Bytes> updateFileHash;

    /**
     * Create a new {@link WritableUpdateFileStore} instance.
     *
     * @param states The state to use.
     */
    public WritableUpdateFileStore(@NonNull final WritableStates states) {
        super(states);
        requireNonNull(states);
        updateFileID = states.getSingleton(FreezeServiceImpl.UPGRADE_FILE_ID_KEY);
        updateFileHash = states.getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY);
    }

    /**
     * Sets or clears the update file ID.
     *
     * @param updateFileID The update file ID to set. If null, clears the update file ID.
     */
    public void updateFileID(@Nullable final FileID updateFileID) {
        this.updateFileID.put(updateFileID);
    }

    /**
     * Sets or clears the update file hash.
     *
     * @param updateFileHash The update file hash to set. If null, clears the update file hash.
     */
    public void updateFileHash(@Nullable final Bytes updateFileHash) {
        this.updateFileHash.put(updateFileHash);
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
        Bytes fileHash = updateFileHash.get();
        return (fileHash == null ? Optional.empty() : Optional.of(fileHash));
    }
}
