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

package com.hedera.node.app.state;

import static java.util.Objects.requireNonNull;

import com.swirlds.state.HederaState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.StateChangesListener;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link HederaState} that wraps another {@link HederaState} and provides a {@link #commit()} method that
 * commits all modifications to the underlying state.
 */
public class WrappedHederaState implements HederaState {

    private final HederaState delegate;
    private final Map<String, WrappedWritableStates> writableStatesMap = new HashMap<>();

    /**
     * Constructs a {@link WrappedHederaState} that wraps the given {@link HederaState}.
     *
     * @param delegate the {@link HederaState} to wrap
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public WrappedHederaState(@NonNull final HederaState delegate) {
        this.delegate = requireNonNull(delegate, "delegate must not be null");
    }

    public void register(@NonNull StateChangesListener listener) {
        Objects.requireNonNull(listener);
        for (final var writableStates : writableStatesMap.values()) {
            writableStates.register(listener);
        }
    }

    /**
     * Returns {@code true} if the state of this {@link WrappedHederaState} has been modified.
     *
     * @return {@code true}, if the state has been modified; otherwise {@code false}
     */
    public boolean isModified() {
        for (final var writableStates : writableStatesMap.values()) {
            if (writableStates.isModified()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * The {@link ReadableStates} instances returned from this method are based on the {@link WritableStates} instances
     * for the same service name. This means that any modifications to the {@link WritableStates} will be reflected
     * in the {@link ReadableStates} instances returned from this method.
     * <p>
     * Unlike other {@link HederaState} implementations, the returned {@link ReadableStates} of this implementation
     * must only be used in the handle workflow.
     */
    @Override
    @NonNull
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return new ReadonlyStatesWrapper(getWritableStates(serviceName));
    }

    /**
     * {@inheritDoc}
     *
     * This method guarantees that the same {@link WritableStates} instance is returned for the same {@code serviceName}
     * to ensure all modifications to a {@link WritableStates} are kept together.
     */
    @Override
    @NonNull
    public WritableStates getWritableStates(@NonNull String serviceName) {
        return writableStatesMap.computeIfAbsent(
                serviceName, s -> new WrappedWritableStates(delegate.getWritableStates(s)));
    }

    /**
     * Writes all modifications to the underlying {@link HederaState}.
     */
    public void commit() {
        for (final var writableStates : writableStatesMap.values()) {
            writableStates.commit();
        }
    }
}
