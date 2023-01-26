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
package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WritableSingletonStateBase<T> extends ReadableSingletonStateBase<T>
        implements WritableSingletonState<T> {
    private final Consumer<T> backingStoreMutator;
    private boolean modified;
    private T value;

    public WritableSingletonStateBase(
            @NonNull final String stateKey,
            @NonNull final Supplier<T> backingStoreAccessor,
            @NonNull final Consumer<T> backingStoreMutator) {
        super(stateKey, backingStoreAccessor);
        this.backingStoreMutator = Objects.requireNonNull(backingStoreMutator);
    }

    @Override
    public T get() {
        // Possible pattern: "put" and then "get". In this case, "read" should be false!! Otherwise
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
    public boolean modified() {
        return modified;
    }

    public void commit() {
        if (modified) {
            backingStoreMutator.accept(value);
        }
    }

    @Override
    public void reset() {
        this.modified = false;
        this.value = null;
        super.reset();
    }
}
