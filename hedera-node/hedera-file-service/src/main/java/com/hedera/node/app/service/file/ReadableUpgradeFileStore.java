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

package com.hedera.node.app.service.file;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.merkle.Schema;
import com.swirlds.state.spi.ReadableQueueState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Upgrade Files.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public interface ReadableUpgradeFileStore {

    /**
     * Gets the "state key" that uniquely identifies this {@link ReadableQueueState} within the {@link
     * Schema} which are scoped to the service implementation. The key is therefore not globally
     * unique, only unique within the service implementation itself.
     *
     * <p>The call is idempotent, always returning the same value. It must never return null.
     *
     * @return The state key. This will never be null, and will always be the same value for an
     *     instance of {@link ReadableQueueState}.
     */
    @NonNull
    String getStateKey();

    /**
     * Retrieves but does not remove the element at the head of the queue, or returns null if the queue is empty.
     *
     * @return The element at the head of the queue, or null if the queue is empty
     */
    @Nullable
    File peek(FileID fileID);

    /**
     * Gets the full contents of the file from state.
     * @return The full contents of the file
     * @throws IOException if the file cannot be read
     */
    Bytes getFull(FileID fileID) throws IOException;
}
