/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state;

import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The full state used by Hedera. The primary implementation is based on a merkle tree, and the data
 * structures provided by the hashgraph platform. But most of our code doesn't need to know that
 * detail, and are happy with just the API provided by this interface.
 */
public interface HederaState {
    /**
     * Creates a {@link ReadableStates} for the given named service. If such a service doesn't
     * exist, an empty {@link ReadableStates} is returned.
     *
     * @param serviceName The name of the service.
     * @return A collection of {@link ReadableKVState} instances belonging to the service.
     */
    @NonNull
    ReadableStates createReadableStates(@NonNull String serviceName);

    /**
     * Creates a {@link WritableStates} for the given named service. If such a service doesn't
     * exist, an empty {@link WritableStates} is returned.
     *
     * @param serviceName The name of the service.
     * @return A collection of {@link WritableKVState} instance belonging to the service.
     */
    @NonNull
    WritableStates createWritableStates(@NonNull String serviceName);

    /**
     * Gets the {@link RecordCache}, used for storing all in-flight and recently sent records,
     * which is needed for deduplication purposes.
     *
     * @return A non-null {@link RecordCache}.
     */
    @NonNull
    RecordCache getRecordCache();
}
