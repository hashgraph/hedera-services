/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An implementation of {@link ReadableStates} that delegates to another instance, and filters the
 * available set of states.
 */
public class FilteredReadableStates implements ReadableStates {
    /** The {@link ReadableStates} to delegate to */
    private final ReadableStates delegate;
    /** The set of states to honor in {@link #delegate}. */
    private final Set<String> stateKeys;

    /**
     * Create a new instance.
     *
     * @param delegate The instance to delegate to
     * @param stateKeys The set of keys in {@code delegate} to expose
     */
    public FilteredReadableStates(
            @NonNull final ReadableStates delegate, @NonNull final Set<String> stateKeys) {
        this.delegate = Objects.requireNonNull(delegate);

        // Only include those state keys that are actually in the underlying delegate
        final var set = new HashSet<String>(stateKeys.size());
        for (final var stateKey : stateKeys) {
            if (delegate.contains(stateKey)) {
                set.add(stateKey);
            }
        }

        this.stateKeys = Collections.unmodifiableSet(set);
    }

    @NonNull
    @Override
    public <K extends Comparable<K>, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
        Objects.requireNonNull(stateKey);
        if (!stateKeys.contains(stateKey)) {
            throw new IllegalArgumentException("Could not find state " + stateKey);
        }

        return delegate.get(stateKey);
    }

    @Override
    public boolean contains(@NonNull String stateKey) {
        return stateKeys.contains(stateKey) && delegate.contains(stateKey);
    }

    @NonNull
    @Override
    public Set<String> stateKeys() {
        return stateKeys;
    }
}
