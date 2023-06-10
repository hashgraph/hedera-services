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

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with freeze states.
 */
// This is a temporary location for this class. It will be moved to FileService.
// @todo('Issue #6856')
public class WritableSpecialFileStore extends ReadableSpecialFileStoreImpl {
    /** The underlying data storage class that holds the prepared update file number. */
    private final WritableSingletonState<FileID> preparedUpdateFileIDState;
    /** The underlying data storage class that holds the prepared update file hash. */
    private final WritableSingletonState<Bytes> preparedUpdateFileHashState;

    /**
     * Create a new {@link WritableSpecialFileStore} instance.
     *
     * @param states The state to use.
     */
    public WritableSpecialFileStore(@NonNull final WritableStates states) {
        super(states);
        requireNonNull(states);
        preparedUpdateFileIDState = states.getSingleton(FreezeServiceImpl.PREPARED_UPGRADE_FILEID_KEY);
        preparedUpdateFileHashState = states.getSingleton(FreezeServiceImpl.PREPARED_UPGRADE_FILE_HASH_KEY);
    }

    /**
     * Sets the prepared update file ID.
     *
     * @param preparedUpdateFileID The prepared update file ID to set.
     *                             If null, clears the prepared update file ID.
     */
    public void preparedUpdateFileID(@Nullable final FileID preparedUpdateFileID) {
        preparedUpdateFileIDState.put(preparedUpdateFileID);
    }

    /**
     * Sets the prepared update file hash.
     *
     * @param preparedUpdateFileHash The prepared update file hash to set.
     *                               If null, clears the prepared update file hash.
     */
    public void preparedUpdateFileHash(@Nullable final Bytes preparedUpdateFileHash) {
        preparedUpdateFileHashState.put(preparedUpdateFileHash);
    }

    @Override
    @Nullable
    public FileID preparedUpdateFileID() {
        return preparedUpdateFileIDState.get();
    }

    @Override
    @Nullable
    public Bytes preparedUpdateFileHash() {
        return preparedUpdateFileHashState.get();
    }
}
