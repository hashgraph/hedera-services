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

package com.hedera.node.app.service.file.impl;

import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for working with Files.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail. This
 * class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableFileStoreImpl extends ReadableFileStoreImpl {
    /** The underlying data storage class that holds the file data. */
    private final WritableKVState<EntityNum, File> filesState;

    /**
     * Create a new {@link WritableFileStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public WritableFileStoreImpl(@NonNull final WritableStates states) {
        super(states);
        this.filesState = Objects.requireNonNull(states.get(BLOBS_KEY));
    }

    /**
     * Persists a new {@link File} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param file - the file to be mapped onto a new {@link MerkleTopic} and persisted.
     */
    public void put(@NonNull final File file) {
        filesState.put(EntityNum.fromLong(Objects.requireNonNull(file).fileNumber()), file);
    }

    /**
     * Commits the changes to the underlying data storage. TODO: Not sure if the stores have
     * responsibility of committing the changes. This might change in the future.
     */
    public void commit() {
        if (filesState instanceof WritableKVStateBase) ((WritableKVStateBase) filesState).commit();
    }

    /**
     * Returns the {@link File} with the given number. If no such file exists, returns {@code
     * Optional.empty()}
     *
     * @param fileNum - the number of the file to be retrieved.
     */
    public @Nullable Optional<File> get(final long fileNum) {
        final var file = filesState.get(EntityNum.fromLong(fileNum));
        return Optional.ofNullable(file);
    }

    /**
     * Returns the {@link File} with the given number using {@link WritableKVState}. If no such file
     * exists, returns {@code Optional.empty()}
     *
     * @param fileNum - the number of the file to be retrieved.
     */
    public @Nullable Optional<File> getForModify(final long fileNum) {
        final var file = filesState.getForModify(EntityNum.fromLong(fileNum));
        return Optional.ofNullable(file);
    }

    /**
     * Returns the number of files in the state.
     *
     * @return the number of files in the state.
     */
    public long sizeOfState() {
        return filesState.size();
    }

    /**
     * Returns the set of files modified in existing state.
     *
     * @return the set of files modified in existing state
     */
    public @NonNull Set<EntityNum> modifiedFiles() {
        return filesState.modifiedKeys();
    }

    /**
     * remove the file from the state.
     *
     * @param fileNum - the number of the file to be removed from state.
     */
    public void removeFile(final long fileNum) {
        filesState.remove(EntityNum.fromLong(fileNum));
    }
}
