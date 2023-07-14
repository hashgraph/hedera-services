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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.spi.state.WritableQueueState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Predicate;

/**
 * Provides write methods for modifying underlying data storage mechanisms for working with Files.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail. This
 * class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableUpgradeStore extends ReadableUpgradeStoreImpl implements WritableQueueState<File> {
    /** The underlying data storage class that holds the file data. */
    private final WritableQueueState<File> writableUpgradeState;

    private static final Predicate<File> TRUE_PREDICATE = new TruePredicate();

    /**
     * Create a new {@link WritableUpgradeStore} instance.
     *
     * @param states The state to use.
     */
    public WritableUpgradeStore(@NonNull final WritableStates states) {
        super(states);
        this.writableUpgradeState = requireNonNull(states.getQueue(getStateKey()));
    }

    public void add(@NonNull File file) {
        requireNonNull(file);
        writableUpgradeState.add(file);
    }

    public void resetFileContents() {
        while (writableUpgradeState.removeIf(TRUE_PREDICATE) != null)
            ;
    }

    @Nullable
    public File removeIf(@NonNull Predicate<File> predicate) {
        return writableUpgradeState.removeIf(predicate);
    }

    private static class TruePredicate implements Predicate<File> {
        @Override
        public boolean test(File file) {
            return true;
        }
    }
}
