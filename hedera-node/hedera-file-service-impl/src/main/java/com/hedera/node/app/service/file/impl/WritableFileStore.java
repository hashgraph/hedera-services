/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.file.impl.schemas.V0490FileSchema.BLOBS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.spi.ids.WritableEntityCounters;
import com.hedera.node.app.spi.validation.EntityType;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for working with Files.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail. This
 * class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableFileStore extends ReadableFileStoreImpl {
    /** The underlying data storage class that holds the file data. */
    private final WritableKVState<FileID, File> filesState;

    private final WritableEntityCounters entityCounters;

    /**
     * Create a new {@link WritableFileStore} instance.
     *
     * @param states The state to use.
     */
    public WritableFileStore(
            @NonNull final WritableStates states, @NonNull final WritableEntityCounters entityCounters) {
        super(states, entityCounters);
        this.filesState = requireNonNull(states.get(BLOBS_KEY));
        this.entityCounters = entityCounters;
    }

    /**
     * Persists an updated {@link File} into the state, as well as exporting its ID to the transaction
     * receipt. If a file with the same ID already exists, it will be overwritten.
     *
     * @param file - the file to be persisted.
     */
    public void put(@NonNull final File file) {
        filesState.put(requireNonNull(file).fileId(), file);
    }

    /**
     * Persists a new {@link File} into the state, as well as exporting its ID to the transaction.
     * Also increments the entity counter for the file.
     * @param file - the file to be persisted.
     */
    public void putNew(@NonNull final File file) {
        put(file);
        entityCounters.incrementEntityTypeCount(EntityType.FILE);
    }

    /**
     * Returns the {@link File} with the given number. If no such file exists, returns {@code
     * Optional.empty()}
     *
     * @param fileId - the id of the file to be retrieved.
     */
    public @NonNull Optional<File> get(final FileID fileId) {
        final var file = filesState.get(fileId);
        return Optional.ofNullable(file);
    }

    /**
     * Returns the {@link File} with the given number using {@link WritableKVState}. If no such file
     * exists, returns {@code Optional.empty()}
     *
     * @param fileId - the id of the file to be retrieved.
     */
    public @NonNull Optional<File> getForModify(final FileID fileId) {
        final var file = filesState.getForModify(fileId);
        return Optional.ofNullable(file);
    }

    /**
     * Returns the set of files modified in existing state.
     *
     * @return the set of files modified in existing state
     */
    public @NonNull Set<FileID> modifiedFiles() {
        return filesState.modifiedKeys();
    }

    /**
     * remove the file from the state.
     *
     * @param fileId - the id of the file to be removed from state.
     */
    public void removeFile(final FileID fileId) {
        filesState.remove(fileId);
        entityCounters.decrementEntityTypeCounter(EntityType.FILE);
    }
}
