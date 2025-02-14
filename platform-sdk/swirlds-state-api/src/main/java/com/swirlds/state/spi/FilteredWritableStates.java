// SPDX-License-Identifier: Apache-2.0
package com.swirlds.state.spi;

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
    public FilteredWritableStates(@NonNull final WritableStates delegate, @NonNull final Set<String> stateKeys) {
        super(delegate, stateKeys);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @NonNull
    @Override
    public <K, V> WritableKVState<K, V> get(@NonNull String stateKey) {
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

    @NonNull
    @Override
    public <E> WritableQueueState<E> getQueue(@NonNull String stateKey) {
        Objects.requireNonNull(stateKey);
        if (!contains(stateKey)) {
            throw new IllegalArgumentException("Could not find queue state " + stateKey);
        }

        return delegate.getQueue(stateKey);
    }

    public WritableStates getDelegate() {
        return delegate;
    }
}
