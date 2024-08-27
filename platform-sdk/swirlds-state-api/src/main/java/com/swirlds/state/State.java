/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state;

import com.swirlds.common.FastCopyable;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The full state used of the app. The primary implementation is based on a merkle tree, and the data
 * structures provided by the hashgraph platform. But most of our code doesn't need to know that
 * detail, and are happy with just the API provided by this interface.
 */
public interface State extends FastCopyable {

    /**
     * Returns a {@link ReadableStates} for the given named service. If such a service doesn't
     * exist, an empty {@link ReadableStates} is returned.
     *
     * @param serviceName The name of the service.
     * @return A collection of {@link ReadableKVState} instances belonging to the service.
     */
    @NonNull
    ReadableStates getReadableStates(@NonNull String serviceName);

    /**
     * Returns a {@link WritableStates} for the given named service. If such a service doesn't
     * exist, an empty {@link WritableStates} is returned.
     *
     * @param serviceName The name of the service.
     * @return A collection of {@link WritableKVState} instance belonging to the service.
     */
    @NonNull
    WritableStates getWritableStates(@NonNull String serviceName);

    /**
     * Registers a listener to be notified on each commit if the {@link WritableStates} created by this {@link State}
     * are marked as {@link CommittableWritableStates}. Implementations need not support unregistering listeners, as
     * there is no real case that a client would want to be notified of only some commits made to the state.
     *
     * @param listener The listener to be notified.
     * @throws UnsupportedOperationException if the state does not support listeners.
     */
    default void registerCommitListener(@NonNull final StateChangeListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    default State copy() {
        throw new UnsupportedOperationException();
    }
}
