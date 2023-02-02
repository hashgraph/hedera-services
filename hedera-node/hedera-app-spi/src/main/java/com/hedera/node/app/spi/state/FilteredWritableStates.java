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
package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

/**
 * An implementation of {@link WritableStates} that delegates to another instance, and filters the
 * available set of states.
 */
public class FilteredWritableStates extends FilteredReadableStates implements WritableStates {
    /** The {@link WritableStates} to delegate to */
    private final WritableStates delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The instance to delegate to
     * @param stateKeys The set of keys in {@code delegate} to expose
     */
    public FilteredWritableStates(
            @NonNull final WritableStates delegate, @NonNull final Set<String> stateKeys) {
        super(delegate, stateKeys);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @NonNull
    @Override
    public <K extends Comparable<K>, V> WritableKVState<K, V> get(@NonNull String stateKey) {
        Objects.requireNonNull(stateKey);
        if (!contains(stateKey)) {
            throw new IllegalArgumentException("Could not find k/v state " + stateKey);
        }

        return delegate.get(stateKey);
    }

    @NonNull
    @Override
    public <T> WritableSingletonState<T> getSingleton(@NonNull String stateKey) {
        Objects.requireNonNull(stateKey);
        if (!contains(stateKey)) {
            throw new IllegalArgumentException("Could not find singleton state " + stateKey);
        }

        return delegate.getSingleton(stateKey);
    }
}
