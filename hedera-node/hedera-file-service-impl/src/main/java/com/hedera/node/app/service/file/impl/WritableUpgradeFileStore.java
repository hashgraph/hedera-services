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

package com.hedera.node.app.service.file.impl;

import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;
import static com.hedera.node.app.service.file.impl.FileServiceImpl.UPGRADE_DATA_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.spi.WritableKVState;
import com.swirlds.platform.state.spi.WritableQueueState;
import com.swirlds.platform.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Provides write methods for modifying underlying data storage mechanisms for working with Files.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail. This
 * class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableUpgradeFileStore extends ReadableUpgradeFileStoreImpl {

    private final WritableStates states;

    private final WritableKVState<FileID, File> writableUpgradeFileState;

    private static final Predicate<ProtoBytes> TRUE_PREDICATE = new TruePredicate();

    /**
     * Create a new {@link WritableUpgradeFileStore} instance.
     *
     * @param states The state to use.
     */
    public WritableUpgradeFileStore(@NonNull final WritableStates states) {
        super(states);
        this.states = requireNonNull(states);
        writableUpgradeFileState = requireNonNull(states.get(BLOBS_KEY));
    }

    public void add(@NonNull final File file) {
        requireNonNull(file);
        writableUpgradeFileState.put(file.fileIdOrThrow(), file);
    }

    public void addUpgradeContent(@NonNull final FileID fileID, Bytes content) {
        final WritableQueueState<ProtoBytes> upgradeState = getUpgradeState(fileID);
        upgradeState.add(new ProtoBytes(content));
    }

    public void append(@NonNull final Bytes bytes, @NonNull final FileID fileID) {
        requireNonNull(bytes);
        requireNonNull(fileID);
        final WritableQueueState<ProtoBytes> upgradeState = getUpgradeState(fileID);
        upgradeState.add(new ProtoBytes(bytes));
    }

    @SuppressWarnings({"StatementWithEmptyBody"})
    public void resetFileContents(@NonNull final FileID fileID) {
        final WritableQueueState<ProtoBytes> upgradeState = getUpgradeState(fileID);
        while (upgradeState.removeIf(TRUE_PREDICATE) != null)
            ;
    }

    private static class TruePredicate implements Predicate<ProtoBytes> {
        @Override
        public boolean test(final ProtoBytes file) {
            return true;
        }
    }

    @NonNull
    private WritableQueueState<ProtoBytes> getUpgradeState(@NonNull FileID fileID) {
        return Objects.requireNonNull(states.getQueue(UPGRADE_DATA_KEY.formatted(fileID)));
    }
}
