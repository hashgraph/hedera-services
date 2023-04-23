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

package com.hedera.node.app.service.admin.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * A {@link WritableSpecialFileStore} is a store for special files that are persisted in the state.
 *
 * This class will be moved to network service. It should be deleted here once it is moved over.
 */
public class WritableSpecialFileStore {
    /** The underlying data storage class that holds the file data.
     * Can contain files 0.0.150 to 159 */
    private final WritableKVState<Long, byte[]> upgradeFilesById;

    /**
     * Create a new {@link WritableSpecialFileStore} instance.
     *
     * @param states The state to use.
     */
    public WritableSpecialFileStore(@NonNull final WritableStates states) {
        requireNonNull(states);
        this.upgradeFilesById = states.get(FreezeServiceImpl.UPGRADE_FILES_KEY);
    }

    /**
     * Persists a new upgrade file into the state
     *
     * @param fileId - the ID of the file to be persisted. Must be in the range 150 to 159.
     * @param upgradeFile - the file to be persisted.
     */
    public void put(@NonNull final Long fileId, @NonNull final byte[] upgradeFile) {
        if (fileId < 150 || fileId > 159) {
            throw new IllegalArgumentException("File ID must be in the range 150 to 159");
        }
        upgradeFilesById.put(fileId, upgradeFile);
    }

    /**
     * Returns the upgrade file bytes for the given fileId. If no such upgrade file exists,
     * returns {@code Optional.empty()}
     * @param fileId - the ID of the file to be persisted
     */
    public Optional<byte[]> get(final Long fileId) {
        final byte[] upgradeFile = upgradeFilesById.get(fileId);
        return Optional.ofNullable(upgradeFile);
    }

    /**
     * Get the number of upgrade files in the state.
     * @return the number of upgrade files in the state.
     */
    public long sizeOfState() {
        return upgradeFilesById.size();
    }

    public void commit() {
        // TODO - implement
    }
}
