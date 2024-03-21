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

package com.swirlds.platform.state.spi;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A convenient base class for mutable singletons.
 *
 * @param <T> The type
 */
public class WritableSingletonStateBase<T> extends ReadableSingletonStateBase<T> implements WritableSingletonState<T> {
    private final Consumer<T> backingStoreMutator;
    private boolean modified;
    private T value;

    /**
     * Creates a new instance.
     *
     * @param stateKey The state key for this instance
     * @param backingStoreAccessor A {@link Supplier} that provides access to the value in the
     *     backing store.
     * @param backingStoreMutator A {@link Consumer} for mutating the value in the backing store.
     */
    public WritableSingletonStateBase(
            @NonNull final String stateKey,
            @NonNull final Supplier<T> backingStoreAccessor,
            @NonNull final Consumer<T> backingStoreMutator) {
        super(stateKey, backingStoreAccessor);
        this.backingStoreMutator = Objects.requireNonNull(backingStoreMutator);
    }

    @Override
    public T get() {
        // Possible pattern: "put" and then "get". In this case, "read" should be false!! Otherwise,
        // we invalidate tx when we don't need to
        if (modified) {
            return value;
        }

        value = super.get();
        return value;
    }

    @Override
    public void put(T value) {
        this.modified = true;
        this.value = value;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    /**
     * Flushes all changes into the underlying data store. This method should <strong>ONLY</strong>
     * be called by the code that created the {@link WritableSingletonStateBase} instance or owns
     * it. Don't cast and commit unless you own the instance!
     */
    public void commit() {
        if (modified) {
            backingStoreMutator.accept(value);
        }
        reset();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears the "modified" and cached value, in addition to the super implementation
     */
    @Override
    public void reset() {
        this.modified = false;
        this.value = null;
        super.reset();
    }
}
