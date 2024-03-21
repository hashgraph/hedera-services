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
import static com.hedera.node.app.service.file.impl.FileServiceImpl.UPGRADE_FILE_KEY;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.spi.ReadableKVState;
import com.swirlds.platform.state.spi.ReadableQueueState;
import com.swirlds.platform.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Files.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableUpgradeFileStoreImpl implements ReadableUpgradeFileStore {

    /** The underlying data storage class that holds the file data. */
    private final ReadableStates states;

    private final ReadableKVState<FileID, File> upgradeFileState;

    /**
     * Create a new {@link ReadableUpgradeFileStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableUpgradeFileStoreImpl(@NonNull final ReadableStates states) {
        this.states = Objects.requireNonNull(states);
        upgradeFileState = Objects.requireNonNull(states.get(BLOBS_KEY));
    }

    @Override
    @NonNull
    public String getStateKey() {
        return UPGRADE_DATA_KEY;
    }

    @NonNull
    public String getFileStateKey() {
        return UPGRADE_FILE_KEY;
    }

    @Override
    @Nullable
    public File peek(final FileID fileID) {
        return upgradeFileState.get(fileID);
    }

    @Override
    @NonNull
    public Bytes getFull(final FileID fileID) throws IOException {
        ByteArrayOutputStream collector = new ByteArrayOutputStream();
        final ReadableQueueState<ProtoBytes> upgradeState =
                Objects.requireNonNull(states.getQueue(UPGRADE_DATA_KEY.formatted(fileID)));
        final Bytes fullContents;
        if (upgradeFileState.get(fileID) != null) {
            final var iterator = upgradeState.iterator();
            while (iterator.hasNext()) {
                final var file = iterator.next();
                collector.write(file.value().toByteArray());
            }
            fullContents = Bytes.wrap(collector.toByteArray());
        } else {
            fullContents = Bytes.EMPTY;
        }
        return fullContents;
    }
}
