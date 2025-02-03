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

package com.swirlds.state.spi;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A convenient base class for mutable singletons.
 *
 * @param <T> The type
 */
public abstract class WritableSingletonStateBase<T> extends ReadableSingletonStateBase<T> implements WritableSingletonState<T> {

    /** Modified value buffered in this mutable state */
    private T modification;

    /** A list of listeners to be notified of changes to the state */
    private final List<SingletonChangeListener<T>> listeners = new ArrayList<>();

    /**
     * Creates a new instance.
     *
     * @param stateKey The state key for this instance
     */
    public WritableSingletonStateBase(@NonNull final String serviceName, @NonNull final String stateKey) {
        super(serviceName, stateKey);
    }

    /**
     * Register a listener to be notified of changes to the state on {@link #commit()}. We do not support unregistering
     * a listener, as the lifecycle of a {@link WritableSingletonState} is scoped to the set of mutations made to a
     * state in a round; and there is no case where an application would only want to be notified of a subset of those
     * changes.
     *
     * @param listener the listener to register
     */
    public void registerListener(@NonNull final SingletonChangeListener<T> listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public T get() {
        // If there is a modification, then we've already done a "put" or "remove"
        // and should return based on the modification
        if (isModified()) {
            return modification;
        } else {
            return super.get();
        }
    }

    @Override
    public void put(T value) {
        this.modification = value;
    }

    @Override
    public boolean isModified() {
        return modification != null;
    }

    /**
     * Flushes all changes into the underlying data store. This method should <strong>ONLY</strong>
     * be called by the code that created the {@link WritableSingletonStateBase} instance or owns
     * it. Don't cast and commit unless you own the instance!
     */
    public void commit() {
        if (modification == null) {
            removeFromDataSource();
        } else {
            putIntoDataSource(modification);
        }

        listeners.forEach(l -> l.singletonUpdateChange(modification));

        reset();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears the "modified" and cached value, in addition to the super implementation
     */
    @Override
    public void reset() {
        this.modification = null;
        super.reset();
    }

    /**
     * Puts the given value into the underlying data source.
     *
     * @param value value to put
     */
    protected abstract void putIntoDataSource(@NonNull T value);

    /**
     * Removes the value related to this singleton from the underlying data source.
     */
    protected abstract void removeFromDataSource();
}
