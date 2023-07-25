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

import static com.hedera.node.app.service.file.impl.FileServiceImpl.UPGRADE_DATA_KEY;
import static com.hedera.node.app.service.file.impl.FileServiceImpl.UPGRADE_FILE_KEY;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.ReadableUpgradeStore;
import com.hedera.node.app.spi.state.ReadableQueueState;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Files.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableUpgradeStoreImpl implements ReadableUpgradeStore {
    /** The underlying data storage class that holds the file data. */
    private final ReadableQueueState<Bytes> upgradeState;

    private final ReadableSingletonState<File> upgradeFileState;

    /**
     * Create a new {@link ReadableUpgradeStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableUpgradeStoreImpl(@NonNull final ReadableStates states) {
        this.upgradeState = Objects.requireNonNull(states.getQueue(UPGRADE_DATA_KEY));
        this.upgradeFileState = Objects.requireNonNull(states.getSingleton(UPGRADE_FILE_KEY));
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
    public File peek() {
        return upgradeFileState.get();
    }

    @Override
    @NonNull
    public Iterator<File> iterator() {
        return List.of(upgradeFileState.get()).iterator();
    }

    @Override
    @NonNull
    public Bytes getFull() throws IOException {
        ByteArrayOutputStream collector = new ByteArrayOutputStream();
        final var iterator = upgradeState.iterator();
        while (iterator.hasNext()) {
            final var file = iterator.next();
            collector.write(file.toByteArray());
        }
        return Bytes.wrap(collector.toByteArray());
    }
}
